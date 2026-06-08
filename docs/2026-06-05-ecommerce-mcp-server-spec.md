# E-Commerce MCP Server (SpringBoot) — Design Spec

> The Java/SpringBoot service for the E-Commerce AI Assistant.
> Status: Draft | Date: 2026-06-05
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
- The business-tool **MCP surface** (Spring AI `@McpTool` methods)
- **HITL approval enforcement** — write tools refuse to execute without a valid `approval_id`

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
  │       @McpTool business tools + approval enforcement
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
A PO row is only ever inserted by an **already-approved** `purchase_order_create` call, so it
starts at `placed` — the pending/approved gate lives in `approval_record` (§4), not on the PO
itself. `unit_cost` (paid to supplier) is tracked separately from `product.price` (charged to
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
30+ users with varying levels, 10+ suppliers. **To add:** `purchase_order` /
`purchase_order_item` tables (DDL + a few historical received POs for analytics).

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
  status         VARCHAR(10) NOT NULL DEFAULT 'pending',  -- pending | approved | consumed | rejected | expired
  rejection_reason TEXT NULL,                    -- human-supplied reason when rejected
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at     DATETIME NOT NULL,
  rejected_at    DATETIME NULL,                  -- set when rejected
  consumed_at    DATETIME NULL,                  -- set when the write executes (one-time use)
  KEY idx_status (status)
);
```

The extra columns (`tool_name`, `session_id`, `consumed_at`, and the `consumed` status) exist
to enforce tool binding, actor/session binding, and one-time use — see §4.3.

## 3. MCP Tools

Business capability is exposed as Spring AI `@McpTool` methods. Spring AI derives the
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
| purchase_order_create | Write | PurchaseOrderService.create | Create a supplier purchase order — restock (requires approval_id) |
| purchase_order_receive | Write | PurchaseOrderService.receive | Mark a PO received → increment inventory (requires approval_id) |
| order_update | Write | OrderService.update | Update a customer order's fulfillment status (requires approval_id) |
| request_approval | Write | ApprovalService.create | Take structured operation params; Java builds the canonical authorization payload (params + server preconditions), hashes it, derives the human-facing card server-side, stores a pending record, returns approval_id |

Planned post-core read tool: `get_statistics` (`StatsService.get`) for aggregation-first
analytics once the parent Agent needs it.

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

Write operations are **server-enforced**, not prompt-based. The server rejects any
write tool call without a valid `approval_id`. This is the core security
responsibility of the Java project.

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
Agent calls the write tool (e.g. purchase_order_create) with approval_id + the same operation_params
  → service re-derives trusted identity + current DB preconditions and runs the validation contract (§4.3)
    → valid   → execute the write, set status=consumed + consumed_at
    → invalid → reject with a typed error
```

**The Agent never authors what the human sees.** `request_approval` takes only structured
parameters; Java renders the approval card from those parameters and live DB state. This closes
the gap where an Agent could display "receive 5 items" while the hashed payload receives 500 —
the card and the hash are derived from the *same* server-side payload.

**State freshness:** the approval pins the DB facts that are required to authorize the write,
not every field shown on the card. If an order/PO state or product cost/status changes between
approval creation and the resumed write, the write tool must reject with a stale-approval error
and require a new approval. Volatile display-only fields, such as current inventory quantity on
`purchase_order_create`, can remain in `operation_detail` without entering the hashed payload.

### 4.3 Enforcement Contract (required checks)

Before any write tool executes, the service layer MUST verify **all** of:

1. **Exists & approved** — `approval_id` exists and `status = approved` (not pending/rejected/expired/consumed).
2. **Hash + precondition match** — rebuild the canonical `operation_payload` from the incoming
   operation params plus current DB preconditions/snapshot; its hash must equal the stored hash.
   This rejects both Agent tampering and stale approvals after relevant DB state changes.
