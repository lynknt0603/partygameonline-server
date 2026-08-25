-- DEV demo data for ranking/login smoke tests.
--
-- Creates or refreshes lynknt01..lynknt20 with password `123`, then adds
-- completed NOB matches, round snapshots and ELO state. This is intentionally
-- demo data: do not run it against a production database.
--
-- Passwords are AES-256-GCM values produced with app.encryption.key=DEV_AES_KEY
-- (the same format as AesPasswordCipher: SHA-256 key, 12-byte IV, ciphertext,
-- authentication tag, Base64 encoded).

BEGIN;

INSERT INTO users (
    id,
    display_name,
    username,
    password_aes,
    user_key,
    created_date,
    update_date
)
VALUES
    (gen_random_uuid(), 'Lynknt01', 'lynknt01', '1MonSWlqp+YU0KgECTLyzBCAWwyTdGA8e2Vq+t/f2g==', gen_random_uuid()::text, NOW() - INTERVAL '20 days', NOW()),
    (gen_random_uuid(), 'Lynknt02', 'lynknt02', 'Hwb0dHTkcurW8dmn1X3rA3uzAc8gp+bua/FbGSJT1w==', gen_random_uuid()::text, NOW() - INTERVAL '19 days', NOW()),
    (gen_random_uuid(), 'Lynknt03', 'lynknt03', 'spu5E/gAqv08EaBDeFHVHXOrdAVZH8qHyi5TNPLEpw==', gen_random_uuid()::text, NOW() - INTERVAL '18 days', NOW()),
    (gen_random_uuid(), 'Lynknt04', 'lynknt04', 'Jav9KJ2RfNoFCAmZHpfdyB4ESFJTB4wPMuYXnii+aQ==', gen_random_uuid()::text, NOW() - INTERVAL '17 days', NOW()),
    (gen_random_uuid(), 'Lynknt05', 'lynknt05', 'tX4JAwdTOUe4iI3jfDkGG1mbcyQ8hobFFUIgYS42tg==', gen_random_uuid()::text, NOW() - INTERVAL '16 days', NOW()),
    (gen_random_uuid(), 'Lynknt06', 'lynknt06', 'FLpidadwVtU+RSgYqGMHVNUvsqNDwkWrb7D7P1TSXw==', gen_random_uuid()::text, NOW() - INTERVAL '15 days', NOW()),
    (gen_random_uuid(), 'Lynknt07', 'lynknt07', 'a9IigehiCez233G+PGpfZjs2CcOANsOIu+ULkT6ulQ==', gen_random_uuid()::text, NOW() - INTERVAL '14 days', NOW()),
    (gen_random_uuid(), 'Lynknt08', 'lynknt08', '7h+DB6WNwUMdcWi6cdgiyeAUiqPBx4WjJPTqBWwzWQ==', gen_random_uuid()::text, NOW() - INTERVAL '13 days', NOW()),
    (gen_random_uuid(), 'Lynknt09', 'lynknt09', 'NxUfhKAfJceoKdomwdiNqVzgvsBdrWnPz/vPnEFfrA==', gen_random_uuid()::text, NOW() - INTERVAL '12 days', NOW()),
    (gen_random_uuid(), 'Lynknt10', 'lynknt10', 'Hkuoik8JLzGIryETtdJGpfCdVD1+CawLciy/jBwdKQ==', gen_random_uuid()::text, NOW() - INTERVAL '11 days', NOW()),
    (gen_random_uuid(), 'Lynknt11', 'lynknt11', 'H4NXEGbtBY1knhrhKByiHtg8FY9uO+lBPOjnFpAysQ==', gen_random_uuid()::text, NOW() - INTERVAL '10 days', NOW()),
    (gen_random_uuid(), 'Lynknt12', 'lynknt12', 'gFJZUZ0mKhn+/IAgCUpRoYoskEA48QIh4toXpUHV5w==', gen_random_uuid()::text, NOW() - INTERVAL '9 days', NOW()),
    (gen_random_uuid(), 'Lynknt13', 'lynknt13', 'T41dkO75Dxd/s2BemgOpH+x4DXFcjx16KWXK5Uo4Aw==', gen_random_uuid()::text, NOW() - INTERVAL '8 days', NOW()),
    (gen_random_uuid(), 'Lynknt14', 'lynknt14', 'W/4EGkoKPJssintTO6LuoRi0ilt/5Sv3CS6ufshSJA==', gen_random_uuid()::text, NOW() - INTERVAL '7 days', NOW()),
    (gen_random_uuid(), 'Lynknt15', 'lynknt15', '5R8wF05788IYe+PxZ+zvV2JDhcEJnKnTYyARg9K8tA==', gen_random_uuid()::text, NOW() - INTERVAL '6 days', NOW()),
    (gen_random_uuid(), 'Lynknt16', 'lynknt16', 'IVZ5/sCHSZOQEnFpnmp6nmA51crRHBigWkpkd1uQEw==', gen_random_uuid()::text, NOW() - INTERVAL '5 days', NOW()),
    (gen_random_uuid(), 'Lynknt17', 'lynknt17', 'NdxEcaCL3qFpUzyWxoVkkgKydtSaLJbEBpzwMUJJrA==', gen_random_uuid()::text, NOW() - INTERVAL '4 days', NOW()),
    (gen_random_uuid(), 'Lynknt18', 'lynknt18', 'zmaGcoNBN5pLqkJcbXv48VqIRuaiF1zOEsai2LC2Gg==', gen_random_uuid()::text, NOW() - INTERVAL '3 days', NOW()),
    (gen_random_uuid(), 'Lynknt19', 'lynknt19', '29fpsZ6IqfP/+mkbLnryNJEeNxE5fW4ECcOO50gsgQ==', gen_random_uuid()::text, NOW() - INTERVAL '2 days', NOW()),
    (gen_random_uuid(), 'Lynknt20', 'lynknt20', 'k6hobVuL4f4sT5T5p81IxxtRHINXWHSb4x4zRbOosg==', gen_random_uuid()::text, NOW() - INTERVAL '1 day', NOW())
