package com.partygameonline.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.game.nob.domain.NobEvent;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebSocketRoomRealtimePublisherSecurityTests {

    @Test
    void sharedGameEventsDoNotExposeSecretValuesOrActors() {
        Map<String, Object> payload = WebSocketRoomRealtimePublisher.gameEventsPayload(List.of(
                NobEvent.of("NOB_MOON_MARK_COUNT_CHANGED", Map.of("playerId", "p1", "value", 4)),
                NobEvent.of("NOB_PRIVATE_MOON_SEEN", Map.of("viewerId", "p1")),
                NobEvent.of("NOB_DECISION_REQUIRED", Map.of("actorId", "p1"))
        ));

        assertThat(payload).containsEntry("actorPlayerId", null);
        List<?> events = (List<?>) payload.get("events");
        assertThat(events).hasSize(3);
        assertThat(((NobEvent) events.get(0)).payload()).containsEntry("playerId", "p1");
        assertThat(((NobEvent) events.get(0)).payload()).doesNotContainKey("value");
        assertThat(((NobEvent) events.get(1)).payload()).doesNotContainKey("viewerId");
        assertThat(((NobEvent) events.get(2)).payload()).doesNotContainKey("actorId");
    }

    @Test
    void sharedWhereIsTheBoneEventsDoNotRevealActionTypeTimingOrCount() {
        Map<String, Object> payload = WebSocketRoomRealtimePublisher.gameEventsPayload(List.of(
                WheresTheBoneEvent.of("BONE_TAKEN", Map.of("hour", 2)),
                WheresTheBoneEvent.of("PACKMATES_SELECTED", Map.of("count", 1))
        ));

        assertThat(payload).containsEntry("actorPlayerId", null);
        List<?> events = (List<?>) payload.get("events");
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isEqualTo(new WheresTheBoneEvent("STATE_CHANGED", Map.of()));
    }
}