3. **Tool binding** — the stored `tool_name` equals the tool now executing (an order approval can't authorize a PO).
4. **Actor/session binding** — the trusted request `user_id` **and** `session_id` match the record's. An approval issued in one session/actor cannot be replayed by another.
5. **Not expired** — `now < expires_at`.
6. **One-time use** — `consumed_at IS NULL`; on success, atomically set `status=consumed`, `consumed_at=now` (e.g. a conditional `UPDATE ... WHERE status='approved'`) so concurrent calls can't double-spend one approval.

Any failure → typed error, no write. All write attempts (allowed and rejected) should be auditable.

**Why `operation_hash`:** it pins the approval to the exact operation and DB facts shown to the
user. If the Agent submits different params, or if relevant DB state changes after approval, the
hash won't match and the write is rejected — preventing post-approval tampering and stale-impact
writes.

**Canonical serialization:** the hash MUST be computed over a deterministic serialization
(sorted keys, fixed number/date formats, normalized whitespace) so the same logical payload
always hashes identically on both the `request_approval` and the write call. Include only stable,
domain-relevant preconditions in the payload; do not include volatile display-only fields such as
localized labels or formatting. SHA-256 is fine.

Still genuinely open (implementer's call):
- Expiry duration, and whether expired rows are swept by a job or just rejected lazily on read.

### 4.4 Approval Transition Endpoint (non-agent)

The approve/reject transition is an **authenticated REST endpoint, not an MCP tool**. The Agent
must never be able to approve its own request — only a human (via the frontend → FastAPI) can.

```
POST /approvals/{approval_id}/approve     (auth required)
POST /approvals/{approval_id}/reject      body: { reason }   (auth required; reason is persisted)
```

Contract:
- **Authentication required** — caller is the human operator (e.g. via the FastAPI/frontend
  session), carrying the `user_id`/`session_id`. These are NOT MCP tools and are not in the
  Agent's tool list, so the Agent cannot reach them.
- **Actor binding** — the approving user/session must match the `approval_record` it transitions
  (you can't approve another session's request).
- **Valid transitions only** — `pending → approved` / `pending → rejected`; reject anything
  already `approved`/`consumed`/`rejected`/`expired`. Approving does not execute the write — it
  only flips status so the resumed Agent's write call can pass §4.3.
- A read endpoint (`GET /approvals/{id}` or list pending for a session) backs the frontend
  approval card; it returns the **server-rendered** `operation_detail`, never Agent prose.

This is the one REST surface the agent path genuinely requires (see §5).

## 5. Project Structure

```
com.ecommerce.agent/
├── tool/           # @McpTool MCP tools (exposed via Spring AI)
├── controller/     # Approval REST endpoints (§4.4) — human/FastAPI only, NOT MCP
├── service/        # Business logic + approval enforcement
├── mapper/         # MyBatis mappers (XML for complex queries)
├── entity/         # MyBatis entities
├── dto/            # Tool request/response DTOs
├── auth/           # Trusted actor context and actor value object
└── config/         # Spring configuration (MCP server, datasource, auth)
```

> `controller/` is **required** — it hosts the authenticated approve/reject/read endpoints
> from §4.4. Business reads/writes stay on the MCP `@McpTool` path; the controller exists only for
> the human-driven approval transition (and any future non-agent client).

## 6. Technology Stack

| Concern | Choice |
|---------|--------|
| Language / runtime | Java + SpringBoot |
| MCP server | Spring AI — `spring-ai-starter-mcp-server-webmvc` (`STREAMABLE` HTTP at `/mcp`) |
| Persistence | MyBatis + MySQL (`ecommerce_db`) |
| Tool schema | Generated by Spring AI from `@McpTool` / `@McpToolParam` |

(Exact SpringBoot / Spring AI versions to be pinned at scaffold time.)

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
- [ ] `get_statistics` aggregations (whatever the analyst sub-agent needs)
- [x] `request_approval` → build canonical payload (params + server preconditions), hash it,
      **render the card server-side**, create pending `approval_record` (tool/actor/session binding)
- [x] Authenticated approve/reject/read endpoints (§4.4) — `controller/`, not MCP tools
- [x] Write tools `purchase_order_create` (→ `placed` PO), `purchase_order_receive`
      (→ inventory +qty), `order_update` — each enforcing the full §4.3 contract
- [x] SpringBoot tests run against hermetic Testcontainers MySQL initialized from
      `schema.sql` + `data.sql` (no live local MySQL dependency)
- [ ] Parent/Python project verifies end-to-end from DeepAgents `MultiServerMCPClient` with this
      Java service running as the SpringBoot MCP server under test: tool discovery, a read call,
      and an approved write — plus rejection cases (wrong hash, stale DB preconditions, replay
      across session, expired, tampered detail vs payload)

**First milestone (matches parent Week 1):** MCP server up with the read-only tools,
reachable from the Agent — "Check phone category inventory" returns data.
