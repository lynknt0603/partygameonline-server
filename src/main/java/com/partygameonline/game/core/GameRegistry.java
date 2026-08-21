package com.partygameonline.game.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GameRegistry {

    private final Map<String, GameManifest> manifests;
    private final Map<String, GameEngine<?, ?, ?>> engines;
    private final Map<String, GameStateProjector<?, ?>> projectors;

    public GameRegistry(List<GameManifest> manifests) {
        this(manifests, List.of(), List.of());
    }

    @Autowired
    public GameRegistry(
            List<GameManifest> manifests,
            List<GameEngine<?, ?, ?>> engines,
            List<GameStateProjector<?, ?>> projectors
    ) {
        this.manifests = indexManifests(manifests);
        this.engines = indexEngines(engines);
        this.projectors = indexProjectors(projectors);
        if (!this.engines.keySet().equals(this.projectors.keySet())) {
            throw new IllegalStateException(
                    "Each GameEngine must have exactly one GameStateProjector; engines="
                            + this.engines.keySet()
                            + " projectors="
                            + this.projectors.keySet()
            );
        }
    }

    public List<GameManifest> all() {
        return List.copyOf(manifests.values());
    }

    public Optional<GameManifest> findById(String gameId) {
        return Optional.ofNullable(manifests.get(gameId));
    }

    public Optional<GameEngine<?, ?, ?>> findEngine(String gameId) {
        return Optional.ofNullable(engines.get(gameId));
    }

    public Optional<GameStateProjector<?, ?>> findProjector(String gameId) {
        return Optional.ofNullable(projectors.get(gameId));
    }

    public boolean hasEngine(String gameId) {
        return engines.containsKey(gameId);
    }

    @SuppressWarnings("unchecked")
    public Object createGame(String gameId, GameConfig config, RandomSource random) {
        GameEngine<Object, Object, Object> engine = requireEngine(gameId);
        return engine.createGame(config, random);
    }

    @SuppressWarnings("unchecked")
    public Object decodeAction(String gameId, Map<String, Object> payload) {
        GameEngine<Object, Object, Object> engine = requireEngine(gameId);
        return engine.decodeAction(payload == null ? Map.of() : payload);
    }

    @SuppressWarnings("unchecked")
    public ValidationResult validate(String gameId, Object state, PlayerContext actor, Object action) {
        GameEngine<Object, Object, Object> engine = requireEngine(gameId);
        return engine.validate(state, actor, action);
    }

    @SuppressWarnings("unchecked")
    public GameResult<Object, Object> apply(
            String gameId,
            Object state,
            PlayerContext actor,
            Object action,
            RandomSource random
    ) {
        GameEngine<Object, Object, Object> engine = requireEngine(gameId);
        return engine.apply(state, actor, action, random);
    }

    @SuppressWarnings("unchecked")
    public GameResult<Object, Object> onPlayerAbandoned(
            String gameId,
            Object state,
            PlayerContext player,
            RandomSource random
    ) {
        GameEngine<Object, Object, Object> engine = requireEngine(gameId);
        return engine.onPlayerAbandoned(state, player, random);
    }

    @SuppressWarnings("unchecked")
    public Object project(String gameId, Object state, PlayerContext viewer) {
        GameStateProjector<Object, Object> projector =
                (GameStateProjector<Object, Object>) findProjector(gameId)
                        .orElseThrow(() -> new IllegalStateException("No projector for game: " + gameId));
        return projector.project(state, viewer);
    }

    Collection<String> ids() {
        return manifests.keySet();
    }

    @SuppressWarnings("unchecked")
    private GameEngine<Object, Object, Object> requireEngine(String gameId) {
        return (GameEngine<Object, Object, Object>) findEngine(gameId)
                .orElseThrow(() -> new IllegalStateException("No engine for game: " + gameId));
    }

    private static Map<String, GameManifest> indexManifests(List<GameManifest> manifests) {
        Map<String, GameManifest> byId = new LinkedHashMap<>();
        for (GameManifest manifest : manifests) {
            GameManifest previous = byId.put(manifest.id(), manifest);
            if (previous != null) {
                throw new IllegalStateException("Duplicate game id: " + manifest.id());
            }
        }
        return Collections.unmodifiableMap(byId);
    }

    private static Map<String, GameEngine<?, ?, ?>> indexEngines(List<GameEngine<?, ?, ?>> engines) {
        Map<String, GameEngine<?, ?, ?>> byId = new LinkedHashMap<>();
        if (engines == null) {
            return Collections.unmodifiableMap(byId);
        }
        for (GameEngine<?, ?, ?> engine : engines) {
            GameEngine<?, ?, ?> previous = byId.put(engine.gameType(), engine);
            if (previous != null) {
                throw new IllegalStateException("Duplicate game engine: " + engine.gameType());
            }
        }
        return Collections.unmodifiableMap(byId);
    }

    private static Map<String, GameStateProjector<?, ?>> indexProjectors(
            List<GameStateProjector<?, ?>> projectors
    ) {
        Map<String, GameStateProjector<?, ?>> byId = new LinkedHashMap<>();
        if (projectors == null) {
            return Collections.unmodifiableMap(byId);
        }
        for (GameStateProjector<?, ?> projector : projectors) {
            GameStateProjector<?, ?> previous = byId.put(projector.gameType(), projector);
            if (previous != null) {
                throw new IllegalStateException("Duplicate game projector: " + projector.gameType());
            }
        }
        return Collections.unmodifiableMap(byId);
    }
}
