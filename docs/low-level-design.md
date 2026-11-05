# Boldfit D2C Take-Home: Build Plan and Low-Level Design

## 1. What the assignment asks for

The attached assignment asks for a minimal D2C checkout slice, intended for roughly one evening (4–6 hours):

- A catalog of about five SKUs.
- A cart-to-checkout flow.
- A payment intent using a mocked payment provider (no live credentials).
- A customer order-status page.
- `POST /webhooks/payment`: verify an HMAC-signed provider payload and process replayed/duplicate deliveries idempotently.
- Explicitly call out handled edge cases, including stock, double-submit, failed/partial payment, and webhook replay.
- React + TypeScript frontend; backend may be TypeScript/Node or Java/Spring. REST JSON, clear DTOs, auth-aware design; a hardcoded bearer token is acceptable.
- A README with run steps, assumptions, tradeoffs, design decisions, one non-happy-path failure, what would change after a first pass, and what would be done in week two.
- Optional: a small JSON shape for an internal agent to request retrying a failed payment with human approval.

The rubric weights API correctness (30%), checkout/payment failure handling (25%), design decisions (20%), customer UI (15%), and code quality/README (10%).

## 2. Recommended scope and priority

### P0 — required for a credible submission

1. Seed five products and two clearly labeled demo customer accounts.
2. Render a public catalog and guest cart; allow add/remove/change quantity before login.
3. Show a login prompt when the shopper proceeds to checkout; load/merge their saved cart after login.
4. Persist a cart per user and merge guest cart quantities into it on login.
5. Create checkout/order with server-side price and stock validation.
6. Prevent duplicate checkout submission with a client idempotency key.
7. Create a mock payment intent and expose a way to simulate success/failure.
8. Accept a signed webhook, verify HMAC using constant-time comparison, and deduplicate provider event IDs.
9. Transition order/payment state safely; show status and prior orders for the authenticated user.
10. Persist data and document how to run locally.
11. Include design notes and demonstrate at least one failure path.

### P1 — small polish if time remains

- Loading, empty, validation, and recoverable error states in the UI.
- Cart persisted in local storage (cart is convenience only; server is authoritative).
- Basic API tests for pricing/stock, idempotency, signature rejection, duplicate delivery, and payment transitions.
- Optional internal retry request schema with human approval fields; do not build an agent UI.

### Explicitly out of scope

Full catalog/search/CMS/admin, live gateway credentials, shipping/tax integrations, Kubernetes/Kafka/microservices, OAuth/SSO, password reset, and production-grade identity/inventory systems.

## 3. Stack choice

**Frontend:** React + TypeScript + Vite; React Router (or a tiny two-route state machine); native `fetch`; CSS modules/plain CSS. Keep dependencies light.

**Backend:** Java 21 + Spring Boot (Spring Web, Bean Validation, Spring Data JPA). Keep business logic in focused services and persistence behind repositories. Use DTOs at the API boundary; do not expose JPA entities.

**Catalog seed data:** Do not call DummyJSON at runtime. Copy the pasted response into a checked-in fixture such as `backend/src/main/resources/seed/products.json`, then have a Spring `CommandLineRunner` read it and insert the chosen five products into PostgreSQL on first startup only. Use a DB-empty check or a Flyway seed marker so restarts never reset stock or overwrite data. Store the product title/description/thumbnail (or first image) as display fields and retain the source ID/SKU only as seed metadata if useful. Assign local stable SKUs, INR prices in paise, and stock; do not use the fixture USD prices/stock as authoritative checkout values. Add an image `onError` fallback in the UI. The pasted response contains 30 mixed-category products (beauty, fragrance, furniture, groceries), so select five to meet the assignment scope; if keeping the Boldfit fitness context, replace those five titles with fitness-relevant names while reusing the image URL pattern/data shape. The checked-in fixture makes startup reproducible and removes runtime network dependence.

**Persistence:** PostgreSQL with Flyway migrations and seed data. PostgreSQL is appropriate for the stock and transaction requirements; Docker Compose makes its local setup reproducible. Use database transactions and conditional stock updates so concurrent checkout cannot oversell.

**Tests/tooling:** Maven or Gradle (choose the one you already know), JUnit 5, and MockMvc. Use Testcontainers only if already familiar and time permits; Compose PostgreSQL plus focused service/API tests is sufficient.

