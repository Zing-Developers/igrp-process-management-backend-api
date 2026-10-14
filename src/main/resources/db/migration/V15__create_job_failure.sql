-- When and how often an engine job failed. Activiti keeps neither: a dead-letter job has no
-- timestamp, and a retried job gets a new id each attempt. Keyed by the job's execution, which stays
-- the same across attempts; JobFailureRecorder writes it on failure and clears it on success.
CREATE TABLE IF NOT EXISTS t_job_failure (
  execution_id        VARCHAR(64)   NOT NULL,
  process_instance_id VARCHAR(64)   NOT NULL,
  attempts            INTEGER       NOT NULL,
  first_failed_at     TIMESTAMP(6)  NOT NULL,
  last_failed_at      TIMESTAMP(6)  NOT NULL,
  last_error          VARCHAR(4000),
  CONSTRAINT pk_t_job_failure PRIMARY KEY (execution_id)
);

CREATE INDEX IF NOT EXISTS idx_t_job_failure_process ON t_job_failure (process_instance_id);
