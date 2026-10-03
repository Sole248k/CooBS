-- ==============================================================================
-- 01_init_user.sql: Setup Core User and Privileges for Oracle Database
-- ==============================================================================

-- Ensure we operate within the application pluggable database (XEPDB1)
ALTER SESSION SET CONTAINER = XEPDB1;

-- Grant tablespace quota and permissions to application user
ALTER USER core_user QUOTA UNLIMITED ON USERS;
GRANT CREATE TABLE, CREATE VIEW, CREATE SEQUENCE, CREATE PROCEDURE, CREATE TRIGGER TO core_user;

ALTER SESSION SET CURRENT_SCHEMA = CORE_USER;

BEGIN
    EXECUTE IMMEDIATE 'GRANT CREATE TABLE, CREATE VIEW, CREATE SEQUENCE, CREATE PROCEDURE, CREATE TRIGGER TO core_user';
EXCEPTION
    WHEN OTHERS THEN
        NULL;
END;
/
