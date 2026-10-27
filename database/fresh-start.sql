-- =============================================================================
--  StayTrack — FRESH START
--
--  Wipes EVERYTHING so the system looks brand new:
--    no users, no tenants, no admin, no rooms,
--    no payments, no service requests, no notifications,
--    no OTP codes, no audit logs.
--
--  The tables themselves stay, so the app keeps working.
--
--  HOW TO RUN (pgAdmin):
--    1. Stop the application first
--    2. Right-click the `staytrack` database -> Query Tool
--    3. Paste this file and press F5
--    4. Check the result table at the bottom — every count must be 0
--
--  HOW TO RUN (command line):
--    psql -U postgres -d staytrack -f database/fresh-start.sql
--
--  AFTER RUNNING:
--    * app.seed.enabled=true  -> the admin account is re-created on next start
--    * app.seed.enabled=false -> the database stays completely empty
-- =============================================================================

TRUNCATE TABLE
    audit_logs,
    notifications,
    payments,
    service_requests,
    otp_codes,
    tenant_profiles,
    rooms,
    users
RESTART IDENTITY CASCADE;

-- Verification — every row count must be 0
SELECT 'users'           AS table_name, COUNT(*) AS row_count FROM users
UNION ALL SELECT 'rooms',            COUNT(*) FROM rooms
UNION ALL SELECT 'tenant_profiles',  COUNT(*) FROM tenant_profiles
UNION ALL SELECT 'payments',         COUNT(*) FROM payments
UNION ALL SELECT 'service_requests', COUNT(*) FROM service_requests
UNION ALL SELECT 'notifications',    COUNT(*) FROM notifications
UNION ALL SELECT 'otp_codes',        COUNT(*) FROM otp_codes
UNION ALL SELECT 'audit_logs',       COUNT(*) FROM audit_logs;
