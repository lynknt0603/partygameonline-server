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

## JPA

Entities live under `*.infrastructure` and are not API types.

- `UserEntity` / `UserJpaRepository`
- `MatchEntity` / `MatchJpaRepository`
- `MatchPlayerEntity` / `MatchPlayerJpaRepository`

No `@ManyToMany`, no bidirectional graph, no EAGER collections. `match_players.match_id` is a UUID column, not a `@ManyToOne`.
