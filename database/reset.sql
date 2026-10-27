-- =============================================================================
--  StayTrack — reset script
--
--  Deletes EVERY record from the database but keeps the tables themselves:
--    * no admin account
--    * no tenant accounts
--    * no demo rooms
--    * no payments
--    * no service requests
--    * no notifications / OTP codes / audit logs
--
--  Use this when demo data from an earlier run is still showing up.
--  The tables are re-created automatically by schema.sql the next time the
--  application starts.
--
--  How to run it (pgAdmin):
--    1. Right-click the `staytrack` database
--    2. Choose "Query Tool"
--    3. Paste this file and press F5
--
--  How to run it (command line):
--    psql -U postgres -d staytrack -f database/reset.sql
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

-- Confirmation
SELECT 'Reset complete — all records removed.' AS status;
