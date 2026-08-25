# Backend architecture (implemented)

This document describes **what exists now**. Planned modules are named only so later phases know where to put code.

## Current shape (BE-12)

Modular monolith. Single Spring Boot 4.1.0 process. Java 21. Maven. PostgreSQL for durable data. Server-side session auth.

```text
com.partygameonline
├── common/          request id + API errors
├── config/          reserved
├── security/        SecurityFilterChain, CSRF, CORS, JSON 401/403
├── session/         guest session API
├── user/
│   └── infrastructure/   UserEntity + UserJpaRepository
├── catalog/         GET /api/v1/games
├── room/            in-memory lobby, shared RoomLocks
├── realtime/        raw JSON /ws, room + per-player game broadcasts
├── game/
│   ├── core/        GameManifest, GameEngine, GameRegistry, RandomSource, projector
│   ├── runtime/     GameRuntimeService, sessions, GameActionDispatcher
│   └── nob/          Night of Bloodlines engine + projector
├── history/         GET /api/v1/matches + persistence
└── PartyGameOnlineApplication.java
```

`POST /rooms/{id}/start` creates an in-memory `GameSession` when a `GameEngine` is registered. Night of Bloodlines has an engine, so a valid start goes to `IN_GAME`.
See `docs/GAME-ENGINE.md`.

## HTTP

- App port: `8080`
- Actuator: `/actuator/health`, `/actuator/info` only (public)
- Session: `POST /api/v1/session/guest`, `GET /api/v1/session/me`, `DELETE /api/v1/session`
- CSRF bootstrap: `GET /api/v1/csrf`
- Catalogue: `GET /api/v1/games`, `GET /api/v1/games/{gameId}` (public)
- Rooms (session): create/list/get/join/leave/ready/start
- History (session): `GET /api/v1/matches`, `GET /api/v1/matches/{matchId}`
- WebSocket: `/ws` (session handshake, JSON envelope)
- Other `/api/**` requires a session
- Errors use one JSON body (`errorCode`, `message`, `timestamp`, `path`, `requestId`, `fieldErrors`)
- Correlation header: `X-Request-Id` (generated when missing)
- Frontend contracts: `docs/REST-API.md`, `contracts/rest/`, `contracts/websocket/`

## Persistence

- Active rooms: in-memory `RoomRepository` (not PostgreSQL)
- Live game state: in-memory `GameSession` (Night of Bloodlines engine).
- Users + completed matches: PostgreSQL + Flyway
- Schema details: `docs/DATABASE.md`
- Hibernate `ddl-auto=validate`. No auto schema mutation.

## Authority rule (future gameplay)

The browser sends intent only. The server validates, applies rules, and broadcasts projected views. Never trust client-supplied game results.
