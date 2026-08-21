package com.partygameonline.realtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RequestIdDeduper {

    private static final int MAX_PER_PLAYER = 64;

    private final ConcurrentHashMap<String, Deque<String>> seen = new ConcurrentHashMap<>();

    public boolean isDuplicate(String playerId, String requestId) {
        if (playerId == null || requestId == null || requestId.isBlank()) {
            return false;
        }
        Deque<String> ids = seen.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        synchronized (ids) {
            if (ids.contains(requestId)) {
                return true;
            }
            ids.addLast(requestId);
            while (ids.size() > MAX_PER_PLAYER) {
                ids.removeFirst();
            }
            return false;
        }
    }

    Map<String, Deque<String>> snapshot() {
        return seen;
    }
}
