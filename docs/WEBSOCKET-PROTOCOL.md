# WebSocket protocol (implemented)

Raw JSON at `/ws`. No STOMP. The handshake uses the HTTP session cookie. The browser does not choose identity.

Frontend checklist: `contracts/websocket/PROTOCOL.md`. Error codes: `contracts/websocket/ERROR-CODES.md`.

## Connect

1. Create a guest session over REST (`PGOSESSION` cookie)
2. Open `ws://host/ws` with that cookie
3. Server sends `CONNECTED` with the authenticated `playerId`

Unauthenticated handshakes are rejected. `Origin` is checked against `app.security.cors.allowed-origins` (never `*`).

## Envelope

Client:

```json
{
  "version": 1,
  "type": "ROOM_SNAPSHOT",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "roomId": "ABCD",
  "lastServerSequence": 0,
  "payload": {}
}
```

`requestId` is required. `playerId` in the payload is ignored.

Server:

```json
{
  "version": 1,
  "type": "PLAYER_JOINED",
  "roomId": "ABCD",
  "serverSequence": 1,
  "requestId": null,
  "payload": {
    "playerId": "...",
    "room": {}
  }
}
```

`serverSequence` increases on accepted room/game state changes (join, leave, ready, start, disconnect, reconnect, accepted `GAME_ACTION`). Snapshots and `ACTION_REJECTED` reuse the current sequence (or omit it).

On missed messages, send `ROOM_SNAPSHOT` with `lastServerSequence`. The server does not replay events; it sends a player-specific snapshot. Replace local state.

## Client types

| type | result |
| --- | --- |
| `ROOM_SNAPSHOT` | `ROOM_SNAPSHOT` if the player is a member. Includes `payload.view`, recent `payload.chat`, and a `GAME_SNAPSHOT` when a session exists |
| `ROOM_CHAT` | Broadcast `ROOM_CHAT` to room members. Payload `{ text }` (max 240 chars). This is the room chat channel (equivalent of `/topic/room.{roomId}.chat`) |
| `GAME_ACTION` | Dispatched to the room's engine. Duplicate `requestId` → `DUPLICATE_REQUEST`. No session → `ACTION_REJECTED` / `GAME_NOT_RUNNING` |
| anything else | `ERROR` / `UNKNOWN_TYPE` |

Rejected actions do not increment `serverSequence`. Accepted actions send per-player `GAME_EVENTS` (and `GAME_FINISHED` if the engine reports a winner). Each player receives **only their** `payload.view`.

## Server types published from REST / engine

`PLAYER_JOINED`, `PLAYER_LEFT`, `PLAYER_READY_CHANGED`, `PLAYER_DISCONNECTED`, `PLAYER_RECONNECTED`, `ROOM_SETTINGS_CHANGED`, `ROOM_CHAT`, `GAME_STARTED`, `GAME_EVENTS`, `GAME_SNAPSHOT`, `GAME_FINISHED`.

Each room-scoped message includes `payload.room` (`RoomResponse`). Game messages that have a session also include this viewer's `payload.view`.

## Player-specific game view (`demo-card-game`)

```json
{
  "you": "p1",
  "currentPlayerId": "p1",
  "turnNumber": 1,
  "yourTurn": true,
  "hasDrawn": false,
  "hasPlayed": false,
  "hand": ["H-06", "C-01", "D-11", "S-03", "H-12"],
  "deckSize": 42,
  "opponentHandSize": 5,
  "discard": [],
  "finished": false,
  "winnerPlayerId": null
}
```

Never: opponent card ids, deck order, seed, or future randomness.

NOB (`night-of-bloodlines`) uses the same envelope. View shape and `NOB_*` actions: `docs/NOB_FRONTEND_API_PROMPT.md`.

## Demo actions

```json
{ "type": "DRAW_CARD" }
{ "type": "PLAY_CARD", "cardId": "H-06" }
{ "type": "END_TURN" }
```

Public events: `CARD_DRAWN` (no card id), `CARD_PLAYED` (`cardId` is public), `TURN_ENDED`, `GAME_WON`, `GAME_FORFEIT`.

## Reconnect

WebSocket close does **not** remove the seat. The player is marked `DISCONNECTED` and a grace timer starts (`app.realtime.disconnect-grace`, default 30s).

A new `/ws` connection with the same session:

1. Cancels the grace timer
2. Restores `CONNECTED` (lobby ready must be set again)
3. Publishes `PLAYER_RECONNECTED`
4. Sends `ROOM_SNAPSHOT` and, if a game is running, `GAME_SNAPSHOT` for this viewer

If `ROOM_SNAPSHOT` includes `lastServerSequence` behind the room, the server also sends `RESYNC_REQUIRED` then the snapshots. There is no event replay — replace local state.

If grace expires while still disconnected:

- `WAITING`: the player is removed (same as leave)
- `IN_GAME`: the demo engine forfeits and the opponent wins (`GAME_FINISHED` / `GAME_FORFEIT`)

`requestId` remains idempotent across retries.
