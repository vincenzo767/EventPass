-- Creates the database and application user for a local PostgreSQL installation.
-- Run as a PostgreSQL superuser, e.g.:
--   psql -U postgres -f scripts/create-local-db.sql
-- Replace the password, and put the same value in DB_PASSWORD in your .env file.

CREATE ROLE eventpass WITH LOGIN PASSWORD 'change-me';
CREATE DATABASE eventpass OWNER eventpass;
