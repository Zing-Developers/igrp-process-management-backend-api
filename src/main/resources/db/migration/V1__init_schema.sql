CREATE SEQUENCE IF NOT EXISTS revinfo_seq
  START WITH 1
  INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS revinfo (
  rev INTEGER NOT NULL DEFAULT nextval('revinfo_seq'),
  revtstmp BIGINT,
  CONSTRAINT pk_revinfo PRIMARY KEY (rev)
);

CREATE TABLE IF NOT EXISTS revchanges (
  rev INTEGER NOT NULL,
  entityname VARCHAR(255),
  CONSTRAINT fk_revchanges_revinfo
    FOREIGN KEY (rev)
    REFERENCES revinfo (rev)
);

CREATE TABLE IF NOT EXISTS t_area (
  id UUID NOT NULL,
  application_base VARCHAR(255) NOT NULL,
  code VARCHAR(255) NOT NULL,
  name VARCHAR(255),
  description VARCHAR(255),
  area_id UUID,
  status VARCHAR(255) NOT NULL,
  color VARCHAR(255),
  created_date TIMESTAMP(6) NOT NULL,
  created_by VARCHAR(255) NOT NULL,
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  CONSTRAINT pk_t_area PRIMARY KEY (id),
  CONSTRAINT uk_t_area_application_base_code UNIQUE (code, application_base),
  CONSTRAINT fk_t_area_area
    FOREIGN KEY (area_id)
    REFERENCES t_area (id)
);

CREATE TABLE IF NOT EXISTS t_area_process (
  id UUID NOT NULL,
  area_id UUID,
  proc_release_key VARCHAR(255) NOT NULL,
  proc_release_id VARCHAR(255) NOT NULL,
  status VARCHAR(255) NOT NULL,
  version VARCHAR(255),
  removed_at TIMESTAMP(6),
  removed_by VARCHAR(255),
  name VARCHAR(255),
  created_date TIMESTAMP(6) NOT NULL,
  created_by VARCHAR(255) NOT NULL,
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  CONSTRAINT pk_t_area_process PRIMARY KEY (id),
  CONSTRAINT uk_t_area_process_area_id_proc_release_id UNIQUE (proc_release_id, area_id),
  CONSTRAINT fk_t_area_process_area
    FOREIGN KEY (area_id)
    REFERENCES t_area (id)
);

CREATE TABLE IF NOT EXISTS t_iam_user_profile (
  id UUID NOT NULL,
  username VARCHAR(255) NOT NULL,
  email VARCHAR(255),
  first_name VARCHAR(255),
  last_name VARCHAR(255),
  full_name VARCHAR(255),
  sub VARCHAR(255) NOT NULL,
  created_date TIMESTAMP(6) NOT NULL,
  created_by VARCHAR(255) NOT NULL,
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  CONSTRAINT pk_t_iam_user_profile PRIMARY KEY (id),
  CONSTRAINT uk_t_iam_user_profile_username UNIQUE (username),
  CONSTRAINT uk_t_iam_user_profile_email UNIQUE (email),
  CONSTRAINT uk_t_iam_user_profile_sub UNIQUE (sub)
);

CREATE TABLE IF NOT EXISTS t_process_artifact (
  id UUID NOT NULL,
  name VARCHAR(255) NOT NULL,
  key VARCHAR(255) NOT NULL,
  process_definition_id VARCHAR(255) NOT NULL,
  form_key VARCHAR(255) NOT NULL,
  candidate_groups VARCHAR(255),
  priority INTEGER,
  due_date VARCHAR(255),
  created_date TIMESTAMP(6) NOT NULL,
  created_by VARCHAR(255) NOT NULL,
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  CONSTRAINT pk_t_process_artifact PRIMARY KEY (id),
  CONSTRAINT uk_t_process_artifact_process_definition_id_key UNIQUE (process_definition_id, key)
);

