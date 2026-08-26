# REST API (implemented)

Prefix: `/api/v1`. Successful responses are the resource DTO (no `{ code, message, data }` wrapper).

Identity is always taken from the HTTP session. Request bodies that include `playerId` are ignored.

Auth and CSRF: `contracts/rest/SECURITY.md`.

## Endpoints

| Method | Path | Auth | Success |
| --- | --- | --- | --- |
| GET | `/api/v1/csrf` | public | `CsrfTokenResponse` + `XSRF-TOKEN` cookie |
| POST | `/api/v1/session/guest` | public + CSRF | 201 `SessionResponse` |
| GET | `/api/v1/session/me` | session | 200 `SessionResponse` |
| DELETE | `/api/v1/session` | session + CSRF | 204 |
| GET | `/api/v1/games` | public | 200 `GameResponse[]` |
| GET | `/api/v1/games/{gameId}` | public | 200 `GameResponse` |
| GET | `/api/v1/rooms` | session | 200 public `WAITING` rooms |
| POST | `/api/v1/rooms` | session + CSRF | 201 `RoomResponse` |
| GET | `/api/v1/rooms/{roomId}` | session | 200 `RoomResponse` |
| POST | `/api/v1/rooms/{roomId}/join` | session + CSRF | 200 `RoomResponse` |
| POST | `/api/v1/rooms/{roomId}/leave` | session + CSRF | 204 |
| PUT | `/api/v1/rooms/{roomId}/ready` | session + CSRF | 200 `RoomResponse` |
| POST | `/api/v1/rooms/{roomId}/start` | session + CSRF | 200 `RoomResponse` |
| PUT | `/api/v1/rooms/{roomId}/settings` | session + CSRF | 200 `RoomResponse` (host, WAITING; NOB timers) |
| GET | `/api/v1/matches` | session | 200 page of `MatchResponse` |
| GET | `/api/v1/matches/{matchId}` | session | 200 `MatchResponse` |
| GET | `/api/v1/profile/me/stats` | session | 200 `ProfileStatsResponse` |
| PATCH | `/api/v1/profile/me` | session + CSRF | 200 `SessionResponse` |
| GET | `/api/v1/profile/{usernameOrPlayerId}` | session | 200 public `ProfileStatsResponse` |
| GET | `/api/v1/players/search?query=...&limit=20` | session | 200 `PlayerSearchResponse[]` |
| GET | `/api/v1/rankings?gameId=night-of-bloodlines&sort=highestElo&bloodline=...` | session | 200 `RankingResponse` |
| GET | `/actuator/health` | public | `{ "status": "UP" }` |
| GET | `/actuator/info` | public | `{ "app": { "name", "phase" } }` |

## DTOs

### SessionResponse

```json
{ "playerId": "...", "displayName": "Linh", "kind": "GUEST", "currentRoomId": null }
```

`currentRoomId` is the live room the player is seated in, or omitted/null when they are not in a room.

`POST /session/guest` body: `{ "displayName": "Linh" }` (`1..32`). A second POST on an existing guest session keeps `playerId` and updates `displayName`. Member sessions are not downgraded to guests.

`PATCH /profile/me` body: `{ "displayName": "Linh Nguyen" }` (`1..32`). This updates the visible name in rooms and keeps the member's username and login session unchanged. `username` is the account login name; `displayName` is the name shown to other players.

### GameResponse

```json
{ "id": "night-of-bloodlines", "name": "Night of Bloodlines", "minPlayers": 4, "maxPlayers": 11, "enabled": true }
```

The catalogue currently exposes Night of Bloodlines with `enabled: true`.

NOB-specific REST (session + CSRF on POST):

| Method | Path | Success |
| --- | --- | --- |
| POST | `/api/v1/games/nob/rooms/{roomId}/start` | 200 `RoomResponse` (alias of generic start) |
| GET | `/api/v1/games/nob/rooms/{roomId}/snapshot` | 200 viewer `NobView` |
| POST | `/api/v1/games/nob/rooms/{roomId}/command` | 200 viewer `NobView` |

`NobView.players[]` includes `elo`, `eloDelta`, and `newElo` during a round
summary (and the final result). `eloDelta` is signed, for example `+55` or
`-45`. The profile endpoint also returns `nobStats.elo` and
`nobStats.highestElo`.

Generic `POST /api/v1/rooms/{roomId}/start` starts NOB rooms once all required players are present.

### RankingResponse

`GET /api/v1/rankings` uses the selected game's ELO state from
`user_game_statistic`; omit `gameId` for the default NOB ranking or pass
`gameId=not-in-my-pot` for Not In My Pot.
`sort` accepts `highestElo`, `wins`, or `bloodlineWins`; `bloodline` optionally
filters to `VAMPIRE`, `WEREWOLF`, or `HALFBLOOD`. The response includes the top
three `podium` entries, paged rows in `entries`, and the current player's `me`
entry when they are ranked. Member entries also include `username` so the web
client can link to `/profile/{username}`.

### PlayerSearchResponse

