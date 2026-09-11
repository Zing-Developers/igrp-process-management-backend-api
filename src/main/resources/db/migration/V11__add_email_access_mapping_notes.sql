-- Free-text notes on an email access mapping (who asked for it, ticket, contact), shown in the console.
ALTER TABLE t_email_access_mapping ADD COLUMN IF NOT EXISTS notes TEXT;
