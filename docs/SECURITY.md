# Security (implemented)

Server-side session authentication. CSRF stays on. No custom JWT in MVP.

## What exists

- Guest session in the HTTP session (`PGOSESSION`, HttpOnly)
- `PlayerPrincipal` is the only authoritative identity
- Client-supplied `playerId` is ignored on REST and WebSocket
- Guests are **not** written to `users`
- CSRF: session token; `GET /api/v1/csrf` returns `headerName` + `token` and sets readable `XSRF-TOKEN` cookie
- CORS allow-list from `app.security.cors.allowed-origins` (never `*`)
- WebSocket `/ws` handshake uses the same HTTP session; Origin is allow-listed
- Registered passwords are BCrypt hashes; legacy AES rows are upgraded after a successful login
- Game actions and room chat have per-player rate limits; each player may hold at most two WebSockets
- JSON `401 UNAUTHENTICATED`, `403 FORBIDDEN` / `CSRF_REJECTED`
- Actuator public surface is only `health` and `info`. `env` / `beans` / `configprops` are disabled
- Errors never return Java exception messages, stack traces, SQL, or hidden hands

## Endpoints

See `contracts/rest/SECURITY.md`.

## Config

```text
CORS_ALLOWED_ORIGINS   comma-separated; empty = same-origin only
APP_ENCRYPTION_KEY     temporary legacy AES migration key; not used by BCrypt rows
COOKIE_SECURE          true in production HTTPS
COOKIE_SAME_SITE       lax (default)
DISCONNECT_GRACE       WebSocket disconnect grace (default 30s)
```

Dev profile allows `http://localhost:5173` and `http://127.0.0.1:5173`.

## Production checklist

1. Terminate TLS in front of the app. Set `COOKIE_SECURE=true`.
2. Serve the SPA and API same-site (reverse proxy `/api` and `/ws`).
3. Set `CORS_ALLOWED_ORIGINS` only if the browser is on another origin. Never `*`.
4. Do not expose Actuator beyond `health`/`info` on the public network.
5. Rotate the PostgreSQL password; do not commit credentials.
6. Prefer PostgreSQL 14+ (Hibernate 7.4 warns on 13.x).
7. Logs must not include cookies, session ids, CSRF tokens, hands, or deck order.
8. Put a distributed IP/account rate limit in the edge proxy (Render/Cloudflare) for login and room creation; the app also throttles each login source to 20 attempts/minute.

## Not implemented

Registered login (`/api/v1/auth/*`), roles beyond the authenticated guest player.
