# Not In My Pot! — FE/API contract

## Game identity and lifecycle

- Catalogue/room id: `not-in-my-pot`
- Product game code: `NOT_IN_MY_POT`
- Players: 3–8
- Phases: `STARTING`, `PLAYING`, `RESOLVING_ACTION`, `GAME_OVER`
- Every accepted command increments `stateVersion`.
- `commandId` is deduplicated by the authoritative state. A retry does not apply a
  second mutation.

Create/join/ready through the existing room APIs. Start with either:

```http
POST /api/v1/rooms/{roomId}/start
POST /api/v1/games/not-in-my-pot/rooms/{roomId}/start
```

Then use:

```http
GET  /api/v1/games/not-in-my-pot/rooms/{roomId}/snapshot
POST /api/v1/games/not-in-my-pot/rooms/{roomId}/command
```

The same action payloads can be sent through WebSocket `GAME_ACTION`.

## Initial state

The server shuffles roles separately from the deck, deals three cards to each player,
and chooses the first active player randomly. Role distribution, `OUT_OF_HOUSE` card
count, and target score are fixed by player count:

| players | Vegetarian | Meat Eater | `OUT_OF_HOUSE` | target score |
| ---: | ---: | ---: | ---: | ---: |
| 3 | 2 | 1 | 12 | 5 |
| 4 | 3 | 1 | 14 | 8 |
| 5 | 4 | 1 | 16 | 11 |
| 6 | 4 | 2 | 18 | 6 |
| 7 | 5 | 2 | 20 | 9 |
| 8 | 6 | 2 | 22 | 12 |

Ingredient cards are 20 `VEGETABLE` (+1), 10 `SALT` (0), and 12 `MEAT` (-2). There
are three each of `SCOOP_OUT`, `SLOTTED_SPOON`, `EMERGENCY_SHOPPING`, and `TRASH_OUT`.
Card ids are server-owned opaque identifiers; FE may display `category`/`type` but
must never submit a score/value.

## Viewer-specific view

Every REST snapshot and WebSocket game message contains only the requesting player's
projection:

```json
{
  "gameType": "not-in-my-pot",
  "roomId": "ABCD",
  "you": "player-1",
  "phase": "PLAYING",
  "stateVersion": 12,
  "serverTime": "2026-08-26T09:00:00Z",
  "finished": false,
  "currentPlayerId": "player-1",
  "turnNumber": 4,
  "targetScore": 8,
  "winnerFaction": null,
  "winnerPlayerIds": [],
  "players": [],
  "myRole": "VEGETARIAN",
  "myHand": [],
  "drawPileCount": 42,
  "potCardCount": 5,
  "discardPileCount": 8,
  "publicRoles": {},
  "publicEvents": [],
  "pendingAction": null,
  "privateInspectedCards": [],
  "finalPotScore": null,
  "finalPot": [],
  "canDeclarePotReady": false,
  "canAct": true
}
```

`players[].role` is present only for the viewer's own player, an expelled player, or
after game over. `myHand` is private. The live pot exposes only its count. The final
pot is bottom-to-top and card values are revealed only after `GAME_OVER`.

An action-resolution view has `pendingAction.type`:

- `SELECT_TARGET`: `allowedTargetPlayerIds` is public and the actor chooses one.
- `REORDER_POT_CARDS`: only the actor receives `privateInspectedCards` and its
  `allowedCardIds`; submit the same cards in the desired top-to-bottom order.
- `RETURN_SHOPPING_CARDS`: only the actor receives the allowed hand card ids; submit
  exactly two cards in top-to-second order.

`pendingAction.deadline` is the server deadline. A timeout is applied automatically;
the FE does not need to send a timeout command.

## Commands

All commands accept `commandId` and optional `expectedVersion`.

### Play an ingredient / bluff

```json
{
  "type": "PLAY_INGREDIENT",
  "cardId": "NIMP-I-MEAT-04",
  "declaredType": "VEGETABLE"
}
```

The actual card is placed on the top of the pot, while the declaration is public.
The server validates card ownership and declaration syntax, not whether the claim is
truthful.

### Play an action

```json
{
  "type": "PLAY_ACTION",
  "cardId": "NIMP-A-OUT_OF_HOUSE-03",
  "actionType": "OUT_OF_HOUSE",
  "targetPlayerId": "player-2"
}
```

