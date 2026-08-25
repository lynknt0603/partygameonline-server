# Frontend WebSocket contract

Canonical detail: `docs/WEBSOCKET-PROTOCOL.md`. This file is the SPA checklist.

## Connect

1. `GET /api/v1/csrf` then `POST /api/v1/session/guest` (cookies + CSRF).
2. Open `ws://host/ws` **with the same cookies**. Vite should proxy `/ws` so the cookie is first-party.
3. Server sends `CONNECTED` with the session `playerId`. Ignore any `playerId` you might put in a payload.

Handshake Origin is allow-listed. Unauthenticated sockets are rejected.

## Envelope

Client:

```json
{
  "version": 1,
  "type": "GAME_ACTION",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "roomId": "ABCD",
  "lastServerSequence": 3,
  "payload": { "type": "NOB_DRAFT_PICK", "cardInstanceId": "card-instance-1" }
}
```

`requestId` is required on every client message. Reuse of the same id by the same player returns `ACTION_REJECTED` / `DUPLICATE_REQUEST` and is not applied again.

Server:

```json
{
  "version": 1,
  "type": "GAME_EVENTS",
  "roomId": "ABCD",
  "serverSequence": 4,
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {}
}
```

`serverSequence` is per room. It increases only on **accepted** state changes. Rejections keep the previous sequence.

On reconnect, send `ROOM_SNAPSHOT` with `lastServerSequence`. The server answers with a full player-specific snapshot (no event replay). Replace local state.

## Client types

| type | payload | server replies |
| --- | --- | --- |
| `ROOM_SNAPSHOT` | `{}` | `ROOM_SNAPSHOT` (+ `GAME_SNAPSHOT` if a game session exists) |
| `GAME_ACTION` | action object | `GAME_EVENTS` / `GAME_FINISHED` or `ACTION_REJECTED` |

## Server types

Lobby: `CONNECTED`, `ROOM_SNAPSHOT`, `PLAYER_JOINED`, `PLAYER_LEFT`, `PLAYER_READY_CHANGED`, `PLAYER_DISCONNECTED`, `PLAYER_RECONNECTED`, `ERROR`.

Game: `GAME_STARTED`, `GAME_EVENTS`, `GAME_SNAPSHOT`, `GAME_FINISHED`, `ACTION_REJECTED`.

`RESYNC_REQUIRED` is sent when `lastServerSequence` is behind the room, followed by snapshots. Disconnect starts a grace period (`app.realtime.disconnect-grace`); reconnect restores the same `playerId` and sends snapshots. After grace, a waiting player is removed and an in-game player forfeits.

Almost every room-scoped message includes `payload.room` (`RoomResponse`). Game messages that have a session also include **this viewer's** `payload.view`. Never treat another player's message as your view.

## Night of Bloodlines view and actions

The `night-of-bloodlines` projection is viewer-specific. Send `NOB_*` action payloads through `GAME_ACTION`; the server validates the current phase, pending decision, and selected cards/targets before publishing the next projected view.

Error codes: `contracts/websocket/ERROR-CODES.md`.
