# E-Commerce MCP Server (SpringBoot) — Design Spec

> The Java/SpringBoot service for the E-Commerce AI Assistant.
> Status: Java scope implemented (read tools + `request_approval`, backend execute-by-approval,
> HITL enforcement, `get_statistics`); pending
> cross-project E2E from the Python caller (§7) | Date: 2026-06-05
> Parent spec: [2026-05-25-ecommerce-agent-design.md](../../docs/superpowers/specs/2026-05-25-ecommerce-agent-design.md)

This document scopes **only the Java project** (`ecommerce-mcp-server`). The Agent
orchestration, FastAPI web layer, sandbox, memory system, and frontend are covered
in the parent spec and are out of scope here. Cross-boundary context is included
where it constrains the Java design (tool contracts, approval flow).

End-to-end verification from `MultiServerMCPClient` is a **parent/Python project**
responsibility. This Java project is the server under test: it should expose the
MCP tools, enforce approvals, and provide SpringBoot tests/smoke checks, but it
should not own the Agent caller, LangGraph checkpoint, or FastAPI HITL loop.

## 1. Role & Boundary

This service is the **MCP server** for the assistant: it exposes e-commerce business
capability as MCP tools and owns the database. It is the single trust boundary for
all reads and writes to `ecommerce_db`.

**Java owns:**
- Business logic (query / create / update over products, orders, inventory, users, suppliers)
- The MySQL database and all SQL
- The business-tool **MCP surface** (Spring AI `@McpTool` methods): reads + `request_approval`
- **HITL approval enforcement** — approved writes execute only through the backend
  `/approvals/{approval_id}/execute` endpoint from stored server payload

**Java does NOT own (lives in the Python/Agent side):**
- Agent orchestration, routing, context management (DeepAgents + LangGraph)
- The web/SSE/WebSocket layer that talks to the frontend (FastAPI)
- Sandbox code execution (`run_code`, file parsing, report generation)
- Visualization (ModelScope MCP, 26 chart tools)
- The HITL *interrupt/resume* loop (LangGraph checkpoint) — Java only creates and
  validates approval records; the pause/resume around them is the Agent's job.

```
DeepAgents Agent (MultiServerMCPClient)
  ├──► SpringBoot MCP Server  ◄── this spec
  │       @McpTool read tools + request_approval
  │       REST approve/reject/read/execute + approval enforcement
  │       Service → Mapper → MySQL (ecommerce_db)
  ├──► ModelScope MCP (charts)
  └──► Python MCP (sandbox / run_code)
```

The Agent connects over a network MCP transport (streamable-HTTP / SSE), so the
server must use the `webmvc` MCP starter, not stdio.

## 2. Database Design

Database: `ecommerce_db` (MySQL). Schema and seed data already exist in
`src/main/resources/schema.sql` and `data.sql`.

Test scope is hermetic: SpringBoot tests activate the `test` profile and use Testcontainers
MySQL via `src/test/resources/application-test.properties`, then initialize the temporary
database from the same `schema.sql` and `data.sql`. Tests require a Docker daemon, but must not
require a developer's local port-3306 MySQL instance or a pre-seeded `ecommerce_db`.

### 2.1 Business Tables

Two distinct order documents, modeled separately as in real ops systems: **customer sales
orders** (`orders`) and **supplier purchase orders** (`purchase_order`). Sales orders are
**read-mostly** — queried for analytics, with fulfillment-status changes (e.g. shipped,
cancelled) allowed only through the approved `order_update` write path. Supplier purchase
orders are the procurement write path.

| Table | Purpose | Key Fields |
|-------|---------|------------|
| product | Product catalog | product_id, name, category, price, cost, status |
| orders | Customer sales orders | order_id, user_id, total_amount, status, created_at, paid_at |
| order_item | Sales order line items | item_id, order_id, product_id, quantity, unit_price |
| user | Customer accounts | user_id, username, phone, email, level, registered_at |
| inventory | Stock management | product_id, quantity, safety_stock, warehouse |
| supplier | Supplier info | supplier_id, name, contact, rating, lead_time |
| review | Product reviews | review_id, user_id, product_id, rating, content |
| purchase_order | Supplier purchase orders (procurement) | po_id, supplier_id, status, total_cost, created_at, received_at |
| purchase_order_item | PO line items | po_item_id, po_id, product_id, quantity, unit_cost |

`purchase_order.status` lifecycle: `placed` → `received` (inventory incremented) / `cancelled`.
A PO row is only ever inserted by the backend executor after a matching approval has been approved,
so it starts at `placed` — the pending/approved gate lives in `approval_record` (§4), not on the
PO itself. `unit_cost` (paid to supplier) is tracked separately from `product.price` (charged to
customers).

