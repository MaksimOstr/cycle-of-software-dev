--liquibase formatted sql

--changeset ivan:identity-schema
CREATE SCHEMA identity;

CREATE TABLE identity.users (
    id            uuid PRIMARY KEY,
    email         varchar(255) NOT NULL UNIQUE,
    full_name     varchar(255) NOT NULL,
    -- only the PBKDF2 hash of the password is stored
    password_hash varchar(255) NOT NULL,
    role          varchar(20)  NOT NULL,
    registered_at timestamptz  NOT NULL,
    failed_logins integer      NOT NULL,
    locked_until  timestamptz,
    blocked       boolean      NOT NULL
);

CREATE TABLE identity.password_reset_tokens (
    token      varchar(64) PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES identity.users (id) ON DELETE CASCADE,
    expires_at timestamptz NOT NULL
);
CREATE INDEX password_reset_tokens_user_idx ON identity.password_reset_tokens (user_id);

--changeset ivan:reviews-schema
CREATE SCHEMA reviews;

CREATE TABLE reviews.reviews (
    id          uuid PRIMARY KEY,
    product_id  uuid          NOT NULL,
    customer_id uuid          NOT NULL,
    rating      integer       NOT NULL CHECK (rating BETWEEN 1 AND 5),
    review_text varchar(2000) NOT NULL,
    created_at  timestamptz   NOT NULL,
    status      varchar(20)   NOT NULL,
    -- one review per product per customer
    UNIQUE (product_id, customer_id)
);

--changeset ivan:administration-schema
CREATE SCHEMA administration;

CREATE TABLE administration.audit_log (
    id          uuid PRIMARY KEY,
    event_type  varchar(100)  NOT NULL,
    details     varchar(4000) NOT NULL,
    occurred_at timestamptz   NOT NULL
);
CREATE INDEX audit_log_occurred_idx ON administration.audit_log (occurred_at);

-- the store has one row of settings
CREATE TABLE administration.store_settings (
    id                      integer PRIMARY KEY CHECK (id = 1),
    store_name              varchar(255)   NOT NULL,
    courier_price           numeric(12, 2) NOT NULL,
    nova_poshta_price       numeric(12, 2) NOT NULL,
    free_shipping_threshold numeric(12, 2) NOT NULL
);

INSERT INTO administration.store_settings (id, store_name, courier_price, nova_poshta_price, free_shipping_threshold)
VALUES (1, 'ShopSphere', 120.00, 80.00, 2000.00);
