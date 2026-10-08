# ShopSphere — Online Store

ShopSphere is an online store for a small retail business: a catalog with product variants, a cart with promo
codes, checkout with online card payment or cash on delivery, order tracking, reviews and a management panel
for managers and administrators.

The project is developed during the "Software Development Life Cycle" laboratory works:

| Lab | Result | Location |
|-----|--------|----------|
| 1 | Requirements elicitation and Software Requirements Specification | [`docs/lab-1`](docs/lab-1) |
| 2 | Software architecture (UML component, deployment, class, sequence and state diagrams) | [`docs/lab-2`](docs/lab-2) |
| 3 | Agile process in Jira: backlog, Sprint 1, estimates, assignment | [`docs/lab-3`](docs/lab-3) |
| 4 | Basic code structure: modules, classes and functions following SOLID | this repository (`src`, `tests`) |

## Technology stack

- Java 25, Spring Boot 4.1 (Spring Web MVC, Bean Validation)
- Gradle 9 (wrapper included)
- JUnit 5, AssertJ, Spring MockMvc for tests
- Storage: in-memory repositories in this stage. PostgreSQL 17 with Flyway, JWT security, Stripe, Nova Poshta
  and SMTP adapters are the next backlog items (see [Roadmap](#roadmap)).

## Architecture

The backend is a **modular monolith** (Laboratory Work 2, section 2.2): one Spring Boot application divided into
ten business modules with explicit public interfaces. Every module has the same internal layers:

```
<module>/
  api/             public contract for other modules: interfaces, DTOs, domain events
  domain/          entities, value objects, business rules, repository and gateway interfaces
  application/     use cases (application services), one class per group of related use cases
  infrastructure/  implementations of the domain interfaces (in-memory repositories, provider adapters)
```

Rules (checked automatically by `ModuleBoundariesTest`):

1. A module may use only the `api` package of another module, never its domain or services.
2. The shared kernel (`shared`) does not depend on any module.
3. The domain model does not depend on Spring or on infrastructure classes.

Modules communicate synchronously through `api` interfaces (for example, `CheckoutService` → `InventoryApi.reserve`)
and asynchronously through domain events (`OrderPlaced`, `OrderStatusChanged`, `PaymentSucceeded`,
`UserRegistered`) published through `DomainEventPublisher`. Notifications and the audit log listen to these events.

The REST API (`web` package) corresponds to the Web & Security Layer: controllers, request validation, the
uniform error format and role checks.

## Repository structure

```
/src/main/java/com/cycleofsoftwaredev
  CycleOfSoftwareDevApplication.java   application entry point
  shared/           shared kernel: Money, Actor, exceptions, DomainEvent, DomainEventPublisher
  identity/         users, registration, login, password hashing
  catalog/          categories, products, variants, search
  inventory/        stock and reservations for orders
  cart/             carts and promo codes
  ordering/         Order aggregate and its state machine, checkout, order management
  payment/          online payments, provider webhooks, refunds
  delivery/         delivery methods and delivery cost
  reviews/          product reviews and moderation
  notification/     emails sent in reaction to domain events
  administration/   store settings, audit log, sales dashboard
  web/              REST controllers, error handling, current user resolution
  bootstrap/        demo data for local runs
/src/main/resources application.properties
/tests/java         unit, architecture and integration tests (Gradle test source set)
/docs               documentation of laboratory works 1–3 (specification, architecture, Agile process)
README.md           this file
.gitignore          files excluded from Git (build output, IDE settings, logs)
build.gradle        build configuration
```

## Modules

| Module | Responsibility | Public API | Owner |
|--------|----------------|------------|-------|
| shared | Money value object, Actor, domain exceptions, event bus abstraction | `Money`, `DomainEventPublisher` | Maksym |
| identity | Registration, login, users and roles | `IdentityApi`, `UserRegistered` | Ivan |
| catalog | Category tree, products, variants, search | `CatalogApi` | Maksym |
| inventory | Stock quantities, reservation / commit / release / restock | `InventoryApi` | Maksym |
| cart | Cart of a guest or customer, promo codes | `CartApi` | Maksym |
| ordering | Checkout with idempotency key, order state machine, cancellation, returns, unpaid order expiry | `PurchaseVerificationApi`, `SalesStatisticsApi`, order events | Maksym |
| payment | Payment sessions, idempotent webhook handling, refunds | `PaymentApi`, `PaymentSucceeded` | Maksym |
| delivery | Delivery methods and cost with free-shipping threshold | `DeliveryApi` | Maksym |
| reviews | Reviews of purchased products, moderation | — | Ivan |
| notification | Emails for registration and order status changes | — (event listener) | Ivan |
| administration | Store settings, audit log, sales report | `SettingsApi` | Ivan |
| web | REST API, validation, uniform errors, role checks | `/api/v1/**` | Ivan |

## SOLID in the code

| Principle | Where it is applied |
|-----------|---------------------|
| **S** — Single Responsibility | `User` keeps only user data; password hashing is in `PasswordHasher`, password rules in `PasswordPolicy`, login in `AuthenticationService`, registration in `RegistrationService`. `CheckoutService` places orders, `OrderService` manages them after checkout, `OrderQueryService` only reads. |
| **O** — Open/Closed | `DeliveryService` receives all `DeliveryCostPolicy` beans: a new delivery method is a new policy class, `DeliveryService` is not changed (see `DeliveryServiceTest.newDeliveryMethodIsAddedWithoutChangingTheService`). `NotificationService` works with a list of `EmailComposer`s: a new email is a new composer. |
| **L** — Liskov Substitution | All `DeliveryCostPolicy` implementations (`CourierCostPolicy`, `NovaPoshtaCostPolicy`, `StorePickupCostPolicy`) follow one contract — the cost is never null or negative and does not grow with the subtotal. `DeliveryCostPolicyContractTest` runs the same checks against every implementation. In-memory repositories can be replaced with database ones without changing the services. |
| **I** — Interface Segregation | The Ordering module exposes two narrow interfaces instead of one: `PurchaseVerificationApi` for Reviews and `SalesStatisticsApi` for the sales dashboard. The payment provider port is split into `PaymentGateway` (sessions) and `RefundGateway` (refunds). |
| **D** — Dependency Inversion | Services depend on abstractions: repositories (`OrderRepository`, `UserRepository`, …), gateways (`PaymentGateway`, `EmailSender`), other modules' APIs (`InventoryApi`, `CartApi`, …), `DomainEventPublisher` and `Clock`. Concrete classes (`InMemoryOrderRepository`, `SimulatedPaymentProvider`, `LoggingEmailSender`) are injected by Spring, so PostgreSQL, Stripe or SMTP can be plugged in without changing business code. |

## Getting started

### Requirements

- JDK 25 (Gradle itself and all libraries are downloaded by the Gradle wrapper)
- Free port 8080

### Build and test

```bash
./gradlew build        # compile and run all tests (Windows: gradlew.bat build)
./gradlew test         # run tests only; report: build/reports/tests/test/index.html
```

### Run

```bash
./gradlew bootRun
```

The application starts on `http://localhost:8080` and loads demo data: categories, products with stock and the
promo code `WELCOME10`. Demo accounts (password in brackets):

| Role | Email |
|------|-------|
| Administrator | `admin@shopsphere.local` (`Admin12345`) |
| Manager | `manager@shopsphere.local` (`Manager12345`) |
| Customer | `customer@shopsphere.local` (`Customer12345`) |

Until JWT authentication is implemented (backlog item SHOP-17), the caller is identified by the `X-User-Id`
header. The ids of the demo users are printed in the log at startup, and `POST /api/v1/auth/login` returns the id
of any user.

### Example: buy a product

```bash
# 1. find a product and take a variant id
curl "http://localhost:8080/api/v1/products?query=hoodie"

# 2. create a cart and add the variant
curl -X POST http://localhost:8080/api/v1/carts
curl -X POST http://localhost:8080/api/v1/carts/{cartId}/items \
     -H "Content-Type: application/json" -d '{"variantId":"{variantId}","quantity":2}'
curl -X POST http://localhost:8080/api/v1/carts/{cartId}/promo-code \
     -H "Content-Type: application/json" -d '{"code":"WELCOME10"}'

# 3. place the order (the Idempotency-Key protects against double submission)
curl -X POST http://localhost:8080/api/v1/orders -H "Content-Type: application/json" \
     -H "Idempotency-Key: 7d4f6c1e-0001" \
     -d '{"cartId":"{cartId}","fullName":"Petro Customer","email":"petro@example.com","phone":"+380501234567",
          "deliveryMethod":"NOVA_POSHTA","deliveryAddress":"Kyiv, branch 12","paymentMethod":"CARD_ONLINE"}'

# 4. simulate the payment provider webhook (sessionId is the last part of paymentUrl)
curl -X POST http://localhost:8080/api/v1/payments/webhook -H "Content-Type: application/json" \
     -d '{"id":"evt_1","type":"checkout.session.completed","sessionId":"{sessionId}"}'

# 5. the order is now PAID (a guest tracks the order with the email used at checkout)
curl "http://localhost:8080/api/v1/orders/{orderNumber}?email=petro@example.com"
```

Emails are written to the application log (`EMAIL to=...`).

### REST API overview

| Method and path | Description | Access |
|-----------------|-------------|--------|
| `POST /api/v1/auth/register`, `POST /api/v1/auth/login` | Registration and login | everyone |
| `GET /api/v1/categories`, `GET /api/v1/products?query=&categoryId=`, `GET /api/v1/products/{id}` | Catalog | everyone |
| `POST /api/v1/carts`, `GET /api/v1/carts/{id}`, `POST/PUT/DELETE /api/v1/carts/{id}/items[/{variantId}]`, `POST /api/v1/carts/{id}/promo-code` | Cart | everyone |
| `POST /api/v1/orders` (header `Idempotency-Key`) | Checkout | everyone |
| `GET /api/v1/orders/{number}` | Order tracking | owner of the order and staff; for a guest order — `?email=` used at checkout |
| `GET /api/v1/me/orders`, `POST /api/v1/orders/{number}/cancel` | Order history and cancellation | customer |
| `GET/POST /api/v1/products/{id}/reviews` | Reviews | everyone / customer who received the product |
| `POST /api/v1/payments/webhook` | Payment provider notifications | payment provider |
| `/api/v1/management/**` (categories, products, variants, stock, promo codes, order status, review moderation) | Management panel | manager, administrator |
| `/api/v1/admin/**` (delivery settings, audit log, sales report) | Administration | administrator |

Every error, including malformed JSON and unknown paths, is returned in one format:
`{"code": "...", "message": "...", "fieldErrors": [...], "timestamp": "..."}`.

| Status | Code | When |
|--------|------|------|
| 400 | `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `BAD_REQUEST` | invalid fields (listed in `fieldErrors`), unreadable JSON or unknown enum value, missing or invalid parameter or header |
| 401 / 403 | `UNAUTHORIZED` / `FORBIDDEN` | unknown user or wrong password / the role or owner does not match |
| 404 | `NOT_FOUND` | entity or path does not exist |
| 405 / 415 | `METHOD_NOT_ALLOWED` / `UNSUPPORTED_MEDIA_TYPE` | wrong HTTP method / body is not JSON |
| 409 | `BUSINESS_RULE_VIOLATED` | a business rule is violated, for example "Only 1 item(s) left" |
| 500 | `INTERNAL_ERROR` | unexpected error; details are written only to the log |

### Tests

| Test | What it checks |
|------|----------------|
| `MoneyTest`, `OrderTest` | Value object arithmetic; every transition of the order state machine (Lab 2, Figure 8) |
| `RegistrationServiceTest`, `ProductServiceTest`, `InventoryServiceTest`, `CartServiceTest`, `PaymentServiceTest`, `ReviewServiceTest`, `NotificationServiceTest`, `AdministrationTest`, `SalesReportServiceTest`, `DeliveryServiceTest` | Use cases of each module |
| `CheckoutServiceTest`, `OrderServiceTest` | Checkout and payment scenarios of Lab 2, Figures 6 and 7: idempotency, stock reservation, unpaid order expiry, late payment refund, cancellation, return |
| `DeliveryCostPolicyContractTest` | Liskov substitution contract of all delivery cost policies |
| `ModuleBoundariesTest` | Module boundaries and dependency rules of the architecture |
| `ShopApiIntegrationTest`, `CycleOfSoftwareDevApplicationTests` | The whole application through the REST API |

## Team

| Member | Roles | Responsibilities and contribution in Lab 4 |
|--------|-------|-------------------------------------------|
| **Maksym Ostrozhynskyi** | Full-Stack Developer, Team Lead | Project setup and build, shared kernel, Catalog, Inventory, Cart, Ordering (checkout, order state machine, order management), Payment, Delivery, demo data, README. Reviews and merges Ivan's branches. |
| **Ivan Yevseiev** | Full-Stack Developer, QA Lead | Identity, Administration (settings, audit log, sales report), Reviews, Notification, REST API layer (Web & Security), architecture tests and end-to-end API tests. Reviews and merges Maksym's branches. |

Module ownership follows the work distribution of Laboratory Work 3 (Jira project SHOP, Table 6).

## Git workflow

- `master` contains only reviewed code and always builds.
- Every task is done in its own branch named `feature/<module-or-task>`, by the owner of the module.
- When the work is ready, the other team member reviews the changes (code review checklist: module boundaries,
  SOLID, tests for new logic, all tests pass) and merges the branch with `git merge --no-ff`, so the history keeps
  the branch and the reviewer as the author of the merge commit.

Branches of Lab 4:

| Branch | Author | Reviewer |
|--------|--------|----------|
| `feature/project-skeleton` | Maksym | Ivan |
| `feature/identity-module` | Ivan | Maksym |
| `feature/catalog-module` | Maksym | Ivan |
| `feature/inventory-module` | Maksym | Ivan |
| `feature/administration-module` | Ivan | Maksym |
| `feature/delivery-module` | Maksym | Ivan |
| `feature/payment-module` | Maksym | Ivan |
| `feature/architecture-tests` | Ivan | Maksym |
| `feature/cart-module` | Maksym | Ivan |
| `feature/ordering-module` | Maksym | Ivan |
| `feature/reviews-module` | Ivan | Maksym |
| `feature/notification-module` | Ivan | Maksym |
| `feature/sales-dashboard` | Ivan | Maksym |
| `feature/rest-api` | Ivan | Maksym |
| `feature/demo-data-and-docs` | Maksym | Ivan |

Fixes after the code review of the whole Lab 4 code base (also made in separate branches):

| Branch | Author | Reviewer | Fix |
|--------|--------|----------|-----|
| `fix/inventory-reservation` | Maksym | Ivan | Several lines of one variant are reserved together; an order cannot reserve twice |
| `fix/api-review-findings` | Ivan | Maksym | Uniform errors for all invalid requests; order details only for the owner and staff; locale-independent email subjects |
| `docs/readme-review-fixes` | Maksym | Ivan | README updated for the fixes |

## Roadmap

Next items of the product backlog (Laboratory Work 3):

- PostgreSQL 17 with Flyway migrations and a schema per module instead of in-memory repositories (SHOP-16)
- JWT authentication with Spring Security instead of the `X-User-Id` header (SHOP-17)
- Stripe Checkout Sessions adapter with webhook signature verification instead of `SimulatedPaymentProvider` (SHOP-39)
- Transactional outbox for domain events and the SMTP email adapter (SHOP-42, SHOP-43)
- React 19 single-page application: Storefront and Management Panel (SHOP-22)
- CI pipeline in GitHub Actions (SHOP-15)
