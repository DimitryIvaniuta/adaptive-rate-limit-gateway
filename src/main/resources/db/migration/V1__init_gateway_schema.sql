CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS access_list (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_type VARCHAR(32) NOT NULL,
    subject_value VARCHAR(256) NOT NULL,
    mode VARCHAR(16) NOT NULL,
    reason VARCHAR(512),
    expires_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_access_list_active_subject_mode
    ON access_list(subject_type, subject_value, mode)
    WHERE active = TRUE;

CREATE INDEX IF NOT EXISTS ix_access_list_subject
    ON access_list(subject_type, subject_value, active, expires_at);

CREATE TABLE IF NOT EXISTS rate_limit_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    request_id VARCHAR(128),
    route_id VARCHAR(128),
    subject_key VARCHAR(512) NOT NULL,
    tenant_id VARCHAR(128),
    client_ip VARCHAR(128),
    api_key_hash VARCHAR(128),
    method VARCHAR(16) NOT NULL,
    path VARCHAR(2048) NOT NULL,
    status_code INTEGER,
    decision VARCHAR(32) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    effective_limit INTEGER NOT NULL,
    remaining_tokens INTEGER NOT NULL,
    abuse_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    error_rate DOUBLE PRECISION NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS ix_rate_limit_audit_time ON rate_limit_audit(occurred_at DESC);
CREATE INDEX IF NOT EXISTS ix_rate_limit_audit_client_ip ON rate_limit_audit(client_ip, occurred_at DESC);
CREATE INDEX IF NOT EXISTS ix_rate_limit_audit_tenant ON rate_limit_audit(tenant_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS ix_rate_limit_audit_decision ON rate_limit_audit(decision, occurred_at DESC);
