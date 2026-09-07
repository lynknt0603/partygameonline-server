# partygameonline-server

Backend service for Party Game Online, a real-time multiplayer board and party game platform. Built as a server-authoritative modular monolith in Spring Boot and Java 21, the system securely manages game rules, hidden information (private roles, secret cards), room lifecycles, and real-time state projection over WebSockets.

## Available Games

| Game | Players | Short description |
|---|---:|---|
| **Liar's Number** (`liars-number`) | 2–6 | A single-loser bluffing game where players pass a face-down number card, make a claim from 1 to 8, and challenge or secretly inspect and pass the card. |
| **Night of Bloodlines** (`night-of-bloodlines`) | 4–11 | A hidden-faction deduction game where Vampires, Werewolves, and Halfbloods use secret role cards, survive the night, and compete for Moon Marks. |
| **Where's the Bone** (`wheres-the-bone`) | 4–8 | A social-deduction game where dogs wake at different hours, the Bone Thief attempts a secret theft, and the group must identify the culprit through discussion and voting. |
| **Not In My Pot!** (`not-in-my-pot`) | 3–8 | A bluffing team game where Vegetarians try to complete a valid pot while Meat Eaters secretly sabotage it with harmful ingredients and deceptive declarations. |

## Liar's Number

Liar's Number (Vietnamese: **Ăn Gian Nói Dối**) is a 2–6 player bluffing game built around hidden information and a single active card. The current sender passes the card face down, declares any number from 1 to 8, and may tell the truth or lie. The receiver can challenge the claim by choosing **Truth** or **Lie**. In games with 3–6 players, the receiver may instead inspect the card privately and pass it to a player who has not seen it, making a new claim of their own.

- The deck contains 64 cards: eight number types, each with seven Normal cards and one Roman card.
- Normal and Roman cards of the same number belong to the same type. A Normal penalty is worth 1 point; a Roman penalty is worth 2 points.
- Penalties are counted separately for each number type. Reaching 4 points of one type causes a loss in a 3–6 player game.
- In a 2-player game, 10 random cards are removed unseen, each player receives 27 cards, inspect-and-pass is disabled, and the loss threshold is 5 points of one type.
- The player who receives the penalty starts the next round. If that player has no card available to start it, they lose.
- A match ends as soon as one loser is determined; every other player wins.
- Each winner receives **+10 ELO**. The sole loser is shown a change of **-(starting player count × 10) ELO**, while the persisted rating never drops below zero. The leaderboard records current ELO and total wins.

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
- src/main/java/com/partygameonline/game/notinmypot: Engine and state projector for Not In My Pot!
- src/main/java/com/partygameonline/game/liarsnumber: Engine, rules, state projector, and API for Liar's Number
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
- Not In My Pot! API Contract: `docs/NOT-IN-MY-POT.md`

## License

All rights reserved.