ON CONFLICT (username) DO UPDATE
SET display_name = EXCLUDED.display_name,
    password_aes = EXCLUDED.password_aes,
    update_date = NOW();

-- Re-running the seed replaces only its reserved demo matches.
DELETE FROM matches WHERE room_id LIKE 'LNK%';

CREATE TEMP TABLE demo_players (
    username       VARCHAR(32) PRIMARY KEY,
    user_id        UUID NOT NULL,
    player_id      VARCHAR(64) NOT NULL,
    display_name   VARCHAR(32) NOT NULL,
    rank_hint      INTEGER NOT NULL,
    elo            INTEGER NOT NULL,
    highest_elo    INTEGER NOT NULL,
    total_matches INTEGER NOT NULL,
    total_wins    INTEGER NOT NULL,
    bloodline     VARCHAR(32) NOT NULL,
    round_wins    INTEGER NOT NULL
) ON COMMIT DROP;

INSERT INTO demo_players (
    username, user_id, player_id, display_name, rank_hint, elo, highest_elo,
    total_matches, total_wins, bloodline, round_wins
)
SELECT
    demo.username,
    u.id,
    u.user_key,
    u.display_name,
    demo.rank_hint,
    demo.elo,
    demo.highest_elo,
    demo.total_matches,
    demo.total_wins,
    demo.bloodline,
    demo.round_wins
FROM (
    VALUES
        ('lynknt01', 1, 6250, 6321, 256, 164, 'VAMPIRE', 3),
        ('lynknt02', 2, 5800, 5867, 210, 140, 'WEREWOLF', 3),
        ('lynknt03', 3, 5450, 5512, 198, 128, 'HALFBLOOD', 2),
        ('lynknt04', 4, 5150, 5239, 180, 128, 'VAMPIRE', 3),
        ('lynknt05', 5, 5000, 5102, 164, 104, 'WEREWOLF', 2),
        ('lynknt06', 6, 4920, 4987, 150, 98, 'HALFBLOOD', 2),
        ('lynknt07', 7, 4800, 4876, 142, 95, 'VAMPIRE', 2),
        ('lynknt08', 8, 4700, 4765, 130, 87, 'WEREWOLF', 3),
        ('lynknt09', 9, 4600, 4652, 118, 76, 'HALFBLOOD', 2),
        ('lynknt10', 10, 4500, 4521, 106, 72, 'VAMPIRE', 1),
        ('lynknt11', 11, 4380, 4405, 102, 68, 'WEREWOLF', 2),
        ('lynknt12', 12, 4270, 4302, 94, 61, 'HALFBLOOD', 3),
        ('lynknt13', 13, 4180, 4205, 88, 58, 'VAMPIRE', 2),
        ('lynknt14', 14, 4080, 4110, 81, 52, 'WEREWOLF', 1),
        ('lynknt15', 15, 3990, 4025, 75, 47, 'HALFBLOOD', 1),
        ('lynknt16', 16, 3900, 3940, 68, 43, 'VAMPIRE', 2),
        ('lynknt17', 17, 3820, 3855, 61, 38, 'WEREWOLF', 1),
        ('lynknt18', 18, 3740, 3770, 55, 34, 'HALFBLOOD', 2),
        ('lynknt19', 19, 3620, 3650, 48, 28, 'VAMPIRE', 1),
        ('lynknt20', 20, 3480, 3515, 40, 22, 'WEREWOLF', 1)
) AS demo(username, rank_hint, elo, highest_elo, total_matches, total_wins, bloodline, round_wins)
JOIN users u ON u.username = demo.username;

