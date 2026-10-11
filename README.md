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

- Java 25, Spring Boot 4.1 (Spring Web MVC, Spring Security, Spring Data JPA, Bean Validation, Actuator)
- springdoc-openapi 3.1 (OpenAPI 3 description of the REST API and Swagger UI)
- Gradle 9 (wrapper included)
- Docker and Docker Compose for running the application in a container, GitHub Actions for CI
- JUnit 5, AssertJ, Spring MockMvc for tests, JaCoCo for test coverage
- Postman collection run with Newman for testing the API of the running application
- PostgreSQL 17: Spring Data JPA (Hibernate) for the repositories, Liquibase for the schema (changes are written
  in SQL), one database schema per module (`catalog`, `inventory`, `cart`, `ordering`, `payment`, `identity`, `reviews`,
  `administration`). All data is stored there, including carts: the cart of a customer is kept until it is
  ordered or emptied, the cart of a guest is removed 30 days after its last change.
- Spring Security with JWT: stateless authentication, short-lived access tokens signed with HMAC-SHA256,
  refresh tokens, access rules by role
- Redis 8: refresh tokens and the counters of login attempts — short-lived data that expires by itself
- Testcontainers: the integration tests run on a real PostgreSQL and Redis started in Docker

## Architecture

The backend is a **modular monolith** (Laboratory Work 2, section 2.2): one Spring Boot application divided into
ten business modules with explicit public interfaces. Every module has the same internal layers:

```
<module>/
  api/             public contract for other modules: interfaces, DTOs, domain events
  domain/          entities, value objects, business rules, repository and gateway interfaces
  application/     use cases (application services), one class per group of related use cases
  infrastructure/  implementations of the domain interfaces (Spring Data JPA repositories, provider adapters)
```

Rules (checked automatically by `ModuleBoundariesTest`):

1. A module may use only the `api` package of another module, never its domain or services.
2. The shared kernel (`shared`) does not depend on any module.
3. The domain model does not depend on Spring or on infrastructure classes. Entities carry only the standard
   JPA mapping annotations (`jakarta.persistence`); the repositories are interfaces of the domain, implemented
   in `infrastructure` by Spring Data.

Modules communicate synchronously through `api` interfaces (for example, `CheckoutService` → `InventoryApi.reserve`)
and asynchronously through domain events (`OrderPlaced`, `OrderStatusChanged`, `PaymentSucceeded`,
`UserRegistered`) published through `DomainEventPublisher`. Notifications and the audit log listen to these events.

The REST API (`web` package) corresponds to the Web & Security Layer: controllers, request validation, the
uniform error format, JWT authentication (`web.security`) and role checks.

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
  web/security/     Spring Security configuration, JWT access tokens, refresh tokens, login rate limit
  bootstrap/        demo data for local runs
/src/main/resources application.properties
                    db/changelog: Liquibase changelog, schema changes in SQL
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
| **L** — Liskov Substitution | All `DeliveryCostPolicy` implementations (`CourierCostPolicy`, `NovaPoshtaCostPolicy`, `StorePickupCostPolicy`) follow one contract — the cost is never null or negative and does not grow with the subtotal. `DeliveryCostPolicyContractTest` runs the same checks against every implementation. The repositories are substitutable too: the unit tests run the services with in-memory repositories and the application runs them with Spring Data JPA ones. |
| **I** — Interface Segregation | The Ordering module exposes two narrow interfaces instead of one: `PurchaseVerificationApi` for Reviews and `SalesStatisticsApi` for the sales dashboard. The payment provider port is split into `PaymentGateway` (sessions) and `RefundGateway` (refunds). |
| **D** — Dependency Inversion | Services depend on abstractions: repositories (`OrderRepository`, `UserRepository`, …), gateways (`PaymentGateway`, `EmailSender`), other modules' APIs (`InventoryApi`, `CartApi`, …), `DomainEventPublisher` and `Clock`. Concrete classes (`JpaOrderRepository`, `SimulatedPaymentProvider`, `LoggingEmailSender`) are injected by Spring. The move from in-memory storage to PostgreSQL replaced only the repository implementations, the services were not changed; Stripe or SMTP can be plugged in the same way. |

