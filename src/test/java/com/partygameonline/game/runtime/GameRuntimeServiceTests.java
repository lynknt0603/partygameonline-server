package com.partygameonline.game.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.game.core.GameConfig;
import com.partygameonline.game.core.GameEngine;
import com.partygameonline.game.core.GameRegistry;
import com.partygameonline.game.core.GameResult;
import com.partygameonline.game.core.GameStateProjector;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.RandomSource;
import com.partygameonline.game.core.ValidationResult;
import com.partygameonline.room.domain.GameRoom;
import com.partygameonline.room.domain.RoomId;
import com.partygameonline.room.domain.RoomName;
import com.partygameonline.room.domain.RoomStatus;
import com.partygameonline.room.domain.RoomVisibility;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameRuntimeServiceTests {

    private GameRuntimeService runtime;
    private InMemoryGameSessionRepository sessions;

    @BeforeEach
    void setUp() {
        sessions = new InMemoryGameSessionRepository();
        GameRegistry registry = new GameRegistry(
                List.of(),
                List.of(new StubEngine()),
                List.of(new StubProjector())
        );
        runtime = new GameRuntimeService(registry, sessions);
    }

    @Test
    void startWithoutEngineLeavesRoomStarting() {
        GameRegistry empty = new GameRegistry(List.of());
        GameRuntimeService noEngine = new GameRuntimeService(empty, sessions);
        GameRoom room = twoPlayerRoom();
        room.setReady("host", true);
        room.setReady("p2", true);
        room.start("host", 2);

        assertThat(noEngine.startGame(room)).isEmpty();
        assertThat(room.getStatus()).isEqualTo(RoomStatus.STARTING);
        assertThat(sessions.findByRoomId("ABCD")).isEmpty();
    }

    @Test
    void startWithEngineCreatesProjectedSession() {
        GameRoom room = startedRoom();

        GameSession session = runtime.startGame(room).orElseThrow();

        assertThat(room.getStatus()).isEqualTo(RoomStatus.IN_GAME);
        assertThat(session.getGameId()).isEqualTo("stub-game");
        Map<String, Object> hostView = cast(runtime.projectView(session, room.findPlayer("host").orElseThrow()));
        Map<String, Object> guestView = cast(runtime.projectView(session, room.findPlayer("p2").orElseThrow()));
        assertThat(hostView.get("hiddenCard")).isEqualTo("host-secret");
        assertThat(hostView.get("opponentHiddenCard")).isNull();
        assertThat(guestView.get("hiddenCard")).isEqualTo("p2-secret");
        assertThat(guestView.get("opponentHiddenCard")).isNull();
    }

    @Test
    void applyRejectsOutOfTurnThenAcceptsAndFinishes() {
        GameRoom room = startedRoom();
        GameSession session = runtime.startGame(room).orElseThrow();

        AppliedAction rejected = runtime.applyAction(
                session,
                PlayerContext.player("p2", "Guest"),
                Map.of("type", "PLAY")
        );
        assertThat(rejected.accepted()).isFalse();
        assertThat(rejected.rejection().errorCode()).isEqualTo("NOT_YOUR_TURN");

        AppliedAction first = runtime.applyAction(
                session,
                PlayerContext.player("host", "Linh"),
                Map.of("type", "PLAY")
        );
        assertThat(first.accepted()).isTrue();
        assertThat(first.result().finished()).isFalse();

        AppliedAction second = runtime.applyAction(
                session,
                PlayerContext.player("p2", "Guest"),
                Map.of("type", "PLAY")
        );
        assertThat(second.accepted()).isTrue();

        AppliedAction winning = runtime.applyAction(
                session,
                PlayerContext.player("host", "Linh"),
                Map.of("type", "PLAY")
        );
        assertThat(winning.accepted()).isTrue();
        assertThat(winning.result().finished()).isTrue();
        assertThat(winning.result().winnerPlayerId()).isEqualTo("host");
        assertThat(session.isFinished()).isTrue();
    }

    private static GameRoom startedRoom() {
        GameRoom room = twoPlayerRoom();
        room.setReady("host", true);
        room.setReady("p2", true);
        room.start("host", 2);
        return room;
    }

    private static GameRoom twoPlayerRoom() {
        GameRoom room = new GameRoom(
                RoomId.parse("ABCD"),
                new RoomName("Lobby"),
                "stub-game",
                "host",
                "Linh",
                2,
                RoomVisibility.PUBLIC,
                Instant.parse("2026-08-19T00:00:00Z")
        );
        room.join("p2", "Guest");
        return room;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object view) {
        return (Map<String, Object>) view;
    }

    private static final class StubEngine implements GameEngine<StubState, String, String> {

        @Override
        public String gameType() {
            return "stub-game";
        }

        @Override
        public StubState createGame(GameConfig config, RandomSource random) {
            Map<String, String> hidden = new LinkedHashMap<>();
            Map<String, Integer> scores = new LinkedHashMap<>();
            for (String playerId : config.playerIds()) {
                hidden.put(playerId, playerId + "-secret");
                scores.put(playerId, 0);
            }
            return new StubState(config.playerIds(), scores, hidden, config.playerIds().getFirst(), false, null);
        }

        @Override
        public String decodeAction(Map<String, Object> payload) {
            Object type = payload.get("type");
            if (!(type instanceof String value) || value.isBlank()) {
                throw new com.partygameonline.game.core.GameActionFormatException("type is required");
            }
            return value;
        }

        @Override
        public ValidationResult validate(StubState state, PlayerContext actor, String action) {
            if (state.finished()) {
                return ValidationResult.reject("GAME_ALREADY_FINISHED", "The game is already finished");
            }
            if (!"PLAY".equals(action)) {
                return ValidationResult.reject("UNKNOWN_ACTION", "Unknown action");
            }
            if (!actor.playerId().equals(state.currentPlayerId())) {
                return ValidationResult.reject("NOT_YOUR_TURN", "It is not your turn");
            }
            return ValidationResult.ok();
        }

        @Override
        public GameResult<StubState, String> apply(
                StubState state,
                PlayerContext actor,
                String action,
                RandomSource random
        ) {
            Map<String, Integer> scores = new HashMap<>(state.scores());
            int next = scores.get(actor.playerId()) + 1;
            scores.put(actor.playerId(), next);
            boolean finished = next >= 2;
            String current = finished
                    ? state.currentPlayerId()
                    : state.playerIds().get((state.playerIds().indexOf(actor.playerId()) + 1) % state.playerIds().size());
            StubState nextState = new StubState(
                    state.playerIds(),
                    scores,
                    state.hiddenCards(),
                    current,
                    finished,
                    finished ? actor.playerId() : null
            );
            if (finished) {
                return GameResult.finished(nextState, List.of("SCORED", "FINISHED"), actor.playerId());
            }
            return GameResult.of(nextState, List.of("SCORED"));
        }
    }

    private static final class StubProjector implements GameStateProjector<StubState, Map<String, Object>> {

        @Override
        public String gameType() {
            return "stub-game";
        }

        @Override
        public Map<String, Object> project(StubState state, PlayerContext viewer) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("currentPlayerId", state.currentPlayerId());
            view.put("scores", state.scores());
            view.put("hiddenCard", state.hiddenCards().get(viewer.playerId()));
            view.put("opponentHiddenCard", null);
            view.put("finished", state.finished());
            view.put("winnerPlayerId", state.winnerPlayerId());
            return view;
        }
    }

    private record StubState(
            List<String> playerIds,
            Map<String, Integer> scores,
            Map<String, String> hiddenCards,
            String currentPlayerId,
            boolean finished,
            String winnerPlayerId
    ) {
    }
}
