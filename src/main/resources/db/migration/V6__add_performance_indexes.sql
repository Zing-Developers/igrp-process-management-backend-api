-- Performance indexes for the hot task/process search and statistics paths.
--
-- Built with CREATE INDEX CONCURRENTLY so the build does NOT take a write-blocking
-- lock on these large production tables. CONCURRENTLY cannot run inside a transaction,
-- so this migration is configured with executeInTransaction=false (see the matching
-- V6__add_performance_indexes.sql.conf file).
--
-- Operational note: if a CONCURRENTLY build is interrupted it leaves an INVALID index
-- behind. IF NOT EXISTS will then NOT rebuild it -- drop the invalid index manually and
-- re-run. Check with:
--   SELECT indexrelid::regclass FROM pg_index WHERE NOT indisvalid;

-- ---------------------------------------------------------------------------
-- t_task_instance
-- ---------------------------------------------------------------------------

-- Unindexed foreign key, joined/filtered on every task search (archived join + filter).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_task_instance_process_instance_id
  ON t_task_instance (process_instance_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_task_instance_status_started_at
  ON t_task_instance (status, started_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_task_instance_started_at
  ON t_task_instance (started_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_task_instance_assigned_by_status
  ON t_task_instance (assigned_by, status);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_task_instance_ended_by_status
  ON t_task_instance (ended_by, status);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_task_instance_task_key
  ON t_task_instance (task_key);

-- ---------------------------------------------------------------------------
-- t_process_instance
-- ---------------------------------------------------------------------------

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_process_instance_status_started
  ON t_process_instance (status, started_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_process_instance_archived_started
  ON t_process_instance (is_archived, started_at DESC);

-- NOTE: redundant -- the unique index uk_t_process_instance_proc_release_id_number
-- (proc_release_id, number) already serves proc_release_id prefix lookups. Kept per the
-- full-list decision; safe to drop to avoid the extra write/maintenance cost.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_process_instance_release
  ON t_process_instance (proc_release_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_process_instance_release_key
  ON t_process_instance (proc_release_key);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_process_instance_application_base
  ON t_process_instance (application_base);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_process_instance_number
  ON t_process_instance (number);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_process_instance_business_key
  ON t_process_instance (business_key);

-- ---------------------------------------------------------------------------
-- t_task_assignment_rule
-- ---------------------------------------------------------------------------

-- NOTE: redundant -- idx_task_assignment_rule_visibility
-- (process_instance_id, task_definition_key, active, consumed) already serves this prefix.
-- Kept per the full-list decision; safe to drop.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_task_assignment_rule_process_task
  ON t_task_assignment_rule (process_instance_id, task_definition_key);