**Repository and containers:** Keep the entire application in **one Git repository and one project folder**. Docker Compose at the repository root builds three services: `frontend` (multi-stage Vite build served by Nginx), `backend` (multi-stage Maven/Gradle build, Spring Boot JAR), and `db` (PostgreSQL with a named volume and health check). The backend waits for database health; Flyway applies schema migrations at startup. Configure Nginx to serve the SPA and proxy `/api/*` and `/webhooks/*` to the backend so the browser uses one origin. Provide `.env.example` and a single `docker compose up --build` run path. No Kubernetes, Redis, or message broker.

**Why this stack:** Java is your primary stack and Spring is explicitly allowed, so it is likely the fastest path for you to implement the business rules confidently. PostgreSQL directly supports transactional inventory semantics; Compose keeps its setup small and repeatable.

## 4. Single-repository layout

Keep one cloneable repository with independent frontend/backend subdirectories and one root Compose file; these are folders in the same repo, not separate repos or submodules. For example:

```text
basic-ecommerce-app/
├── docker-compose.yml
├── .env.example
├── .gitignore
├── README.md
├── frontend/                 # React + TypeScript + Vite
│   ├── Dockerfile
│   ├── nginx.conf             # SPA fallback + /api and /webhooks proxy
│   ├── package.json
│   └── src/
└── backend/                  # Spring Boot + Java
    ├── Dockerfile
    ├── pom.xml                # or build.gradle, choose one
    └── src/main/java/...
        └── src/main/resources/db/migration/
```

Compose uses `build.context: ./frontend` and `build.context: ./backend`, so all images build from this same repository. The database uses the official PostgreSQL image. Run everything from the root with `docker compose up --build`; stop with `docker compose down`. Keep secrets out of Git and document the demo values in `.env.example`.

## 5. Architecture

```text
React SPA
  ├─ GET /api/products
  ├─ POST /api/checkout (Bearer token + Idempotency-Key)
  ├─ POST /api/payments/:paymentId/simulate (demo-only mock control)
  └─ GET /api/orders/:referenceId
             │
             ▼
Spring Boot API ── CheckoutService ── PaymentProvider interface
     │                │                 └─ MockPaymentProvider
     │                ▼
     └────────── JPA repositories ── PostgreSQL

Mock provider webhook simulator ── POST /webhooks/payment
                                      HMAC verification
                                      event dedupe + state transition
```

Keep layers small: controllers validate/map DTOs and errors; services own business rules and transactional boundaries (`@Transactional`); Spring Data repositories own persistence; the payment provider interface isolates the mock implementation. Do not overbuild generic abstractions.

## 6. Domain model and persistence

All monetary values are integer paise (`BIGINT`). Timestamps are UTC `TIMESTAMPTZ`. Internal IDs are UUIDs; `reference_id` is a separate cryptographically random, unguessable customer-facing order reference. Use PostgreSQL `UUID`, `BIGINT`, `BOOLEAN`, and `TIMESTAMPTZ` types as appropriate.

### `products`

- `id` UUID PK
- `sku` TEXT UNIQUE NOT NULL
- `name` TEXT NOT NULL
- `description` TEXT
- `image_url` TEXT NOT NULL
- `price_paise` BIGINT NOT NULL CHECK >= 0
- `stock_available` INTEGER NOT NULL CHECK >= 0
- `active` BOOLEAN NOT NULL DEFAULT TRUE

### `users` and `user_sessions`

- `users`: `id UUID PK`, `email TEXT UNIQUE`, `display_name TEXT`, `password_hash TEXT`, `created_at TIMESTAMPTZ`
- `user_sessions`: `token_hash TEXT PK`, `user_id UUID FK`, `expires_at TIMESTAMPTZ`, `created_at TIMESTAMPTZ`

Store only the hash of opaque bearer tokens, never the raw token. Seed credentials are demo-only.

### `cart_items`

- `user_id UUID FK`, `product_id UUID FK`, `quantity INTEGER CHECK (quantity BETWEEN 1 AND 10)`, `updated_at TIMESTAMPTZ`
- Composite primary key (`user_id`, `product_id`)

### `cart_merges`

- `user_id UUID FK`, `guest_cart_id UUID`, `merged_at TIMESTAMPTZ`
- Composite primary key (`user_id`, `guest_cart_id`) to make guest cart merge retry-safe.

### `orders`

- `id` UUID PK
- `reference_id` TEXT UNIQUE NOT NULL
- `status` TEXT CHECK in `PENDING_PAYMENT`, `CONFIRMED`, `PAYMENT_FAILED`, `EXPIRED`, `CANCELLED`
- `currency` TEXT NOT NULL DEFAULT `INR`
- `subtotal_paise` BIGINT NOT NULL
- `total_paise` BIGINT NOT NULL
- `created_at`, `updated_at` TIMESTAMPTZ NOT NULL
- `version` INTEGER NOT NULL DEFAULT 0 (optional optimistic guard)

