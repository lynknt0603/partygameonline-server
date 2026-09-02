package com.partygameonline.auth.application;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-process login throttle. A reverse proxy should add a distributed/IP rule
 * as well, but this protects a single Render instance from credential floods.
 */
@Component
public final class AuthRateLimiter {

    static final int DEFAULT_MAX_ATTEMPTS = 20;
    static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);
    private static final int MAX_TRACKED_ADDRESSES = 10_000;

    private final int maxAttempts;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public AuthRateLimiter() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_WINDOW);
    }

    AuthRateLimiter(int maxAttempts, Duration window) {
        if (maxAttempts < 1 || window == null || window.isZero() || window.isNegative()
                || window.toMillis() < 1) {
            throw new IllegalArgumentException("A rate limiter needs a positive limit and window");
        }
        this.maxAttempts = maxAttempts;
        this.windowMillis = window.toMillis();
    }

    public boolean tryAcquire(String remoteAddress) {
        String key = remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
        long now = System.currentTimeMillis();
        evictExpired(now);
        Window window = windows.computeIfAbsent(key, ignored -> new Window(now));
        synchronized (window) {
            if (now - window.startedAt >= windowMillis) {
                window.startedAt = now;
                window.count = 0;
            }
            if (window.count >= maxAttempts) {
                return false;
            }
            window.count++;
            return true;
        }
    }

    private void evictExpired(long now) {
        if (windows.size() <= MAX_TRACKED_ADDRESSES) {
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
