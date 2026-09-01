CREATE TABLE user_achievement (
    id UUID NOT NULL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    achievement_code VARCHAR(64) NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0 CHECK (progress >= 0),
    target INTEGER NOT NULL CHECK (target > 0),
    unlocked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_user_achievement_user_code UNIQUE (user_id, achievement_code)
);

CREATE INDEX idx_user_achievement_user ON user_achievement (user_id);

CREATE TABLE user_avatar_unlock (
    id UUID NOT NULL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    avatar_key VARCHAR(128) NOT NULL,
    source VARCHAR(64) NOT NULL,
    unlocked_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_user_avatar_unlock_user_avatar UNIQUE (user_id, avatar_key)
);

CREATE INDEX idx_user_avatar_unlock_user ON user_avatar_unlock (user_id);

INSERT INTO user_avatar_unlock (id, user_id, avatar_key, source, unlocked_at)
SELECT (md5(u.id::text || ':' || avatar.avatar_key))::uuid,
       u.id, avatar.avatar_key, 'FREE', CURRENT_TIMESTAMP
FROM users u
CROSS JOIN (VALUES
    ('default.png'), ('09_happy_dog.png'), ('10_black_cat.png'),
    ('11_calm_panda.png'), ('15_rabbit.png'), ('16_frog.png')
) AS avatar(avatar_key)
ON CONFLICT (user_id, avatar_key) DO NOTHING;

INSERT INTO user_achievement
    (id, user_id, achievement_code, progress, target, unlocked_at, created_at, updated_at)
SELECT (md5(u.id::text || ':' || achievement.code))::uuid,
       u.id, achievement.code, 0, achievement.target, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM users u
CROSS JOIN (VALUES
    ('NIMP_POT_REVEALED', 10), ('NIMP_TOFU_PLAYED', 20),
    ('NIMP_MEAT_PLAYED', 20), ('NIMP_VEGETABLE_PLAYED', 20),
    ('NIMP_VEGETARIAN_WINS', 10), ('NIMP_MEAT_EATER_WINS', 10),
    ('NOB_HALFBLOOD_PLAYED', 20), ('NOB_VAMPIRE_PLAYED', 20),
    ('NOB_WEREWOLF_PLAYED', 20), ('NOB_HALFBLOOD_WINS', 20),
    ('NOB_VAMPIRE_WINS', 20), ('NOB_WEREWOLF_WINS', 20),
    ('RANKING_TOP_ONE', 1), ('ACHIEVEMENT_MASTER', 13)
) AS achievement(code, target)
ON CONFLICT (user_id, achievement_code) DO NOTHING;
