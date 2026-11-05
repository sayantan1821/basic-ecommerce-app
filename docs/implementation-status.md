# Implementation Status Against the Prepared LLD

The original build plan and low-level design is preserved in [`low-level-design.md`](low-level-design.md). This note records where the shipped code differs from that proposed design, so the plan is not mistaken for an exact description of every current implementation detail.

## Current architecture

- **Frontend:** React + TypeScript + Vite. `App.tsx` coordinates catalog, local cart, auth, checkout, payment return, and order history components.
- **Backend:** Java 21 + Spring Boot MVC, with separate `controller`, `service`, `repository`, `model`, `dto`, and `security` packages.
- **Database:** PostgreSQL with Flyway migrations and a persistent Docker volume.
- **Runtime:** one repository and one Docker Compose stack for Nginx frontend, backend API, and PostgreSQL.
- **Catalog seed:** five products and their image URL arrays are inserted/updated by Flyway. There is no runtime DummyJSON dependency or `CommandLineRunner` seed.

## Prepared design vs current implementation

| Area | Prepared LLD | Current code |
| --- | --- | --- |
| Catalog | Checked-in JSON fixture read at startup | Five product records seeded by Flyway migrations |
| Cart | Proposed authenticated cart tables and idempotent guest-cart merge | Browser-local cart is retained when signing in on that browser; there is no account-synced cart or cross-device merge |
| Authentication | Small demo login, with a short sample expiry | Email/password signup and login plus one Flyway-seeded demo user; opaque bearer tokens expire after 12 hours and only their SHA-256 hashes are stored |
| Checkout | Server-side prices, stock checks, and idempotency | Implemented transactionally; idempotency key is scoped by user, same-key/same-cart replays the result, and a different cart conflicts |
| Payment provider | Deterministic mock provider and local simulator | Stripe test-mode Checkout; no local mock simulator is implemented |
| Webhook path | `POST /webhooks/payment` with HMAC-SHA256 | `POST /webhooks/stripe` with Stripe SDK signature verification over the raw body |
| Payment events | Mock signed events and deduplication | Stripe event-ID deduplication, payment/order transitions, metadata/amount/currency checks, and stock release on failed/expired payment |
| Order history | Authenticated and owner-scoped | Implemented at `GET /api/orders`; individual order lookup and reconciliation are also owner-scoped |

The central assignment deviation is payment provider and route: the current Stripe implementation supplies a real signed event lifecycle but does not satisfy the assignment's mock-provider and `/webhooks/payment` interface. The prepared LLD's mock simulator and server-side cart merge are design proposals, not current endpoints or tables.

## Current API surface

| Method and path | Access | Purpose |
| --- | --- | --- |
| `GET /api/products` | Public | List the seeded active catalog |
| `POST /api/auth/signup` | Public | Create an account and session |
| `POST /api/auth/login` | Public | Sign in with email/password |
| `POST /api/auth/demo` | Public | Sign in as the migration-seeded demo account |
| `POST /api/auth/logout` | Bearer token | Revoke the current session |
| `POST /api/checkout` | Bearer token + `Idempotency-Key` | Validate cart, reserve stock, and persist order/payment |
| `POST /api/payments/{paymentId}/stripe-session` | Bearer token | Create or reuse an owned payment's Stripe Checkout Session |
| `GET /api/orders` | Bearer token | List the authenticated user's orders |
| `GET /api/orders/by-reference/{referenceId}` | Bearer token | Read an owned order and payment status |
| `POST /api/orders/by-reference/{referenceId}/reconcile` | Bearer token | Explicitly reconcile a returned Stripe session |
| `POST /webhooks/stripe` | Stripe signature | Verify, deduplicate, and process Stripe Checkout events |

## Database entities currently used

The schema is managed by migrations under `backend/src/main/resources/db/migration/`:

- `products`: seeded SKU, descriptions, image URL array, price in paise, active flag, and available stock.
- `app_users` and `auth_sessions`: normalized account identity and hashed, expiring/revocable bearer sessions.
- `orders` and `order_items`: user-owned order state and immutable item/SKU/price snapshots.
- `payments`: one payment per order, provider reference, Stripe session/intent fields, status, failure code, and `paid_at`.
- `checkout_idempotency`: composite user/key identity, normalized-cart hash, and resulting order.
- `webhook_events`: provider event ID, type, payload hash, processing timestamps, and result.

There are no `cart_items` or `cart_merges` tables in the current schema.

## Verification status and remaining work

JUnit 5/Mockito tests cover representative checkout validation/idempotency and stock failure cases, Stripe webhook signature/dedupe/success/expiry behavior, and user-scoped order queries. They have been added but not run in this environment because Maven and a Maven wrapper were unavailable. Transaction rollback, concurrent stock reservation against PostgreSQL, and a complete browser-to-webhook integration flow still need verification.

For strict assignment compliance, implement the mock payment adapter/simulator and `POST /webhooks/payment`, add tests for that contract, and run the suite. Keep Stripe as an optional adapter if desired. The server-side cart and cross-device merge described in the prepared LLD are additional product scope; the current same-browser cart continuity covers the present UI flow but not that expanded design.