## Integration of the system (Laboratory Work 5)

**Integration method.** The architecture is a modular monolith, so all components are integrated locally into one
Spring Boot application: Spring creates the services of every module and connects them through the `api`
interfaces and domain events. The application is integrated with its infrastructure — PostgreSQL and Redis —
through Docker Compose, and the whole system is packed into containers, which makes the result independent of
the developer's computer. It is checked from outside through the REST API with a Postman collection.
Microservice communication does not have to be configured.

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
| Web layer → Spring Security | every request passes the security filter chain: the JWT is validated, the role is checked by URL rules, then by `CurrentUserResolver` | `AuthenticationApiTest`, `ShopApiIntegrationTest` |
| Application → PostgreSQL | Spring Data JPA repositories, Liquibase changelog, transactions and row locks of the services | all Spring tests (Testcontainers), `CartPersistenceTest` |
| Application → Redis | refresh tokens and the login rate limit through `StringRedisTemplate` | `AuthenticationApiTest` |
| Application → environment | Docker Compose: PostgreSQL, Redis and the application image with health checks; the same in CI | CI job "Docker image", Postman collection |

**How the integration was done:**

1. Components were finished and tested separately first: every feature was developed in its own branch with unit
   tests of its module (see [Git workflow](#git-workflow)).
2. Dependencies were synchronized in `build.gradle` (see [Dependency management](#dependency-management)).
3. The whole system is checked by `SystemIntegrationTest` (two scenarios through the REST API that touch all ten
   modules) and `NonFunctionalRequirementsTest` (response time, simultaneous orders for the last items).
4. The application is started in a container (`docker compose up --build`) and the Postman collection
   [`docs/lab-5/ShopSphere.postman_collection.json`](docs/lab-5/ShopSphere.postman_collection.json) is run against
   it: 54 requests with 91 checks, all pass.
5. GitHub Actions repeats the build, the tests and the container start for every pull request.

Features completed in Lab 5 (they were missing after Lab 4): password recovery, account lock after failed logins,
user management for administrators, editing, hiding and deleting products, renaming and deleting categories,
sorting and pagination of the product list, the order list with filters for managers.

After that the application was moved from in-memory storage to real infrastructure:

- **PostgreSQL** stores all data (users, catalog, stock, carts, promo codes, orders, payments, reviews, audit log,
  settings); the schema is created by Liquibase changes written in SQL, one database schema per module.
- **Transactions**: every use case of a service is one transaction. Checkout either reserves the stock, saves
  the order and creates the payment, or changes nothing. Stock rows and orders are locked with
  `SELECT ... FOR UPDATE` while they are changed, so simultaneous orders cannot oversell.
- **Queries**: the catalog page, the order list and the sales statistics are computed by PostgreSQL
  (`WHERE`, `ORDER BY`, `LIMIT`, aggregates), so a request loads only the 24 products or 50 orders of its page,
  not the whole table; the stock of all products of a page is read with one query.
- **JWT**: login returns an access token (15 minutes) and a refresh token (7 days); the server keeps no session.
- **Redis** keeps what must expire by itself: refresh tokens (so logout and token rotation work) and the
  counters that limit login and registration to 20 requests per minute per IP address.

## Getting started

### Requirements

- Docker with Docker Compose: PostgreSQL and Redis run in containers, and the tests start their own PostgreSQL
  and Redis with Testcontainers
- JDK 25 to build and run from the sources (Gradle and all libraries are downloaded by the Gradle wrapper);
  it is not needed if the application is started in a container too
- Free ports 8080 (application), 5432 (PostgreSQL) and 6379 (Redis)

### Build and test

```bash
# Docker must be running: the integration tests start PostgreSQL and Redis in containers
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
| `spring-boot-starter-data-jpa` | 4.1.1 (Spring Boot BOM), Hibernate 7.4 | repositories and transactions |
| `spring-boot-starter-liquibase` | 4.1.1 (Spring Boot BOM), Liquibase 5.0 | database schema migrations |
| `org.postgresql:postgresql` | 42.7 (Spring Boot BOM) | PostgreSQL JDBC driver |
| `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-resource-server` | 4.1.1 (Spring Boot BOM), Spring Security 7.1 | authentication, access rules, signing and validation of JWT |
| `spring-boot-starter-data-redis` | 4.1.1 (Spring Boot BOM), Lettuce 7.5 | Redis client: refresh tokens, login rate limit |
| `spring-boot-testcontainers`, `testcontainers-postgresql` | 4.1.1 / 2.0 (Spring Boot BOM) | PostgreSQL and Redis in Docker for the integration tests |
| `spring-boot-starter-test`, `spring-boot-starter-webmvc-test` | 4.1.1 (Spring Boot BOM) | JUnit 5, AssertJ, MockMvc |
| JaCoCo (Gradle plugin) | 0.8.15 | test coverage report and the 70% coverage check |

How the versions are kept synchronized:

- Spring, Jackson, Tomcat, validation and test libraries have no version in `build.gradle`: the version comes from
  the BOM of Spring Boot 4.1.1 (`io.spring.dependency-management` plugin), so they always match each other.
- Versions of the libraries outside the BOM (springdoc, JaCoCo) are declared once in the `ext` block.
- `failOnVersionConflict()` stops the build if two libraries require different versions of one dependency.
  It found one conflict: Liquibase needs `commons-text` 1.15.0 and springdoc 1.13.1, so the version is fixed
  explicitly (`commonsTextVersion`, the newer one is compatible with both).
- `./gradlew dependencies --configuration runtimeClasspath` prints the resolved dependency tree.

### Continuous integration

The workflow [`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs in GitHub Actions for every pull request
and for every push to `master`:

1. **Build and test** — `./gradlew build` on JDK 25: compilation, all tests (PostgreSQL and Redis are started by
   Testcontainers), the 70% coverage check; the test and coverage reports are attached to the run.
2. **Docker image** — `docker compose up --build --wait` builds the image and starts it with PostgreSQL and Redis;
   the job passes when all health checks pass, the catalog answers and the demo administrator can sign in.

### Run

The application needs PostgreSQL and Redis. [`docker-compose.yml`](docker-compose.yml) starts them with the
database, user, password and ports that the application uses by default, so nothing has to be configured.

**Databases in Docker, the application from the sources** (convenient for development and for a demo):

```bash
docker compose up -d postgres redis   # PostgreSQL 17 on localhost:5432, Redis 8 on localhost:6379
./gradlew bootRun                     # the application on http://localhost:8080
```

**Everything in containers** (the image is built from the sources by the multi-stage [`Dockerfile`](Dockerfile)):

```bash
docker compose up --build -d      # PostgreSQL, Redis and the application
docker compose ps                 # all three containers become "healthy"
docker compose logs -f shopsphere # application log: applied migrations, demo user ids, emails
docker compose down               # stop; the data stays in the volume
docker compose down -v            # stop and delete the data
```

| Setting | Environment variable | Default |
|---------|----------------------|---------|
| Database URL | `SHOPSPHERE_DB_URL` | `jdbc:postgresql://localhost:5432/shopsphere` |
| Database user and password | `SHOPSPHERE_DB_USER`, `SHOPSPHERE_DB_PASSWORD` | `shopsphere` / `shopsphere` (local development only) |
| Redis host and port | `SHOPSPHERE_REDIS_HOST`, `SHOPSPHERE_REDIS_PORT` | `localhost`, `6379` |
| Signing key of the access tokens (at least 32 characters) | `SHOPSPHERE_JWT_SECRET` | a development placeholder from `application.properties`: the application starts without setup and tokens stay valid after a restart |
| Ports published by Docker Compose | `SHOPSPHERE_PORT`, `POSTGRES_PORT`, `REDIS_PORT` | `8080`, `5432`, `6379` |
| Password of the PostgreSQL container | `POSTGRES_PASSWORD` | `shopsphere` |

The defaults (database password `shopsphere`, the placeholder JWT key) are meant only for a local run and a
demo; the application writes a warning to the log while the placeholder key is used. Real values are never
written into the repository: for Docker Compose copy [`.env.example`](.env.example) to `.env` (the file is in
`.gitignore`) and set `POSTGRES_PASSWORD` and `SHOPSPHERE_JWT_SECRET` there.

At startup Liquibase applies the changes from [`src/main/resources/db/changelog`](src/main/resources/db/changelog)
that are not in the database yet, and Hibernate checks that the entities match the schema
(`spring.jpa.hibernate.ddl-auto=validate`). A schema change is a new SQL file in `db/changelog/changes`
added to `db.changelog-master.yaml`; applied files are never edited.

The application starts on `http://localhost:8080` and loads demo data: categories, products with stock and the
promo code `WELCOME10` (only into an empty database). Demo accounts (password in brackets):

| Role | Email |
|------|-------|
| Administrator | `admin@shopsphere.local` (`Admin12345`) |
| Manager | `manager@shopsphere.local` (`Manager12345`) |
| Customer | `customer@shopsphere.local` (`Customer12345`) |

### Authentication

The API uses JWT. `POST /api/v1/auth/login` checks the email and the password and returns two tokens:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" \
     -d '{"email":"manager@shopsphere.local","password":"Manager12345"}'
# {"accessToken":"eyJhbGciOiJIUzI1NiJ9...","refreshToken":"...","tokenType":"Bearer","expiresIn":900,"user":{...}}

curl http://localhost:8080/api/v1/management/orders -H "Authorization: Bearer <accessToken>"
```

- The **access token** is a JWT signed with HMAC-SHA256. It contains the user id, the role and the expiry time
  (15 minutes) and is sent with every request in the `Authorization: Bearer` header. The server keeps no session,
  so any instance of the application can serve the request.
- The **refresh token** (7 days) is exchanged for a new pair with `POST /api/v1/auth/refresh`. It works once:
  the used token is removed from Redis and a new one is issued. `POST /api/v1/auth/logout` removes the refresh
  token, after that new access tokens cannot be obtained.
- Access rules are checked by Spring Security: `/api/v1/admin/**` needs the Administrator role,
  `/api/v1/management/**` — Manager or Administrator, the order history, cancellation and reviews — any signed-in
  user. The catalog, carts, checkout and order tracking by email are open for guests.
- The user is also read from the database on every request, so a blocked account is rejected at once, although
  its token has not expired yet.
- Login, registration and password reset are limited to 20 requests per minute from one IP address (HTTP 429).

In Swagger UI: call `POST /api/v1/auth/login`, copy `accessToken`, press **Authorize** and paste it.

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
| `http://localhost:8080/swagger-ui.html` | Swagger UI: every endpoint can be called from the browser; the **Authorize** button accepts the access token |
| `http://localhost:8080/v3/api-docs` | OpenAPI 3 description of the REST API (JSON) |
| `http://localhost:8080/actuator/health` | Health check: `{"status":"UP"}` while the application, PostgreSQL and Redis are working |

### Postman collection

[`docs/lab-5/ShopSphere.postman_collection.json`](docs/lab-5/ShopSphere.postman_collection.json) contains the whole
scenario for the running application with demo data: health check, registration, login and token refresh, catalog
management, cart, checkout, card payment, order processing, review moderation, user management, sales report,
audit log, error cases and logout — 54 requests with 91 checks. Every request saves the ids and the access
tokens for the next ones, so nothing has to be copied by hand.

- Postman: *Import* → choose the file → *Run collection*.
- Command line (Node.js is required): `npx newman run docs/lab-5/ShopSphere.postman_collection.json`
- The collection signs in several times, so it can be run twice a minute; the third run in the same minute gets
  HTTP 429 from the login rate limit.
- Another address: add `--env-var baseUrl=http://localhost:9090`.

### REST API overview

| Method and path | Description | Access |
|-----------------|-------------|--------|
| `POST /api/v1/auth/register`, `POST /api/v1/auth/login` | Registration; login returns the access and refresh tokens. The account is locked for 15 minutes after 5 failed logins | everyone |
| `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me` | New pair of tokens for a refresh token; logout (the refresh token is revoked); the signed-in user | everyone with a refresh token / signed-in user |
| `POST /api/v1/auth/password-reset`, `POST /api/v1/auth/password-reset/confirm` | Password recovery: a code is sent by email, is valid for 1 hour and works once | everyone |
| `GET /api/v1/categories`, `GET /api/v1/products?query=&categoryId=&sort=&page=&size=`, `GET /api/v1/products/{id}` | Catalog: category tree (up to 3 levels), search, products of a category with its subcategories, sorting (`NAME`, `PRICE_ASC`, `PRICE_DESC`), pages of 24 products | everyone |
| `POST /api/v1/carts`, `GET /api/v1/carts/{id}`, `POST/PUT/DELETE /api/v1/carts/{id}/items[/{variantId}]`, `POST /api/v1/carts/{id}/promo-code` | Cart | everyone |
| `POST /api/v1/orders` (header `Idempotency-Key`) | Checkout | everyone |
| `GET /api/v1/orders/{number}` | Order tracking | owner of the order and staff; for a guest order — `?email=` used at checkout |
| `GET /api/v1/me/orders`, `POST /api/v1/orders/{number}/cancel` | Order history and cancellation | customer |
| `GET/POST /api/v1/products/{id}/reviews` | Reviews | everyone / customer who received the product |
| `POST /api/v1/payments/webhook` | Payment provider notifications | payment provider |
| `/api/v1/management/**` (create, edit and delete categories and products, hide products, variants, stock, promo codes, order status, review moderation) | Management panel | manager, administrator |
| `GET /api/v1/management/orders?status=&paymentMethod=&deliveryMethod=&from=&to=&search=&page=&size=` | Order list with filters and search by order number, customer email or phone; newest first, 50 per page | manager, administrator |
| `/api/v1/admin/**` (delivery settings, audit log, sales report) | Administration | administrator |
| `GET /api/v1/admin/users?query=`, `GET /api/v1/admin/users/{id}`, `POST /api/v1/admin/users/{id}/block`, `POST .../unblock`, `PUT /api/v1/admin/users/{id}/role` | User management: search, block and unblock accounts, assign roles; every change is written to the audit log | administrator |

Every error, including malformed JSON and unknown paths, is returned in one format:
`{"code": "...", "message": "...", "fieldErrors": [...], "timestamp": "..."}`.

| Status | Code | When |
|--------|------|------|
| 400 | `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `BAD_REQUEST` | invalid fields (listed in `fieldErrors`), unreadable JSON or unknown enum value, missing or invalid parameter or header |
| 401 / 403 | `UNAUTHORIZED` / `FORBIDDEN`, `ACCOUNT_BLOCKED` | no access token, an invalid or expired one, wrong password / the role or owner does not match, the account is blocked by an administrator |
| 404 | `NOT_FOUND` | entity or path does not exist |
| 405 / 415 | `METHOD_NOT_ALLOWED` / `UNSUPPORTED_MEDIA_TYPE` | wrong HTTP method / body is not JSON |
| 409 | `BUSINESS_RULE_VIOLATED`, `CONFLICT` | a business rule is violated, for example "Only 1 item(s) left"; the same data is being changed by another request |
| 429 | `TOO_MANY_REQUESTS` | more than 20 login, registration or password reset requests per minute from one address |
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
| `ShopApiIntegrationTest`, `CycleOfSoftwareDevApplicationTests` | The whole application through the REST API, on PostgreSQL and Redis in Testcontainers |
| `AuthenticationApiTest` | JWT: login, forged and expired tokens, access rules by role, refresh token rotation, logout, blocked users, login rate limit |
| `CartPersistenceTest` | Carts in PostgreSQL: items keep their order, abandoned guest carts are removed after 30 days |
| `ProductSearchPostgresTest` | Catalog search executed by PostgreSQL: text in name and description, category with subcategories, sorting, paging, special characters |
| `SystemIntegrationTest` | Integration of all modules: a cash order from catalog to review and a card order with payment, cancellation and refund; emails, sales report and audit log |
| `NonFunctionalRequirementsTest` | 95% of catalog, search and cart responses within 500 ms on 5,000 products; 20 simultaneous orders for 5 items create exactly 5 orders |
| `PlatformEndpointsTest` | Health check and OpenAPI document |

138 tests; line coverage of the server code is about 94% (JaCoCo), the build fails below 70%. The unit tests of
the services use in-memory repositories from `/tests` and run in milliseconds; every test that starts the
application works with the real PostgreSQL and Redis.

## Requirements fulfillment

Status of every requirement of the Requirements Specification (Laboratory Work 1). **Done** — works as specified;
**Partial** — the main part works, the difference is described; **Not implemented** — left in the backlog.
The scope of the labs is the server side with real storage and security: PostgreSQL, Redis and JWT are in place.
The React storefront and the adapters of external services (Stripe, Nova Poshta, SMTP) are not implemented;
the code has ports (interfaces) for them and simulated implementations.

### Functional requirements

18 of 44 requirements are done, 15 are partial, 11 are not implemented. Of the 23 High-priority requirements
16 are done and 7 are partial.

| Requirement | Priority | Status | How it is fulfilled |
|-------------|----------|--------|---------------------|
| **3.1 User account management** | | | |
| User registration | High | Partial | `POST /auth/register`: unique email, password of 8+ characters with a letter and a digit, welcome email. The confirmation link is not implemented: the account is active at once |
| Login and logout | High | Done | `POST /auth/login` returns a JWT access token and a refresh token; `POST /auth/logout` revokes the refresh token. The account is locked for 15 minutes after 5 failed attempts |
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
| Add to cart | High | Partial | Carts for guests and customers are stored in PostgreSQL; the cart of a guest is removed 30 days after its last change. Merging of the guest cart after login is not implemented |
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

10 of 26 requirements are met, 8 are partially met, 8 are not met yet (5 of them belong to the frontend that
does not exist yet, the others need the production environment).

| Requirement | Status | Evidence or reason |
|-------------|--------|--------------------|
| **4.1 Performance** | | |
| Server response time (95% within 500 ms) | Partial | `NonFunctionalRequirementsTest`: the 95th percentile of catalog, search and cart requests is below 500 ms on 5,000 products. A load of 1,000 concurrent users was not tested |
| Page load time | Not met | No frontend yet |
| Search speed (1 s for 100,000 products) | Partial | PostgreSQL filters, sorts and returns only the requested page (`JdbcProductSearch`); on 5,000 products (the size of the store catalog) a search takes milliseconds. Not measured on 100,000 products: the text is searched as a substring, which scans the table, so a full-text or trigram index is the next step |
| Peak order throughput (50 orders per minute) | Partial | 20 simultaneous checkouts are processed in less than a second in the test; a long load test was not done |
| **4.2 Scalability** | | |
| Adding servers | Partial | The application is stateless: no session (JWT), all data in PostgreSQL and Redis, order numbers from a database sequence, row locks instead of in-process locks. Running several instances behind a load balancer was not tested |
| Data volume | Partial | Data is stored in PostgreSQL with indexes on the lookup columns. The catalog search, the order list and the sales statistics are SQL queries that return one page or one aggregate. The audit log, the user search and the product list of the Management Panel still load whole tables |
| **4.3 Reliability and availability** | | |
| Availability 99.5% | Partial | Health check `/actuator/health` covers the application, PostgreSQL and Redis; Docker `HEALTHCHECK`, `restart: unless-stopped`, graceful shutdown. There is no production hosting to measure |
| Backups and recovery | Not met | The data is kept in a Docker volume and survives restarts, but there is no automatic backup |
| Order and payment integrity | Met | Idempotency key with a unique constraint, processed webhook events are remembered, checkout is one transaction, stock rows are locked while reserved: `CheckoutServiceTest`, `PaymentServiceTest`, `SystemIntegrationTest`, `NonFunctionalRequirementsTest` |
| **4.4 Security** | | |
| Encrypted traffic | Not met | The application serves HTTP; TLS is terminated by the Application Load Balancer in the deployment architecture of Lab 2 |
| Password storage | Met | PBKDF2-HMAC-SHA256 with a random salt and 100,000 iterations (`Pbkdf2PasswordHasher`); passwords and hashes are never returned or logged |
| No card data stored | Met | Only the payment session id of the provider is stored |
| Role-based access control | Met | Spring Security validates the JWT of every request and checks the role by URL; customers see only their own orders; a blocked account and a changed role take effect at once: `AuthenticationApiTest` |
| Protection from attacks | Met | Request validation, parameterized queries through JPA (SQL injection), errors never expose internals, account lock after 5 failed logins, 20 login and registration requests per minute per IP address (Redis). XSS protection belongs to the frontend |
| Secrets management | Partial | The JWT signing key and the database password are read from environment variables (`.env` is in `.gitignore`, `.env.example` shows the names). For a run without setup the repository contains development placeholders for both, and the application warns in the log while the placeholder key is used; a secrets store is not connected. Demo accounts are switched off with `shopsphere.demo-data.enabled=false` |
| **4.5 Usability and accessibility** | | |
| Ease of ordering, WCAG 2.1 AA, Responsive design, Browser support | Not met (4) | These requirements belong to the storefront, which is not implemented; the API is used through Swagger UI and Postman |
| **4.6 User interface** | | |
| Ukrainian and English | Not met | Messages of the API are in English only |
| Price and date format | Partial | Prices are in UAH with two decimal places; dates are sent in ISO 8601, the DD.MM.YYYY format is the task of the frontend |
| Clear error messages | Met | Validation errors list every field with a plain message (`fieldErrors`); all errors have one format |
| **4.7 Maintainability** | | |
| Test coverage 70% | Met | About 94% of lines; `./gradlew build` fails below 70% (`jacocoTestCoverageVerification`) |
| CI and code review | Met | GitHub Actions builds and tests every pull request; a pull request is merged by the other team member |
| API documentation | Met | OpenAPI 3 document `/v3/api-docs` and Swagger UI, generated by springdoc from the code |
| Logging and monitoring | Met | Application log (including sent emails), health check endpoint used by Docker and CI |

## Team

| Member | Roles | Responsibilities and contribution in Lab 4 |
|--------|-------|-------------------------------------------|
| **Maksym Ostrozhynskyi** | Full-Stack Developer, Team Lead | Project setup and build, shared kernel, Catalog, Inventory, Cart, Ordering (checkout, order state machine, order management), Payment, Delivery, demo data, README. Reviews and merges Ivan's branches. |
| **Ivan Yevseiev** | Full-Stack Developer, QA Lead | Identity, Administration (settings, audit log, sales report), Reviews, Notification, REST API layer (Web & Security), architecture tests and end-to-end API tests. Reviews and merges Maksym's branches. |

Module ownership follows the work distribution of Laboratory Work 3 (Jira project SHOP, Table 6).

Contribution in Lab 5 (integration):

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

Branches of the move to real storage and security (after Lab 5, in merge order):

| Branch | Author | Reviewer | Result |
|--------|--------|----------|--------|
| `feature/postgres-persistence` | Maksym | Ivan | PostgreSQL, Liquibase, JPA for catalog, stock, promo codes, orders and payments; transactions and row locks; Testcontainers; PostgreSQL in Docker Compose and CI |
| `feature/postgres-accounts` | Ivan | Maksym | Users, password reset tokens, reviews, audit log and store settings in PostgreSQL |
| `feature/postgres-carts` | Maksym | Ivan | Carts in PostgreSQL, removal of abandoned guest carts |
| `feature/jwt-security` | Ivan | Maksym | Spring Security, JWT access and refresh tokens, logout, login rate limit, Redis, Postman collection with tokens |
| `feature/sql-search` | Maksym | Ivan | Catalog search, order list and sales statistics as SQL queries with paging; stock of a page in one query |

## Roadmap

Next items of the product backlog (Laboratory Work 3); they close the requirements that are partial or not
implemented in the [Requirements fulfillment](#requirements-fulfillment) tables:

- Full-text or trigram index for the catalog search; filters by price, brand and attributes
- Stripe Checkout Sessions adapter with webhook signature verification instead of `SimulatedPaymentProvider` (SHOP-39)
- Transactional outbox for domain events and the SMTP email adapter (SHOP-42, SHOP-43)
- React 19 single-page application: Storefront and Management Panel (SHOP-22)
- Email confirmation, profile and address book, product images and attributes, wishlist, report export
- Production environment: HTTPS on the load balancer, database backups, several application instances
