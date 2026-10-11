--liquibase formatted sql

--changeset maksym:unique-sku
-- An article number belongs to one variant in the whole catalog, whatever the letter case. The service checks it
-- before saving; the index also stops two managers who add the same article number at the same moment.
CREATE UNIQUE INDEX product_variants_sku_idx ON catalog.product_variants (lower(sku));