INSERT INTO user_game_statistic (
    id, user_id, game_code, elo, elo_nob, highest_elo, total_match, total_win,
    version, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    player_id,
    'night-of-bloodlines',
    elo,
    elo,
    highest_elo,
    total_matches,
    total_wins,
    0,
    NOW() - (rank_hint * INTERVAL '1 day'),
    NOW()
FROM demo_players
ON CONFLICT (user_id, game_code) DO UPDATE
SET elo = EXCLUDED.elo,
    elo_nob = EXCLUDED.elo_nob,
    highest_elo = EXCLUDED.highest_elo,
    total_match = EXCLUDED.total_match,
    total_win = EXCLUDED.total_win,
    updated_at = NOW();

CREATE TEMP TABLE demo_matches (
    match_id    UUID PRIMARY KEY,
    player_id   VARCHAR(64) NOT NULL,
    user_id     UUID NOT NULL,
    display_name VARCHAR(32) NOT NULL,
    rank_hint   INTEGER NOT NULL,
    bloodline   VARCHAR(32) NOT NULL,
    result      VARCHAR(16) NOT NULL,
    score       INTEGER NOT NULL,
    started_at  TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL
) ON COMMIT DROP;

INSERT INTO demo_matches (
    match_id, player_id, user_id, display_name, rank_hint, bloodline, result, score,
    started_at, finished_at
)
SELECT
    gen_random_uuid(),
    player_id,
    user_id,
    display_name,
    rank_hint,
    bloodline,
    CASE WHEN round_wins >= 2 THEN 'WIN' ELSE 'LOSS' END,
    8 + round_wins,
    NOW() - (rank_hint * INTERVAL '1 day') - INTERVAL '8 minutes',
    NOW() - (rank_hint * INTERVAL '1 day')
FROM demo_players;

INSERT INTO matches (
    id, game_id, room_id, winner_player_id, result, elo_processed,
    started_at, finished_at, created_at
)
SELECT
    match_id,
    'night-of-bloodlines',
    'LNK' || LPAD(rank_hint::text, 5, '0'),
    CASE WHEN result = 'WIN' THEN player_id ELSE NULL END,
    result,
    TRUE,
    started_at,
    finished_at,
    started_at
FROM demo_matches;

INSERT INTO match_players (
    id, match_id, user_id, player_id, display_name, seat, created_at,
    result, score, role, bloodline
)
SELECT
    gen_random_uuid(),
    match_id,
    user_id,
    player_id,
    display_name,
    1,
    started_at,
    result,
    score,
    CASE bloodline
        WHEN 'VAMPIRE' THEN 'VAMPIRE'
        WHEN 'WEREWOLF' THEN 'WEREWOLF'
        ELSE 'SORCERESS'
    END,
    bloodline
FROM demo_matches;

INSERT INTO nob_game_rounds (
    id, game_id, round_number, player_id, bloodline, result, round_result,
    last_hope_triggered, score, elo_delta, created_at
)
SELECT
    gen_random_uuid(),
    match.match_id,
    rounds.round_number,
    player.player_id,
    player.bloodline,
    CASE WHEN rounds.round_number <= player.round_wins THEN 'WIN' ELSE 'LOSS' END,
    'SCORE',
    FALSE,
    match.score,
    0,
    match.started_at + (rounds.round_number * INTERVAL '1 minute')
FROM demo_players player
JOIN demo_matches match ON match.player_id = player.player_id
CROSS JOIN LATERAL generate_series(1, 3) AS rounds(round_number);

COMMIT;

-- Quick verification:
-- SELECT username, display_name FROM users WHERE username LIKE 'lynknt%' ORDER BY username;
-- SELECT display_name, highest_elo, total_win, total_match FROM user_game_statistic s
-- JOIN users u ON u.user_key = s.user_id WHERE s.game_code = 'night-of-bloodlines'
-- ORDER BY highest_elo DESC;
