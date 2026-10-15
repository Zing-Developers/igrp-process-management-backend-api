-- The sync after background jobs, the reconciliation and the failed-jobs console look processes up
-- by their engine id; without this each lookup scanned t_process_instance.
CREATE INDEX IF NOT EXISTS idx_t_process_instance_engine_number
  ON t_process_instance (engine_process_number);
