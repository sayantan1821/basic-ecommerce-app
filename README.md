# Basic Ecommerce App

Single-repository take-home project: React + TypeScript frontend, Spring Boot API, and PostgreSQL.

## Run with Docker Compose

1. Copy `.env.example` to `.env` and adjust the local demo values if desired.
2. Run `docker compose up --build` from this directory.
3. Open <http://localhost:8080>.
4. Stop with `docker compose down`. Add `-v` only if you intend to delete the local database volume.

Nginx serves the SPA and proxies `/api/*` and `/webhooks/*` to the backend. PostgreSQL data is stored in a named volume. The backend applies Flyway migrations on startup.

## Project layout

- `frontend/`: React, TypeScript, Vite, and Nginx container.
- `backend/`: Java 21, Spring Boot, Maven, and Flyway.
- `docker-compose.yml`: local three-service stack.

All credentials in `.env.example` are local demo values only.

## Catalog and cart

- `GET /api/products` returns active products from PostgreSQL, including `imageUrls` (array), INR price in paise, and available stock.
- Flyway migration `V1__create_products.sql` creates and seeds the initial five-product catalog once. The seed data is local; the UI does not call DummyJSON at runtime.
- Product images are returned as an array and the catalog cards provide previous/next carousel controls. The guest cart is stored in browser local storage. It supports add, remove, quantity changes (up to 10 and available stock), subtotal calculation, and an empty-cart state. Checkout stays disabled until the login/checkout slice is implemented.


## Checkout API (backend slice)

`POST /api/checkout` requires `Authorization: Bearer <DEMO_API_TOKEN>` and an `Idempotency-Key` header. The body contains product IDs and quantities only; prices and totals are calculated from PostgreSQL. A successful first request returns `201`; a same-key, same-cart retry returns the original order/payment with `200`. Reusing the key for a different cart returns `409`.

```json
{
  "items": [
    { "productId": "00000000-0000-4000-8000-000000000001", "quantity": 2 }
  ]
}
```

Stock is conditionally reserved inside the same PostgreSQL transaction that writes order, item snapshots, mock payment intent, and idempotency record. If any item cannot be reserved, the transaction rolls back all stock changes. The mock payment remains `PENDING`; webhook completion is a later slice. The frontend passes the shared demo token as a build-time Vite value (`DEMO_API_TOKEN`), which is exposed in the browser bundle by design and must never be treated as a real secret. Checkout shows a demo-access confirmation before sending the request.