Relationships:
- order_item.order_id → orders.order_id
- order_item.product_id → product.product_id
- orders.user_id → user.user_id
- inventory.product_id → product.product_id
- review.product_id → product.product_id, review.user_id → user.user_id
- purchase_order.supplier_id → supplier.supplier_id
- purchase_order_item.po_id → purchase_order.po_id
- purchase_order_item.product_id → product.product_id

Sample data (seeded): 50+ products across 5+ categories, 200+ customer orders over 6 months,
30+ users with varying levels, 10+ suppliers. The `purchase_order` / `purchase_order_item`
tables and `approval_record` are in `schema.sql`, with a few historical received POs seeded for
analytics.

### 2.2 Approval Table

Backs the server-enforced HITL flow (§4).

```sql
CREATE TABLE approval_record (
  approval_id    VARCHAR(36) PRIMARY KEY,
  operation_hash VARCHAR(64) NOT NULL,           -- canonical hash of operation_payload (§4.3)
  tool_name      VARCHAR(40) NOT NULL,           -- the write tool this approval authorizes
  operation_type VARCHAR(20) NOT NULL,           -- create | modify | receive | ...
  operation_payload JSON NOT NULL,               -- canonical authorization payload: params + server preconditions (hashed)
  operation_detail  JSON NOT NULL,               -- server-rendered card/diff/impact (from canonical payload + DB; never Agent prose)
  user_id        BIGINT NOT NULL,                -- actor the approval is bound to
  session_id     VARCHAR(64) NOT NULL,           -- session the approval is bound to
  status         VARCHAR(20) NOT NULL DEFAULT 'pending',  -- pending | approved | consumed | invalidated | failed | rejected | expired
  rejection_reason TEXT NULL,                    -- human-supplied reason when rejected
  execution_result JSON NULL,                    -- deterministic backend execution result or terminal error
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at     DATETIME NOT NULL,
  rejected_at    DATETIME NULL,                  -- set when rejected
  consumed_at    DATETIME NULL,                  -- set when the write executes (one-time use)
  executed_at    DATETIME NULL,                  -- set when execute completes (success, invalidated, failed)
  KEY idx_status (status)
);
```

The extra columns (`tool_name`, `session_id`, `consumed_at`, `execution_result`, `executed_at`,
and the `consumed` / `invalidated` / `failed` statuses) exist to enforce tool binding,
actor/session binding, one-time use, and auditable backend execution — see §4.3.

## 3. MCP Tools

Read capability and approval requests are exposed as Spring AI `@McpTool` methods. Spring AI derives the
MCP tool schema from the method signature plus `@McpToolParam` metadata, and the
`spring-ai-starter-mcp-server-webmvc` starter serves them over streamable-HTTP/SSE
for the DeepAgents client to discover and call.

Tool names follow a consistent `{domain}_{action}` convention. Every tool below is a SpringBoot
`@McpTool`. Visualization and sandbox/`run_code` tools that the sub-agents also use are **not**
served here — they belong to the ModelScope and Python MCP servers (see parent spec §8.2).

| MCP Tool | Type | Service Method | Description |
|----------|------|----------------|-------------|
| product_query | Read | ProductService.page | Query product catalog with pagination and category filter |
| product_search | Read | ProductService.search | Search products by name (fuzzy) |
| order_query | Read | OrderService.searchDetails | Search customer sales orders + items with date/product filters |
| inventory_query | Read | InventoryService.query | Query inventory levels for products/warehouses |
| inventory_low_stock | Read | InventoryService.findLowStockItems | List items below safety stock |
| user_query | Read | UserService.query | Query customer accounts (by name/level/id) |
| supplier_query | Read | SupplierService.search | Search suppliers by name (fuzzy) |
| supplier_top | Read | SupplierService.findTopSuppliers | List top suppliers by rating and lead time |
| purchase_order_query | Read | PurchaseOrderService.query | Query supplier purchase orders |
| request_approval | Write | ApprovalService.create | Take structured operation params; Java builds the canonical authorization payload (params + server preconditions), hashes it, derives the human-facing card server-side, stores a pending record, returns approval_id |
| get_statistics | Read | StatsService.get | Return aggregation-first overview stats for orders, inventory, products, purchase orders, and top-selling products. Top sellers are ranked by **realized sales** — `order_item` rows whose order is `paid`/`shipped`/`completed`, excluding `cancelled`/`pending` — so the figure reflects revenue actually earned, not gross intake |

