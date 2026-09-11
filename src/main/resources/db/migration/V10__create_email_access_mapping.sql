-- Email access mappings (docs/SPEC_EMAIL_ACCESS_MAPPING.md): permissions granted to the holder of a
-- validated Keycloak token by its email claim, for callers without an IRN session.
CREATE TABLE IF NOT EXISTS t_email_access_mapping (
    id            UUID PRIMARY KEY,
    email         TEXT        NOT NULL,
    description   TEXT,
    permissions   TEXT        NOT NULL,
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    expires_at    TIMESTAMPTZ,
    created_by    TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by    TEXT,
    updated_at    TIMESTAMPTZ,
    revoked_by    TEXT,
    revoked_at    TIMESTAMPTZ
);

-- One active mapping per email (stored lower-cased); revoked rows stay for audit and a new mapping
-- for the same email can be created afterwards.
CREATE UNIQUE INDEX IF NOT EXISTS uq_email_access_mapping_active_email
    ON t_email_access_mapping (email) WHERE active;
