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
| `ROOM_ALREADY_JOINED` | 409 | Already in this room |
| `ALREADY_IN_ROOM` | 409 | Already in another room |
| `NOT_ROOM_MEMBER` | 403 | Ready/leave/start as a non-member |
| `NOT_ROOM_HOST` | 403 | Non-host called start |
| `ROOM_ALREADY_STARTED` | 409 | Mutating a started room as if it were waiting |
| `NOT_ENOUGH_PLAYERS` | 409 | Below game `minPlayers` |
| `PLAYERS_NOT_READY` | 409 | Someone is not `READY` |
| `INVALID_MAX_PLAYERS` | 400 | `maxPlayers` outside the game range |
| `MATCH_NOT_FOUND` | 404 | Unknown match, or you did not play it |

Gameplay rule failures (`NOT_YOUR_TURN`, `CARD_NOT_IN_HAND`, …) are **WebSocket** `ACTION_REJECTED`, not REST.
