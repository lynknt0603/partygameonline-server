package com.partygameonline.game.nob;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.game.core.SeededRandomSource;
import com.partygameonline.game.nob.domain.NobBloodline;
import com.partygameonline.game.nob.domain.NobGameState;
import com.partygameonline.game.nob.domain.NobMoonMark;
import com.partygameonline.game.nob.scoring.NobScoringService;
import java.util.List;
import org.junit.jupiter.api.Test;

class NobMoonMarkPoolTests {

    @Test
    void gameStartsWithTenTwosFifteenThreesTenFours() {
        NobGameState state = NobTestSupport.fourPlayers(1);
        assertThat(state.moonMarkPoolCount(2)).isEqualTo(10);
        assertThat(state.moonMarkPoolCount(3)).isEqualTo(15);
        assertThat(state.moonMarkPoolCount(4)).isEqualTo(10);
        assertThat(state.getMoonMarkPool()).hasSize(35);
        assertThat(circulating(state)).isEqualTo(35);
    }

    @Test
    void drawingAValueReducesThatCountInThePool() {
        NobGameState state = NobTestSupport.fourPlayers(2);
        NobMoonMark drawn = state.drawMoonMark(new SeededRandomSource(2));
        assertThat(drawn).isNotNull();
        assertThat(state.moonMarkPoolCount(drawn.value())).isEqualTo(drawn.value() == 2 ? 9 : drawn.value() == 3 ? 14 : 9);
        assertThat(state.getMoonMarkPool()).hasSize(34);
        assertThat(circulating(state)).isEqualTo(34);
        state.player("p1").getMoonMarks().add(drawn);
        assertThat(circulating(state)).isEqualTo(35);
    }

    @Test
    void roundSummaryKeepsUnpickedTokensInThePool() {
        NobGameState state = summaryReady();
        int twos = state.moonMarkPoolCount(2);
        int threes = state.moonMarkPoolCount(3);
        int fours = state.moonMarkPoolCount(4);
        state.beginRoundSummary(List.of("p1"), new SeededRandomSource(4));
        assertThat(state.getMoonTokenOffers().get("p1")).hasSize(3);
        assertThat(state.getMoonMarkPool()).hasSize(32);
        assertThat(circulating(state)).isEqualTo(35);

        String option = state.getMoonTokenOffers().get("p1").getFirst().optionId();
        int claimed = state.getMoonTokenOffers().get("p1").getFirst().mark().value();
        NobTestSupport.apply(state, "p1", NobTestSupport.option(option));
        assertThat(state.player("p1").getMoonMarks()).extracting(NobMoonMark::value).containsExactly(claimed);
        assertThat(state.getMoonMarkPool()).hasSize(34);
        assertThat(state.moonMarkPoolCount(2)).isEqualTo(claimed == 2 ? twos - 1 : twos);
        assertThat(state.moonMarkPoolCount(3)).isEqualTo(claimed == 3 ? threes - 1 : threes);
        assertThat(state.moonMarkPoolCount(4)).isEqualTo(claimed == 4 ? fours - 1 : fours);
        assertThat(circulating(state)).isEqualTo(35);
    }

    @Test
    void emptyPoolDoesNotMintExtraTokens() {
        NobGameState state = NobTestSupport.fourPlayers(3);
        state.getMoonMarkPool().clear();
        state.awardMoonMark(state.player("p1"), new SeededRandomSource(3));
        assertThat(state.player("p1").moonMarkCount()).isZero();
        state.beginRoundSummary(List.of("p1"), new SeededRandomSource(3));
        assertThat(state.getMoonTokenOffers()).isEmpty();
        assertThat(state.hasUnclaimedMoonPick("p1")).isFalse();
        assertThat(circulating(state)).isZero();
    }

    private static NobGameState summaryReady() {
        NobGameState state = NobTestSupport.fourPlayers(6);
        state.getDraftHands().clear();
        state.getPlayers().forEach(player -> {
            player.getHand().clear();
            player.getMoonMarks().clear();
            player.setCurrentBloodline(player.getSeat() % 2 == 0
                    ? NobBloodline.vampire(player.getSeat() / 2 + 1)
                    : NobBloodline.werewolf(player.getSeat() / 2 + 1));
        });
        var result = NobScoringService.compareSurvivors(state);
        state.setLastRoundResult(new com.partygameonline.game.nob.domain.NobRoundResult(result.name(), "VAMPIRE", false));
        return state;
    }

    private static int circulating(NobGameState state) {
        int held = state.getPlayers().stream().mapToInt(player -> player.getMoonMarks().size()).sum();
        int offered = state.getMoonTokenOffers().values().stream().mapToInt(List::size).sum();
        return state.getMoonMarkPool().size() + held + offered;
    }
}
