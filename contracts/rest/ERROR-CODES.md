# REST error codes

Every error body has `errorCode`, `message`, `timestamp`, `path`, `requestId`, `fieldErrors`.

| errorCode | HTTP | When |
| --- | --- | --- |
| `UNAUTHENTICATED` | 401 | No session |
| `FORBIDDEN` | 403 | Authenticated but not allowed |
| `CSRF_REJECTED` | 403 | Missing/invalid CSRF on a mutating request |
| `VALIDATION_FAILED` | 400 | Bean Validation (`fieldErrors` filled) |
| `NOT_FOUND` | 404 | Unknown HTTP path |
| `METHOD_NOT_ALLOWED` | 405 | Wrong HTTP method |
| `INTERNAL_ERROR` | 500 | Unexpected. Message is generic; no Java internals |
| `GAME_NOT_FOUND` | 404 | Unknown catalogue id |
| `GAME_DISABLED` | 400 | Catalogue exists but `enabled=false` |
| `ROOM_NOT_FOUND` | 404 | Unknown room id |
| `ROOM_FULL` | 409 | Join over `maxPlayers` |
| `ROOM_LOCKED` | 409 | Host locked the waiting room; new players cannot join |
| `ROOM_ALREADY_JOINED` | 409 | Already in this room |
| `ALREADY_IN_ROOM` | 409 | Already in another room |
| `NOT_ROOM_MEMBER` | 403 | Ready/leave/start as a non-member |
| `NOT_ROOM_HOST` | 403 | Non-host called start |
| `ROOM_ALREADY_STARTED` | 409 | Mutating a started room as if it were waiting |
| `NOT_ENOUGH_PLAYERS` | 409 | Below game `minPlayers` |
| `PLAYERS_NOT_READY` | 409 | Someone is not `READY` |
| `INVALID_MAX_PLAYERS` | 400 | `maxPlayers` outside the game range |
| `MATCH_NOT_FOUND` | 404 | Unknown match, or you did not play it |
| `WRONG_GAME` | 409 | The room is not a `not-in-my-pot` room for the game-specific endpoint |
| `GAME_NOT_RUNNING` | 409 | The room has no active game session |
| `DUPLICATE_REQUEST` | 409 | The command id was already applied |
| `STALE_VERSION` | 409 | `expectedVersion` does not match the current game state |
| `NOT_YOUR_TURN` | 409 | Command is not allowed for the current actor |
| `PLAYER_EXPELLED` | 409 | Expelled player attempted to act |
| `WRONG_PHASE` | 409 | Command is not valid in the current phase |
| `CARD_NOT_IN_HAND` | 409 | Selected card is not owned by the actor |
| `WRONG_CARD_CATEGORY` | 409 | Ingredient/action card used for the wrong command |
| `INVALID_DECLARATION` | 409 | Ingredient declaration is not supported |
| `INVALID_ACTION_TYPE` | 409 | Action type is not supported |
| `ACTION_TYPE_MISMATCH` | 409 | Declared action type does not match the server card |
| `INVALID_TARGET` | 409 | Target is missing, invalid, or unavailable |
| `SELF_TARGET` | 409 | Actor targeted themself |
| `TARGET_EXPELLED` | 409 | Target is no longer active |
| `WRONG_PENDING_ACTION` | 409 | Another pending decision must be completed first |
| `INVALID_REORDER` | 409 | Reorder list is not exactly the inspected cards |
| `DUPLICATE_CARD` | 409 | A card id was submitted more than once |
| `INVALID_CARD_COUNT` | 409 | Pending action requires a different number of cards |
| `MEAT_EATER_CANNOT_DECLARE` | 409 | Only a Vegetarian can declare the pot ready |
| `POT_ALREADY_ACTED` | 409 | Pot Ready is allowed only at turn beginning |

Gameplay rule failures (`NOT_YOUR_TURN`, `CARD_NOT_IN_HAND`, …) are **WebSocket**
`ACTION_REJECTED` when sent over `/ws`, or HTTP `409` with the same `errorCode` when
sent to the game-specific REST command endpoint.
