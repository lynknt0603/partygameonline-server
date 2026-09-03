# Security (implemented)

## Authentication

- Stateless AES-256-GCM bearer tokens; no `HttpSession`, `PGOSESSION`, or
  `XSRF-TOKEN` runtime flow.
- The encryption key remains only in backend configuration.
- Tokens contain authenticated player identity and an expiry, and AES-GCM
  rejects any modified ciphertext.
- Member tokens are resolved against the current database user on every
  request, so deleted users and stale profile fields are not trusted.
- Guest, registration, login, session, and profile responses return a refreshed
  `accessToken`.
- REST uses `Authorization: Bearer <token>`.
- WebSocket uses subprotocols `boardverse` and `bearer.<token>` because browser
  WebSocket APIs cannot set an Authorization header.
- Logout deletes the token on the client. Tokens expire after the configured
  TTL; immediate server-side revocation is not implemented.

## Other controls

- Registered passwords are BCrypt hashes; legacy password AES rows are only
  supported by the temporary migration key.
- CORS is an exact allow-list and credentialed CORS is disabled.
- Client-supplied player IDs are ignored.
- Game actions and chat are rate-limited and validated server-side.
- Only Actuator health/info are public.
- Error responses do not expose stack traces, SQL, hidden hands, or deck order.

## Configuration

```text
AUTH_TOKEN_KEY         required; unique random secret of at least 32 characters
AUTH_TOKEN_TTL         token lifetime, 5m to 30d (default 24h)
CORS_ALLOWED_ORIGINS   exact comma-separated SPA origins; never *
APP_ENCRYPTION_KEY     temporary legacy password migration key only
DISCONNECT_GRACE       WebSocket disconnect grace (default 30s)
```

Production must keep `AUTH_TOKEN_KEY` stable across restarts. Rotating it logs
out all players immediately. Never expose it through Vite variables or commit
the production value.
