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

## Night of Bloodlines game view

`night-of-bloodlines` uses the same envelope structure for all `NOB_*` action types and projected views. The projection is viewer-specific: it includes that player's hand, private bloodline observations, pending decisions, and Moon Mark values while exposing only public seats, counts, and reveals to other players.

## Not In My Pot! game view

`not-in-my-pot` uses the same `GAME_ACTION` envelope for these engine commands:

```text
PLAY_INGREDIENT
PLAY_ACTION
SELECT_TARGET
ACKNOWLEDGE_SLOTTED_SPOON
RETURN_SHOPPING_CARDS
DECLARE_POT_READY
```

The payload is viewer-specific. `myRole` and `myHand` belong only to the authenticated
player; live pot cards, other hands, and unrevealed roles are omitted. During
`SLOTTED_SPOON`, only the acting player receives `privateInspectedCards`. During
`EMERGENCY_SHOPPING`, only the acting player receives the five-card hand and return
selection. After `GAME_OVER`, roles and the final pot are revealed.

Example action:

```json
{
  "version": 1,
  "type": "GAME_ACTION",
  "requestId": "nimp-command-1",
  "roomId": "ABCD",
  "payload": {
    "type": "PLAY_ACTION",
    "expectedVersion": 12,
    "cardId": "NIMP-A-OUT_OF_HOUSE-03",
    "actionType": "OUT_OF_HOUSE",
    "targetPlayerId": "player-2"
  }
}
```

The server publishes public event metadata in `GAME_EVENTS` and includes each
recipient's `payload.view`. `commandId` is optional in the payload when the envelope
`requestId` is stable; the dispatcher uses that request id for engine idempotency.

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
- `IN_GAME`: the Night of Bloodlines engine applies its abandonment rules and publishes the resulting game state.

`requestId` remains idempotent across retries.
