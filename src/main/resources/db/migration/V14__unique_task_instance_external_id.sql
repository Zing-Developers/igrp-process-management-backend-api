-- One app task per engine task. The sync after background jobs and the requests that pull engine
-- tasks serialize on the process row, so they do not duplicate; this index is the backstop, and it
-- also serves the lookups by external_id, which until now scanned the table.
-- Earlier versions had no guard against duplicates: if any exist, stop here rather than pick which
-- copy to drop. Find them with the query in the message, resolve them, then deploy again.
DO $$
DECLARE
  dup RECORD;
BEGIN
  SELECT external_id, count(*) AS copies INTO dup
  FROM t_task_instance
  WHERE external_id IS NOT NULL
  GROUP BY external_id
  HAVING count(*) > 1
  LIMIT 1;
  IF FOUND THEN
    RAISE EXCEPTION 'V14: t_task_instance has duplicated external_id (e.g. % with % rows). List them with: SELECT external_id, count(*) FROM t_task_instance WHERE external_id IS NOT NULL GROUP BY external_id HAVING count(*) > 1',
      dup.external_id, dup.copies;
  END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_t_task_instance_external_id
  ON t_task_instance (external_id) WHERE external_id IS NOT NULL;