### `order_items`

- `id` UUID PK
- `order_id` UUID FK
- `product_id` UUID FK
- `sku_snapshot`, `name_snapshot` TEXT NOT NULL
- `unit_price_paise`, `line_total_paise` BIGINT NOT NULL; `quantity` INTEGER NOT NULL
- UNIQUE (`order_id`, `product_id`)

### `payments`

- `id` UUID PK
- `order_id` UUID FK UNIQUE NOT NULL (one payment attempt in the first pass)
- `provider` TEXT NOT NULL (`mock`)
- `provider_reference` TEXT UNIQUE
- `status` TEXT CHECK in `PENDING`, `SUCCEEDED`, `FAILED`
- `amount_paise` BIGINT NOT NULL
- `failure_code` TEXT NULL
- `created_at`, `updated_at` TIMESTAMPTZ NOT NULL

### `checkout_idempotency`

- `key` UUID PK
- `request_hash` TEXT NOT NULL
- `order_id` UUID FK NOT NULL
- `created_at` TIMESTAMPTZ NOT NULL

### `webhook_events`

- `provider_event_id` TEXT PK
- `event_type` TEXT NOT NULL
- `payload_hash` TEXT NOT NULL
- `received_at` TIMESTAMPTZ NOT NULL
- `processed_at` TIMESTAMPTZ NULL
- `result` TEXT NOT NULL (`PROCESSED`, `IGNORED`, `REJECTED`)

## 7. Authentication and cart merge

Add a deliberately small **demo login**, because checkout should require an identified customer in this experience. Seed two demo users in PostgreSQL with demo-only credentials, hash passwords with BCrypt, and clearly publish those credentials in the README. Do not implement registration, password reset, OAuth, or account recovery. `POST /api/auth/login` validates credentials and returns an expiring opaque bearer token; store only its hash in a `user_sessions` table. A Spring filter resolves the token to a user ID and places that identity in the request context. Configure a short demo expiry (for example, 8 hours). Product listing remains public; cart, checkout, order history, order status, and payment simulation require a valid bearer token. The payment webhook remains authenticated by HMAC, not user bearer token.

Guest cart lives in local storage. When the user clicks **Proceed to checkout**, show the login form. After login, send guest cart lines to the merge endpoint. Merge quantities by product ID (`saved quantity + guest quantity`, capped at the product limit); remove inactive/unavailable products and return the merged cart plus notices. Persist the merged cart in PostgreSQL so it is available on the user’s next login. Clear the local guest cart only after the merge succeeds. Repeating the merge must not add the same guest quantities again: include a guest-cart ID and record processed merge IDs per user, or implement an idempotent replace/sync operation.

Past **orders** are not silently re-added to the cart on every login, since that can unexpectedly recreate a purchase. Provide an authenticated order-history page with a **Buy again** action that adds selected prior order items to the saved cart; this gives the user explicit control. If “previous purchase merged” means an existing saved cart, it is merged automatically at login as described above. All demo accounts are seeded local identities and this remains a take-home login, not production identity management.

## 8. API contract

Use JSON consistently. Spring validation handles malformed DTOs; a `@RestControllerAdvice` maps errors to the common shape. Error format: `{ "error": { "code": "OUT_OF_STOCK", "message": "...", "details": {} } }`. Never trust client prices or totals.

### `POST /api/auth/login`

Public endpoint. Body: `{ "email": "demo@example.com", "password": "..." }`. Return `{ "accessToken": "...", "expiresAt": "...", "user": { "id": "...", "name": "..." } }`; invalid credentials return a generic `401`. Frontend stores the token in memory/session storage for this demo and sends it as `Authorization: Bearer ...`.

### Cart endpoints (authenticated)

- `GET /api/cart` returns the current user's saved cart with current product availability/prices.
- `PUT /api/cart` replaces the user's cart with normalized `{ "guestCartId": "...", "items": [{ "productId": "...", "quantity": 1 }] }`; make repeated submissions of a guestCartId idempotent. Or provide a dedicated `POST /api/cart/merge` endpoint with processed guestCartId tracking.
- `GET /api/orders` returns the authenticated user's previous orders; `POST /api/cart/items/from-order/:orderId` explicitly adds eligible items from a prior order (Buy again).

### `GET /api/products`

`200 { "products": [{ "id", "sku", "name", "description", "imageUrl", "pricePaise", "available" }] }`

Only active products are returned. Do not expose internal fields.

### `POST /api/checkout`