The former write tools `purchase_order_create`, `purchase_order_receive`, and `order_update` are
not advertised on the MCP surface. They remain as backend service operations and execute only from
`POST /approvals/{approval_id}/execute` using the stored `operation_payload`.

**Post-MVP write tools** (documented so the approval framework anticipates them, not built yet):
`order_cancel` (High-risk batch — "cancel pending orders >30 days") and any delete tool, both
with the double-confirm flow.

### 3.0 Read-Tool Design Rules

To keep query results from bloating the Agent's context (parent spec §2.2):

- **Pagination is mandatory** on list tools — `current`/`size` with sane defaults (e.g. `size=10`)
  and a hard cap (e.g. `size ≤ 100`). Never return an unbounded result set.
- **Aggregation-first** — push counts/sums/group-bys into `get_statistics` and SQL rather than
  returning raw rows for the Agent to total up.
- Return only the columns the tool's purpose needs; trim large free-text fields (e.g. review
  `content`) unless explicitly requested.

### 3.1 Tool Definition Example

```java
// tool/ProductTools.java
@Component
public class ProductTools {

    private final ProductService productService;

    @McpTool(name = "product_query",
          description = "分页查询商品列表，支持按名称模糊查询和分类筛选。")
    public PageResult<ProductDTO> queryProducts(
            @McpToolParam(required = false, description = "分类") String category,
            @McpToolParam(required = false, description = "名称模糊匹配") String name,
            @McpToolParam(description = "页码") int current,
            @McpToolParam(description = "每页数量") int size) {
        return productService.page(category, name, current, size);
    }
}
```

Read tools return data straight back to the Agent's context. The tool layer stays
thin — it delegates to services and does no business logic of its own.

## 4. HITL Approval Enforcement

Write operations are **server-enforced**, not prompt-based. The Agent can request an approval, but
it cannot call any write tool. Business writes execute only when the authenticated REST caller
invokes `/approvals/{approval_id}/execute`, and Java dispatches from the stored canonical payload.
This is the core security responsibility of the Java project.

### 4.1 Risk Levels

| Operation | Risk | Requirement | MVP |
|-----------|------|-------------|-----|
| Query | Low | None | ✅ |
| Create (`purchase_order_create`) | Medium | Single confirm, show full PO details | ✅ |
| Modify (`purchase_order_receive`, `order_update`) | Medium | Single confirm, show diff/impact | ✅ |
| Delete / Batch | High | Show impact scope + double confirm | ⬜ post-MVP |

Delete/Batch is documented so the framework anticipates it, but no MVP write tool performs it.

### 4.2 Flow (Java's perspective)

Trusted identity is **not** part of the Agent-authored payload. For MCP calls, Java derives
`user_id` and `session_id` from trusted request metadata (for example a service-authenticated
FastAPI header/JWT/session binding), never from `@McpToolParam` fields or `operation_payload`.
The Agent can request an operation, but it cannot choose which actor/session the approval binds to.

Current implementation: `TrustedActorFilter` requires a service-authenticated
`X-Service-Token` and then derives the actor from `X-User-Id` / `X-Session-Id`, storing it in
`TrustedActorContext` for the controller and MCP tool layers. The parent FastAPI caller owns the
real user session and is responsible for forwarding these trusted headers; the Agent never sees
or fills them as tool parameters.

**Gateway trust boundary:** FastAPI authenticates human operators with its own HttpOnly session
cookie, resolves the real operator identity, and forwards the corresponding `spring_user_id` as
`X-User-Id` plus the conversation id as `X-Session-Id`. Spring trusts only callers with a valid
`X-Service-Token`; it treats `X-User-Id` / `X-Session-Id` as the trusted actor binding for that
service-authenticated request. Approval records are owned by the `(userId, sessionId)` pair, and
every read, approve/reject transition, and execute attempt is scoped through the same
`isSameActor` check. The FastAPI gateway no longer sends a fixed `"1"` user id; it forwards the
authenticated operator's Spring user id on MCP tools and approval REST calls.

Deployment note: the token used to call MCP tools should not be exposed to Agent-generated code
or sandbox networking. For production, prefer separate credentials for the MCP tool surface and
the human approval REST surface (or a human JWT for `/approvals/**`) so the approval transition is
cryptographically distinct from the Agent tool path.

