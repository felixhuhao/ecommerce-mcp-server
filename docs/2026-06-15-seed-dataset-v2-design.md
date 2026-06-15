# Seed Dataset v2 Design

## 1. Goal

Create a demo-grade `schema.sql` + `data.sql` v2 for the expanded five-specialist agent system:

- `sales-analyst`
- `order-manager`
- `purchasing`
- `inventory`
- `customer-insights`

The dataset should be deterministic, bilingual where useful, and scenario-seeded for reliable manual
demos. It should not rely on prompt tricks or random data accidents.

This is a schema/data contract update, not just a live database patch.

## 2. Decisions

| Topic | Decision |
| --- | --- |
| Schema version | Treat as v2 seed schema/data. |
| SKU support | Add a real `product.sku` column. |
| Search contract | `product_query` / `product_search` should search SKU, name, and category. |
| Inventory readability | `inventory_query` and `inventory_low_stock` should return product SKU + name with stock fields. |
| Specialist tool support | Inventory can resolve SKUs through enriched inventory results and/or `product_search`; purchasing gets product identity lookup for SKU-based PO prompts. |
| Reload behavior | Loading `schema.sql` + `data.sql` resets the local demo database. |
| Product naming | Bilingual names where useful, with stable English/SKU tokens for operator prompts. |
| Data style | Curated hero rows first, randomized filler second. |

## 3. Current Gap

The v1 schema has no SKU column. `product_search` only searches product `name` and `category`, so
operator prompts like `SKU-119` cannot resolve unless the SKU is manually embedded in the product
name.

That makes the agent look worse than the product should be:

```text
Operator: what's the current stock level for SKU-119?
Agent: find the productId yourself
```

v2 should make SKU an actual backend identity field.

## 4. Schema Changes

### 4.1 Product

Add:

```sql
sku VARCHAR(40) NOT NULL
```

Recommended indexes:

```sql
UNIQUE KEY uk_product_sku (sku),
KEY idx_product_name (name),
KEY idx_product_category (category),
KEY idx_product_status (status)
```

`product` becomes:

```text
product_id, sku, name, category, price, cost, status, created_at, updated_at
```

### 4.2 Product DTO / Tool Contract

Update `ProductResult` to include `sku`.

Update product search/query SQL so keyword searches match:

```sql
sku LIKE ...
OR name LIKE ...
OR category LIKE ...
```

Keep direct `productId` lookup for service internals and approval preconditions.

### 4.3 Inventory DTO / Tool Contract

Update inventory read results so operators and agents do not see anonymous product IDs:

```text
InventoryResult:
  productId, sku, productName, quantity, safetyStock, warehouse, updatedAt

InventoryLowStockResult:
  productId, sku, productName, quantity, safetyStock, shortage, warehouse, updatedAt
```

Implementation options:

- join `inventory` to `product` in `InventoryMapper`, or
- keep mapper rows separate and enrich in `InventoryService`.

Default: join in mapper/service wherever the current code style is simplest, but lock the output in
tool/service tests. `inventory_low_stock` must be human-readable without an extra product lookup.

### 4.4 Specialist Tool Contract Alignment

The v2 data only works if the agent specialists can resolve the identifiers used in demo prompts.

Required Python-side catalog/tag changes:

- Inventory may keep using `inventory_query` / `inventory_low_stock`, but if it is expected to answer
  arbitrary SKU stock prompts through `product_search(SKU) -> inventory_query(productId)`, its tool
  tags must include `products.search`.
- Purchasing must include `products.search` because PO creation prompts use SKU and the approval
  payload requires product IDs.
- Inventory and purchasing prompts must mention that `product_search` can resolve SKU/name to
  `productId` before inventory or approval calls.
- Do not give either specialist broad analytics tools for this; product identity lookup is a narrow
  read capability.

Expected tool paths:

```text
inventory stock lookup:
  product_search(SKU) -> inventory_query(productId)

inventory low-stock list:
  inventory_low_stock(...)  # already includes sku + productName

purchasing create PO by SKU:
  product_search(SKU) -> supplier_query/supplier_top -> request_approval(...)
```

### 4.5 Optional Future Schema

Do not add these in v2 unless needed:

- supplier-product mapping table
- customer external key
- multi-warehouse inventory rows per product
- product aliases table

Current tools can support strong demos with one inventory row per product and supplier capability
encoded through curated supplier/order data.

## 5. Dataset Shape

Use deterministic generation:

- fixed random seed
- curated hero rows with fixed IDs/SKUs
- filler rows generated after hero rows

Recommended scale:

- 80-120 products across 6-8 categories
- 60-100 customers
- 600-1000 customer orders across 9-12 months
- 10-15 suppliers
- 30-50 historical purchase orders
- 400+ reviews
- inventory row for every active product

The exact counts can be smaller if test runtime becomes noisy, but hero rows must remain stable.

## 6. Hero Scenarios

### 6.1 Inventory Specialist

Stable prompts:

```text
what's the current stock level for SKU-119?
which items are below reorder point right now?
is SKU-LOW-003 at risk of stocking out?
```

Seed rows:

| SKU | Product | Category | Inventory | Safety Stock | Purpose |
| --- | --- | --- | ---: | ---: | --- |
| `SKU-119` | `移动电源 / Power Bank` | electronics | 410 | 51 | happy-path stock lookup |
| `SKU-LOW-003` | `快充充电器 / Fast Charger` | electronics | 12 | 80 | clear low-stock case |
| `SKU-LOW-021` | `LED台灯 / LED Desk Lamp` | home | 18 | 60 | second low-stock case |
| `SKU-OK-042` | `瑜伽垫 / Yoga Mat` | sports | 220 | 40 | healthy-stock comparison |

Expected tool path:

```text
product_search(SKU) -> inventory_query(productId)
```

`inventory_low_stock` should not require `product_search`; it must return `sku` and `productName`
directly.

### 6.2 Purchasing Specialist

Stable prompts:

```text
create a purchase order for 200 units of SKU-LOW-003 from Supplier 7
which supplier should we use for restocking power banks?
receive purchase order <known placed PO id>
```

Seed rows:

- Supplier 7 should be a good electronics supplier with clear lead time and rating.
- `SKU-LOW-003` should have current cost that makes PO total easy to verify.
- At least one `placed` PO should exist for receive-flow manual tests.
- Several `received` historical POs should exist for `purchase_order_query`.

Suggested fixed supplier:

```text
supplier_id=7
name=深圳华强电子供应链 / Shenzhen Huaqiang Electronics Supply
```

Supplier 7 should intentionally serve the electronics restock demos. Avoid using a sports supplier
for electronics SKU prompts.

### 6.3 Sales Analyst

Stable prompts:

```text
forecast next month's sales for SKU-119 and chart it
show monthly revenue by category for the last six months
what are the top products by revenue?
```

Seed rows:

- `SKU-119` should have monthly order history across at least 9 months.
- Monthly sales should have a visible trend, not purely random noise.
- At least two categories should have distinct trends for chart demos.
- Include enough completed/paid/shipped orders so `get_statistics` top products are meaningful.

Expected tool path:

```text
order_query/product_query/stage_sales_analysis_inputs -> execute -> chart tool
```

or `get_statistics` for simple aggregate questions.

### 6.4 Customer Insights Specialist

Stable prompts:

```text
who are our top repeat customers?
show customer 7's order history
which customers have high lifetime value?
```

Seed rows:

| Customer | Shape |
| --- | --- |
| customer 7 | repeat high-value buyer with multiple completed orders |
| customer 12 | new customer with one large order |
| customer 23 | frequent low-AOV buyer |
| customer 31 | churn-ish: older orders, no recent purchases |

Order data should make repeat vs one-time and lifetime-value questions obvious.

### 6.5 Order Manager

Stable prompts:

```text
what's the status of order 1007?
ship order 1007
cancel order 1008
```

Seed rows:

| Order | Status | Purpose |
| --- | --- | --- |
| `1007` | `paid` | shippable order-status update demo |
| `1008` | `pending` | cancellable order demo |
| `1009` | `completed` | non-actionable status explanation |
| `1010` | `cancelled` | already-cancelled boundary |

Order items should use recognizable SKUs so approval cards are readable.

## 7. Bilingual Naming

Use names like:

```text
移动电源 / Power Bank
快充充电器 / Fast Charger
LED台灯 / LED Desk Lamp
瑜伽垫 / Yoga Mat
```

Benefits:

- English operator prompts resolve reliably.
- Chinese source data still feels realistic.
- UI cards remain readable in both languages.

## 8. Generator Plan

Refactor `generate_data.py` into clearer sections:

1. constants and helpers
2. curated products
3. filler products
4. suppliers
5. customers
6. inventory
7. curated orders
8. trend/filler orders
9. reviews
10. purchase orders
11. schema writer
12. data writer

Use helper functions for SQL escaping and fixed timestamps.

Keep the generator as the source of truth; do not hand-edit generated SQL except for emergency
debugging.

## 9. Tests

Java tests to update/add:

- `ProductMapperTest`: search by SKU returns the expected product.
- `ProductToolTest`: `product_search("SKU-119")` returns `sku` and `productId`.
- `InventoryToolTest`: `inventory_query(productId for SKU-119)` returns expected quantity.
- `InventoryToolTest`: `inventory_low_stock` includes `sku` and `productName`.
- `InventoryToolTest`: `inventory_query` includes `sku` and `productName`.
- `PurchaseOrderToolTest`: known placed PO can be queried.
- `StatisticsToolTest`: top-products output includes curated sales rows.
- Existing approval tests updated for shifted product columns.

Python smoke tests to update after Java lands:

- inventory specialist manual/eval case can use `SKU-119`.
- purchasing prompts can use SKU after purchasing receives `products.search`.
- provider/tag tests lock `products.search` for inventory and purchasing.

## 10. Migration / Local Reload

Because `schema.sql` drops and recreates tables, v2 reload is destructive by design.

Manual local reload:

```bash
mysql -u root -p < src/main/resources/schema.sql
mysql -u root -p ecommerce_db < src/main/resources/data.sql
```

For the current Docker MySQL dev container, use the configured local credentials instead of root.

After reload:

- restart `ecommerce-mcp-server`
- restart Python agent backend if it caches MCP tool schemas
- retest SKU/product flows through MCP before UI testing

## 11. Acceptance

- `product_search("SKU-119")` returns a product with `sku="SKU-119"`.
- `inventory_query(productId=<SKU-119 product>)` returns the seeded stock level.
- `inventory_low_stock` returns `sku` and `productName` for each result.
- Each of the five specialists has at least three reliable manual demo prompts.
- Data supports both read-only and approval/proposal flows.
- `./mvnw test` passes against Testcontainers seeded from v2 SQL.
- README/docs mention the v2 SKU field and reset-on-load behavior.

## 12. Open Questions

1. Should supplier capability be modeled explicitly with a supplier-product table?
   - Default: no for v2; keep scope to SKU + curated supplier/PO data.
2. Should order IDs be fixed at `1007+` or use existing auto-increment starting at `1`?
   - Default: fixed explicit IDs for hero orders, because manual demos benefit from memorable IDs.
     Start filler order IDs at `1100+` or assign all IDs explicitly to avoid auto-increment
     collisions.
3. Should product IDs stay stable for current tests?
   - Default: yes for common low IDs used by approval tests; add SKU without moving core product IDs.