`actionType` is optional when the card id is enough. `OUT_OF_HOUSE` adds one door;
the third door expels the target, reveals their role, discards their hand, and skips
their future turns. `SCOOP_OUT` removes up to two top pot cards without revealing
them. `SLOTTED_SPOON` inspects up to three top cards privately and then reorders them.
`EMERGENCY_SHOPPING` draws three and returns exactly two. `TRASH_OUT` discards the
target's hand and draws three replacement cards without revealing them.

### Finish a pending action

```json
{ "type": "SELECT_TARGET", "targetPlayerId": "player-2" }
```

```json
{
  "type": "REORDER_POT_CARDS",
  "cardIds": ["NIMP-I-SALT-02", "NIMP-I-MEAT-07"]
}
```

```json
{
  "type": "RETURN_SHOPPING_CARDS",
  "cardIds": ["NIMP-I-VEGETABLE-04", "NIMP-A-SCOOP_OUT-01"]
}
```

### Declare pot ready

```json
{ "type": "DECLARE_POT_READY" }
```

Only a Vegetarian may use this at the beginning of their own turn, before playing a
card. The server calculates the actual pot score. Vegetarian wins when the score is
at least the player-count target; otherwise Meat Eater wins.

## Win conditions and result

The server checks after relevant operations in this order:

1. all active Meat Eaters expelled → `VEGETARIAN`;
2. active Vegetarian count equals active Meat Eater count → `MEAT_EATER`;
3. draw pile empty → `MEAT_EATER`.

After game over, all roles and the complete pot are visible. `winnerPlayerIds` contains
every player in the winning faction, including an expelled player. The player summary
contains `oldElo`, `eloDelta`, and `newElo` after the shared ELO service records the
match.

## WebSocket mapping

Send the action inside the existing envelope:

```json
{
  "version": 1,
  "type": "GAME_ACTION",
  "requestId": "client-generated-uuid",
  "roomId": "ABCD",
  "payload": {
    "type": "PLAY_INGREDIENT",
    "commandId": "client-generated-uuid",
    "expectedVersion": 12,
    "cardId": "NIMP-I-VEGETABLE-01",
    "declaredType": "MEAT"
  }
}
```

The server sends `GAME_EVENTS` with public event metadata and a viewer-specific
`payload.view`; `GAME_FINISHED` follows when the result is final. Expelled players
continue receiving public state and may chat, but cannot play, become a new target,
take a turn, or declare the pot.

Public event types include `TURN_STARTED`, `INGREDIENT_DECLARED`, `ACTION_STARTED`,
`TARGET_SELECTION_REQUIRED`, `PLAYER_DOOR_UPDATED`, `PLAYER_EXPELLED`,
`SCOOP_OUT_RESOLVED`, `POT_REORDER_REQUIRED`, `POT_REORDERED`,
`EMERGENCY_SHOPPING_RESOLVED`, `SHOPPING_RETURN_REQUIRED`, `SHOPPING_CARDS_RETURNED`,
`TRASH_OUT_RESOLVED`, `POT_REVEALED`, `ACTION_TIMED_OUT`, and `GAME_ENDED`.

## Rule rejection codes

The engine returns a stable error code suitable for FE translation:
`DUPLICATE_REQUEST`, `STALE_VERSION`, `NOT_IN_GAME`, `PLAYER_EXPELLED`,
`NOT_YOUR_TURN`, `WRONG_PHASE`, `CARD_NOT_IN_HAND`, `WRONG_CARD_CATEGORY`,
`INVALID_DECLARATION`, `INVALID_ACTION_TYPE`, `ACTION_TYPE_MISMATCH`,
`INVALID_TARGET`, `SELF_TARGET`, `TARGET_EXPELLED`, `WRONG_PENDING_ACTION`,
`INVALID_REORDER`, `DUPLICATE_CARD`, `INVALID_CARD_COUNT`,
`MEAT_EATER_CANNOT_DECLARE`, and `POT_ALREADY_ACTED`.

## Reconnect and abandonment

Reconnect uses the existing room WebSocket snapshot flow and returns the same private
projection. Disconnect grace is handled by the platform. If a player abandons during
an action resolution, the server completes a safe deterministic fallback, discards
their hand, skips them, and keeps the role private unless a normal rule or game end
reveals it.