Headers: `Authorization: Bearer <login-issued-token>`, `Idempotency-Key: <random UUID>`.

Body: `{ "items": [{ "productId": "...", "quantity": 2 }] }`

Success `201` (or `200` for a replay):

```json
{
  "order": { "referenceId": "...", "status": "PENDING_PAYMENT", "totalPaise": 123400 },
  "payment": { "id": "...", "status": "PENDING", "mockClientSecret": "..." }
}
```

Validation: non-empty items, unique product IDs after normalization, integer quantity 1–10, active products, current prices, sufficient stock. Same key + same normalized request returns the original result. Same key + different request returns `409 IDEMPOTENCY_KEY_REUSED`. New key with insufficient stock returns `409 OUT_OF_STOCK`. Never accept price, amount, order status, or provider status from this request.

### `POST /api/payments/:paymentId/simulate`

Authenticated route protected by the login-issued bearer token; require ownership of the payment/order. Body: `{ "outcome": "success" | "failure", "failureCode"?: "DECLINED" }`. This is a local provider simulator: it creates a provider-shaped event and signs it with the configured webhook secret, then calls the same webhook processing service (in-process is fine). Return the resulting payment/order states. Make it clear this endpoint is not a real payment API.

### `POST /webhooks/payment`

Headers: `X-Provider-Event-Id`, `X-Provider-Signature: sha256=<hex>`.

Body example: `{ "id": "evt_...", "type": "payment.succeeded", "data": { "paymentReference": "...", "amountPaise": 123400, "currency": "INR" } }`.

Verify signature over the **raw request bytes** with HMAC-SHA256 and a secret from environment; use Java `Mac` and `MessageDigest.isEqual` for constant-time comparison after validating equal-length decoded signatures. In Spring, configure the webhook route to retain raw request bytes (for example, a dedicated byte-array request body handler) before JSON parsing. In one PostgreSQL transaction: insert event ID under a unique constraint, load payment/order, verify amount/currency/reference and legal transition, update payment and order, then mark event processed. Commit before returning `200`. Duplicate event ID returns `200` without reapplying. Bad signature returns `401`; malformed payload `400`; unknown payment or amount mismatch gets recorded/handled as ignored or rejected and alerted in logs, returning a non-retrying `2xx` only if safely recorded. A transient DB failure returns `5xx` so the provider retries.

### `GET /api/orders/:referenceId`

Returns only customer-safe fields: `{ "order": { "referenceId", "status", "totalPaise", "items": [...], "updatedAt" }, "payment": { "status", "failureCode?" } }`. Unknown reference returns `404`. Require login and verify the order belongs to the authenticated user. The opaque reference ID is defense-in-depth, not a substitute for ownership checks.

## 9. State machine and invariants

Order transitions:

```text
PENDING_PAYMENT ── valid success event ──> CONFIRMED
PENDING_PAYMENT ── valid failure event ──> PAYMENT_FAILED
PENDING_PAYMENT ── timeout/admin expiry ──> EXPIRED
```

`CONFIRMED`, `PAYMENT_FAILED`, and `EXPIRED` are terminal in this first version. Duplicate or stale events cannot reverse a terminal state. If a success event arrives after failure, record the event but do not silently confirm; flag it for reconciliation/refund handling. Never fulfill based solely on a browser redirect or simulate response: the verified webhook is authoritative.

Stock policy: reserve/decrement stock in the checkout transaction and do not release it automatically in the 4–6 hour build (document that reservations can strand stock on abandoned/failed payments). If implementing failure release safely, add `inventory_reservations` and a one-time release flag/transaction; do not simply increment on every failure webhook. Given time, simpler stock decrement on checkout with a clear reservation TTL limitation is acceptable. Stronger design: create reservations with expiry and release them via a scheduled cleanup, but only if there is time to implement and prove idempotency.

Core invariants:

1. Server-calculated order total equals the sum of immutable item snapshots.
2. Stock never goes negative; all requested quantities are reserved atomically or none are.
3. A checkout idempotency key maps to one request hash and one result.
4. A provider event ID affects state at most once.
5. A payment cannot be applied to a different order or amount.
6. Only valid state transitions occur; browser state cannot confirm an order.

## 10. Checkout transaction algorithm