CREATE TABLE IF NOT EXISTS t_process_instance (
  id UUID NOT NULL,
  proc_release_key VARCHAR(255) NOT NULL,
  proc_release_id VARCHAR(255) NOT NULL,
  number VARCHAR(255),
  business_key VARCHAR(255),
  version VARCHAR(255),
  started_at TIMESTAMP(6),
  started_by VARCHAR(255),
  ended_at TIMESTAMP(6),
  ended_by VARCHAR(255),
  canceled_at TIMESTAMP(6),
  canceled_by VARCHAR(255),
  obs_cancel VARCHAR(255),
  status VARCHAR(255),
  application_base VARCHAR(255) NOT NULL,
  name VARCHAR(255),
  engine_process_number VARCHAR(255),
  priority INTEGER,
  is_archived BOOLEAN,
  created_date TIMESTAMP(6) NOT NULL,
  created_by VARCHAR(255) NOT NULL,
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  CONSTRAINT pk_t_process_instance PRIMARY KEY (id),
  CONSTRAINT uk_t_process_instance_proc_release_id_number UNIQUE (proc_release_id, number)
);

CREATE TABLE IF NOT EXISTS t_process_instance_sequence (
  id UUID NOT NULL,
  name VARCHAR(255) NOT NULL,
  prefix VARCHAR(255) NOT NULL,
  check_digit_size SMALLINT NOT NULL,
  padding SMALLINT NOT NULL,
  date_format VARCHAR(255),
  next_number BIGINT NOT NULL,
  number_increment SMALLINT NOT NULL,
  process_definition_key VARCHAR(255) NOT NULL,
  separator VARCHAR(255),
  created_date TIMESTAMP(6) NOT NULL,
  created_by VARCHAR(255) NOT NULL,
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  CONSTRAINT pk_t_process_instance_sequence PRIMARY KEY (id),
  CONSTRAINT sequence_uk_process_definition_key UNIQUE (process_definition_key)
);

CREATE TABLE IF NOT EXISTS t_task_priority (
  id UUID NOT NULL,
  process_definition_key VARCHAR(255) NOT NULL,
  code VARCHAR(255) NOT NULL,
  label VARCHAR(255) NOT NULL,
  weight INTEGER NOT NULL,
  color VARCHAR(255),
  created_date TIMESTAMP(6) NOT NULL,
  created_by VARCHAR(255) NOT NULL,
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  CONSTRAINT pk_t_task_priority PRIMARY KEY (id),
  CONSTRAINT uk_t_task_priority_code_process_definition_key UNIQUE (code, process_definition_key)
);

CREATE TABLE IF NOT EXISTS t_task_instance (
  id UUID NOT NULL,
  task_key VARCHAR(50),
  external_id VARCHAR(255),
  form_key VARCHAR(255),
  name VARCHAR(100),
  candidate_groups VARCHAR(255),
  process_instance_id UUID,
  started_at TIMESTAMP(6),
  started_by VARCHAR(100),
  assigned_by VARCHAR(100),
  assigned_at TIMESTAMP(6),
  priority INTEGER,
  ended_at TIMESTAMP(6),
  ended_by VARCHAR(255),
  status VARCHAR(255) NOT NULL,
  variables JSONB,
  forms JSONB,
  due_date TIMESTAMP(6),
  created_date TIMESTAMP(6) NOT NULL,
  created_by VARCHAR(255) NOT NULL,
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  CONSTRAINT pk_t_task_instance PRIMARY KEY (id),
  CONSTRAINT fk_t_task_instance_process_instance
    FOREIGN KEY (process_instance_id)
    REFERENCES t_process_instance (id)
);

