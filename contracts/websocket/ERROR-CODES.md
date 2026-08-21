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
| `NOT_YOUR_TURN` | ACTION_REJECTED | Demo: not current player |
| `ALREADY_DRAWN` | ACTION_REJECTED | Demo: second `DRAW_CARD` this turn |
| `DECK_EMPTY` | ACTION_REJECTED | Demo: draw from empty deck |
| `CARD_NOT_IN_HAND` | ACTION_REJECTED | Demo: card missing or opponent's |
| `GAME_ALREADY_FINISHED` | ACTION_REJECTED | Demo: action after winner |
| `NOT_IN_GAME` | ACTION_REJECTED | Actor is not a seated player |

Do not send `playerId`, damage, winner, or a new hand from the client. The server ignores identity in the payload.
