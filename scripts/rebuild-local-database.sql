-- Local-only reset for the databases documented in docs/DATABASE.md.
-- Run this file as the PostgreSQL superuser, for example:
--   psql -h 127.0.0.1 -U postgres -d postgres -f scripts/rebuild-local-database.sql
-- This intentionally removes all local development and test data.

DROP DATABASE IF EXISTS partygameonline_test;
DROP DATABASE IF EXISTS partygameonline;

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'partygameonline') THEN
        CREATE ROLE partygameonline LOGIN PASSWORD 'partygameonline';
    ELSE
        ALTER ROLE partygameonline WITH LOGIN PASSWORD 'partygameonline';
    END IF;
END
$$;

CREATE DATABASE partygameonline OWNER partygameonline;
CREATE DATABASE partygameonline_test OWNER partygameonline;
