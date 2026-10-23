-- Reprocesses of a failed job (POST /failed-jobs/{jobId}/retry): how many, when and by whom the last
-- one. attempts keeps counting every failed run, reprocesses included; these say how many of the
-- cycles were started by hand. Cleared with the row when the job succeeds.
ALTER TABLE t_job_failure ADD COLUMN IF NOT EXISTS reprocess_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE t_job_failure ADD COLUMN IF NOT EXISTS last_reprocessed_at TIMESTAMP(6);
ALTER TABLE t_job_failure ADD COLUMN IF NOT EXISTS last_reprocessed_by VARCHAR(255);
