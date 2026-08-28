-- Audit completeness: record WHO revoked an M2M key, not just when.
-- (Rotation authorship is already captured as the replacement key's created_by.)
ALTER TABLE t_m2m_api_key ADD COLUMN IF NOT EXISTS revoked_by TEXT;
