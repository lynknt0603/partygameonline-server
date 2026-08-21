package com.partygameonline.game.nob.infrastructure;

import com.partygameonline.game.nob.domain.NobEvent;
import com.partygameonline.game.nob.domain.NobGameState;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class NobGameAuditService {

    private static final Logger log = LoggerFactory.getLogger(NobGameAuditService.class);
    private static final Set<String> PRIVATE_EVENTS = Set.of(
            "NOB_PRIVATE_BLOODLINE_SEEN",
            "NOB_PRIVATE_CARD_SEEN",
            "NOB_PRIVATE_MOON_MARK_SEEN",
            "NOB_PRIVATE_KNOWLEDGE_UPDATED",
            "NOB_DRAFT_HAND",
            "NOB_DRAFT_RECEIVED",
            "NOB_MY_BLOODLINE_ASSIGNED",
            "NOB_MY_MOON_MARK_RECEIVED",
            "NOB_MY_BLOODLINE_KNOWLEDGE_LOST_AFTER_SWAP"
    );

    private final JdbcTemplate jdbcTemplate;

    public NobGameAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(NobGameState state, List<NobEvent> events) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO nob_game_session
                        (room_id, status, version, phase, round_number, finished, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, now(), now())
                    ON CONFLICT (room_id) DO UPDATE SET
                        status = EXCLUDED.status,
                        version = EXCLUDED.version,
                        phase = EXCLUDED.phase,
                        round_number = EXCLUDED.round_number,
                        finished = EXCLUDED.finished,
                        updated_at = now()
                    """,
                    state.getRoomId(),
                    state.isFinished() ? "FINISHED" : "IN_GAME",
                    state.getVersion(),
                    state.getPhase().name(),
                    state.getRoundNumber(),
                    state.isFinished()
            );
            for (NobEvent event : events) {
                if (PRIVATE_EVENTS.contains(event.type())) {
                    continue;
                }
                jdbcTemplate.update(
                        """
                        INSERT INTO nob_game_event
                            (id, room_id, sequence, event_type, visibility, payload_json, created_at)
                        VALUES (gen_random_uuid(), ?, ?, ?, 'PUBLIC', ?, now())
                        """,
                        state.getRoomId(),
                        state.getVersion(),
                        event.type(),
                        publicPayload(event)
                );
            }
        } catch (RuntimeException ex) {
            log.warn("NOB audit persist skipped roomId={} reason={}", state.getRoomId(), ex.getMessage());
        }
    }

    private static String publicPayload(NobEvent event) {
        if (event.payload() == null || event.payload().isEmpty()) {
            return "{}";
        }
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (var entry : event.payload().entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(entry.getKey()).append("\":\"").append(String.valueOf(value).replace("\"", "")).append('"');
        }
        json.append('}');
        return json.toString();
    }
}
