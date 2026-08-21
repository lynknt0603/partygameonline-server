# partygameonline-server

Backend service for Party Game Online, a real-time multiplayer party and board game platform.

The system is built as a server-authoritative modular monolith in Spring Boot and Java 21, designed to support concurrent rooms, guest sessions, and real-time WebSocket communication across diverse party game modules.

## Architecture & Overview

- **Server-Authoritative Game Logic:** Game state transitions, action validation, and hidden information remain exclusively on the server.
- **Per-Viewer State Projection:** Each client receives only the information visible to that specific player (hidden roles, private cards, and unrevealed tokens are kept secure in memory).
- **Pluggable Game Engine:** Games implement `GameEngine` and `GameStateProjector` contracts without coupling core networking or lobby systems to specific game rules.
- **In-Memory Live State:** Active rooms, locks, and game sessions run entirely in-memory for sub-millisecond latency.
- **Durable Persistence:** Match records, history, and users are persisted in PostgreSQL via Flyway migrations.

## Game Modules

| Game Identifier | Name | Players | Description |
|---|---|---|---|
| `night-of-bloodlines` | Night of Bloodlines | 4–11 | Social deduction and bluffing game featuring 5 night phases, card drafting, Moon Mark mechanics, and live reactions. |
| `demo-card-game` | Demo Card Game | 2 | Turn-based 52-card game testing draw/play validation, state transitions, and victory conditions. |

## Tech Stack

- **Java 21**
- **Spring Boot 4.1.0** (Spring WebMVC, Spring WebSocket, Spring Security, Spring Data JPA)
- **PostgreSQL 13+** with Flyway migration
- **Testing:** JUnit 5, Mockito, Spring Boot Test

## Repository Layout

```text
com.partygameonline
├── PartyGameOnlineApplication.java
├── catalog/          # Game metadata and catalog discovery
├── common/           # Request ID filter, exception handlers
├── config/           # App configuration and scheduling
├── game/
│   ├── core/         # Game engine contracts and registry
│   ├── runtime/      # Active session management and dispatcher
│   ├── games/demo/   # Demo card game module
│   └── nob/          # Night of Bloodlines engine and projector
├── history/          # Match history persistence and query API
├── realtime/         # WebSocket handler, message envelopes, chat
├── room/             # Room repository, seats, concurrency locks
├── security/         # Security filter chain, CSRF, CORS
├── session/          # Guest session cookie management
└── user/             # User JPA entities and repositories
```

## Getting Started

### Prerequisites

- JDK 21+
- PostgreSQL 13+

### Database Setup

```sql
CREATE DATABASE partygameonline;
CREATE DATABASE partygameonline_test;
```

### Build & Test

```bash
# Run tests
./mvnw clean test

# Build package
./mvnw clean package
```

On Windows:
```powershell
.\mvnw.cmd clean test
```

### Run

```bash
# Standard profile
./mvnw spring-boot:run

# Dev profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Health check endpoints:
- `GET /actuator/health`
- `GET /actuator/info`

## Key API Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/csrf` | Obtain CSRF token |
| POST | `/api/v1/session/guest` | Create guest session |
| GET | `/api/v1/session/me` | Current session and room info |
| GET | `/api/v1/games` | List available games |
| POST | `/api/v1/rooms` | Create a room |
| POST | `/api/v1/rooms/{id}/join` | Join a room |
| POST | `/api/v1/rooms/{id}/ready` | Toggle ready state |
| POST | `/api/v1/rooms/{id}/start` | Start game (host only) |
| WS | `/ws` | Real-time WebSocket connection |
| GET | `/api/v1/matches` | Finished match history |

## Documentation

- Architecture: `docs/BACKEND-ARCHITECTURE.md`
- Game Engine: `docs/GAME-ENGINE.md`
- Database: `docs/DATABASE.md`
- REST API: `docs/REST-API.md`
- WebSocket Protocol: `docs/WEBSOCKET-PROTOCOL.md`
- Game Rules (VI): `docs/NOB_GAME_RULES_VI.md`

## License

All rights reserved.
