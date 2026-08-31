# partygameonline-server

Backend service for Party Game Online, a real-time multiplayer board and party game platform. Built as a server-authoritative modular monolith in Spring Boot and Java 21, the system securely manages game rules, hidden information (private roles, secret cards), room lifecycles, and real-time state projection over WebSockets.

## Tech Stack

- Language & Framework: Java 21, Spring Boot 4.1.0 (Spring WebMVC, Spring WebSocket, Spring Security, Spring Data JPA)
- Database & Migration: PostgreSQL 13+, Flyway
- Testing: JUnit 5, Mockito, Spring Boot Test
- Build Tool: Maven (Maven Wrapper)

## Project Structure

- src/main/java/com/partygameonline/catalog: Game catalog discovery and configuration
- src/main/java/com/partygameonline/room: Room management, player seats, and concurrency locking
- src/main/java/com/partygameonline/game/core: Game engine interfaces, contracts, and registry
- src/main/java/com/partygameonline/game/runtime: Active game session dispatcher and lifecycle management
- src/main/java/com/partygameonline/game/nob: Engine and state projector for Night of Bloodlines
- src/main/java/com/partygameonline/game/wheresthebone: Engine and state projector for Where's The Bone
- src/main/java/com/partygameonline/realtime: WebSocket handlers, message envelopes, and live chat
- src/main/java/com/partygameonline/history: Match history recording and persistence
- src/main/java/com/partygameonline/ranking: ELO rating policies and calculation
- src/main/java/com/partygameonline/security: Security filter chain, CORS, and CSRF protection
- src/main/java/com/partygameonline/session: Guest session management and cookie resolution

## Prerequisites

- JDK 21 or higher
- PostgreSQL 13 or higher

## Database Setup

Create the required PostgreSQL databases:

```sql
CREATE DATABASE partygameonline;
CREATE DATABASE partygameonline_test;
```

Configure database credentials in `src/main/resources/application.properties` or through environment variables.

## Build and Test Guide

### 1. Run All Tests

Run unit and integration tests using the included Maven Wrapper:

- On Linux / macOS:
```bash
./mvnw clean test
```

- On Windows:
```cmd
mvnw.cmd clean test
```

### 2. Run Specific Test Cases

Run a single test class:
```bash
./mvnw test -Dtest=PartyGameOnlineApplicationTests
```

Run a specific test method:
```bash
./mvnw test -Dtest=PartyGameOnlineApplicationTests#contextLoads
```

### 3. Build Package

- Build and package with tests:
```bash
./mvnw clean package
```

- Build and package skipping tests (fast):
```bash
./mvnw clean package -DskipTests
```

The executable `.jar` file will be generated in the `target/` directory.

## Running the Application

### 1. Run via Maven

- Default profile:
```bash
./mvnw spring-boot:run
```

- Development profile:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 2. Run via Helper Scripts

- On macOS / Linux:
```bash
./run.sh
```

- On Windows (PowerShell):
```powershell
.\run.ps1
```

Health and monitoring endpoints:
- Health check: `GET http://localhost:8080/actuator/health`
- Application info: `GET http://localhost:8080/actuator/info`

## Key REST and WebSocket Endpoints

| Method | Path | Description |
|---|---|---|
| GET | /api/v1/csrf | Retrieve CSRF token |
| POST | /api/v1/session/guest | Create guest player session |
| GET | /api/v1/session/me | Get current session and active room |
| GET | /api/v1/games | List available game titles |
| POST | /api/v1/rooms | Create a game room |
| POST | /api/v1/rooms/{id}/join | Join a game room |
| POST | /api/v1/rooms/{id}/ready | Toggle ready status |
| POST | /api/v1/rooms/{id}/start | Start game (host only) |
| WS | /ws | Real-time WebSocket connection |
| GET | /api/v1/matches | Query finished match history |

## Documentation References

- System Architecture: `docs/BACKEND-ARCHITECTURE.md`
- Game Engine Design: `docs/GAME-ENGINE.md`
- Database Schema: `docs/DATABASE.md`
- REST API Reference: `docs/REST-API.md`
- WebSocket Protocol: `docs/WEBSOCKET-PROTOCOL.md`
- Night of Bloodlines Rules: `docs/NOB_GAME_RULES_VI.md`

## License

All rights reserved.
