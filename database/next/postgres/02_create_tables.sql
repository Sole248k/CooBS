-- ==============================================================================
-- 02_create_tables.sql: PostgreSQL Immutable Forensic Audit Store Schema
-- Architecture Role: Append-only, Tamper-Evident Transaction Mutation Audit
-- Reference: docs/ENTERPRISE_ARCHITECTURE_DEFENSE_DOSSIER.md Section 7.2
-- ==============================================================================

SET search_path TO audit_store, public;

-- ==============================================================================
-- IMMUTABLE AUDIT STORE TABLE
-- Holds forensic records of all balance mutations committed in Oracle Master
-- ==============================================================================
CREATE TABLE IF NOT EXISTS audit_store.ledger_mutation_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    reference_no VARCHAR(36),
    transaction_type VARCHAR(30) NOT NULL,
    amount NUMERIC(18, 4) NOT NULL,
    old_balance NUMERIC(18, 4) NOT NULL,
    new_balance NUMERIC(18, 4) NOT NULL,
    previous_hash VARCHAR(64),
    current_hash VARCHAR(64) NOT NULL,
    actor_id BIGINT,
    client_ip VARCHAR(45) DEFAULT '127.0.0.1',
    event_timestamp TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- PERFORMANCE & COMPLIANCE QUERY INDEXES
CREATE INDEX IF NOT EXISTS idx_audit_store_account ON audit_store.ledger_mutation_audit(account_id);
CREATE INDEX IF NOT EXISTS idx_audit_store_transaction ON audit_store.ledger_mutation_audit(transaction_id);
CREATE INDEX IF NOT EXISTS idx_audit_store_timestamp ON audit_store.ledger_mutation_audit(event_timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_store_hash ON audit_store.ledger_mutation_audit(current_hash);
