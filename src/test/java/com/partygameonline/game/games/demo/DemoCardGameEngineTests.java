package com.partygameonline.game.games.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.partygameonline.game.core.GameActionFormatException;
import com.partygameonline.game.core.GameConfig;
import com.partygameonline.game.core.GameResult;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.SeededRandomSource;
import com.partygameonline.game.core.ValidationResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DemoCardGameEngineTests {

    private final DemoCardGameEngine engine = new DemoCardGameEngine();
    private final PlayerContext host = PlayerContext.player("host", "Linh");
    private final PlayerContext guest = PlayerContext.player("p2", "Minh");

    @Test
    void dealsFiveCardsEachAndSameSeedIsDeterministic() {
        DemoGameState first = engine.createGame(config(), new SeededRandomSource(7));
        DemoGameState second = engine.createGame(config(), new SeededRandomSource(7));

        assertThat(first.handOf("host")).hasSize(5);
        assertThat(first.handOf("p2")).hasSize(5);
        assertThat(first.deck()).hasSize(42);
        assertThat(first.currentPlayerId()).isEqualTo("host");
        assertThat(first.handOf("host")).isEqualTo(second.handOf("host"));
        assertThat(first.deck()).isEqualTo(second.deck());
        assertThat(first.handOf("host")).doesNotContainAnyElementsOf(first.handOf("p2"));
    }

    @Test
    void rejectsOutOfTurnOpponentCardMissingCardAndSecondDraw() {
        DemoGameState state = engine.createGame(config(), new SeededRandomSource(11));
        String hostCard = state.handOf("host").getFirst();
        String guestCard = state.handOf("p2").getFirst();

        assertThat(engine.validate(state, guest, new DemoAction.PlayCard(guestCard)).errorCode())
                .isEqualTo("NOT_YOUR_TURN");
        assertThat(engine.validate(state, host, new DemoAction.PlayCard(guestCard)).errorCode())
                .isEqualTo("CARD_NOT_IN_HAND");
        assertThat(engine.validate(state, host, new DemoAction.PlayCard("XX-99")).errorCode())
                .isEqualTo("CARD_NOT_IN_HAND");

        DemoGameState afterDraw = engine.apply(state, host, new DemoAction.DrawCard(), new SeededRandomSource(11)).state();
        assertThat(engine.validate(afterDraw, host, new DemoAction.DrawCard()).errorCode()).isEqualTo("ALREADY_DRAWN");
        assertThat(engine.validate(afterDraw, host, new DemoAction.PlayCard(hostCard)).valid()).isTrue();
    }

    @Test
    void secondPlaySameTurnIsRejectedThenEndTurnResets() {
        DemoGameState state = engine.createGame(config(), new SeededRandomSource(3));
        String first = state.handOf("host").getFirst();
        String second = state.handOf("host").get(1);

        state = engine.apply(state, host, new DemoAction.PlayCard(first), new SeededRandomSource(3)).state();
        assertThat(engine.validate(state, host, new DemoAction.PlayCard(second)).errorCode()).isEqualTo("ALREADY_PLAYED");

        state = engine.apply(state, host, new DemoAction.EndTurn(), new SeededRandomSource(3)).state();
        assertThat(state.hasPlayed()).isFalse();
        assertThat(state.currentPlayerId()).isEqualTo("p2");
        assertThat(engine.validate(state, guest, new DemoAction.PlayCard(state.handOf("p2").getFirst())).valid()).isTrue();
    }

    @Test
    void drawPlayAndEndTurnAdvanceCurrentPlayer() {
        DemoGameState state = engine.createGame(config(), new SeededRandomSource(21));
        state = engine.apply(state, host, new DemoAction.DrawCard(), new SeededRandomSource(21)).state();
        state = engine.apply(state, host, new DemoAction.PlayCard(state.handOf("host").getFirst()), new SeededRandomSource(21)).state();
        state = engine.apply(state, host, new DemoAction.EndTurn(), new SeededRandomSource(21)).state();

        assertThat(state.currentPlayerId()).isEqualTo("p2");
        assertThat(state.turnNumber()).isEqualTo(2);
        assertThat(state.hasDrawn()).isFalse();
        assertThat(state.hasPlayed()).isFalse();
        assertThat(state.handOf("host")).hasSize(5);
    }

    @Test
    void unknownPayloadIsRejected() {
        assertThatThrownBy(() -> engine.decodeAction(Map.of("type", "CHEAT")))
                .isInstanceOf(GameActionFormatException.class);
        assertThatThrownBy(() -> engine.decodeAction(Map.of("type", "PLAY_CARD")))
                .isInstanceOf(GameActionFormatException.class);
    }

    @Test
    void abandonedPlayerLosesToOpponent() {
        DemoGameState state = engine.createGame(config(), new SeededRandomSource(4));
        GameResult<DemoGameState, DemoEvent> result = engine.onPlayerAbandoned(state, host, new SeededRandomSource(4));
        assertThat(result.finished()).isTrue();
        assertThat(result.winnerPlayerId()).isEqualTo("p2");
    }

    @Test
    void finishedGameRejectsFurtherActions() {
        DemoGameState finished = engine.createGame(config(), new SeededRandomSource(5)).finishedAs("host");
        ValidationResult validation = engine.validate(finished, host, new DemoAction.EndTurn());
        assertThat(validation.errorCode()).isEqualTo("GAME_ALREADY_FINISHED");
    }

    private static GameConfig config() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("host", "Linh");
        names.put("p2", "Minh");
        return new GameConfig(DemoCardGameManifest.ID, "ABCD", List.of("host", "p2"), names, 1L);
    }
}
