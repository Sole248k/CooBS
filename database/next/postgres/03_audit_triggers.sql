-- ==============================================================================
-- 03_audit_triggers.sql: Forensic SHA-256 Chaining & Immutability Enforcement
-- Reference: docs/ENTERPRISE_ARCHITECTURE_DEFENSE_DOSSIER.md Section 7.2
-- ==============================================================================

SET search_path TO audit_store, public;

-- 1. TRIGGER FUNCTION: Cryptographic SHA-256 Hash Chaining
CREATE OR REPLACE FUNCTION audit_store.fn_compute_ledger_hash_chain()
RETURNS TRIGGER AS $$
DECLARE
    v_last_hash VARCHAR(64);
    v_payload TEXT;
BEGIN
    -- Retrieve the most recent hash for this account (or globally)
    SELECT current_hash INTO v_last_hash
    FROM audit_store.ledger_mutation_audit
    WHERE account_id = NEW.account_id
    ORDER BY audit_id DESC
    LIMIT 1;

    -- If first transaction for this account, use genesis seed
    IF v_last_hash IS NULL THEN
        v_last_hash := '0000000000000000000000000000000000000000000000000000000000000000';
    END IF;

    NEW.previous_hash := v_last_hash;

    -- Compute SHA-256 hash across tamper-critical fields including reference_no
    v_payload := v_last_hash || '|' ||
                 NEW.transaction_id || '|' ||
                 NEW.account_id || '|' ||
                 COALESCE(NEW.reference_no, '') || '|' ||
                 NEW.transaction_type || '|' ||
                 NEW.amount || '|' ||
                 NEW.new_balance || '|' ||
                 to_char(NEW.event_timestamp, 'YYYY-MM-DD"T"HH24:MI:SS.USOF');

    NEW.current_hash := encode(digest(v_payload, 'sha256'), 'hex');

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_compute_hash_chain ON audit_store.ledger_mutation_audit;
CREATE TRIGGER trg_compute_hash_chain
BEFORE INSERT ON audit_store.ledger_mutation_audit
FOR EACH ROW
EXECUTE FUNCTION audit_store.fn_compute_ledger_hash_chain();


-- 2. TRIGGER FUNCTION: Strict Immutability Guard (Blocks UPDATE and DELETE)
CREATE OR REPLACE FUNCTION audit_store.fn_enforce_audit_immutability()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'COMPLIANCE VIOLATION: Immutable audit ledger cannot be modified or deleted (ERRCODE 55000)'
        USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_prevent_audit_tampering ON audit_store.ledger_mutation_audit;
CREATE TRIGGER trg_prevent_audit_tampering
BEFORE UPDATE OR DELETE ON audit_store.ledger_mutation_audit
FOR EACH ROW
EXECUTE FUNCTION audit_store.fn_enforce_audit_immutability();
