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
| GET | `/actuator/health` | public | `{ "status": "UP" }` |
| GET | `/actuator/info` | public | `{ "app": { "name", "phase" } }` |

## DTOs

### SessionResponse

```json
{ "playerId": "...", "displayName": "Linh", "kind": "GUEST", "currentRoomId": null }
```

`currentRoomId` is the live room the player is seated in, or omitted/null when they are not in a room.

`POST /session/guest` body: `{ "displayName": "Linh" }` (`1..32`). A second POST on the same session keeps `playerId` and updates `displayName`.

### GameResponse

```json
{ "id": "demo-card-game", "name": "Demo Card Game", "minPlayers": 2, "maxPlayers": 2, "enabled": true }
```

`night-of-bloodlines` is listed with `enabled: true`, `minPlayers: 4`, `maxPlayers: 11`.

NOB-specific REST (session + CSRF on POST):

| Method | Path | Success |
| --- | --- | --- |
| POST | `/api/v1/games/nob/rooms/{roomId}/start` | 200 `RoomResponse` (alias of generic start) |
| GET | `/api/v1/games/nob/rooms/{roomId}/snapshot` | 200 viewer `NobView` |
| POST | `/api/v1/games/nob/rooms/{roomId}/command` | 200 viewer `NobView` |

Generic `POST /api/v1/rooms/{roomId}/start` still starts NOB. See `docs/NOB_FRONTEND_API_PROMPT.md`.

### CreateRoomRequest

```json
{ "gameId": "demo-card-game", "name": "Linh's Room", "maxPlayers": 2, "visibility": "PUBLIC" }
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
  "gameId": "demo-card-game",
  "hostPlayerId": "...",
  "maxPlayers": 2,
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

Starting `demo-card-game` moves the room to `IN_GAME` (engine present).

Room ids are 4 characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`.

### MatchResponse

`GET /api/v1/matches?page=0&size=20` (page default 0, size default 20, max 50). Only matches the current player sat in. Newest `finishedAt` first.

```json
{
  "content": [
    {
      "id": "...",
      "gameId": "demo-card-game",
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
