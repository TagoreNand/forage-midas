-- Midas Core baseline schema (Phase 6). PostgreSQL-flavoured DDL representing the schema the
-- JPA entities expect. Flyway owns the production schema (set spring.jpa.hibernate.ddl-auto=validate
-- and spring.flyway.enabled=true at the Phase 6 cutover); H2 dev keeps ddl-auto=update.

CREATE TABLE user_record (
    id      BIGSERIAL PRIMARY KEY,
    name    VARCHAR(255) NOT NULL,
    balance REAL         NOT NULL DEFAULT 0,
    version BIGINT       NOT NULL DEFAULT 0  -- optimistic-lock token (Phase 2)
);

-- Append-only double-entry ledger. amount_minor is signed (negative = debit, positive = credit);
-- the conservation invariant requires SUM(amount_minor) = 0 across the whole table.
CREATE TABLE ledger_entry (
    id           UUID         PRIMARY KEY,
    account_id   BIGINT       NOT NULL,
    amount_minor BIGINT       NOT NULL,
    transfer_id  UUID         NOT NULL,
    currency     VARCHAR(3)   NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_ledger_transfer ON ledger_entry (transfer_id);
CREATE INDEX idx_ledger_account  ON ledger_entry (account_id, created_at DESC);

-- Transactional outbox (Phase 2): events written atomically with postings, relayed to Kafka.
CREATE TABLE outbox (
    id             UUID         PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id   VARCHAR(255) NOT NULL,
    event_type     VARCHAR(255) NOT NULL,
    payload        VARCHAR(2000) NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    published      BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_outbox_unpublished ON outbox (published, occurred_at);
