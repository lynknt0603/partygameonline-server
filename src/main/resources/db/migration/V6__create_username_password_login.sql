ALTER TABLE users RENAME COLUMN created_at TO created_date;
ALTER TABLE users RENAME COLUMN updated_at TO update_date;

ALTER TABLE users
    ADD COLUMN username VARCHAR(32),
    ADD COLUMN password_aes VARCHAR(512),
    ADD COLUMN user_key VARCHAR(64);

UPDATE users
SET user_key = id::text
WHERE user_key IS NULL;

ALTER TABLE users ALTER COLUMN user_key SET NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT uq_users_username UNIQUE (username),
    ADD CONSTRAINT uq_users_user_key UNIQUE (user_key),
    ADD CONSTRAINT ck_users_credentials_together CHECK (
        (username IS NULL AND password_aes IS NULL)
        OR (username IS NOT NULL AND password_aes IS NOT NULL)
    );

CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_user_key ON users (user_key);