```
Agent calls request_approval(tool_name, operation_params)   ← structured params only, no prose/identity
  → ApprovalService resolves trusted {user_id, session_id} from request metadata
  → ApprovalService reads the live DB rows needed to evaluate the operation
  → builds canonical operation_payload = operation_params + server-derived preconditions/snapshot
    (e.g. current order/PO status, supplier/product existence, product active status/cost, normalized unit cost)
  → computes operation_hash from that canonical operation_payload (§4.3)
  → DERIVES the human-facing card (summary / diff / impact) from the canonical payload + DB state
    (e.g. resolves product names, current inventory, supplier, computed total_cost)
  → inserts a pending approval_record bound to {user_id, session_id, tool_name},
    storing both operation_payload and the server-rendered operation_detail
  → returns {approval_id, operation_hash}
  ── (human approves/rejects via the §4.4 endpoint; Agent paused on a LangGraph checkpoint) ──
  → on approve, approval_record.status = approved
FastAPI/human path calls POST /approvals/{approval_id}/execute
  → ApprovalExecutor loads the stored operation_payload; request body contains no operation params
  → executor re-derives trusted identity + current DB preconditions and runs the validation contract (§4.3)
    → valid   → execute the write, set status=consumed + consumed_at + executed_at + execution_result
    → stale   → set status=invalidated + execution_result; require a fresh approval
    → failed  → roll back business write, set status=failed + execution_result
    → infra   → roll back, return HTTP 503 {reasonCode: infrastructure_error, retryable: true};
                keep status=approved so the same approval_id can be retried
```

**The Agent never authors what the human sees.** `request_approval` takes only structured
parameters; Java renders the approval card from those parameters and live DB state. This closes
the gap where an Agent could display "receive 5 items" while the hashed payload receives 500 —
the card and the hash are derived from the *same* server-side payload.

**State freshness:** the approval pins the DB facts that are required to authorize the write,
not every field shown on the card. If an order/PO state or product cost/status changes between
approval creation and backend execution, the executor must reject with a stale-approval error
and require a new approval. Volatile display-only fields, such as current inventory quantity on
`purchase_order_create`, can remain in `operation_detail` without entering the hashed payload.

### 4.3 Enforcement Contract (required checks)

Before any approved write executes, the backend executor MUST verify **all** of:

1. **Exists & approved** — `approval_id` exists and `status = approved` (not pending/rejected/expired/consumed).
2. **Hash integrity + live precondition match** — re-canonicalize the stored `operation_payload`
   and verify `operation_hash`; then rebuild the canonical payload from the stored operation params
   plus current DB preconditions/snapshot. Its hash must equal the stored hash. This rejects DB
   tampering and stale approvals after relevant DB state changes.
