/*
 * Legacy GET /api/v1/csrf controller intentionally disabled.
 *
 * It previously returned a session-bound token and wrote XSRF-TOKEN. The SPA
 * now receives an AES-GCM access token from guest/register/login responses.
 * The exact executable version remains in Git commit 2568fd5.
 */
