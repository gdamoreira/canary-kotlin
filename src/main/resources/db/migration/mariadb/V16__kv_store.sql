-- Persistent key/value store for the KV engine.
--
-- Canary defines `kv_store` in schema.sql:861-867 with a MySQL primary key on
-- `key_name`, a `timestamp` column and a `longblob` value; `KVSQL::load/loadPrefix`
-- query it by exact key and by `LIKE 'prefix%'` and `KVSQL::save` upserts or deletes
-- the row (src/kv/kv_sql.cpp:21-108). The value column holds the serialized
-- `Canary.protobuf.kv.ValueWrapper` message from src/protobuf/kv.proto, so it
-- stays a LONGBLOB. `value` is backtick-quoted to stay clear of keyword
-- ambiguity.
CREATE TABLE IF NOT EXISTS kv_store (
    key_name VARCHAR(191) NOT NULL,
    `timestamp` BIGINT NOT NULL,
    `value` LONGBLOB NOT NULL,
    CONSTRAINT kv_store_pk PRIMARY KEY (key_name)
);
