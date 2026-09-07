package com.partygameonline.realtime;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Component;

@Component
public class RequestIdDeduper {

    private static final int MAX_PER_PLAYER = 64;
    private static final int MAX_TRACKED_PLAYERS = 2_000;
    private static final Duration PLAYER_TTL = Duration.ofMinutes(15);

    private final int maxPerPlayer;
    private final int maxTrackedPlayers;
    private final long playerTtlMillis;
    private final LongSupplier currentTimeMillis;
    private final LinkedHashMap<String, PlayerRequests> seen = new LinkedHashMap<>(16, 0.75f, true);

    public RequestIdDeduper() {
        this(MAX_PER_PLAYER, MAX_TRACKED_PLAYERS, PLAYER_TTL, System::currentTimeMillis);
    }

    RequestIdDeduper(
            int maxPerPlayer,
            int maxTrackedPlayers,
            Duration playerTtl,
            LongSupplier currentTimeMillis
    ) {
        if (maxPerPlayer < 1 || maxTrackedPlayers < 1 || playerTtl == null
                || playerTtl.isZero() || playerTtl.isNegative() || currentTimeMillis == null) {
            throw new IllegalArgumentException("Deduplication limits and TTL must be positive");
        }
        this.maxPerPlayer = maxPerPlayer;
        this.maxTrackedPlayers = maxTrackedPlayers;
        this.playerTtlMillis = playerTtl.toMillis();
        this.currentTimeMillis = currentTimeMillis;
    }

    public synchronized boolean isDuplicate(String playerId, String requestId) {
        if (playerId == null || requestId == null || requestId.isBlank()) {
            return false;
        }
        long now = currentTimeMillis.getAsLong();
        evictExpiredPlayers(now);
        PlayerRequests playerRequests = seen.get(playerId);
        if (playerRequests == null) {
            evictLeastRecentlyUsedPlayerAtCapacity();
            playerRequests = new PlayerRequests(now);
            seen.put(playerId, playerRequests);
        } else {
            playerRequests.lastSeenAt = now;
        }
        if (playerRequests.requestIds.contains(requestId)) {
            return true;
        }
        playerRequests.requestIds.addLast(requestId);
        while (playerRequests.requestIds.size() > maxPerPlayer) {
            playerRequests.requestIds.removeFirst();
        }
        return false;
    }

    synchronized Map<String, Deque<String>> snapshot() {
        Map<String, Deque<String>> copy = new LinkedHashMap<>();
        seen.forEach((playerId, requests) -> copy.put(playerId, new ArrayDeque<>(requests.requestIds)));
        return copy;
    }

    synchronized int trackedPlayerCount() {
        evictExpiredPlayers(currentTimeMillis.getAsLong());
        return seen.size();
    }

    private void evictExpiredPlayers(long now) {
        Iterator<Map.Entry<String, PlayerRequests>> iterator = seen.entrySet().iterator();
        while (iterator.hasNext()) {
            PlayerRequests requests = iterator.next().getValue();
            if (now - requests.lastSeenAt < playerTtlMillis) {
                break;
            }
            iterator.remove();
        }
    }

    private void evictLeastRecentlyUsedPlayerAtCapacity() {
        if (seen.size() < maxTrackedPlayers) {
            return;
        }
        Iterator<String> iterator = seen.keySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static final class PlayerRequests {
        private final Deque<String> requestIds = new ArrayDeque<>();
        private long lastSeenAt;

        private PlayerRequests(long lastSeenAt) {
            this.lastSeenAt = lastSeenAt;
        }
    }
}
