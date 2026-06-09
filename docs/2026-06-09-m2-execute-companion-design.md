# M2 Companion Change — Backend Execute-by-`approval_id` (Java)

> Java/SpringBoot companion change for the parent **M2 Approved Action Workflow**.
> Status: Implemented in Java scope | Date: 2026-06-09
> Parent Java spec: [2026-06-05-ecommerce-mcp-server-spec.md](2026-06-05-ecommerce-mcp-server-spec.md)
> Parent agent design: [../../ecommerce-agent/docs/2026-05-25-ecommerce-agent-design.md](../../ecommerce-agent/docs/2026-05-25-ecommerce-agent-design.md) §5.2
> Roadmap: [../../ecommerce-agent/docs/2026-06-09-product-roadmap.md](../../ecommerce-agent/docs/2026-06-09-product-roadmap.md) M2 + §5.4, risk R13
> Python companion spec: [../../ecommerce-agent/docs/2026-06-09-m2-approved-action-workflow-design.md](../../ecommerce-agent/docs/2026-06-09-m2-approved-action-workflow-design.md)

## 1. Why This Change

The parent Java spec (§3, §4) shipped the original HITL model: the three write tools
(`purchase_order_create`, `purchase_order_receive`, `order_update`) are **agent-reachable
`@McpTool`s** that take `approval_id` + the operation params, and the service layer **re-hashes the
incoming params** against the stored approval before executing.

The M2 redesign (parent design §5.2, "propose → approve → **backend execute**") moves to a
**deterministic backend executor keyed by `approval_id`** in which the LLM never re-issues write
params and holds no tool capable of executing a write. This requires a Java companion change:

1. Add an authenticated **`POST /approvals/{approval_id}/execute`** endpoint that loads the stored
   canonical payload and executes from it.
2. **Remove the three write `@McpTool`s** from the agent-reachable MCP surface.
3. **Extend the `approval_record` lifecycle** with `execution_result` and the `invalidated` /
   `failed` states.

This is a deliberately reopened slice of already-tested approval enforcement (risk R13): treat it as
its own reviewed change and re-run the negative-case matrix (§7).

## 2. Scope

This document scopes **only** the Java/`ecommerce-mcp-server` change. The agent, FastAPI
orchestration, MongoDB conversation thread, and the per-session event stream are in the Python
companion spec. The approve/reject/read endpoints (§4.4 of the parent Java spec) already exist and
are unchanged except that **approve still only flips status — it does not execute** (now explicit,
because execute is a separate endpoint).

Net result of M2: the agent-reachable write surface is **`request_approval` plus reads only**. Every
business write is performed by the backend executor from a stored, human-approved payload.

## 3. Schema Change — `approval_record`

Extend the existing table (parent Java spec §2.2). Additive columns + two new status values; the
existing binding/audit columns are unchanged.

```sql
ALTER TABLE approval_record
  ADD COLUMN execution_result JSON NULL,        -- deterministic result of the executed write (PO id, new status, inventory delta, ...)
  ADD COLUMN executed_at      DATETIME NULL;     -- set when execution completes (success or failed)
```

`status` value set becomes:

```
pending → approved → consumed      -- happy path: approved, then executed successfully
                   → invalidated   -- live preconditions drifted at execute time; a fresh approval is required
                   → failed        -- passed validation but the write itself errored; execution_result holds the error for audit/retry
        → rejected | expired       -- unchanged
```

- `consumed` keeps its meaning ("the authorized write executed exactly once") and now also carries
  `execution_result` + `executed_at`.
- `invalidated` and `failed` are **terminal** for that approval; the operator must obtain a new
  approval to retry the business intent.

## 4. Deterministic Executor + Execute Endpoint

### 4.1 Endpoint

```
POST /approvals/{approval_id}/execute     (authenticated; human/FastAPI surface, NOT an MCP tool)
```

- **Authentication** identical to the approve/reject endpoints (parent §4.4). The implemented scope
  reuses the **shared `X-Service-Token`** service-auth model, carrying the trusted actor via
  `X-User-Id`/`X-Session-Id`. A **distinct** human/approval credential — cryptographically separating
  the approval transition from the agent tool path (parent §4.2) — is a **deferred M4 hardening**
  item (meaningful once multi-user authenticated console users exist), not implemented in this Java
  scope. This endpoint is **not** in the agent's tool list either way.
- **Request body:** none required — the `approval_id` path param is the sole input. The endpoint
  never accepts operation params; the stored payload is authoritative.
- **Response:** the parsed `execution_result` object (and final status). Idempotent replays return
  the same body.

### 4.2 Executor contract

`ApprovalExecutor.execute(approvalId, trustedActor)`:

1. Load the `approval_record`; lazily mark `expired` if past `expires_at`.
2. **Idempotent short-circuit:** if `status = consumed`, return the stored `execution_result`
   (success replay). If `status = failed`, return the stored failure result. If
   `rejected`/`expired`/`invalidated`, return a typed terminal error.
3. Otherwise require `status = approved`; run the **full parent §4.3 validation contract** against
   the **stored** `operation_payload`:
   - exists + approved, hash integrity (re-canonicalize the stored payload, compare to
     `operation_hash`), tool binding, **actor/session binding to the trusted caller**, not expired.
   - **Live precondition recheck:** re-read the DB rows the payload pinned (order/PO status, product
     active/cost, supplier existence, etc.). If they drifted → set `status = invalidated`, store a
     reason in `execution_result`, return a typed "stale approval" error. **No write.**
4. **Claim under row lock (double-spend guard):** open a transaction and `SELECT … FOR UPDATE` the
   row, re-confirming `status='approved'` (equivalently, a conditional `UPDATE … WHERE
   status='approved'`). The lock serializes concurrent executes — exactly one caller proceeds; a
   loser, on acquiring the lock, sees a non-`approved` status and falls back to step 2 (returns the
   stored result). Hold this transaction across step 5.
