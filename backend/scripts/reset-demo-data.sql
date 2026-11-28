-- Resets an EventPass database to a clean state so the demo data is re-seeded on the next start.
-- WARNING: deletes ALL events, registrations, staff accounts and audit logs. Never run on real data.
--
--   psql "$DATABASE_URL" -f backend/scripts/reset-demo-data.sql
--   (then restart the application with DEMO_SEED=true)

BEGIN;
TRUNCATE audit_logs, registrations, events, staff_users RESTART IDENTITY CASCADE;
COMMIT;
