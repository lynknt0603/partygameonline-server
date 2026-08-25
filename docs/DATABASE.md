# Database

Durable data only. Active rooms and live game state stay in memory.

## Engine

PostgreSQL. Schema is owned by Flyway. Hibernate `ddl-auto` is `validate`.

Local databases created for this machine:

| Database | Use |
| --- | --- |
| `partygameonline` | `dev` / default run |
| `partygameonline_test` | `test` profile |

Role: `partygameonline` / `partygameonline`.

## Environment

```text
DB_URL=jdbc:postgresql://127.0.0.1:5432/partygameonline
DB_USERNAME=partygameonline
DB_PASSWORD=partygameonline
```

Do not commit real production credentials.

## Migrations

`src/main/resources/db/migration/`

| File | Purpose |
| --- | --- |
| `V1__create_users.sql` | Durable user identity |
| `V2__create_matches.sql` | Completed-match header |
| `V3__create_match_players.sql` | Players in a completed match |
| `V4__match_history_fields.sql` | `room_id`, `winner_player_id`, `result` |
| `V5__nob_game_audit.sql` | NOB live audit session and public events |
| `V6__create_username_password_login.sql` | Username/password credentials |
| `V7__add_match_player_statistics.sql` | Final game player score and NOB identity fields |
| `V8__create_nob_game_rounds.sql` | Per-player round snapshots for Night of Bloodlines |
| `V9__create_user_game_statistics.sql` | Per-user/per-game ELO state and round ELO deltas |
| `V10__add_match_elo_marker.sql` | Idempotent match ELO processing marker |
| `V11__rename_user_game_statistic_table.sql` | Canonical singular statistic table name |

`user_credentials` and `friendships` are not created yet.

## Tables (BE-02)

### users

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | Server-generated |
| display_name | VARCHAR(32) | Not login identity |
| created_at | TIMESTAMPTZ | |
| updated_at | TIMESTAMPTZ | |

Guests may later exist only as a session (`player_id`) without a `users` row. That is still open.

### matches

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| game_id | VARCHAR(64) | Catalogue slug, not rules |
| started_at | TIMESTAMPTZ | From the live session |
| finished_at | TIMESTAMPTZ | Set when the match is persisted as complete |
| created_at | TIMESTAMPTZ | |
| room_id | VARCHAR(8) | Lobby code at persist time (V4) |
| winner_player_id | VARCHAR(64) | V4 |
| result | VARCHAR(32) | `COMPLETED` or `FORFEIT` (V4) |
| elo_processed | BOOLEAN | ELO/statistics end-game idempotency marker |

No hidden card data. History API: `GET /api/v1/matches`.

### match_players

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| match_id | UUID FK → matches | Cascade delete |
| user_id | UUID FK → users | Nullable (guest / unlinked) |
| player_id | VARCHAR(64) | Authoritative in-match identity |
| display_name | VARCHAR(32) | Snapshot at persist time |
| seat | SMALLINT | Nullable |
| result | VARCHAR(16) | `WIN` / `LOSS` (V4) |
| created_at | TIMESTAMPTZ | |

Unique `(match_id, player_id)`.

### nob_game_rounds

The `matches` row is the generic completed-game header (`game_id` identifies the
catalogue game). NOB writes one row per player per completed round in this child
table, so a single NOB game can contribute multiple round statistics for the
same profile.

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| game_id | UUID FK → matches | Cascade delete |
| round_number | INTEGER | Round number inside the game |
| player_id | VARCHAR(64) | In-game player identity |
| bloodline | VARCHAR(32) | Bloodline used in this round |
| result | VARCHAR(16) | `WIN` / `LOSS` for this player in this round |
| round_result | VARCHAR(32) | NOB round scoring result |
| last_hope_triggered | BOOLEAN | NOB round rule flag |
| score | INTEGER | Cumulative Moon Mark score at round scoring |
| elo_delta | INTEGER | ELO change preview shown for this round; rating writes commit with the completed match |
| created_at | TIMESTAMPTZ | |

Unique `(game_id, round_number, player_id)`.

### user_game_statistic

One row is maintained for each `(user_id, game_code)`. New players start at
ELO 5000 and ELO never goes below zero. `elo` is the generic per-game rating;
`elo_nob` is the explicit Night of Bloodlines column reserved for the NOB
leaderboard and future game-specific columns.

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| user_id | VARCHAR(64) | Stable in-game/user identity |
| game_code | VARCHAR(64) | Catalogue game id |
| elo | INTEGER | Current rating |
| elo_nob | INTEGER | Current NOB rating |
| highest_elo | INTEGER | High-water mark |
| total_match | INTEGER | Completed matches, incremented once |
| total_win | INTEGER | Completed matches won |
| version | BIGINT | Optimistic version for concurrent writes |
| created_at / updated_at | TIMESTAMPTZ | |

## JPA

Entities live under `*.infrastructure` and are not API types.

- `UserEntity` / `UserJpaRepository`
- `MatchEntity` / `MatchJpaRepository`
- `MatchPlayerEntity` / `MatchPlayerJpaRepository`
- `UserGameStatisticEntity` / `UserGameStatisticJpaRepository`

No `@ManyToMany`, no bidirectional graph, no EAGER collections. `match_players.match_id` is a UUID column, not a `@ManyToOne`.
