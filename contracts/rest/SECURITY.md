# Frontend security contract

Authentication is a **server-side HTTP session cookie**, not JWT.

## Cookies

| Cookie | HttpOnly | Purpose |
| --- | --- | --- |
| `PGOSESSION` | yes | Session identity |
| `XSRF-TOKEN` | no | CSRF token for the SPA to read |

Send cookies on every API call: `credentials: "include"`.

## CSRF

CSRF is on. The token is bound to the HTTP session.

Bootstrap:

1. `GET /api/v1/csrf` (public) — returns `{ headerName, parameterName, token }` and also sets a readable `XSRF-TOKEN` cookie
2. Keep the session cookie (`PGOSESSION`)
3. On `POST` / `PUT` / `PATCH` / `DELETE`, send `headerName: token` from that response (typically `X-XSRF-TOKEN`)

Do not hard-code the header name. Use the value from `GET /api/v1/csrf`.

Do not send `playerId`. The server generates it.

## Session API

| Method | Path | Auth |
| --- | --- | --- |
| GET | `/api/v1/csrf` | public |
| POST | `/api/v1/session/guest` | public + CSRF |
| GET | `/api/v1/session/me` | session |
| DELETE | `/api/v1/session` | session + CSRF |

## CORS (dev)

Dev may allow the Vite origin (`http://localhost:5173`) with credentials.

Production should be same-site (reverse proxy). Do not use `allowedOrigins("*")` with cookies.

Recommended local setup: Vite proxy `/api` and `/ws` to the backend so cookies are first-party (`SameSite=Lax` works). Cross-origin cookies on HTTP localhost are unreliable.

## HTTPS

Production cookies should be `Secure`. Set `COOKIE_SECURE=true`.
