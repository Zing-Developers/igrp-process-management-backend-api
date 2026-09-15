-- Where the tables were created by Hibernate (ddl-auto=update) instead of the migration, String
-- columns came out as varchar(255); a full permission catalogue is longer than that and the insert
-- failed. Widen to TEXT (a no-op where the migration already made them TEXT).
ALTER TABLE t_email_access_mapping ALTER COLUMN permissions TYPE TEXT;
ALTER TABLE t_email_access_mapping ALTER COLUMN description TYPE TEXT;
ALTER TABLE t_email_access_mapping ALTER COLUMN notes TYPE TEXT;
ALTER TABLE t_m2m_api_key ALTER COLUMN permissions TYPE TEXT;
