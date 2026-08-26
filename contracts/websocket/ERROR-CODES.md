# WebSocket error codes

Transport / envelope problems use server type `ERROR`.

Rejected gameplay uses server type `ACTION_REJECTED`. Both carry:

```json
{ "errorCode": "NOT_YOUR_TURN", "message": "It is not your turn" }
```

`ACTION_REJECTED` does not increment `serverSequence`.

| errorCode | Type | When |
| --- | --- | --- |
| `MALFORMED_REQUEST` | ERROR | JSON is not an envelope |
| `MISSING_REQUEST_ID` | ERROR | `requestId` missing/blank |
| `UNKNOWN_TYPE` | ERROR | Unknown or blank `type` |
| `ROOM_REQUIRED` | ERROR | `roomId` missing |
| `NOT_ROOM_MEMBER` | ERROR / ACTION_REJECTED | Snapshot or action from a non-member |
| `ROOM_NOT_FOUND` | ACTION_REJECTED | Unknown room on `GAME_ACTION` |
| `DUPLICATE_REQUEST` | ACTION_REJECTED | Same player reused `requestId` |
| `GAME_NOT_RUNNING` | ACTION_REJECTED | No live session (or already finished) |
| `MALFORMED_ACTION` | ACTION_REJECTED | Engine could not decode `payload` |
| `NOT_YOUR_TURN` | ACTION_REJECTED | Action is not allowed for the actor in the current phase or decision |
| `CARD_NOT_IN_HAND` | ACTION_REJECTED | Selected card is not owned by the actor |
| `WRONG_CARD_CATEGORY` | ACTION_REJECTED | Ingredient/action card used for the wrong command |
| `INVALID_DECLARATION` | ACTION_REJECTED | Ingredient declaration is not supported |
| `INVALID_ACTION_TYPE` | ACTION_REJECTED | Action type is not supported |
| `ACTION_TYPE_MISMATCH` | ACTION_REJECTED | Declared action type does not match the server card |
| `INVALID_TARGET` | ACTION_REJECTED | Target is missing, invalid, or unavailable |
| `SELF_TARGET` | ACTION_REJECTED | Actor targeted themself |
| `TARGET_EXPELLED` | ACTION_REJECTED | Target is no longer active |
| `PLAYER_EXPELLED` | ACTION_REJECTED | Expelled player attempted to act |
| `WRONG_PHASE` | ACTION_REJECTED | Command is not valid in the current phase |
| `WRONG_PENDING_ACTION` | ACTION_REJECTED | Another pending decision must be completed first |
| `INVALID_REORDER` | ACTION_REJECTED | Reorder list is not exactly the inspected cards |
| `DUPLICATE_CARD` | ACTION_REJECTED | A card id was submitted more than once |
| `INVALID_CARD_COUNT` | ACTION_REJECTED | Pending action requires a different number of cards |
| `STALE_VERSION` | ACTION_REJECTED | `expectedVersion` is older than the current state |
| `MEAT_EATER_CANNOT_DECLARE` | ACTION_REJECTED | Only a Vegetarian can declare the pot ready |
| `POT_ALREADY_ACTED` | ACTION_REJECTED | Pot Ready is allowed only at turn beginning |
| `TIMEOUT_NOT_DUE` | ACTION_REJECTED | A pending decision has not expired |
| `GAME_ALREADY_FINISHED` | ACTION_REJECTED | Action was sent after the match finished |
| `NOT_IN_GAME` | ACTION_REJECTED | Actor is not a seated player |

Do not send `playerId`, damage, winner, or a new hand from the client. The server ignores identity in the payload.
