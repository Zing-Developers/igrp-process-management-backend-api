-- Audit completeness: WHO/WHEN of the last mutation (create, revoke, or the rotate stamp on the
-- old key), mirroring the platform's last_modified_* pair on AuditEntity tables.
ALTER TABLE t_m2m_api_key ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
ALTER TABLE t_m2m_api_key ADD COLUMN IF NOT EXISTS updated_by TEXT;

-- Backfill: a revoked key was last touched by the revocation, anything else by its creation.
UPDATE t_m2m_api_key
SET updated_at = COALESCE(revoked_at, created_at),
    updated_by = COALESCE(revoked_by, created_by)
WHERE updated_at IS NULL;
