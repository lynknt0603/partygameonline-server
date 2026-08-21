package com.partygameonline.game.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class GameRegistryTests {

    @Test
    void resolvesManifestByIdWithoutSwitch() {
        GameManifest demo = new StubManifest("demo-card-game", "Demo");
        GameRegistry registry = new GameRegistry(List.of(demo, new StubManifest("other", "Other")));

        assertThat(registry.findById("demo-card-game")).contains(demo);
        assertThat(registry.findById("missing")).isEmpty();
        assertThat(registry.all()).extracting(GameManifest::id).containsExactly("demo-card-game", "other");
    }

    @Test
    void rejectsDuplicateGameIds() {
        assertThatThrownBy(() -> new GameRegistry(List.of(
                new StubManifest("demo-card-game", "A"),
                new StubManifest("demo-card-game", "B")
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("demo-card-game");
    }

    @Test
    void resolvesEngineByGameTypeWithoutSwitch() {
        StubEngine engine = new StubEngine("demo-card-game");
        StubProjector projector = new StubProjector("demo-card-game");
        GameRegistry registry = new GameRegistry(
                List.of(new StubManifest("demo-card-game", "Demo")),
                List.of(engine),
                List.of(projector)
        );

        assertThat(registry.hasEngine("demo-card-game")).isTrue();
        assertThat(registry.findEngine("demo-card-game")).contains(engine);
        assertThat(registry.findProjector("demo-card-game")).contains(projector);
        assertThat(registry.hasEngine("other")).isFalse();
    }

    @Test
    void rejectsEngineWithoutMatchingProjector() {
        assertThatThrownBy(() -> new GameRegistry(
                List.of(new StubManifest("demo-card-game", "Demo")),
                List.of(new StubEngine("demo-card-game")),
                List.of()
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("projector");
    }

    private record StubManifest(String id, String name) implements GameManifest {

        @Override
        public int minPlayers() {
            return 1;
        }

        @Override
        public int maxPlayers() {
            return 2;
        }

        @Override
        public boolean enabled() {
            return true;
        }
    }

    private static final class StubEngine implements GameEngine<Object, Object, Object> {

        private final String gameType;

        private StubEngine(String gameType) {
            this.gameType = gameType;
        }

        @Override
        public String gameType() {
            return gameType;
        }

        @Override
        public Object createGame(GameConfig config, RandomSource random) {
            return "state";
        }

        @Override
        public Object decodeAction(java.util.Map<String, Object> payload) {
            return payload;
        }

        @Override
        public ValidationResult validate(Object state, PlayerContext actor, Object action) {
            return ValidationResult.ok();
        }

        @Override
        public GameResult<Object, Object> apply(
                Object state,
                PlayerContext actor,
                Object action,
                RandomSource random
        ) {
            return GameResult.of(state, List.of());
        }
    }

    private record StubProjector(String gameType) implements GameStateProjector<Object, Object> {

        @Override
        public Object project(Object authoritativeState, PlayerContext viewer) {
            return authoritativeState;
        }
    }
}