`GET /api/v1/players/search?query=...&limit=20` searches case-insensitively by
member `username` or `playerId`. Known guest player IDs from completed matches
are also returned; their `username` is `null`. Queries shorter than two
characters return an empty array. `limit` defaults to 20 and is capped at 50.

```json
[
  {
    "playerId": "...",
    "username": "lynknt05",
    "displayName": "Lynknt05"
  }
]
```

`ProfileStatsResponse.player` includes both `username` (null for guests) and
`displayName`, so clients can distinguish an account username from the visible
player name. Public profiles can be opened by username or player ID.

### CreateRoomRequest

```json
{ "gameId": "night-of-bloodlines", "name": "Linh's Room", "maxPlayers": 4, "visibility": "PUBLIC" }
```

`visibility` is `PUBLIC` or `PRIVATE` (default PUBLIC). `maxPlayers` optional; must be within the game's min/max.

### ReadyRequest

```json
{ "ready": true }
```

### RoomResponse

```json
{
  "id": "ABCD",
  "name": "Linh's Room",
  "gameId": "night-of-bloodlines",
  "hostPlayerId": "...",
  "maxPlayers": 4,
  "visibility": "PUBLIC",
  "status": "WAITING",
  "serverSequence": 0,
  "createdAt": "2026-08-19T00:00:00Z",
  "players": [
    { "playerId": "...", "displayName": "Linh", "state": "CONNECTED" }
  ]
}
```

`status`: `WAITING` | `STARTING` | `IN_GAME` | `FINISHED`.  
Player `state`: `CONNECTED` | `READY` | `DISCONNECTED`.

Starting Night of Bloodlines moves the room to `IN_GAME` once its minimum player count and ready checks pass.

Room ids are 4 characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`.

### MatchResponse

`GET /api/v1/matches?page=0&size=20` (page default 0, size default 20, max 50). Only matches the current player sat in. Newest `finishedAt` first.

```json
{
  "content": [
    {
      "id": "...",
      "gameId": "night-of-bloodlines",
      "roomId": "ABCD",
      "startedAt": "...",
      "finishedAt": "...",
      "winnerPlayerId": "...",
      "result": "COMPLETED",
      "players": [
        { "playerId": "...", "displayName": "Linh", "seat": 0, "winner": true }
      ]
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

`result` is `COMPLETED` or `FORFEIT`. History never includes hands, deck order, or other hidden cards. A match you did not play is `MATCH_NOT_FOUND`.

## Errors

One shape:

```json
{
  "errorCode": "ROOM_FULL",
  "message": "The room is full",
  "timestamp": "...",
  "path": "/api/v1/rooms/ABCD/join",
  "requestId": "...",
  "fieldErrors": []
}
```

Send `X-Request-Id` to correlate; the server echoes it. Codes: `contracts/rest/ERROR-CODES.md`.

## Not In My Pot!

The game catalogue id/room `gameId` is `not-in-my-pot`; the product game code is
`NOT_IN_MY_POT`. It supports 3–8 players and is registered in `GET /api/v1/games`.

### Start

Use the existing lobby flow (`POST /api/v1/rooms/{roomId}/start`) or the game-specific
alias below. The caller must be the room host and all existing ready checks still apply.

```http
POST /api/v1/games/not-in-my-pot/rooms/{roomId}/start
```

### Snapshot

```http
GET /api/v1/games/not-in-my-pot/rooms/{roomId}/snapshot
```

The response is a viewer-specific `NotInMyPotView`. A player receives `myRole` and
`myHand`; other hands, unrevealed roles, the draw pile, and the live pot contents are
never returned. `stateVersion` can be sent back as `expectedVersion` for optimistic
concurrency checks.

### Command

```http
POST /api/v1/games/not-in-my-pot/rooms/{roomId}/command
Content-Type: application/json
```

```json
{
  "commandId": "client-generated-uuid",
  "type": "PLAY_INGREDIENT",
  "expectedVersion": 12,
  "cardId": "NIMP-I-MEAT-04",
  "declaredType": "VEGETABLE"
}
```

Supported command types and fields:

| type | fields |
| --- | --- |
| `PLAY_INGREDIENT` | `cardId`, `declaredType` (`VEGETABLE`, `SALT`, `MEAT`) |
| `PLAY_ACTION` | `cardId`, optional `actionType`, optional `targetPlayerId` |
| `SELECT_TARGET` | `targetPlayerId` |
| `REORDER_POT_CARDS` | `cardIds` in `TOP` → `BOTTOM` order |
| `RETURN_SHOPPING_CARDS` | `cardIds` with exactly two cards, in `TOP` → `SECOND` order |
| `DECLARE_POT_READY` | no extra fields; only a Vegetarian at the start of their turn |

`commandId` is idempotent. A missing id is generated for REST requests; clients should
send a stable id when retrying. Rejected gameplay commands return HTTP `409` with the
standard error body and do not change the game.

For the full view fields, privacy rules, action lifecycle, and WebSocket examples see
[`docs/NOT-IN-MY-POT.md`](NOT-IN-MY-POT.md).