3. **Tool binding** — the stored `tool_name` equals the operation dispatched by the executor
   (an order approval can't authorize a PO).
4. **Actor/session binding** — the trusted request `user_id` **and** `session_id` match the record's. An approval issued in one session/actor cannot be replayed by another.
5. **Not expired** — `now < expires_at`.
6. **One-time use** — `consumed_at IS NULL`; execute under row lock / conditional update so
   concurrent execute calls can't double-spend one approval. Replays of `consumed` return the
   stored `execution_result`.

Any failure → typed error, no write. All write attempts (allowed and rejected) should be auditable.

**Why `operation_hash`:** it pins the approval to the exact operation and DB facts shown to the
user. If relevant DB state changes after approval, the hash won't match and execution is
invalidated. The Agent never re-submits write params in M2, so post-approval param tampering is
removed from the tool surface.

**Canonical serialization:** the hash MUST be computed over a deterministic serialization
(sorted keys, fixed number/date formats, normalized whitespace) so the same logical payload
always hashes identically on both the `request_approval` and the write call. Include only stable,
domain-relevant preconditions in the payload; do not include volatile display-only fields such as
localized labels or formatting. SHA-256 is fine.

**Expiry policy:** approvals default to a 15-minute TTL. Expired `pending` / `approved` rows are
rejected and lazily marked `expired` when read, approved/rejected, or executed. A sweep job is not
required for the MVP because the enforcement path is fail-closed.

### 4.4 Approval Transition Endpoint (non-agent)

The approve/reject transition is an **authenticated REST endpoint, not an MCP tool**. The Agent
must never be able to approve its own request — only a human (via the frontend → FastAPI) can.

```
POST /approvals/{approval_id}/approve     (auth required)
POST /approvals/{approval_id}/reject      body: { reason }   (auth required; reason is persisted)
POST /approvals/{approval_id}/execute     (auth required; no body; executes stored payload)
```

Contract:
- **Authentication required** — caller is the human operator (e.g. via the FastAPI/frontend
  session), carrying the `user_id`/`session_id`. These are NOT MCP tools and are not in the
  Agent's tool list, so the Agent cannot reach them.
- **Actor binding** — the approving user/session must match the `approval_record` it transitions
  (you can't approve another session's request).
- **Valid transitions only** — approve/reject are `pending → approved` / `pending → rejected`;
  execute is `approved → consumed|invalidated|failed`. Approving does not execute the write — it
  only flips status so the backend execute endpoint can pass §4.3.
- **Execute response** — returns the final status plus `executionResult` as a parsed JSON object
  rather than a JSON-encoded string. Retryable database/infrastructure errors return HTTP `503`
  with `reasonCode = infrastructure_error` and `retryable = true`, leaving the approval `approved`.
- A read endpoint (`GET /approvals/{id}` or list pending for a session) backs the frontend
  approval card; it returns the **server-rendered** `operation_detail`, never Agent prose.

This is the one REST surface the agent path genuinely requires (see §5).

## 5. Project Structure

```
com.ecommerce.agent/
├── tool/           # @McpTool MCP tools (exposed via Spring AI)
├── controller/     # Approval REST endpoints (§4.4) — human/FastAPI only, NOT MCP
├── service/        # Business logic + ApprovalExecutor enforcement
├── approval/       # Canonical payload/detail builder (server-derived preconditions, §4.2)
├── mapper/         # MyBatis-Plus mappers (annotation-based @Select/@Update; XML available if a query needs it)
├── domain/         # MyBatis-Plus entities
├── dto/            # Tool request/response DTOs
├── auth/           # Trusted actor context and actor value object
└── config/         # Spring configuration (MCP server, datasource, auth)
```

> `controller/` is **required** — it hosts the authenticated approve/reject/read/execute endpoints
> from §4.4. Business reads stay on the MCP `@McpTool` path; business writes execute through the
> non-MCP backend executor.

## 6. Technology Stack

| Concern | Choice |
|---------|--------|
| Language / runtime | Java 21 + SpringBoot 4.0.6 |
| MCP server | Spring AI 2.0.0-M8 — `spring-ai-starter-mcp-server-webmvc` (`STREAMABLE` HTTP at `/mcp`) |
| Persistence | MyBatis-Plus 3.5.x + MySQL (`ecommerce_db`) |
| Tool schema | Generated by Spring AI from `@McpTool` / `@McpToolParam` |
| Tests | JUnit 5 + Testcontainers MySQL (`test` profile); requires a Docker daemon |

## 7. Roadmap (Java scope)

Tracks the Java slice of the parent roadmap. Cross-project Agent E2E is tracked
separately because the caller lives in the parent/Python project.

- [x] MySQL schema (7 base business tables)
- [x] Seed sample data (products, customer orders, users, suppliers)
- [x] Add `purchase_order` / `purchase_order_item` tables + `approval_record` (with the §2.2
      binding/audit columns); seed a few historical received POs
- [x] SpringBoot + Spring AI MCP server scaffold; serve over streamable-HTTP/SSE
- [x] Core read-only tools: `product_query`, `product_search`, `order_query`, `inventory_query`,
      `inventory_low_stock`, `user_query`, `supplier_query`, `supplier_top`,
      `purchase_order_query` (with pagination/limit defaults + caps where applicable, §3.0)
- [x] `get_statistics` aggregations for inventory, orders, products, purchase orders, and top sellers
      (top sellers ranked by realized sales — `paid`/`shipped`/`completed` only)
- [x] `request_approval` → build canonical payload (params + server preconditions), hash it,
      **render the card server-side**, create pending `approval_record` (tool/actor/session binding)
- [x] Authenticated approve/reject/read/execute endpoints (§4.4) — `controller/`, not MCP tools
- [x] Backend-executed writes `purchase_order_create` (→ `placed` PO), `purchase_order_receive`
      (→ inventory +qty), `order_update` — each enforcing the full §4.3 contract from stored payload
- [x] Remove agent-reachable MCP write tools; MCP advertises reads + `request_approval` only
- [x] SpringBoot tests run against hermetic Testcontainers MySQL initialized from
      `schema.sql` + `data.sql` (no live local MySQL dependency)
- [ ] Parent/Python project verifies end-to-end from DeepAgents `MultiServerMCPClient` with this
      Java service running as the SpringBoot MCP server under test: tool discovery, a read call,
      and an approved write — plus rejection cases (wrong hash, stale DB preconditions, replay
      across session, expired, tampered detail vs payload)

**First milestone (matches parent Week 1):** MCP server up with the read-only tools,
reachable from the Agent — "Check phone category inventory" returns data.
