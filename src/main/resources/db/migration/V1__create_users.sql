CREATE TABLE users (
    id           UUID         NOT NULL,
    display_name VARCHAR(32)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id)
);
