DO $$
BEGIN
  IF to_regclass('t_task_instance') IS NOT NULL THEN
    CREATE TABLE IF NOT EXISTS t_task_instance_candidate_group (
      task_instance_id UUID NOT NULL,
      group_id VARCHAR(255) NOT NULL,
      CONSTRAINT pk_task_instance_candidate_group PRIMARY KEY (task_instance_id, group_id),
      CONSTRAINT fk_task_instance_candidate_group_task
        FOREIGN KEY (task_instance_id)
        REFERENCES t_task_instance (id)
        ON DELETE CASCADE
    );

    CREATE INDEX IF NOT EXISTS idx_task_instance_candidate_group_group
      ON t_task_instance_candidate_group (group_id, task_instance_id);

    INSERT INTO t_task_instance_candidate_group (task_instance_id, group_id)
    SELECT task.id, trim(candidate_group.group_id)
    FROM t_task_instance task
    CROSS JOIN LATERAL regexp_split_to_table(task.candidate_groups, ',') AS candidate_group(group_id)
    WHERE task.candidate_groups IS NOT NULL
      AND trim(candidate_group.group_id) <> ''
    ON CONFLICT DO NOTHING;
  END IF;
END $$;
