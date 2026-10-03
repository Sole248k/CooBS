-- ==============================================================================
-- Cloud-side PostgreSQL: one empty database per owning service.
-- Tables are created by each service's Flyway migrations (owned by Dev 5).
-- This folder is separate from database/postgres/, which the audit container mounts whole.
-- ==============================================================================
CREATE DATABASE identity_db;      -- auth-service
CREATE DATABASE customer_db;      -- customer-service
CREATE DATABASE transaction_db;   -- transaction-service (schema txn), orchestration-service (schema saga)
CREATE DATABASE notification_db;  -- notification-service
