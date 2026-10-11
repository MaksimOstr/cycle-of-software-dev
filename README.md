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
| 5 | Integration of the components and dependency management: completed features, integration tests, Docker, CI, requirements fulfillment | [Integration](#integration-of-the-system-laboratory-work-5), [Requirements fulfillment](#requirements-fulfillment), [`docs/lab-5`](docs/lab-5) |

## Technology stack

- Java 25, Spring Boot 4.1 (Spring Web MVC, Bean Validation, Actuator)
- springdoc-openapi 3.1 (OpenAPI 3 description of the REST API and Swagger UI)
- Gradle 9 (wrapper included)
- Docker and Docker Compose for running the application in a container, GitHub Actions for CI
- JUnit 5, AssertJ, Spring MockMvc for tests, JaCoCo for test coverage
- Postman collection run with Newman for testing the API of the running application
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
/docs               documentation of laboratory works 1–3 (specification, architecture, Agile process),
                    Postman collection of laboratory work 5
README.md           this file
.gitignore          files excluded from Git (build output, IDE settings, logs)
build.gradle        build configuration and dependencies
Dockerfile          multi-stage build of the application image
docker-compose.yml  starts the application in a container
.github/workflows   continuous integration (GitHub Actions)
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

## Integration of the system (Laboratory Work 5)

**Integration method.** The architecture is a modular monolith, so all components are integrated locally into one
Spring Boot application: Spring creates the services of every module and connects them through the `api`
interfaces and domain events. The integrated system is then packed into a Docker image, which makes the result
independent of the developer's computer, and is checked from outside through the REST API with a Postman
collection. Microservice communication does not have to be configured.

**Integration points** and the tests that check them:

| Integration point | How the components are connected | Checked by |
|-------------------|----------------------------------|------------|
| Web layer → Identity | `CurrentUserResolver` asks `IdentityApi.findUser` for the role and the blocked flag of the caller | `ShopApiIntegrationTest` |
| Cart → Catalog, Inventory | `CatalogApi.findVariant` gives the current price, `InventoryApi.availableQuantity` limits the quantity | `CartServiceTest`, `SystemIntegrationTest` |
| Ordering → Cart, Delivery, Inventory, Payment | `CheckoutService` calls `CartApi`, `DeliveryApi`, `InventoryApi.reserve` and `PaymentApi.startPayment` | `CheckoutServiceTest`, `SystemIntegrationTest` |
| Delivery → Administration | delivery prices and the free-shipping threshold come from `SettingsApi` | `DeliveryServiceTest`, `SystemIntegrationTest` |
| Payment → Ordering → Inventory | event `PaymentSucceeded` marks the order paid and commits the reserved stock; cancellation restocks and refunds | `OrderServiceTest`, `SystemIntegrationTest` |
| Reviews → Ordering | `PurchaseVerificationApi.hasReceivedProduct` allows a review only after delivery | `ReviewServiceTest`, `SystemIntegrationTest` |
| Administration → Ordering | the sales report reads `SalesStatisticsApi` | `SalesReportServiceTest`, `SystemIntegrationTest` |
| Identity, Ordering → Notification | events `UserRegistered`, `PasswordResetRequested`, `OrderPlaced`, `OrderStatusChanged` become emails | `NotificationServiceTest`, `SystemIntegrationTest` |
| All modules → Audit log | every domain event is recorded by `AuditLogService` | `AdministrationTest`, `SystemIntegrationTest` |
| Module boundaries | a module imports only the `api` package of another module | `ModuleBoundariesTest` |
| Application → environment | Docker image with a health check, the same build in CI | CI job "Docker image", Postman collection |

**How the integration was done:**

1. Components were finished and tested separately first: every feature was developed in its own branch with unit
   tests of its module (see [Git workflow](#git-workflow)).
2. Dependencies were synchronized in `build.gradle` (see [Dependency management](#dependency-management)).
3. The whole system is checked by `SystemIntegrationTest` (two scenarios through the REST API that touch all ten
   modules) and `NonFunctionalRequirementsTest` (response time, simultaneous orders for the last items).
4. The application is started in a container (`docker compose up --build`) and the Postman collection
   [`docs/lab-5/ShopSphere.postman_collection.json`](docs/lab-5/ShopSphere.postman_collection.json) is run against
   it: 49 requests with 80 checks, all pass.
5. GitHub Actions repeats the build, the tests and the container start for every pull request.

Features completed in Lab 5 (they were missing after Lab 4): password recovery, account lock after failed logins,
user management for administrators, editing, hiding and deleting products, renaming and deleting categories,
sorting and pagination of the product list, the order list with filters for managers.

## Getting started

### Requirements

- JDK 25 (Gradle itself and all libraries are downloaded by the Gradle wrapper) **or** Docker with Docker Compose
  (then nothing else has to be installed)
- Free port 8080

### Build and test

```bash
./gradlew build        # compile, run all tests and check the test coverage (Windows: gradlew.bat build)
./gradlew test         # run tests only; report: build/reports/tests/test/index.html
                       # coverage report: build/reports/jacoco/test/html/index.html
```

### Dependency management

All dependencies are declared in one file, [`build.gradle`](build.gradle); the Gradle wrapper downloads them, so
nothing has to be installed manually.

| Dependency | Version | Used for |
|------------|---------|----------|
| `spring-boot-starter-webmvc` | 4.1.1 (Spring Boot BOM) | REST API |
| `spring-boot-starter-validation` | 4.1.1 (Spring Boot BOM) | validation of requests |
| `spring-boot-starter-actuator` | 4.1.1 (Spring Boot BOM) | health check `/actuator/health` |
| `springdoc-openapi-starter-webmvc-ui` | 3.1.1 | OpenAPI 3 document and Swagger UI |
| `spring-boot-starter-test`, `spring-boot-starter-webmvc-test` | 4.1.1 (Spring Boot BOM) | JUnit 5, AssertJ, MockMvc |
| JaCoCo (Gradle plugin) | 0.8.15 | test coverage report and the 70% coverage check |

How the versions are kept synchronized:

- Spring, Jackson, Tomcat, validation and test libraries have no version in `build.gradle`: the version comes from
  the BOM of Spring Boot 4.1.1 (`io.spring.dependency-management` plugin), so they always match each other.
- Versions of the libraries outside the BOM (springdoc, JaCoCo) are declared once in the `ext` block.
- `failOnVersionConflict()` stops the build if two libraries require different versions of one dependency.
- `./gradlew dependencies --configuration runtimeClasspath` prints the resolved dependency tree.

### Continuous integration

The workflow [`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs in GitHub Actions for every pull request
and for every push to `master`:

1. **Build and test** — `./gradlew build` on JDK 25: compilation, all tests, the 70% coverage check; the test and
   coverage reports are attached to the run.
2. **Docker image** — builds the image from the `Dockerfile`, starts the container and waits until the health
   check answers `UP`.

### Run

Locally, with the JDK:

```bash
./gradlew bootRun
```

Or in a container (the image is built from the sources by the multi-stage [`Dockerfile`](Dockerfile), so the
result does not depend on the software installed on the computer):

```bash
docker compose up --build -d      # build the image and start the application
docker compose ps                 # the container becomes "healthy" when /actuator/health answers UP
docker compose logs -f            # application log: demo user ids, emails
docker compose down               # stop and remove the container
```

Another port can be chosen with `SHOPSPHERE_PORT=9090 docker compose up -d`.

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

### API documentation and health check

| URL | What it is |
|-----|------------|
| `http://localhost:8080/swagger-ui.html` | Swagger UI: every endpoint can be called from the browser |
| `http://localhost:8080/v3/api-docs` | OpenAPI 3 description of the REST API (JSON) |
| `http://localhost:8080/actuator/health` | Health check: `{"status":"UP"}` while the application is running |

### Postman collection

[`docs/lab-5/ShopSphere.postman_collection.json`](docs/lab-5/ShopSphere.postman_collection.json) contains the whole
scenario for the running application with demo data: health check, registration and login, catalog management,
cart, checkout, card payment, order processing, review moderation, user management, sales report, audit log and
error cases. Every request saves the ids for the next ones, so nothing has to be copied by hand.

- Postman: *Import* → choose the file → *Run collection*.
- Command line (Node.js is required): `npx newman run docs/lab-5/ShopSphere.postman_collection.json`
- Another address: add `--env-var baseUrl=http://localhost:9090`.

### REST API overview

| Method and path | Description | Access |
|-----------------|-------------|--------|
| `POST /api/v1/auth/register`, `POST /api/v1/auth/login` | Registration and login; the account is locked for 15 minutes after 5 failed logins | everyone |
| `POST /api/v1/auth/password-reset`, `POST /api/v1/auth/password-reset/confirm` | Password recovery: a code is sent by email, is valid for 1 hour and works once | everyone |
| `GET /api/v1/categories`, `GET /api/v1/products?query=&categoryId=&sort=&page=&size=`, `GET /api/v1/products/{id}` | Catalog: category tree (up to 3 levels), search, products of a category with its subcategories, sorting (`NAME`, `PRICE_ASC`, `PRICE_DESC`), pages of 24 products | everyone |
| `POST /api/v1/carts`, `GET /api/v1/carts/{id}`, `POST/PUT/DELETE /api/v1/carts/{id}/items[/{variantId}]`, `POST /api/v1/carts/{id}/promo-code` | Cart | everyone |
| `POST /api/v1/orders` (header `Idempotency-Key`) | Checkout | everyone |
| `GET /api/v1/orders/{number}` | Order tracking | owner of the order and staff; for a guest order — `?email=` used at checkout |
| `GET /api/v1/me/orders`, `POST /api/v1/orders/{number}/cancel` | Order history and cancellation | customer |
| `GET/POST /api/v1/products/{id}/reviews` | Reviews | everyone / customer who received the product |
| `POST /api/v1/payments/webhook` | Payment provider notifications | payment provider |
| `/api/v1/management/**` (create, edit and delete categories and products, hide products, variants, stock, promo codes, order status, review moderation) | Management panel | manager, administrator |
| `GET /api/v1/management/orders?status=&paymentMethod=&deliveryMethod=&from=&to=&search=` | Order list with filters and search by order number, customer email or phone | manager, administrator |
| `/api/v1/admin/**` (delivery settings, audit log, sales report) | Administration | administrator |
| `GET /api/v1/admin/users?query=`, `GET /api/v1/admin/users/{id}`, `POST /api/v1/admin/users/{id}/block`, `POST .../unblock`, `PUT /api/v1/admin/users/{id}/role` | User management: search, block and unblock accounts, assign roles; every change is written to the audit log | administrator |

Every error, including malformed JSON and unknown paths, is returned in one format:
`{"code": "...", "message": "...", "fieldErrors": [...], "timestamp": "..."}`.

| Status | Code | When |
|--------|------|------|
| 400 | `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `BAD_REQUEST` | invalid fields (listed in `fieldErrors`), unreadable JSON or unknown enum value, missing or invalid parameter or header |
| 401 / 403 | `UNAUTHORIZED` / `FORBIDDEN`, `ACCOUNT_BLOCKED` | unknown user or wrong password / the role or owner does not match, the account is blocked by an administrator |
| 404 | `NOT_FOUND` | entity or path does not exist |
| 405 / 415 | `METHOD_NOT_ALLOWED` / `UNSUPPORTED_MEDIA_TYPE` | wrong HTTP method / body is not JSON |
| 409 | `BUSINESS_RULE_VIOLATED` | a business rule is violated, for example "Only 1 item(s) left" |
| 423 | `ACCOUNT_LOCKED` | too many failed login attempts |
| 500 | `INTERNAL_ERROR` | unexpected error; details are written only to the log |

### Tests

| Test | What it checks |
|------|----------------|
| `MoneyTest`, `OrderTest` | Value object arithmetic; every transition of the order state machine (Lab 2, Figure 8) |
| `RegistrationServiceTest`, `AccountSecurityTest`, `UserManagementServiceTest`, `ProductServiceTest`, `InventoryServiceTest`, `CartServiceTest`, `PaymentServiceTest`, `ReviewServiceTest`, `NotificationServiceTest`, `AdministrationTest`, `SalesReportServiceTest`, `DeliveryServiceTest` | Use cases of each module |
| `CheckoutServiceTest`, `OrderServiceTest` | Checkout and payment scenarios of Lab 2, Figures 6 and 7: idempotency, stock reservation, unpaid order expiry, late payment refund, cancellation, return |
| `OrderListTest` | Filters and search of the manager's order list |
| `DeliveryCostPolicyContractTest` | Liskov substitution contract of all delivery cost policies |
| `ModuleBoundariesTest` | Module boundaries and dependency rules of the architecture |
| `ShopApiIntegrationTest`, `CycleOfSoftwareDevApplicationTests` | The whole application through the REST API |
| `SystemIntegrationTest` | Integration of all modules: a cash order from catalog to review and a card order with payment, cancellation and refund; emails, sales report and audit log |
| `NonFunctionalRequirementsTest` | 95% of catalog, search and cart responses within 500 ms on 5,000 products; 20 simultaneous orders for 5 items create exactly 5 orders |
| `PlatformEndpointsTest` | Health check and OpenAPI document |

121 tests; line coverage of the server code is about 95% (JaCoCo), the build fails below 70%.

## Requirements fulfillment

Status of every requirement of the Requirements Specification (Laboratory Work 1). **Done** — works as specified;
**Partial** — the main part works, the difference is described; **Not implemented** — left in the backlog.
The scope of the labs is the server side: the React storefront, PostgreSQL, JWT and the real Stripe, Nova Poshta
and SMTP adapters are the next backlog items, and the code already has ports (interfaces) for them.

### Functional requirements

17 of 44 requirements are done, 16 are partial, 11 are not implemented. Of the 23 High-priority requirements
15 are done and 8 are partial.

| Requirement | Priority | Status | How it is fulfilled |
|-------------|----------|--------|---------------------|
| **3.1 User account management** | | | |
| User registration | High | Partial | `POST /auth/register`: unique email, password of 8+ characters with a letter and a digit, welcome email. The confirmation link is not implemented: the account is active at once |
| Login and logout | High | Partial | `POST /auth/login`; the account is locked for 15 minutes after 5 failed attempts. There are no sessions yet (the caller sends `X-User-Id`), so there is nothing to log out from |
| Password recovery | High | Done | `POST /auth/password-reset` sends a code by email, valid for 1 hour and usable once; `.../confirm` sets the new password |
| Profile editing | Medium | Not implemented | |
| Address book | Medium | Not implemented | The address is entered at checkout |
| Account deletion | Medium | Not implemented | An administrator can block an account |
| Sign in with Google | Low | Not implemented | |
| **3.2 Product catalog and search** | | | |
| Category navigation | High | Done | Tree of up to 3 levels; a category shows the products of all its subcategories |
| Product listing | High | Partial | Pages of 24 products, sorting by name and price, price and availability of every variant. Sorting by newest, popularity and rating, images and discounts are not implemented |
| Product details page | High | Partial | Name, description, variants with price and available quantity, approved reviews. Images, old price and attributes are not implemented |
| Product search | High | Partial | Search by name and description. Suggestions and search by brand are not implemented |
| Product filtering | Medium | Partial | Filter by category only |
| Product variants | High | Done | Every variant has its own SKU, price and stock |
| Recently viewed products | Low | Not implemented | |
| **3.3 Shopping cart and wishlist** | | | |
| Add to cart | High | Partial | Carts for guests and customers. The 30-day storage and merging of the guest cart after login are not implemented |
| Manage cart | High | Done | Change quantity, remove items, quantity is limited by the stock, subtotal and discount are recalculated; the delivery cost is added at checkout |
| Promo codes | Medium | Partial | One code per order, percentage discount, validity period, usage limit. Fixed discount and minimum order amount are not implemented |
| Wishlist | Low | Not implemented | |
| **3.4 Checkout, payment and orders** | | | |
| Checkout | High | Done | Guests and customers; name, email and phone are validated; one request with an idempotency key |
| Delivery methods | High | Done | Courier, Nova Poshta branch, store pickup; free delivery above the threshold |
| Payment methods | High | Done | Card online through the payment provider port (a simulated provider instead of Stripe) or cash on delivery |
| Stock reservation | High | Done | Stock is reserved at checkout; an unpaid card order is cancelled after 30 minutes and the stock is released |
| Order confirmation | High | Done | Unique order number in the response and a confirmation email |
| Order history and tracking | High | Done | `GET /me/orders`, statuses New, Paid, Processing, Shipped, Delivered, Cancelled, Returned, tracking number |
| Order cancellation | Medium | Done | Until the order is shipped; a paid order is refunded through the provider port |
| Return request | Medium | Partial | A manager marks an order as returned within 14 days after delivery: the stock is restored and the payment refunded. The customer's request form with items and a reason is not implemented |
| Invoice download | Low | Not implemented | |
| **3.5 Reviews and ratings** | | | |
| Write a review | Medium | Done | Rating 1–5 and text up to 2,000 characters, only after the product is delivered, one review per product |
| Review moderation | Medium | Partial | A review is published only after approval; a manager approves or rejects it, the rejection reason is not stored |
| **3.6 Notifications** | | | |
| Order status emails | High | Done | Emails when an order is created, paid, shipped, delivered or cancelled (written to the log instead of SMTP) |
| Back-in-stock alerts | Low | Not implemented | |
| **3.7 Catalog and inventory management** | | | |
| Product management | High | Partial | Create, edit, hide, show and delete products, variants with prices. Images and attributes are not implemented |
| Category management | High | Partial | Create, rename and delete; a category with products or subcategories cannot be deleted. Moving a category is not implemented |
| Stock management | High | Done | Stock per variant; the response marks variants below the threshold (5) with `lowStock` |
| Bulk import/export | Low | Not implemented | |
| Promo code management | Medium | Partial | Create a code and see how many times it was used. Editing and deactivation are not implemented |
| **3.8 Order management** | | | |
| Order list | High | Done | Filters by status, dates, payment and delivery method; search by order number, email or phone |
| Order status update | High | Done | Status and tracking number; every change is kept in the order history with the author and time |
| Order editing | Medium | Not implemented | |
| **3.9 Administration and analytics** | | | |
| User management | High | Done | Search users, view details, block and unblock, assign the Manager or Administrator role |
| Sales dashboard | Medium | Partial | Number of orders, revenue and average order value for any period compared with the previous one. Top 10 products is not implemented |
| Report export | Low | Not implemented | |
| Audit log | Medium | Partial | Orders, payments, registrations, account and role changes are recorded and shown to the administrator. Catalog and settings changes are not recorded, there are no filters |
| Store settings | Medium | Partial | Delivery prices and the free-shipping threshold. Store contacts and pickup points are not implemented |

### Non-functional requirements

9 of 26 requirements are met, 7 are partially met, 10 are not met yet (5 of them belong to the frontend that
does not exist yet, the others need the database and the production environment).

| Requirement | Status | Evidence or reason |
|-------------|--------|--------------------|
| **4.1 Performance** | | |
| Server response time (95% within 500 ms) | Partial | `NonFunctionalRequirementsTest`: the 95th percentile of catalog, search and cart requests is below 500 ms on 5,000 products. A load of 1,000 concurrent users was not tested |
| Page load time | Not met | No frontend yet |
| Search speed (1 s for 100,000 products) | Partial | The in-memory search answers in milliseconds on 5,000 products; 100,000 products need the PostgreSQL full-text search |
| Peak order throughput (50 orders per minute) | Partial | 20 simultaneous checkouts are processed in less than a second in the test; a long load test was not done |
| **4.2 Scalability** | | |
| Adding servers | Not met | Data is kept in the memory of one instance; after the move to PostgreSQL the container is stateless and can be multiplied |
| Data volume | Not met | Needs the database |
| **4.3 Reliability and availability** | | |
| Availability 99.5% | Partial | Health check `/actuator/health`, Docker `HEALTHCHECK`, `restart: unless-stopped`, graceful shutdown. There is no production hosting to measure |
| Backups and recovery | Not met | No database yet |
| Order and payment integrity | Met | Idempotency key at checkout, processed webhook events are remembered, stock reservation is atomic: `CheckoutServiceTest`, `PaymentServiceTest`, `SystemIntegrationTest`, `NonFunctionalRequirementsTest` |
| **4.4 Security** | | |
| Encrypted traffic | Not met | The application serves HTTP; TLS is terminated by the Application Load Balancer in the deployment architecture of Lab 2 |
| Password storage | Met | PBKDF2-HMAC-SHA256 with a random salt and 100,000 iterations (`Pbkdf2PasswordHasher`); passwords and hashes are never returned or logged |
| No card data stored | Met | Only the payment session id of the provider is stored |
| Role-based access control | Partial | Every management and administration endpoint checks the role on the server, customers see only their own orders, blocked users are rejected. The caller is identified by the `X-User-Id` header, so real authentication (JWT) is still required |
| Protection from attacks | Partial | Request validation, no SQL, errors never expose internals, account lock after failed logins. The limit of 20 requests per minute per IP is not implemented |
| Secrets management | Met | No secrets in the repository; `.env` and local properties are in `.gitignore`. Demo accounts are switched off with `shopsphere.demo-data.enabled=false` |
| **4.5 Usability and accessibility** | | |
| Ease of ordering, WCAG 2.1 AA, Responsive design, Browser support | Not met (4) | These requirements belong to the storefront, which is not implemented; the API is used through Swagger UI and Postman |
| **4.6 User interface** | | |
| Ukrainian and English | Not met | Messages of the API are in English only |
| Price and date format | Partial | Prices are in UAH with two decimal places; dates are sent in ISO 8601, the DD.MM.YYYY format is the task of the frontend |
| Clear error messages | Met | Validation errors list every field with a plain message (`fieldErrors`); all errors have one format |
| **4.7 Maintainability** | | |
| Test coverage 70% | Met | About 95% of lines; `./gradlew build` fails below 70% (`jacocoTestCoverageVerification`) |
| CI and code review | Met | GitHub Actions builds and tests every pull request; a pull request is merged by the other team member |
| API documentation | Met | OpenAPI 3 document `/v3/api-docs` and Swagger UI, generated by springdoc from the code |
| Logging and monitoring | Met | Application log (including sent emails), health check endpoint used by Docker and CI |

## Team

| Member | Roles | Responsibilities and contribution in Lab 4 |
|--------|-------|-------------------------------------------|
| **Maksym Ostrozhynskyi** | Full-Stack Developer, Team Lead | Project setup and build, shared kernel, Catalog, Inventory, Cart, Ordering (checkout, order state machine, order management), Payment, Delivery, demo data, README. Reviews and merges Ivan's branches. |
| **Ivan Yevseiev** | Full-Stack Developer, QA Lead | Identity, Administration (settings, audit log, sales report), Reviews, Notification, REST API layer (Web & Security), architecture tests and end-to-end API tests. Reviews and merges Maksym's branches. |

Module ownership follows the work distribution of Laboratory Work 3 (Jira project SHOP, Table 6).

Contribution in Lab 5:

| Member | Work |
|--------|------|
| **Maksym Ostrozhynskyi** | Dependency management in `build.gradle` (Actuator, springdoc-openapi, JaCoCo, version conflict check), completion of the catalog (product and category management, sorting, pagination), order list for managers, Dockerfile, Docker Compose and the CI workflow. Reviews Ivan's pull requests. |
| **Ivan Yevseiev** | Account security (password recovery, account lock), user management for administrators, integration tests of the whole system, checks of non-functional requirements, Postman collection, the requirements fulfillment report. Reviews Maksym's pull requests. |

## Git workflow

- `master` contains only reviewed code and always builds.
- Every task is done in its own branch named `feature/<module-or-task>`, by the owner of the module.
- When the work is ready, a pull request to `master` is opened on GitHub. The other team member reviews it
  (code review checklist: module boundaries, SOLID, tests for new logic, all tests pass) and merges it with a
  merge commit, so the history keeps the branch.
- Every branch builds on the previous ones, so the pull requests are merged in the order of the tables below.

Branches of Lab 4 (one pull request per branch, in merge order):

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

Branches of Lab 5 (one feature and one developer per branch, in merge order):

| Branch | Author | Reviewer | Result |
|--------|--------|----------|--------|
| `feature/dependency-management` | Maksym | Ivan | Actuator, springdoc-openapi and JaCoCo in `build.gradle`; synchronized versions |
| `feature/account-security` | Ivan | Maksym | Password recovery, account lock after 5 failed logins |
| `feature/catalog-completion` | Maksym | Ivan | Edit, hide and delete products; rename and delete categories; sorting and pages |
| `feature/user-management` | Ivan | Maksym | Search, block and unblock users, assign roles |
| `feature/order-list` | Maksym | Ivan | Order list with filters and search for managers |
| `feature/docker-and-ci` | Maksym | Ivan | Dockerfile, Docker Compose, GitHub Actions workflow |
| `feature/integration-testing` | Ivan | Maksym | System integration tests, non-functional checks, Postman collection, requirements fulfillment |

## Roadmap

Next items of the product backlog (Laboratory Work 3); they close the requirements that are partial or not
implemented in the [Requirements fulfillment](#requirements-fulfillment) tables:

- PostgreSQL 17 with Flyway migrations and a schema per module instead of in-memory repositories (SHOP-16):
  data volume, backups, several application instances
- JWT authentication with Spring Security instead of the `X-User-Id` header, request rate limit (SHOP-17)
- Stripe Checkout Sessions adapter with webhook signature verification instead of `SimulatedPaymentProvider` (SHOP-39)
- Transactional outbox for domain events and the SMTP email adapter (SHOP-42, SHOP-43)
- React 19 single-page application: Storefront and Management Panel (SHOP-22)
- Email confirmation, profile and address book, product images and attributes, wishlist, report export
