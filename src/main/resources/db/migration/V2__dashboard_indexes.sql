CREATE INDEX IF NOT EXISTS ix_rate_limit_audit_route ON rate_limit_audit(route_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS ix_rate_limit_audit_subject ON rate_limit_audit(subject_key, occurred_at DESC);
CREATE INDEX IF NOT EXISTS ix_rate_limit_audit_rejected_time
    ON rate_limit_audit(occurred_at DESC, decision)
    WHERE decision IN ('BLOCKED', 'RATE_LIMITED');