5. **Execute from the stored payload** in that transaction: dispatch on `operation_type`/`tool_name`
   to the existing service method (`PurchaseOrderService.create` / `.receive`, `OrderService.update`),
   passing params read from the stored `operation_payload` — never from the request.
   - **Success** → set `status='consumed'`, `consumed_at = executed_at = now`, store
     `execution_result` (e.g. `{po_id, status, inventory_delta}`), commit. `consumed` therefore
     always means "executed successfully, result recorded."
   - **Execution error** → roll back the business write (no partial effect), then in a separate
     committed status-only write set `status='failed'`, `executed_at=now`, store the error in
     `execution_result`. Return a typed error. `failed` is terminal — a fresh approval is required to
     retry; the approval never lands in a `consumed` state without a real effect.
   - **Infrastructure/database error** (`DataAccessException`) → roll back and return HTTP `503`
     with `reasonCode="infrastructure_error"` and `retryable=true`; leave the approval `approved`
     so the caller can retry the same `approval_id`.

> The service methods that perform the writes are **retained** from the parent spec; only their
> *entry point* changes — they are invoked by `ApprovalExecutor` keyed by `approval_id`, not by an
> `@McpTool` that takes agent-supplied params.

### 4.3 Why this is safe

The agent's tool surface no longer contains any write tool, so the model **cannot** execute or
re-issue a write — it can only `request_approval`. The hash now guards record integrity (the agent
never re-submits params, so the old "change params between two calls" attack is gone), and the live
precondition recheck guards against stale writes.

## 5. Remove Write `@McpTool`s

Delete the `@McpTool` annotations / tool methods for `purchase_order_create`,
`purchase_order_receive`, and `order_update` so Spring AI no longer advertises them on the MCP
surface. The underlying `PurchaseOrderService` / `OrderService` methods stay (now called only by
`ApprovalExecutor`). `request_approval` remains an `@McpTool` (it only creates a `pending` record;
it performs no business write).

After this change, `GET`-ting the MCP tool list must show: the read tools, `get_statistics`, and
`request_approval` — **and none of the three writes.** The Python `mcp_health` probe asserts this
(parent agent app already tracks `blocked_write_or_approval_tools`).

## 6. Idempotency & Recovery

- **Idempotent execute** (§4.2 steps 2 & 4): replaying a `consumed` approval returns the stored
  `execution_result`; concurrent executes resolve to one winner.
- **Stale `approved`-but-unexecuted** (R8 limbo): recovered by simply re-calling
  `POST /approvals/{id}/execute` — the endpoint is the recovery path. FastAPI uses bounded retry.
- **Retryable infrastructure error:** `POST /approvals/{id}/execute` returns `503` with
  `reasonCode="infrastructure_error"` and `retryable=true`; the row remains `approved`, so the same
  approval can be retried.
- **Sweeper:** optional for the MVP; the path is fail-closed (an unexecuted `approved` row does
  nothing until execute is called, and expires on its TTL). A scheduled sweep that marks long-stale
  `approved` rows `expired` may be added later but is not required.

## 7. Negative-Case Matrix (re-run after the change — R13)

Each must produce a typed error and **no business write**, and be auditable:

1. Wrong/tampered hash (stored payload re-canonicalizes to a different hash) → reject.
2. Live preconditions drifted between approve and execute (e.g. PO already received, product
   deactivated) → `invalidated`, fresh approval required.
3. Replay across a different session/actor than the record binds → reject.
4. Expired approval → `expired`, reject.
5. Tampered `operation_detail` vs `operation_payload` (card ≠ hashed payload) → reject (covered by
   server-side rendering + hash).
6. Double-execute / concurrent execute of one approval → exactly one effect; the loser returns the
   stored result.
7. Execute while `pending` (never approved) → reject.
8. Execute after `reject` → reject.
9. Execute when the business write errors post-validation → `failed`, no partial effect, result
   stored.

## 8. Tests

- Extend the existing hermetic Testcontainers-MySQL SpringBoot suite (parent §2, §6).
- Executor unit/slice tests for the happy path, idempotent replay, and every §7 negative case.
- A test asserting the MCP tool list **excludes** the three writes and **includes**
  `request_approval` + reads.
- Keep the existing `request_approval` / approve / reject / read tests green.

## 9. Checklist (Java scope)

- [x] `ALTER TABLE approval_record` — `execution_result`, `executed_at`; new `invalidated`/`failed`
      statuses wired into the status enum/validation.
- [x] `ApprovalExecutor` (§4.2) — dispatch by `operation_type` to existing service methods from the
      stored payload, in a transaction, with the atomic one-time-use claim.
- [x] `POST /approvals/{approval_id}/execute` controller endpoint (authenticated, non-MCP), idempotent.
- [x] Remove `purchase_order_create` / `purchase_order_receive` / `order_update` `@McpTool`s; retain
      their service methods.
- [x] Re-run the §7 negative-case matrix; add executor + tool-list tests.
- [x] Update parent Java spec §3/§4/§7 to reflect execute-by-`approval_id` and the removed write
      tools (or supersede with a pointer to this companion spec).

## 10. Cross-Repo Coordination

The Python companion spec consumes this change at one seam: FastAPI calls `approve` → `execute`
against these endpoints with the shared `X-Service-Token` plus the acting user's
`X-User-Id`/`X-Session-Id` (a distinct human/approval credential is deferred to M4 — §4.1). Keep the
two specs cross-linked. This Java slice is landed (§9 checklist complete, §7 matrix green); the
Python orchestration can wire against it.
