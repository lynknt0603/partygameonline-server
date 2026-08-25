# Game engine (implemented)

Describes **what exists now**.

## Split

| Layer | Responsibility |
| --- | --- |
| `GameEngine` | Rules, validation, state transition, events |
| `GameStateProjector` | Per-viewer view. Never send full `GameState` |
| `GameRuntime` | Session, lock, sequence, dispatch, snapshots |
| Concrete game module | Implements engine + projector; core does not import it |

`GameRegistry` resolves `gameType` → engine and projector from Spring beans. There is no `switch (gameType)`.

A catalogue `GameManifest` may exist without an engine. Starting that room still sets `STARTING` and does not create a session.

## Types

```text
GameEngine<S, A, E>
GameConfig
GameResult
ValidationResult
PlayerContext / ViewerKind
RandomSource / SeededRandomSource
GameStateProjector<S, V>
```

`createGame` and `apply` receive a server `RandomSource`. Tests use `SeededRandomSource`. Production seeds come from `SecureRandom` and are not sent to clients.

## Runtime

```text
GameRuntimeService
GameSession
GameSessionRepository
InMemoryGameSessionRepository
GameActionDispatcher
```

Active sessions live in memory, keyed by room id. Per-room lock is shared with lobby (`RoomLocks`).

## WebSocket

`GAME_ACTION` is decoded by the engine. Rejected actions send `ACTION_REJECTED` and do **not** increment `serverSequence`. Accepted actions send a per-player `GAME_EVENTS` (and `GAME_FINISHED` when done) that includes that viewer's projection only.

`ROOM_SNAPSHOT` includes `payload.view` when a session exists, plus a `GAME_SNAPSHOT` with the same view.

## Night of Bloodlines (`night-of-bloodlines`)

Enabled. 4–11 players. Package `com.partygameonline.game.nob`. Reuses `GameEngine` / `GameStateProjector` / `/ws` `GAME_ACTION`.

Start via generic room start. Actions: `NOB_DRAFT_PICK`, `NOB_PHASE_SUBMIT`, `NOB_CHOOSE_TARGET`, `NOB_CHOOSE_OPTION`, `NOB_HUNTER_DECISION`, `NOB_REACTION`. Timeouts are server-side only.

Projection: own hand/draft/bloodline (when known), own Moon Mark values, own observations, own pending decision. Public: seats, alive, moon **count**, revealed cards, publicly revealed bloodlines. Never other hands, hidden bloodlines, other token values, discard identities.

