/*
 * Legacy session-CSRF filter intentionally disabled.
 *
 * The previous implementation forced creation of an HttpSession-backed
 * CsrfToken on every request. Bearer tokens are explicitly attached by the
 * SPA and are not ambient browser credentials, so that cookie/CSRF mechanism
 * must not run. The exact executable version remains in Git commit 2568fd5.
 */
