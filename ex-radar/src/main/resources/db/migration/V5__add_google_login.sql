ALTER TABLE users ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE users ADD COLUMN provider_user_id VARCHAR(255);
ALTER TABLE users ADD COLUMN display_name_pending BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users ADD CONSTRAINT uk_users_provider UNIQUE(auth_provider, provider_user_id);
