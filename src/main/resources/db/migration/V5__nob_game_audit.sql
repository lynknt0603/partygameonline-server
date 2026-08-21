CREATE TABLE nob_game_session (
    room_id VARCHAR(8) PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    version INTEGER NOT NULL,
    phase VARCHAR(64) NOT NULL,
    round_number INTEGER NOT NULL,
    finished BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE nob_game_event (
    id UUID PRIMARY KEY,
    room_id VARCHAR(8) NOT NULL,
    sequence INTEGER NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    payload_json TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_nob_game_event_room ON nob_game_event (room_id, created_at);
