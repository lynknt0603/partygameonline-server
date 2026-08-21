package com.partygameonline.realtime;

import java.util.Map;

public record WsClientEnvelope(
        Integer version,
        String type,
        String requestId,
        String roomId,
        Long lastServerSequence,
        Map<String, Object> payload
) {
}
