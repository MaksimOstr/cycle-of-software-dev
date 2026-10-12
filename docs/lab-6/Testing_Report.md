# ShopSphere — Testing and Validation Report (Laboratory Work 6)

Team: Maksym Ostrozhynskyi (Team Lead), Ivan Yevseiev (QA Lead).
Repository with the automated tests: <https://github.com/MaksimOstr/cycle-of-software-dev> (folder [`/tests`](../../tests)).

## 1. Preparation

**State of the system.** All ten modules of the modular monolith are finished and integrated (Laboratory Work 5):
one Spring Boot application with PostgreSQL and Redis, started by Docker Compose.

**Requirements to compare with.** The Software Requirements Specification of Laboratory Work 1
([`docs/lab-1/SRS_ShopSphere.docx`](../lab-1/SRS_ShopSphere.docx)): 44 functional requirements (sections 3.1–3.9)
and 26 non-functional requirements (sections 4.1–4.7). Their current status is kept in the
[Requirements fulfillment](../../README.md#requirements-fulfillment) tables of the README; section 5 of this report
lists what the tests of this lab confirmed or changed.

**Tools.**

| Purpose | Tool |
|---------|------|
| Unit, component and system tests | JUnit 5, AssertJ, Mockito (spy of the payment provider) |
| REST API requests in tests | Spring MockMvc with JsonPath |
| Real PostgreSQL 17 and Redis 8 for the tests | Testcontainers (containers are started by the tests, Docker must be running) |
| Test coverage | JaCoCo (the build fails below 70% of lines) |
| API of the running containers | Postman collection [`docs/lab-5`](../lab-5/ShopSphere.postman_collection.json), run with Newman |
| Automation | Gradle tasks per test level, GitHub Actions runs all tests for every pull request |

**Test levels.** Every level has its own Gradle task and its own HTML report (`build/reports/tests/<task>`):

```bash
./gradlew unitTest       # no Spring, no Docker
./gradlew componentTest  # @Tag("component"): a part of the application with real PostgreSQL / Redis, REST API
./gradlew systemTest     # @Tag("system"): user scenarios and non-functional checks on the whole application
./gradlew build          # all levels + coverage check
```

## 2. Results

Run of `./gradlew build` on 12.10.2026 (JDK 25, Windows 11, Docker Desktop):

| Level | Test classes | Tests | Passed | Failed |
|-------|-------------:|------:|-------:|-------:|
| Unit | 29 | 279 | 279 | 0 |
| Component | 16 | 97 | 97 | 0 |
| System | 3 | 10 | 10 | 0 |
| **Total** | **48** | **386** | **386** | **0** |

Before this lab the project had 138 tests (103 unit, 31 component, 4 system).

**Coverage (JaCoCo, production code in `/src`):**

| Counter | Before the lab | After the lab |
|---------|---------------:|--------------:|
| Lines | 94.5% (1688 of 1787) | 98.3% (1762 of 1793) |
| Branches | 78.1% (414 of 530) | 93.5% (501 of 536) |
| Methods | 93.8% | 97.8% |

The Postman collection (54 requests, 91 checks) was run with Newman against the containers started by
`docker compose up --build`. It was repeated on the final code, after the fixes of all six defects of section 4:
all 91 checks passed.

### 2.1 Unit testing

Business rules of every key module are tested without Spring and without a database; services use in-memory
repositories from `/tests`. All 279 tests run in a few seconds.

| Module | Test classes | What is checked |
|--------|--------------|-----------------|
| shared | `MoneyTest` | arithmetic, rounding of percentages to kopecks, currencies |
| catalog | `ProductTest`, `ProductServiceTest` | product and variant data, price of the cheapest variant, unique article number, category tree of 3 levels, paging |
| inventory | `StockItemTest`, `InventoryServiceTest` | reserve / commit / release / restock arithmetic, low-stock threshold |
| cart | `CartTest`, `PromoCodeTest`, `CartServiceTest` | quantity from 1 to 99, discount, validity period and usage limit of a promo code |
| ordering | `OrderTest`, `OrderDetailsTest`, `CheckoutServiceTest`, `OrderServiceTest`, `OrderListTest` | order state machine, contacts and delivery data, totals, idempotent checkout, unpaid order expiry, cancellation, return |
| payment | `PaymentTest`, `PaymentServiceTest` | payment statuses, repeated provider notifications, refund |
| delivery | `DeliveryServiceTest`, `DeliveryCostPolicyContractTest` | delivery cost, free-shipping threshold |
| identity | `UserTest`, `PasswordRulesTest`, `RegistrationServiceTest`, `AccountSecurityTest`, `UserManagementServiceTest` | account lock after 5 failed logins, password policy, PBKDF2 hashing, password recovery, roles |
| reviews | `ReviewTest`, `ReviewServiceTest` | rating 1–5, text limit, moderation, average rating |
| notification, administration | `NotificationServiceTest`, `StoreSettingsTest`, `AdministrationTest`, `SalesReportServiceTest` | emails for events, settings, audit log, sales report |
| architecture | `ModuleBoundariesTest` | a module uses only the `api` package of another module |

### 2.2 Component testing

A component is tested together with what it really works with: the tables of its module in PostgreSQL, Redis,
or the neighbouring modules. The databases are started by Testcontainers.

| Component | Test classes | What is checked |
|-----------|--------------|-----------------|
| Storage of the modules (PostgreSQL) | `CatalogStorageTest`, `InventoryStorageTest`, `CartPersistenceTest`, `OrderStorageTest`, `PaymentStorageTest`, `AccountStorageTest`, `ReviewAndAdministrationStorageTest`, `ProductSearchPostgresTest` | data is read back as it was saved; unique constraints (article number, email, idempotency key, one payment per order, one review per customer); SQL of the catalog search, order list, user search and sales statistics; row locks: 20 simultaneous reservations of 5 items reserve exactly 5 |
| Interaction of Ordering, Cart, Inventory and Payment | `CheckoutTransactionTest` | checkout is one transaction: when the payment provider fails, there is no order, no reservation, the cart is full and the promo code is not spent; payment notification, expiry job, refund of a late payment |
| Security storage (Redis) | `RedisSecurityStorageTest` | only the hash of a refresh token is stored, a token expires and works once for 16 simultaneous requests, the login limit per address |
| Database schema | `DatabaseSchemaTest` | all Liquibase changes are applied to an empty database, one schema per module |
| REST API | `ApiContractTest`, `ShopApiIntegrationTest`, `AuthenticationApiTest`, `PlatformEndpointsTest` | HTTP status and JSON format of every group of endpoints: 200/201 with the documented fields, 400, 401, 403, 404, 409, 415, 423 and 429 in the uniform error format |

### 2.3 System testing

Scenarios of end users go through the REST API only, on the whole application.

| Scenario (`UserScenariosSystemTest`, `SystemIntegrationTest`) | Functions covered |
|---------------------------------------------------------------|-------------------|
| A guest finds a product, pays by card and tracks the order by email | catalog, cart, checkout, payment, order tracking, emails |
| A customer forgot the password | registration, login, logout, account lock, password recovery, order history |
| The manager delivers two orders and takes one back; the administrator reads the sales report | order processing, return with refund, **sales report**, audit log, blocking a user |
| A hidden product and the last items cannot be bought twice | product visibility, stock accuracy |
| A cash order from the catalog to the review; a card order paid once and refunded | all ten modules together |

Non-functional checks (`NonFunctionalRequirementsTest`), measured in the test environment (requests through
MockMvc without a network, PostgreSQL in Docker on a developer laptop):

| Requirement | Limit | Measured | Result |
|-------------|-------|----------|--------|
| Server response time: catalog, search, cart (5,000 products) | 95% within 500 ms | 95th percentile below 500 ms | Pass |
| Search speed on 100,000 products | 1 s | slowest of 6 kinds of requests: 437 ms | Pass |
| Peak order throughput | 50 orders per minute | 50 checkouts of 10 simultaneous customers: 0.8 s | Pass |
| Stock accuracy under simultaneous orders | no overselling | 20 orders for 5 items: 5 accepted, 15 refused | Pass |

Memory use and behaviour under a long load were not measured: this needs a load test of the deployed system
(see the recommendations).

## 3. Validation against the requirements

**Functional requirements.** Every requirement with the status Done or Partial has automated tests of its use
cases. The status of the 44 requirements did not change in this lab: 18 are done, 15 are partial, 11 are not
implemented (the list with explanations is in the README). Testing found that two requirements marked as Done
were not fully met, and both were fixed:

| Requirement | What the tests showed | Now |
|-------------|-----------------------|-----|
| Product variants: "each with its own article number" | the same article number could be given to variants of two products | fixed, Done |
| Checkout: "a guest must provide name, email and phone number" | a phone without a single digit was accepted | fixed, Done |

**Non-functional requirements.**

| Requirement | Before | After | Evidence |
|-------------|--------|-------|----------|
| Search speed (1 s for 100,000 products) | Partial, measured on 5,000 | Met in the test environment | 437 ms on 100,000 products |
| Peak order throughput (50 orders per minute) | Partial | Partial | 50 simultaneous checkouts in 0.8 s; a long peak load together with the response time requirement was not tested |
| Server response time (95% within 500 ms, 1,000 users) | Partial | Partial | met on one user and 5,000 products; 1,000 concurrent users were not tested |
| Order and payment integrity | Met | Met | confirmed on the real database: `CheckoutTransactionTest`, `OrderStorageTest`, `PaymentStorageTest` |
| Password storage, role-based access, protection from attacks | Met | Met | `PasswordRulesTest`, `AccountStorageTest`, `RedisSecurityStorageTest`, `ApiContractTest` |
| Test coverage 70% | Met (94.5%) | Met (98.3%) | JaCoCo |

The other non-functional requirements keep their status: the requirements of sections 4.5 and 4.6 belong to the
storefront that is not implemented, availability and backups need a production environment.

## 4. Defects found

All six defects were found by the new tests and fixed in the same branches; each fix has a test that failed
before it.

| # | Found by | Defect | Impact | Fix |
|---|----------|--------|--------|-----|
| 1 | unit, `OrderDetailsTest` | The phone number of an order was accepted without digits (for example `----------`) | Medium: an order without a usable contact, the manager cannot call the customer | `ContactInfo` requires at least 10 digits |
| 2 | unit, `ProductServiceTest` | The article number was unique only inside one product | Medium: two different goods with one article number in the warehouse and in the order list | check in `ProductService` for the whole catalog and a unique index in the database (change `004-unique-sku.sql`) |
| 3 | unit, `ProductTest` | `Product.update` changed the name before it rejected a missing category | Low: not reachable through the API (the request is validated earlier), but the object stayed half-changed | all data is checked before the first field is changed |
| 4 | unit, `PasswordRulesTest` | A damaged password hash in the database caused an exception instead of "wrong password" | Low: HTTP 500 at login for such an account | `Pbkdf2PasswordHasher.matches` returns `false` |
| 5 | component, `ApiContractTest` | `PATCH /management/reviews/{id}` with an empty body `{}` rejected the review | Medium: a malformed request made a final moderation decision | the field `approved` is required, the answer is 400 |
| 6 | component, `ApiContractTest` | `PUT /management/stock/{variantId}` accepted an id that is not in the catalog | Low: a mistyped id created a stock record of a non-existing variant with the answer 200 | the answer is 404 |

## 5. Weak places and recommendations

Weak places that the testing showed and that are not fixed yet:

1. **Emails are sent before the transaction is committed.** In `CheckoutTransactionTest` the log shows the letter
   "Order … created" for the checkout that was rolled back because the payment provider failed. Recommendation:
   publish domain events after the commit — the transactional outbox that is already planned (SHOP-42).
2. **The usage limit of a promo code is not protected from simultaneous orders.** The code is checked when the
   cart is read and counted at the end of checkout without a row lock (seen in the code while the tests were
   designed, not reproduced by an automated test). Recommendation: count the usage with one
   `UPDATE … WHERE used_count < usage_limit`.
3. **A password reset does not revoke refresh tokens.** Sessions on other devices stay valid for up to 7 days.
   Recommendation: keep the tokens of a user together in Redis and delete them when the password changes.
4. **The catalog search scans the whole table.** 437 ms on 100,000 products is within the limit, but the time
   grows with the catalog. Recommendation: a trigram (`pg_trgm`) or full-text index.
5. **Performance was measured without a network and without real load.** Recommendation: a load test (k6 or
   Gatling) of the application in containers with 1,000 concurrent users, with measurement of memory.
6. **The date in an order number is the UTC date**, so an order placed between 00:00 and 03:00 Kyiv time gets
   the number of the previous day. Recommendation: use the time zone of the store.
7. **The payment notification endpoint does not verify a signature** (the provider is simulated). It has to be
   added together with the Stripe adapter (SHOP-39).

Extension of the functionality: the requirements that are still Partial or Not implemented — the React storefront
(after which the UI can be tested with Selenium or Playwright), email confirmation, profile and address book,
product filters, images, wishlist, report export.

## 6. Conclusions

- 386 automated tests of three levels pass; they cover 98.3% of the lines and 93.5% of the branches of the
  server code.
- The server part meets the functional requirements that are marked as Done; six defects found by the tests are
  fixed, two of them were deviations from the specification.
- The measurable non-functional requirements (response time, search speed, order integrity, security, test
  coverage) are met in the test environment; the requirements that depend on the storefront and on the
  production environment are not met yet and stay in the backlog.
- The weak places of section 5 are proposed as the next items of the backlog; the team has to discuss them
  before the final demonstration.
