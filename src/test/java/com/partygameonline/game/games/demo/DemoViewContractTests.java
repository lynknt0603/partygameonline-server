package com.partygameonline.game.games.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.game.core.PlayerContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DemoViewContractTests {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void projectedJsonMatchesFrontendContractAndHidesOpponentHand() {
        DemoGameState state = new DemoGameState(
                List.of("p1", "p2"),
                Map.of("p1", List.of("H-06", "C-01"), "p2", List.of("S-13", "D-02")),
                List.of("H-01", "H-02"),
                List.of("C-03"),
                "p1",
                1,
                false,
                false,
                false,
                null
        );
        DemoView view = new DemoCardGameProjector().project(state, PlayerContext.player("p1", "Linh"));
        Map<String, Object> json = jsonMapper.convertValue(view, Map.class);

        assertThat(json.keySet()).containsExactlyInAnyOrder(
                "you",
                "currentPlayerId",
                "turnNumber",
                "yourTurn",
                "hasDrawn",
                "hasPlayed",
                "hand",
                "deckSize",
                "opponentHandSize",
                "discard",
                "finished",
                "winnerPlayerId"
        );
        assertThat(json.get("you")).isEqualTo("p1");
        assertThat(json.get("hand")).isEqualTo(List.of("H-06", "C-01"));
        assertThat(json.get("opponentHandSize")).isEqualTo(2);
        assertThat(jsonMapper.writeValueAsString(view)).doesNotContain("S-13", "D-02", "H-01");
    }
}
