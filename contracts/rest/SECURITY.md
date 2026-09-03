# Frontend security contract

Authentication uses an AES-256-GCM bearer token. The AES secret exists only in
the backend `AUTH_TOKEN_KEY` environment variable; the browser never receives
the secret.

## Token flow

1. Call `POST /api/v1/session/guest`, `POST /api/v1/auth/register`, or
   `POST /api/v1/auth/login`.
2. Store the response `accessToken` locally.
3. Send `Authorization: Bearer <accessToken>` on later API calls.
4. Replace the stored token whenever a session/profile response returns a new
   `accessToken`.

Do not send a client-selected `playerId`. The authenticated token is the only
identity source.

CSRF cookies and `credentials: "include"` are not used. Since browsers do not
attach an Authorization bearer token automatically, the old ambient-cookie
CSRF attack does not apply.

## Session API

| Method | Path | Auth |
| --- | --- | --- |
| POST | `/api/v1/session/guest` | public; returns token |
| GET | `/api/v1/session/me` | bearer token |
| DELETE | `/api/v1/session` | bearer token; client then deletes token |

## CORS

Only exact origins from `CORS_ALLOWED_ORIGINS` are accepted. The
`Authorization` header is allowed. Credentialed CORS is disabled because no
cross-site cookies are required.

## Token storage

The SPA stores the encrypted token in local storage so mobile browsers can use
the application even when third-party cookies are blocked. Keep the frontend
free of unsafe HTML injection and maintain a restrictive Content Security
Policy because any script running in the page can read bearer tokens.
