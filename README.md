# E-Commerce MCP Server

Spring Boot MCP server for an e-commerce AI assistant. This service owns the MySQL-backed business data, exposes read/write business capability as MCP tools, and enforces human-in-the-loop approval for risky writes.

Detailed design lives in [docs/2026-06-05-ecommerce-mcp-server-spec.md](docs/2026-06-05-ecommerce-mcp-server-spec.md).

## What This Service Owns

- Product/SKU, inventory, customer, order, supplier, and purchase-order queries
- Supplier purchase-order creation and receiving
- Customer order fulfillment status updates
- Server-side approval payload generation, hashing, binding, expiry, and one-time consumption
- Streamable HTTP MCP endpoint at `/mcp`

The Python/Agent project owns orchestration, LangGraph checkpoints, frontend HITL flow, sandbox tools, and visualization MCP servers.

## Tech Stack

- Java 21
- Spring Boot 4.0.6
- Spring AI MCP Server WebMVC 2.0.0-M8
- MyBatis-Plus 3.5.x
- MySQL 8
- JUnit 5 + Testcontainers MySQL
- Maven Wrapper

## MCP Tools

Read tools:

- `product_query`
- `product_search`
- `order_query`
- `inventory_query`
- `inventory_low_stock`
- `user_query`
- `supplier_query`
- `supplier_top`
- `purchase_order_query`
- `get_statistics`

Write/approval tools:

- `request_approval`
- `purchase_order_create`
- `purchase_order_receive`
- `order_update`

Write tools require a valid `approval_id`. Approvals are bound to the trusted actor, session, tool name, canonical operation payload, and are one-time use.

## Prerequisites

- Java 21
- Docker, required for tests
- MySQL 8 on port `3306`, required for local app runtime
- Maven is optional because the repo includes `./mvnw`

## Configuration

Runtime config is read from `src/main/resources/application.properties`. Local secrets should go in `.env.properties`, which is ignored by git.

Create `.env.properties` in the project root:

```properties
DB_USERNAME=your_mysql_user
DB_PASSWORD=your_mysql_password
APP_SERVICE_TOKEN=dev-service-token
```

`APP_SERVICE_TOKEN` is required for all non-actuator HTTP requests. If it is blank, `/mcp` and `/approvals/*` return `401`.

## Database Setup

The app expects database `ecommerce_db`.

Load schema and seed data into local MySQL:

```bash
mysql -u root -p < src/main/resources/schema.sql
mysql -u root -p ecommerce_db < src/main/resources/data.sql
```

`schema.sql` creates the database if needed. `data.sql` seeds products, SKUs, users, suppliers, inventory, orders, purchase orders, reviews, and approval tables. Loading both files resets the local demo database.

## Run Tests

Tests use the `test` profile and Testcontainers:

```bash
./mvnw test
```

This starts a temporary MySQL container and initializes it from the same `schema.sql` and `data.sql`. Tests do not require a local MySQL server on port `3306`, but they do require Docker.

Run a focused test class:

```bash
./mvnw -Dtest=StatsMapperTest test
```

## Run Locally

After configuring `.env.properties` and loading MySQL data:

```bash
./mvnw spring-boot:run
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

MCP endpoint:

```text
POST http://localhost:8080/mcp
```

Non-actuator requests must include:

```text
X-Service-Token: dev-service-token
X-User-Id: 1
X-Session-Id: local-session
```

The `TrustedActorFilter` derives the actor from these headers. Tool parameters never accept `userId` or `sessionId` directly.

## Run With Docker

The Docker setup runs only the Spring Boot app. It assumes MySQL is already running and already seeded.

```bash
chmod +x scripts/docker-run.sh
./scripts/docker-run.sh
```

The script builds `ecommerce-mcp-server:local`, removes any existing container named
`ecommerce-mcp-server`, then starts the app on port `8080`.

By default the container connects to MySQL through:

```text
host.docker.internal:3306/ecommerce_db
```

You can override these values:

```bash
MYSQL_HOST=host.docker.internal \
MYSQL_PORT=3306 \
MYSQL_DATABASE=ecommerce_db \
APP_PORT=8080 \
./scripts/docker-run.sh
```

Required secrets still come from `.env.properties` or the environment:

```properties
DB_USERNAME=your_mysql_user
DB_PASSWORD=your_mysql_password
APP_SERVICE_TOKEN=dev-service-token
```

The Docker app runtime does not run `schema.sql` or `data.sql`; database setup remains manual or test-only.

## Approval Flow

1. Agent calls `request_approval` with a write tool name and structured operation params.
2. Java reads live DB state, builds the canonical authorization payload, renders the approval detail, hashes the payload, and stores `approval_record`.
3. Human approval happens through REST, not MCP:

```text
GET  /approvals/{id}
POST /approvals/{id}/approve
POST /approvals/{id}/reject
```

4. Agent retries the write tool with the same operation params and the returned `approval_id`.
5. Java verifies approval status, actor/session binding, tool binding, payload hash, expiry, and one-time use before executing the write.

Approval records default to a 15-minute TTL. Expired open approvals are lazily marked `expired` when read, approved/rejected, or consumed.

## Statistics Semantics

`product_query` and `product_search` match active products by SKU, product name, or category.
Inventory reads return SKU and product name alongside stock quantities.

`get_statistics` returns aggregation-first data for:

- Inventory health
- Orders by status
- Products by category
- Purchase orders by status
- Top products by revenue

Top products use realized sales only: `paid`, `shipped`, and `completed` orders. `pending` and `cancelled` orders are excluded.

## Project Layout

```text
src/main/java/com/ecommerce/agent/
├── approval/     canonical approval payload/detail builder
├── auth/         trusted actor context
├── config/       request auth and app config
├── controller/   approval REST endpoints, not MCP tools
├── domain/       database domain objects
├── dto/          tool and service response DTOs
├── mapper/       MyBatis/MyBatis-Plus mappers
├── service/      business logic and approval enforcement
└── tool/         Spring AI @McpTool classes
```

## Useful Commands

```bash
./mvnw test
./mvnw spring-boot:run
./scripts/docker-run.sh
git status --short
```

## Current Scope

The Java MCP server scope is implemented: read tools, write tools, HITL enforcement, REST approval transitions, Testcontainers-backed tests, and `get_statistics`.

Cross-project end-to-end verification from the Python `MultiServerMCPClient` caller is intentionally left to the parent Agent project.
