CREATE TABLE IF NOT EXISTS t_task_assignment_rule_candidate_group (
  assignment_rule_id UUID NOT NULL,
  group_id VARCHAR(255) NOT NULL,
  CONSTRAINT pk_task_assignment_rule_candidate_group
    PRIMARY KEY (assignment_rule_id, group_id),
  CONSTRAINT fk_task_assignment_rule_candidate_group_rule
    FOREIGN KEY (assignment_rule_id)
    REFERENCES t_task_assignment_rule (id)
    ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_task_assignment_rule_candidate_group
  ON t_task_assignment_rule_candidate_group (group_id, assignment_rule_id);
