package com.partygameonline.realtime;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class RoomChatService {

    static final int MAX_TEXT = 240;
    private static final int CAP = 80;
    private final ConcurrentHashMap<String, Deque<RoomChatMessage>> byRoom = new ConcurrentHashMap<>();

    public RoomChatMessage append(String roomId, String playerId, String displayName, String rawText) {
        String text = sanitize(rawText);
        if (text.isEmpty()) {
            return null;
        }
        RoomChatMessage message = new RoomChatMessage(
                UUID.randomUUID().toString(),
                playerId,
                displayName,
                text,
                Instant.now(),
                RoomChatMessage.USER
        );
        Deque<RoomChatMessage> buffer = byRoom.computeIfAbsent(roomId, key -> new ArrayDeque<>());
        synchronized (buffer) {
            buffer.addLast(message);
            while (buffer.size() > CAP) {
                buffer.removeFirst();
            }
        }
        return message;
    }

    public List<RoomChatMessage> recent(String roomId) {
        Deque<RoomChatMessage> buffer = byRoom.get(roomId);
        if (buffer == null) {
            return List.of();
        }
        synchronized (buffer) {
            return new ArrayList<>(buffer);
        }
    }

    public void clear(String roomId) {
        byRoom.remove(roomId);
    }

    static String sanitize(String rawText) {
        if (rawText == null) {
            return "";
        }
        String trimmed = rawText.replace('\u0000', ' ').trim();
        if (trimmed.length() > MAX_TEXT) {
            return trimmed.substring(0, MAX_TEXT).trim();
        }
        return trimmed;
    }
}
