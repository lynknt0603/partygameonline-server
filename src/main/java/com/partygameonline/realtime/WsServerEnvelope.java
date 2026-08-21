package com.partygameonline.realtime;

public record WsServerEnvelope(
        int version,
        String type,
        String roomId,
        Long serverSequence,
        String requestId,
        Object payload
) {

    public static WsServerEnvelope of(
            String type,
            String roomId,
            Long serverSequence,
            String requestId,
            Object payload
    ) {
        return new WsServerEnvelope(1, type, roomId, serverSequence, requestId, payload);
    }
}
