package com.partygameonline.game.games.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.game.core.GameConfig;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.SeededRandomSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DemoCardGameProjectorTests {

    private final DemoCardGameEngine engine = new DemoCardGameEngine();
    private final DemoCardGameProjector projector = new DemoCardGameProjector();

    @Test
    void playerCannotSeeOpponentHandOrDeckOrder() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("host", "Linh");
        names.put("p2", "Minh");
        DemoGameState state = engine.createGame(
                new GameConfig(DemoCardGameManifest.ID, "ABCD", List.of("host", "p2"), names, 9L),
                new SeededRandomSource(9)
        );

        DemoView hostView = projector.project(state, PlayerContext.player("host", "Linh"));
        DemoView guestView = projector.project(state, PlayerContext.player("p2", "Minh"));

        assertThat(hostView.hand()).containsExactlyElementsOf(state.handOf("host"));
        assertThat(hostView.hand()).doesNotContainAnyElementsOf(state.handOf("p2"));
        assertThat(hostView.opponentHandSize()).isEqualTo(5);
        assertThat(guestView.hand()).containsExactlyElementsOf(state.handOf("p2"));
        assertThat(guestView.hand()).doesNotContainAnyElementsOf(state.handOf("host"));
        assertThat(hostView.deckSize()).isEqualTo(42);
        assertThat(hostView.discard()).isEmpty();
        assertThat(hostView.yourTurn()).isTrue();
        assertThat(guestView.yourTurn()).isFalse();
    }
}