1. Validate login-issued bearer token, derive user ID from server-side session, validate idempotency header/DTO, and normalize item order for stable hashing.
2. Hash canonical JSON of product IDs + quantities.
3. Start a PostgreSQL transaction (`@Transactional`).
4. Look up idempotency key. If found, compare request hash; return prior order/payment if equal, else conflict.
5. Load all products; reject if missing/inactive. Compute totals from DB prices.
6. For each SKU, conditionally reserve stock with a repository `@Modifying` update: `UPDATE products SET stock_available = stock_available - :quantity WHERE id = :id AND active = true AND stock_available >= :quantity`; require exactly one row updated. Flush/check row counts inside the transaction; any failure rolls back all prior decrements.
7. Insert order with `PENDING_PAYMENT`, item snapshots, pending payment, and idempotency mapping.
8. Commit. Return mock payment details.

The mock provider's simulate route invokes the same event handler as the webhook. This makes signature verification/dedup/state logic testable without a real gateway.

## 11. Frontend behavior

Routes:

- `/` public catalog and guest cart.
- `/login` demo login form (shown when a guest proceeds to checkout); after success merge guest cart and load the saved cart.
- `/checkout/:referenceId` payment simulation controls plus current status (can be a single checkout confirmation route).
- `/orders/:referenceId` authenticated order status and item summary; refresh button is sufficient, optional polling every 3 seconds while pending.
- `/orders` authenticated order history with explicit Buy again action.

UI states: initial loading, catalog empty/error, cart empty, invalid quantity, checkout submitting, checkout conflict/out-of-stock, payment pending, payment failed with retry guidance, order confirmed. Disable checkout while submitting; retain idempotency key through retry of the same logical submission and generate a new key only when cart contents change. The server remains authoritative on price, stock, and status.

## 12. Edge cases to implement and demonstrate

- Double click / network retry: same idempotency key returns same order.
- Reused idempotency key with altered cart: `409`.
- Two checkouts racing for last unit: conditional stock update means one succeeds.
- Tampered or absent webhook signature: reject without state change.
- Duplicate event ID: acknowledge but apply once.
- Different event IDs for same success: legal state guard prevents duplicate effect.
- Amount/currency/payment reference mismatch: no confirmation.
- Payment failure: terminal failed state; stock reservation policy documented.
- Late success after failure: do not silently reverse; record for reconciliation.
- Unknown order/payment: no cross-order mutation.
- Invalid quantities, inactive SKU, malformed JSON: clear 4xx response.

## 13. Suggested implementation sequence (4–6 hours)

1. **30 min:** Spring Initializr project, Compose services, Flyway schema, checked-in JSON fixture seeder, demo users, README run command.
2. **45 min:** catalog/cart UI and product API.
3. **60 min:** checkout validation, idempotency, transactional stock reservation, order/payment persistence.
4. **60 min:** raw-body HMAC verification, webhook dedupe and state machine, mock simulator.
5. **45 min:** order status UI, loading/error states, end-to-end manual flow.
6. **30–45 min:** focused tests for invariants and failure paths; cleanup and README design decisions.

Login and cart merge add scope; if time slips, keep the login boundary, basic saved-cart merge, and README intact; reduce visual polish and omit order-history Buy again/bonus first. Do not cut signature validation, idempotency, stock atomicity, or terminal-state protection because those directly match the rubric.

## 14. README design decisions to write (required by assignment)

Include three concrete tradeoffs, for example:

1. **PostgreSQL in Compose vs. embedded database:** adds a container but matches the concurrency/inventory model and gives reviewers a reproducible environment; no cloud DB or orchestration is needed.
2. **Webhook as payment authority vs. browser polling/redirect authority:** signed webhook is authoritative and replay-safe; eventual UI status is an acceptable tradeoff for correctness.
3. **One payment attempt and terminal failure vs. multiple attempts:** simpler state model for the timebox; production would add attempt history and controlled retry policy.

Then point to one implemented failure mode (e.g. duplicate checkout or webhook replay), describe what you would refine after first pass, and add one week-two paragraph covering real gateway adapter, payment reconciliation/outbox, reservation expiry, observability, and authentication/session expiry and per-user order/cart ownership.

## 15. Optional bonus schema

Only if core requirements are complete:

```json
{
  "action": "retry_failed_payment",
  "orderId": "ord_internal_uuid",
  "reason": "Customer reports bank timeout; verify payment status first.",
  "approvedBy": "operator@example.com",
  "approvedAt": "2026-11-05T12:00:00Z"
}
```

The server must validate that the order is eligible and that approval is present; this is a schema/example, not an unrestricted retry endpoint.

## 16. Submission checklist

- Public GitHub repository or ZIP.
- README: prerequisites, exact run steps, environment variables, seed behavior, assumptions, API/demo flow, edge cases, design decisions, tests.
- No real credentials or secrets committed; provide a local example secret only.
- Demonstrate success and at least one failure/replay path.
- Ensure all monetary values use paise and all state is server-derived.
