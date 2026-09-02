package com.partygameonline.realtime;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Small per-player fixed-window limiter for the in-memory realtime service.
 * A distributed deployment should enforce the same rule at the edge too.
 */
@Component
public final class GameActionRateLimiter {

    static final int DEFAULT_MAX_REQUESTS = 20;
    static final Duration DEFAULT_WINDOW = Duration.ofSeconds(10);
    private static final int MAX_TRACKED_PLAYERS = 10_000;

    private final int maxRequests;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public GameActionRateLimiter() {
        this(DEFAULT_MAX_REQUESTS, DEFAULT_WINDOW);
    }

    GameActionRateLimiter(int maxRequests, Duration window) {
        if (maxRequests < 1 || window == null || window.isZero() || window.isNegative()
                || window.toMillis() < 1) {
            throw new IllegalArgumentException("A rate limiter needs a positive limit and window");
        }
        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();
    }

    public boolean tryAcquire(String playerId, String operation) {
        if (playerId == null || playerId.isBlank()) {
            return false;
        }
        String normalizedOperation = operation == null ? "UNKNOWN" : operation.trim().toUpperCase(java.util.Locale.ROOT);
        // Due-time validation remains authoritative; scheduler timeouts are
        // not useful as a flood primitive once the room lock is held.
        if ("TIMEOUT".equals(normalizedOperation)) {
            return true;
        }
        // Keep one bucket per player rather than one bucket per client-supplied
        // operation string; otherwise an attacker could bypass the limit by
        // sending a new operation name for every message.
        String key = playerId;
        long now = System.currentTimeMillis();
        evictExpired(now);
        Window window = windows.computeIfAbsent(key, ignored -> new Window(now));
        synchronized (window) {
            if (now - window.startedAt >= windowMillis) {
                window.startedAt = now;
                window.count = 0;
            }
            if (window.count >= maxRequests) {
                return false;
            }
            window.count++;
            return true;
        }
    }

    private void evictExpired(long now) {
        if (windows.size() <= MAX_TRACKED_PLAYERS) {
            return;
        }
        windows.entrySet().removeIf(entry -> {
            Window window = entry.getValue();
            synchronized (window) {
                return now - window.startedAt >= windowMillis;
            }
        });
    }

    private static final class Window {
        private long startedAt;
        private int count;

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
