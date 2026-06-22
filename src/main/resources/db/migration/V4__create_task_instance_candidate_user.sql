CREATE TABLE IF NOT EXISTS t_task_instance_candidate_user (
  task_instance_id UUID NOT NULL,
  user_id VARCHAR(255) NOT NULL,
  CONSTRAINT pk_task_instance_candidate_user PRIMARY KEY (task_instance_id, user_id),
  CONSTRAINT fk_task_instance_candidate_user_task
    FOREIGN KEY (task_instance_id)
    REFERENCES t_task_instance (id)
    ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_task_instance_candidate_user_user
  ON t_task_instance_candidate_user (user_id, task_instance_id);
