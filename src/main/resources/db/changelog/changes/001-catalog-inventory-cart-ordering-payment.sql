--liquibase formatted sql

-- Every module keeps its tables in its own schema (Laboratory Work 2, section 2.4).

--changeset maksym:catalog-schema
CREATE SCHEMA catalog;

CREATE TABLE catalog.categories (
    id        uuid PRIMARY KEY,
    name      varchar(255) NOT NULL,
    parent_id uuid REFERENCES catalog.categories (id)
);

CREATE TABLE catalog.products (
    id          uuid PRIMARY KEY,
    name        varchar(255)  NOT NULL,
    description varchar(2000) NOT NULL,
    category_id uuid          NOT NULL REFERENCES catalog.categories (id),
    active      boolean       NOT NULL
);
CREATE INDEX products_category_idx ON catalog.products (category_id);

CREATE TABLE catalog.product_variants (
    product_id uuid           NOT NULL REFERENCES catalog.products (id) ON DELETE CASCADE,
    position   integer        NOT NULL,
    id         uuid           NOT NULL UNIQUE,
    sku        varchar(255)   NOT NULL,
    name       varchar(255),
    price      numeric(12, 2) NOT NULL CHECK (price > 0),
    PRIMARY KEY (product_id, position)
);

--changeset maksym:inventory-schema
CREATE SCHEMA inventory;

CREATE TABLE inventory.stock_items (
    variant_id          uuid PRIMARY KEY,
    on_hand             integer NOT NULL CHECK (on_hand >= 0),
    reserved            integer NOT NULL CHECK (reserved >= 0),
    low_stock_threshold integer NOT NULL,
    CHECK (reserved <= on_hand)
);

CREATE TABLE inventory.stock_reservations (
    id         uuid PRIMARY KEY,
    order_id   uuid        NOT NULL,
    variant_id uuid        NOT NULL,
    quantity   integer     NOT NULL CHECK (quantity > 0),
    expires_at timestamptz,
    status     varchar(20) NOT NULL
);
CREATE INDEX stock_reservations_order_idx ON inventory.stock_reservations (order_id);

--changeset maksym:cart-schema
CREATE SCHEMA cart;

CREATE TABLE cart.promo_codes (
    code             varchar(64) PRIMARY KEY,
    discount_percent integer     NOT NULL CHECK (discount_percent BETWEEN 1 AND 90),
    valid_until      timestamptz NOT NULL,
    usage_limit      integer     NOT NULL,
    used_count       integer     NOT NULL
);

--changeset maksym:ordering-schema
CREATE SCHEMA ordering;

CREATE SEQUENCE ordering.order_number_seq;

CREATE TABLE ordering.orders (
    id               uuid PRIMARY KEY,
    number           varchar(32)    NOT NULL UNIQUE,
    -- a repeated checkout request with the same key must never create a second order
    idempotency_key  varchar(255)   NOT NULL UNIQUE,
    customer_id      uuid,
    contact_name     varchar(255)   NOT NULL,
    contact_email    varchar(255)   NOT NULL,
    contact_phone    varchar(32)    NOT NULL,
    delivery_method  varchar(20)    NOT NULL,
    delivery_address varchar(500),
    tracking_number  varchar(64),
    payment_method   varchar(20)    NOT NULL,
    promo_code       varchar(64),
    subtotal         numeric(12, 2) NOT NULL,
    discount         numeric(12, 2) NOT NULL,
    delivery_cost    numeric(12, 2) NOT NULL,
    total            numeric(12, 2) NOT NULL,
    status           varchar(20)    NOT NULL,
    created_at       timestamptz    NOT NULL,
    payment_deadline timestamptz,
    delivered_at     timestamptz
);
CREATE INDEX orders_customer_idx ON ordering.orders (customer_id);
CREATE INDEX orders_status_idx ON ordering.orders (status);
CREATE INDEX orders_created_idx ON ordering.orders (created_at);

CREATE TABLE ordering.order_items (
    order_id     uuid           NOT NULL REFERENCES ordering.orders (id) ON DELETE CASCADE,
    position     integer        NOT NULL,
    variant_id   uuid           NOT NULL,
    product_id   uuid           NOT NULL,
    sku          varchar(255),
    product_name varchar(600),
    unit_price   numeric(12, 2) NOT NULL,
    quantity     integer        NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (order_id, position)
);
CREATE INDEX order_items_product_idx ON ordering.order_items (product_id);

CREATE TABLE ordering.order_status_history (
    order_id    uuid        NOT NULL REFERENCES ordering.orders (id) ON DELETE CASCADE,
    position    integer     NOT NULL,
    from_status varchar(20),
    to_status   varchar(20) NOT NULL,
    changed_by  uuid,
    changed_at  timestamptz NOT NULL,
    note        varchar(500),
    PRIMARY KEY (order_id, position)
);

--changeset maksym:payment-schema
CREATE SCHEMA payment;

CREATE TABLE payment.payments (
    id                  uuid PRIMARY KEY,
    order_id            uuid           NOT NULL UNIQUE,
    provider_session_id varchar(255)   NOT NULL UNIQUE,
    payment_url         varchar(500)   NOT NULL,
    amount              numeric(12, 2) NOT NULL,
    created_at          timestamptz    NOT NULL,
    status              varchar(20)    NOT NULL
);

-- ids of the provider events that were already handled: a repeated webhook is ignored
CREATE TABLE payment.processed_webhook_events (
    event_id     varchar(255) PRIMARY KEY,
    processed_at timestamptz NOT NULL
);
