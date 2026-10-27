-- StayTrack database schema. Auto-run tuwing startup, safe ulit-ulitin.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- users: tenants at admins
CREATE TABLE IF NOT EXISTS users (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email          VARCHAR(255) NOT NULL,
  password_hash  VARCHAR(255) NOT NULL,
  first_name     VARCHAR(100) NOT NULL,
  last_name      VARCHAR(100) NOT NULL,
  phone          VARCHAR(30),
  avatar_url     VARCHAR(500),
  role           VARCHAR(20)  NOT NULL DEFAULT 'TENANT'
                   CHECK (role IN ('TENANT', 'ADMIN')),
  status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'REJECTED')),
  email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
  last_login_at  TIMESTAMP WITH TIME ZONE,
  created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS users_email_lower_idx ON users (lower(email));

-- Idinagdag later yung profile photo, safe kahit sa existing na database.
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);

-- rooms: mga kwarto
CREATE TABLE IF NOT EXISTS rooms (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  room_number  VARCHAR(20)  NOT NULL,
  building     VARCHAR(80)  NOT NULL DEFAULT 'North Hall',
  floor        INTEGER,
  monthly_rent NUMERIC(10,2) NOT NULL DEFAULT 0,
  capacity     INTEGER      NOT NULL DEFAULT 1,
  status       VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE'
                 CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'MAINTENANCE')),
  created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT rooms_building_room_unique UNIQUE (building, room_number)
);

-- tenant_profiles: extra info ng tenant
CREATE TABLE IF NOT EXISTS tenant_profiles (
  user_id                 UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  room_id                 UUID REFERENCES rooms(id) ON DELETE SET NULL,
  move_in_date            DATE,
  school_id               VARCHAR(60),
  emergency_contact_name  VARCHAR(120),
  emergency_contact_phone VARCHAR(30),
  contract_url            VARCHAR(500),
  created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Idinagdag later yung contract PDF, safe kahit sa existing na database.
ALTER TABLE tenant_profiles ADD COLUMN IF NOT EXISTS contract_url VARCHAR(500);

-- otp_codes: mga code na ipinapadala
CREATE TABLE IF NOT EXISTS otp_codes (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  code_hash    VARCHAR(255) NOT NULL,
  purpose      VARCHAR(20)  NOT NULL DEFAULT 'SIGNUP'
                 CHECK (purpose IN ('SIGNUP', 'PASSWORD_RESET', 'LOGIN')),
  attempts     INTEGER      NOT NULL DEFAULT 0,
  max_attempts INTEGER      NOT NULL DEFAULT 5,
  expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  consumed_at  TIMESTAMP WITH TIME ZONE,
  created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS otp_codes_user_purpose_idx ON otp_codes (user_id, purpose);

-- service_requests: reklamo at repair
CREATE TABLE IF NOT EXISTS service_requests (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  request_type VARCHAR(20)  NOT NULL DEFAULT 'MAINTENANCE'
                 CHECK (request_type IN ('MAINTENANCE', 'NEIGHBOR')),
  category    VARCHAR(120)  NOT NULL,
  description VARCHAR(2000) NOT NULL,
  photo_url   VARCHAR(500),
  is_anonymous BOOLEAN      NOT NULL DEFAULT FALSE,
  status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING', 'IN_PROGRESS', 'RESOLVED', 'REJECTED', 'CANCELLED')),
  admin_notes VARCHAR(2000),
  handled_by  UUID REFERENCES users(id) ON DELETE SET NULL,
  resolved_at TIMESTAMP WITH TIME ZONE,
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS requests_tenant_idx ON service_requests (tenant_id);
CREATE INDEX IF NOT EXISTS requests_status_idx ON service_requests (status);

-- payments: mga bill
CREATE TABLE IF NOT EXISTS payments (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id        UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  room_id          UUID REFERENCES rooms(id) ON DELETE SET NULL,
  billing_period   DATE          NOT NULL,
  rent_amount      NUMERIC(10,2) NOT NULL DEFAULT 0,
  utilities_amount NUMERIC(10,2) NOT NULL DEFAULT 0,
  other_amount     NUMERIC(10,2) NOT NULL DEFAULT 0,
  total_amount     NUMERIC(10,2) NOT NULL DEFAULT 0,
  amount_paid      NUMERIC(10,2) NOT NULL DEFAULT 0,
  status           VARCHAR(20)   NOT NULL DEFAULT 'UNPAID'
                     CHECK (status IN ('UNPAID', 'SUBMITTED', 'VERIFIED', 'REJECTED', 'OVERDUE')),
  due_date         DATE          NOT NULL,
  proof_url        VARCHAR(500),
  reference_no     VARCHAR(100),
  remarks          VARCHAR(1000),
  submitted_at     TIMESTAMP WITH TIME ZONE,
  verified_by      UUID REFERENCES users(id) ON DELETE SET NULL,
  verified_at      TIMESTAMP WITH TIME ZONE,
  created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT payments_tenant_period_unique UNIQUE (tenant_id, billing_period)
);
CREATE INDEX IF NOT EXISTS payments_tenant_idx ON payments (tenant_id);
CREATE INDEX IF NOT EXISTS payments_status_idx ON payments (status);

-- notifications: mga alert
CREATE TABLE IF NOT EXISTS notifications (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title             VARCHAR(200) NOT NULL,
  body              VARCHAR(1000),
  notification_type VARCHAR(40) NOT NULL DEFAULT 'info',
  read_at           TIMESTAMP WITH TIME ZONE,
  created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS notifications_user_idx ON notifications (user_id, read_at);

-- audit_logs: history ng admin
CREATE TABLE IF NOT EXISTS audit_logs (
  id          BIGSERIAL PRIMARY KEY,
  actor_id    UUID REFERENCES users(id) ON DELETE SET NULL,
  action      VARCHAR(100) NOT NULL,
  entity_type VARCHAR(60),
  entity_id   UUID,
  metadata    TEXT,
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS audit_logs_created_idx ON audit_logs (created_at DESC);

-- view ng tenant, room at utang
CREATE OR REPLACE VIEW v_tenant_overview AS
SELECT
  u.id,
  u.email,
  u.first_name,
  u.last_name,
  u.phone,
  u.status,
  u.email_verified,
  u.created_at,
  r.room_number,
  r.building,
  tp.move_in_date,
  COALESCE(SUM(
    CASE WHEN p.status IN ('UNPAID', 'SUBMITTED', 'OVERDUE', 'REJECTED')
         THEN p.total_amount - p.amount_paid ELSE 0 END
  ), 0) AS outstanding_balance
FROM users u
LEFT JOIN tenant_profiles tp ON tp.user_id = u.id
LEFT JOIN rooms r            ON r.id = tp.room_id
LEFT JOIN payments p         ON p.tenant_id = u.id
WHERE u.role = 'TENANT'
GROUP BY u.id, r.room_number, r.building, tp.move_in_date;