CREATE TABLE IF NOT EXISTS t_task_instance_candidate_group (
  task_instance_id UUID NOT NULL,
  group_id VARCHAR(255) NOT NULL,
  CONSTRAINT pk_task_instance_candidate_group PRIMARY KEY (task_instance_id, group_id),
  CONSTRAINT fk_task_instance_candidate_group_task
    FOREIGN KEY (task_instance_id)
    REFERENCES t_task_instance (id)
    ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS t_task_instance_events (
  id UUID NOT NULL,
  task_instance_id UUID,
  event_type VARCHAR(255) NOT NULL,
  performed_at TIMESTAMP(6),
  performed_by VARCHAR(100),
  note VARCHAR(100),
  status VARCHAR(255) NOT NULL,
  created_date TIMESTAMP(6) NOT NULL,
  created_by VARCHAR(255) NOT NULL,
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  CONSTRAINT pk_t_task_instance_events PRIMARY KEY (id),
  CONSTRAINT fk_t_task_instance_events_task_instance
    FOREIGN KEY (task_instance_id)
    REFERENCES t_task_instance (id)
);

CREATE TABLE IF NOT EXISTS t_variable (
  id UUID NOT NULL,
  name VARCHAR(255) NOT NULL,
  type VARCHAR(255) NOT NULL,
  process_instance_id UUID,
  task_instance_id UUID,
  double_value DOUBLE PRECISION,
  long_value BIGINT,
  text_value VARCHAR(255),
  created_date TIMESTAMP(6) NOT NULL,
  created_by VARCHAR(255) NOT NULL,
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  CONSTRAINT pk_t_variable PRIMARY KEY (id),
  CONSTRAINT fk_t_variable_process_instance
    FOREIGN KEY (process_instance_id)
    REFERENCES t_process_instance (id),
  CONSTRAINT fk_t_variable_task_instance
    FOREIGN KEY (task_instance_id)
    REFERENCES t_task_instance (id)
);

CREATE TABLE IF NOT EXISTS t_task_assignment_rule (
  id UUID NOT NULL,
  process_definition_key VARCHAR(255) NOT NULL,
  process_instance_id UUID,
  task_definition_key VARCHAR(255) NOT NULL,
  assignee VARCHAR(255),
  assignment_mode VARCHAR(255) NOT NULL DEFAULT 'ONE_TIME',
  priority INTEGER,
  consumed BOOLEAN NOT NULL DEFAULT FALSE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_by_task UUID,
  created_date TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(255) NOT NULL DEFAULT 'system',
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  CONSTRAINT pk_t_task_assignment_rule PRIMARY KEY (id),
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

CREATE INDEX IF NOT EXISTS idx_iam_user_profile_username
  ON t_iam_user_profile (username);
CREATE INDEX IF NOT EXISTS idx_iam_user_profile_full_name
  ON t_iam_user_profile (full_name);
CREATE INDEX IF NOT EXISTS idx_iam_user_profile_sub
  ON t_iam_user_profile (sub);
CREATE INDEX IF NOT EXISTS idx_task_instance_candidate_group_group
  ON t_task_instance_candidate_group (group_id, task_instance_id);
CREATE INDEX IF NOT EXISTS idx_task_assignment_rule_visibility
  ON t_task_assignment_rule (process_instance_id, task_definition_key, active, consumed);
CREATE INDEX IF NOT EXISTS idx_task_assignment_rule_created_by_task
  ON t_task_assignment_rule (created_by_task);
CREATE INDEX IF NOT EXISTS idx_task_assignment_rule_definition
  ON t_task_assignment_rule (process_definition_key, task_definition_key, active, consumed);
CREATE INDEX IF NOT EXISTS idx_task_assignment_rule_assignee
  ON t_task_assignment_rule (assignee, active, consumed);
CREATE INDEX IF NOT EXISTS idx_task_assignment_rule_candidate_user
  ON t_task_assignment_rule_candidate_user (user_id, assignment_rule_id);

CREATE TABLE IF NOT EXISTS t_area_aud (
  id UUID NOT NULL,
  rev INTEGER NOT NULL,
  revtype SMALLINT,
  application_base VARCHAR(255),
  code VARCHAR(255),
  name VARCHAR(255),
  description VARCHAR(255),
  area_id UUID,
  status VARCHAR(255),
  color VARCHAR(255),
  created_date TIMESTAMP(6),
  created_by VARCHAR(255),
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  application_base_mod BOOLEAN,
  code_mod BOOLEAN,
  name_mod BOOLEAN,
  description_mod BOOLEAN,
  area_id_mod BOOLEAN,
  status_mod BOOLEAN,
  color_mod BOOLEAN,
  created_date_mod BOOLEAN,
  created_by_mod BOOLEAN,
  last_modified_date_mod BOOLEAN,
  last_modified_by_mod BOOLEAN,
  CONSTRAINT pk_t_area_aud PRIMARY KEY (id, rev),
  CONSTRAINT fk_t_area_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

CREATE TABLE IF NOT EXISTS t_area_process_aud (
  id UUID NOT NULL,
  rev INTEGER NOT NULL,
  revtype SMALLINT,
  area_id UUID,
  proc_release_key VARCHAR(255),
  proc_release_id VARCHAR(255),
  status VARCHAR(255),
  version VARCHAR(255),
  removed_at TIMESTAMP(6),
  removed_by VARCHAR(255),
  name VARCHAR(255),
  created_date TIMESTAMP(6),
  created_by VARCHAR(255),
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  area_id_mod BOOLEAN,
  proc_release_key_mod BOOLEAN,
  proc_release_id_mod BOOLEAN,
  status_mod BOOLEAN,
  version_mod BOOLEAN,
  removed_at_mod BOOLEAN,
  removed_by_mod BOOLEAN,
  name_mod BOOLEAN,
  created_date_mod BOOLEAN,
  created_by_mod BOOLEAN,
  last_modified_date_mod BOOLEAN,
  last_modified_by_mod BOOLEAN,
  CONSTRAINT pk_t_area_process_aud PRIMARY KEY (id, rev),
  CONSTRAINT fk_t_area_process_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

CREATE TABLE IF NOT EXISTS t_iam_user_profile_aud (
  id UUID NOT NULL,
  rev INTEGER NOT NULL,
  revtype SMALLINT,
  username VARCHAR(255),
  email VARCHAR(255),
  first_name VARCHAR(255),
  last_name VARCHAR(255),
  full_name VARCHAR(255),
  sub VARCHAR(255),
  created_date TIMESTAMP(6),
  created_by VARCHAR(255),
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  username_mod BOOLEAN,
  email_mod BOOLEAN,
  first_name_mod BOOLEAN,
  last_name_mod BOOLEAN,
  full_name_mod BOOLEAN,
  sub_mod BOOLEAN,
  created_date_mod BOOLEAN,
  created_by_mod BOOLEAN,
  last_modified_date_mod BOOLEAN,
  last_modified_by_mod BOOLEAN,
  CONSTRAINT pk_t_iam_user_profile_aud PRIMARY KEY (id, rev),
  CONSTRAINT fk_t_iam_user_profile_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

CREATE TABLE IF NOT EXISTS t_process_artifact_aud (
  id UUID NOT NULL,
  rev INTEGER NOT NULL,
  revtype SMALLINT,
  name VARCHAR(255),
  key VARCHAR(255),
  process_definition_id VARCHAR(255),
  form_key VARCHAR(255),
  candidate_groups VARCHAR(255),
  priority INTEGER,
  due_date VARCHAR(255),
  created_date TIMESTAMP(6),
  created_by VARCHAR(255),
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  name_mod BOOLEAN,
  key_mod BOOLEAN,
  process_definition_id_mod BOOLEAN,
  form_key_mod BOOLEAN,
  candidate_groups_mod BOOLEAN,
  priority_mod BOOLEAN,
  due_date_mod BOOLEAN,
  created_date_mod BOOLEAN,
  created_by_mod BOOLEAN,
  last_modified_date_mod BOOLEAN,
  last_modified_by_mod BOOLEAN,
  CONSTRAINT pk_t_process_artifact_aud PRIMARY KEY (id, rev),
  CONSTRAINT fk_t_process_artifact_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

CREATE TABLE IF NOT EXISTS t_process_instance_aud (
  id UUID NOT NULL,
  rev INTEGER NOT NULL,
  revtype SMALLINT,
  proc_release_key VARCHAR(255),
  proc_release_id VARCHAR(255),
  number VARCHAR(255),
  business_key VARCHAR(255),
  version VARCHAR(255),
  started_at TIMESTAMP(6),
  started_by VARCHAR(255),
  ended_at TIMESTAMP(6),
  ended_by VARCHAR(255),
  canceled_at TIMESTAMP(6),
  canceled_by VARCHAR(255),
  obs_cancel VARCHAR(255),
  status VARCHAR(255),
  application_base VARCHAR(255),
  name VARCHAR(255),
  engine_process_number VARCHAR(255),
  priority INTEGER,
  is_archived BOOLEAN,
  created_date TIMESTAMP(6),
  created_by VARCHAR(255),
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  proc_release_key_mod BOOLEAN,
  proc_release_id_mod BOOLEAN,
  number_mod BOOLEAN,
  business_key_mod BOOLEAN,
  version_mod BOOLEAN,
  started_at_mod BOOLEAN,
  started_by_mod BOOLEAN,
  ended_at_mod BOOLEAN,
  ended_by_mod BOOLEAN,
  canceled_at_mod BOOLEAN,
  canceled_by_mod BOOLEAN,
  obs_cancel_mod BOOLEAN,
  status_mod BOOLEAN,
  application_base_mod BOOLEAN,
  name_mod BOOLEAN,
  engine_process_number_mod BOOLEAN,
  priority_mod BOOLEAN,
  is_archived_mod BOOLEAN,
  created_date_mod BOOLEAN,
  created_by_mod BOOLEAN,
  last_modified_date_mod BOOLEAN,
  last_modified_by_mod BOOLEAN,
  CONSTRAINT pk_t_process_instance_aud PRIMARY KEY (id, rev),
  CONSTRAINT fk_t_process_instance_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

CREATE TABLE IF NOT EXISTS t_process_instance_sequence_aud (
  id UUID NOT NULL,
  rev INTEGER NOT NULL,
  revtype SMALLINT,
  name VARCHAR(255),
  prefix VARCHAR(255),
  check_digit_size SMALLINT,
  padding SMALLINT,
  date_format VARCHAR(255),
  next_number BIGINT,
  number_increment SMALLINT,
  process_definition_key VARCHAR(255),
  separator VARCHAR(255),
  created_date TIMESTAMP(6),
  created_by VARCHAR(255),
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  name_mod BOOLEAN,
  prefix_mod BOOLEAN,
  check_digit_size_mod BOOLEAN,
  padding_mod BOOLEAN,
  date_format_mod BOOLEAN,
  next_number_mod BOOLEAN,
  number_increment_mod BOOLEAN,
  process_definition_key_mod BOOLEAN,
  separator_mod BOOLEAN,
  created_date_mod BOOLEAN,
  created_by_mod BOOLEAN,
  last_modified_date_mod BOOLEAN,
  last_modified_by_mod BOOLEAN,
  CONSTRAINT pk_t_process_instance_sequence_aud PRIMARY KEY (id, rev),
  CONSTRAINT fk_t_process_instance_sequence_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

CREATE TABLE IF NOT EXISTS t_task_priority_aud (
  id UUID NOT NULL,
  rev INTEGER NOT NULL,
  revtype SMALLINT,
  process_definition_key VARCHAR(255),
  code VARCHAR(255),
  label VARCHAR(255),
  weight INTEGER,
  color VARCHAR(255),
  created_date TIMESTAMP(6),
  created_by VARCHAR(255),
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  process_definition_key_mod BOOLEAN,
  code_mod BOOLEAN,
  label_mod BOOLEAN,
  weight_mod BOOLEAN,
  color_mod BOOLEAN,
  created_date_mod BOOLEAN,
  created_by_mod BOOLEAN,
  last_modified_date_mod BOOLEAN,
  last_modified_by_mod BOOLEAN,
  CONSTRAINT pk_t_task_priority_aud PRIMARY KEY (id, rev),
  CONSTRAINT fk_t_task_priority_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

CREATE TABLE IF NOT EXISTS t_task_instance_aud (
  id UUID NOT NULL,
  rev INTEGER NOT NULL,
  revtype SMALLINT,
  task_key VARCHAR(50),
  external_id VARCHAR(255),
  form_key VARCHAR(255),
  name VARCHAR(100),
  process_instance_id UUID,
  started_at TIMESTAMP(6),
  started_by VARCHAR(100),
  assigned_by VARCHAR(100),
  assigned_at TIMESTAMP(6),
  priority INTEGER,
  ended_at TIMESTAMP(6),
  ended_by VARCHAR(255),
  status VARCHAR(255),
  variables JSONB,
  forms JSONB,
  due_date TIMESTAMP(6),
  created_date TIMESTAMP(6),
  created_by VARCHAR(255),
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  task_key_mod BOOLEAN,
  external_id_mod BOOLEAN,
  form_key_mod BOOLEAN,
  name_mod BOOLEAN,
  process_instance_id_mod BOOLEAN,
  started_at_mod BOOLEAN,
  started_by_mod BOOLEAN,
  assigned_by_mod BOOLEAN,
  assigned_at_mod BOOLEAN,
  priority_mod BOOLEAN,
  ended_at_mod BOOLEAN,
  ended_by_mod BOOLEAN,
  status_mod BOOLEAN,
  variables_mod BOOLEAN,
  forms_mod BOOLEAN,
  due_date_mod BOOLEAN,
  created_date_mod BOOLEAN,
  created_by_mod BOOLEAN,
  last_modified_date_mod BOOLEAN,
  last_modified_by_mod BOOLEAN,
  CONSTRAINT pk_t_task_instance_aud PRIMARY KEY (id, rev),
  CONSTRAINT fk_t_task_instance_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

CREATE TABLE IF NOT EXISTS t_task_instance_events_aud (
  id UUID NOT NULL,
  rev INTEGER NOT NULL,
  revtype SMALLINT,
  task_instance_id UUID,
  event_type VARCHAR(255),
  performed_at TIMESTAMP(6),
  performed_by VARCHAR(100),
  note VARCHAR(100),
  status VARCHAR(255),
  created_date TIMESTAMP(6),
  created_by VARCHAR(255),
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  task_instance_id_mod BOOLEAN,
  event_type_mod BOOLEAN,
  performed_at_mod BOOLEAN,
  performed_by_mod BOOLEAN,
  note_mod BOOLEAN,
  status_mod BOOLEAN,
  created_date_mod BOOLEAN,
  created_by_mod BOOLEAN,
  last_modified_date_mod BOOLEAN,
  last_modified_by_mod BOOLEAN,
  CONSTRAINT pk_t_task_instance_events_aud PRIMARY KEY (id, rev),
  CONSTRAINT fk_t_task_instance_events_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

CREATE TABLE IF NOT EXISTS t_variable_aud (
  id UUID NOT NULL,
  rev INTEGER NOT NULL,
  revtype SMALLINT,
  name VARCHAR(255),
  type VARCHAR(255),
  process_instance_id UUID,
  task_instance_id UUID,
  double_value DOUBLE PRECISION,
  long_value BIGINT,
  text_value VARCHAR(255),
  created_date TIMESTAMP(6),
  created_by VARCHAR(255),
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  name_mod BOOLEAN,
  type_mod BOOLEAN,
  process_instance_id_mod BOOLEAN,
  task_instance_id_mod BOOLEAN,
  double_value_mod BOOLEAN,
  long_value_mod BOOLEAN,
  text_value_mod BOOLEAN,
  created_date_mod BOOLEAN,
  created_by_mod BOOLEAN,
  last_modified_date_mod BOOLEAN,
  last_modified_by_mod BOOLEAN,
  CONSTRAINT pk_t_variable_aud PRIMARY KEY (id, rev),
  CONSTRAINT fk_t_variable_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

CREATE TABLE IF NOT EXISTS t_task_assignment_rule_aud (
  id UUID NOT NULL,
  rev INTEGER NOT NULL,
  revtype SMALLINT,
  process_definition_key VARCHAR(255),
  process_instance_id UUID,
  task_definition_key VARCHAR(255),
  assignee VARCHAR(255),
  assignment_mode VARCHAR(255),
  priority INTEGER,
  consumed BOOLEAN,
  active BOOLEAN,
  created_by_task UUID,
  created_date TIMESTAMP(6),
  created_by VARCHAR(255),
  last_modified_date TIMESTAMP(6),
  last_modified_by VARCHAR(255),
  process_definition_key_mod BOOLEAN,
  process_instance_id_mod BOOLEAN,
  task_definition_key_mod BOOLEAN,
  assignee_mod BOOLEAN,
  assignment_mode_mod BOOLEAN,
  priority_mod BOOLEAN,
  consumed_mod BOOLEAN,
  active_mod BOOLEAN,
  created_by_task_mod BOOLEAN,
  created_date_mod BOOLEAN,
  created_by_mod BOOLEAN,
  last_modified_date_mod BOOLEAN,
  last_modified_by_mod BOOLEAN,
  CONSTRAINT pk_t_task_assignment_rule_aud PRIMARY KEY (id, rev),
  CONSTRAINT fk_t_task_assignment_rule_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
);
