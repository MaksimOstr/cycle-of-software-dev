--liquibase formatted sql

--changeset maksym:carts
-- Carts are business data: a customer expects to find the cart after days and after a restart of the server.
CREATE TABLE cart.carts (
    id          uuid PRIMARY KEY,
    -- empty for the cart of a guest
    customer_id uuid,
    promo_code  varchar(64),
    updated_at  timestamptz NOT NULL
);
CREATE INDEX carts_customer_idx ON cart.carts (customer_id);
CREATE INDEX carts_updated_idx ON cart.carts (updated_at);

CREATE TABLE cart.cart_items (
    cart_id    uuid    NOT NULL REFERENCES cart.carts (id) ON DELETE CASCADE,
    position   integer NOT NULL,
    variant_id uuid    NOT NULL,
    quantity   integer NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    PRIMARY KEY (cart_id, position)
);
