-- Rewrites the identities the app used to hardcode for system actions ('system-bot@nosi.cv' as the
-- starter of message-started processes, 'system-bot' as the consumers' principal, 'demo@nosi.cv' as
-- the endedBy fallback) to the configured system user (IGRP_PROCESS_SYSTEM_USER_EMAIL).
-- Exact matches only, on the identity columns of every app table including the Envers *_aud copies.
-- 'demo@nosi.cv' is limited to process ended_by, the only place it was a fallback rather than a login.
-- Activiti's own act_* tables are left to the engine.
DO $$
DECLARE
  col RECORD;
BEGIN
  FOR col IN
    SELECT c.table_name, c.column_name
    FROM information_schema.columns c
    JOIN information_schema.tables t
      ON t.table_schema = c.table_schema AND t.table_name = c.table_name AND t.table_type = 'BASE TABLE'
    WHERE c.table_schema = current_schema()
      AND c.table_name LIKE 't\_%'
      AND c.column_name IN ('created_by', 'last_modified_by', 'started_by', 'ended_by', 'canceled_by',
                            'assigned_by', 'performed_by', 'removed_by', 'assignee')
  LOOP
    EXECUTE format('UPDATE %I SET %I = %L WHERE %I IN (%L, %L)',
                   col.table_name, col.column_name, '${system_user_email}',
                   col.column_name, 'system-bot@nosi.cv', 'system-bot');
  END LOOP;
END $$;

UPDATE t_process_instance SET ended_by = '${system_user_email}' WHERE ended_by = 'demo@nosi.cv';
UPDATE t_process_instance_aud SET ended_by = '${system_user_email}' WHERE ended_by = 'demo@nosi.cv';
