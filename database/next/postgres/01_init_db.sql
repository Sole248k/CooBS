-- ==============================================================================
-- 01_init_db.sql: PostgreSQL Audit Store Database & Schema Initialization
-- Architecture Role: Segregated Forensic Audit & Compliance Datastore
-- ==============================================================================

-- Install cryptographic extension for SHA-256 digest computation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create dedicated schema for the Immutable Audit Store
CREATE SCHEMA IF NOT EXISTS audit_store;

-- Configure search path exclusively for audit_store
ALTER DATABASE audit_store SET search_path TO audit_store, public;
