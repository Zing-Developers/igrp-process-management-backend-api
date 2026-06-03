DO $$
BEGIN
  IF to_regclass('t_task_instance') IS NOT NULL
     AND to_regclass('t_process_instance') IS NOT NULL THEN
    CREATE TABLE IF NOT EXISTS t_task_assignment_rule (
      id UUID PRIMARY KEY,
      process_definition_key VARCHAR(255) NOT NULL,
      process_instance_id UUID,
      task_definition_key VARCHAR(255) NOT NULL,
      assignee VARCHAR(255),
      assignment_mode VARCHAR(50) NOT NULL DEFAULT 'ONE_TIME',
      priority INTEGER,
      consumed BOOLEAN NOT NULL DEFAULT FALSE,
      active BOOLEAN NOT NULL DEFAULT TRUE,
      created_by_task UUID,
      created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      created_by VARCHAR(255) NOT NULL DEFAULT 'system',
      last_modified_date TIMESTAMP,
      last_modified_by VARCHAR(255),
      CONSTRAINT fk_task_assignment_rule_process_instance
        FOREIGN KEY (process_instance_id)
        REFERENCES t_process_instance (id)
        ON DELETE CASCADE,
      CONSTRAINT fk_task_assignment_rule_created_by_task
        FOREIGN KEY (created_by_task)
        REFERENCES t_task_instance (id)
        ON DELETE SET NULL
    );

    CREATE TABLE IF NOT EXISTS t_task_assignment_rule_candidate_user (
      assignment_rule_id UUID NOT NULL,
      user_id VARCHAR(255) NOT NULL,
      CONSTRAINT pk_task_assignment_rule_candidate_user
        PRIMARY KEY (assignment_rule_id, user_id),
      CONSTRAINT fk_task_assignment_rule_candidate_user_rule
        FOREIGN KEY (assignment_rule_id)
        REFERENCES t_task_assignment_rule (id)
        ON DELETE CASCADE
    );

    CREATE INDEX IF NOT EXISTS idx_task_assignment_rule_visibility
      ON t_task_assignment_rule (
        process_instance_id,
        task_definition_key,
        active,
        consumed
      );

    CREATE INDEX IF NOT EXISTS idx_task_assignment_rule_created_by_task
      ON t_task_assignment_rule (created_by_task);

    CREATE INDEX IF NOT EXISTS idx_task_assignment_rule_definition
      ON t_task_assignment_rule (
        process_definition_key,
        task_definition_key,
        active,
        consumed
      );

    CREATE INDEX IF NOT EXISTS idx_task_assignment_rule_assignee
      ON t_task_assignment_rule (assignee, active, consumed);

    CREATE INDEX IF NOT EXISTS idx_task_assignment_rule_candidate_user
      ON t_task_assignment_rule_candidate_user (user_id, assignment_rule_id);
  END IF;
END $$;
