package com.partygameonline.game.nob;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.game.core.SeededRandomSource;
import com.partygameonline.game.core.ValidationResult;
import com.partygameonline.game.nob.api.dto.NobView;
import com.partygameonline.game.nob.application.NobRulesEngine;
import com.partygameonline.game.nob.catalog.NobCardCatalog;
import com.partygameonline.game.nob.domain.NobAction;
import com.partygameonline.game.nob.domain.NobBloodline;
import com.partygameonline.game.nob.domain.NobCardInstance;
import com.partygameonline.game.nob.domain.NobGameState;
import com.partygameonline.game.nob.domain.NobPhase;
import com.partygameonline.game.nob.scoring.NobScoringService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NobRoundSummaryTests {

    @Test
    void roundSummaryOffersThreeOpaqueTokensAndDoesNotLeakValues() {
        NobGameState state = summaryState();
        NobView p1 = new NobGameProjector().project(state, NobTestSupport.viewer("p1"));
        NobView p2 = new NobGameProjector().project(state, NobTestSupport.viewer("p2"));

        assertThat(state.getPhase()).isEqualTo(NobPhase.ROUND_SUMMARY);
        assertThat(p1.roundRewardPlayerIds()).contains("p1");
        assertThat(p1.myPendingDecision()).isNotNull();
        assertThat(p1.myPendingDecision().type()).isEqualTo("MOON_MARK_PICK");
        assertThat(p1.myPendingDecision().allowedOptions()).hasSize(3);
        assertThat(p1.currentDecisionType()).isEqualTo("MOON_MARK_PICK");
        assertThat(p1.players()).allMatch(player -> player.publiclyRevealedBloodline() != null);

        String option = p1.myPendingDecision().allowedOptions().getFirst();
        String json = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(p1);
        assertThat(json).doesNotContain("\"value\":");
        assertThat(p2.myPendingDecision()).isNull();

        int before = state.player("p1").moonMarkCount();
        NobTestSupport.apply(state, "p1", NobTestSupport.option(option));
        assertThat(state.player("p1").moonMarkCount()).isEqualTo(before + 1);
        assertThat(state.hasUnclaimedMoonPick("p1")).isFalse();
        assertThat(state.getPhase()).isEqualTo(NobPhase.ROUND_SUMMARY);

        NobView after = new NobGameProjector().project(state, NobTestSupport.viewer("p1"));
        assertThat(after.myPendingDecision()).isNull();
        assertThat(after.myMoonMarkValues()).isNotEmpty();
        assertThat(new NobGameProjector().project(state, NobTestSupport.viewer("p2")).myMoonMarkValues()).isEmpty();
    }

    @Test
    void lastOfferingWinnerPicksTwoOfThreeWhileDead() {
        NobGameState state = lastOfferingSummary(true);
        assertThat(state.player("p1").isAlive()).isFalse();
        assertThat(state.moonPicksNeeded("p1")).isEqualTo(2);
        assertThat(state.hasUnclaimedMoonPick("p1")).isTrue();

        NobView before = new NobGameProjector().project(state, NobTestSupport.viewer("p1"));
        assertThat(before.myPendingDecision()).isNotNull();
        assertThat(before.myPendingDecision().allowedOptions()).hasSize(3);
        ValidationResult deadPick = NobTestSupport.validate(
                state,
                "p1",
                NobTestSupport.option(before.myPendingDecision().allowedOptions().getFirst())
        );
        assertThat(deadPick.valid()).isTrue();

        int honor = state.player("p1").moonMarkCount();
        String first = before.myPendingDecision().allowedOptions().getFirst();
        NobTestSupport.apply(state, "p1", NobTestSupport.option(first));
        assertThat(state.player("p1").moonMarkCount()).isEqualTo(honor + 1);
        assertThat(state.hasUnclaimedMoonPick("p1")).isTrue();
        assertThat(state.getMoonTokenOffers().get("p1")).hasSize(2);

        NobView mid = new NobGameProjector().project(state, NobTestSupport.viewer("p1"));
        assertThat(mid.myPendingDecision()).isNotNull();
        assertThat(mid.myPendingDecision().allowedOptions()).hasSize(2);
        assertThat(mid.myPendingDecision().allowedOptions()).doesNotContain(first);

        String second = mid.myPendingDecision().allowedOptions().getFirst();
        NobTestSupport.apply(state, "p1", NobTestSupport.option(second));
        assertThat(state.player("p1").moonMarkCount()).isEqualTo(honor + 2);
        assertThat(state.hasUnclaimedMoonPick("p1")).isFalse();
        assertThat(new NobGameProjector().project(state, NobTestSupport.viewer("p1")).myPendingDecision()).isNull();
    }

    @Test
    void lastOfferingLoserStillPicksOneToken() {
        NobGameState state = lastOfferingSummary(false);
        assertThat(state.player("p1").isAlive()).isFalse();
        assertThat(state.moonPicksNeeded("p1")).isEqualTo(1);
        assertThat(state.getRoundRewardPlayerIds()).contains("p1");
        NobView view = new NobGameProjector().project(state, NobTestSupport.viewer("p1"));
        String option = view.myPendingDecision().allowedOptions().getFirst();
        int before = state.player("p1").moonMarkCount();
        NobTestSupport.apply(state, "p1", NobTestSupport.option(option));
        assertThat(state.player("p1").moonMarkCount()).isEqualTo(before + 1);
        assertThat(state.hasUnclaimedMoonPick("p1")).isFalse();
    }

    @Test
    void lastOfferingWinnerTimeoutClaimsTwoTokens() {
        NobGameState state = lastOfferingSummary(true);
        int before = state.player("p1").moonMarkCount();
        state.setPhaseDeadline(Instant.now().minusSeconds(1));
        NobRulesEngine.apply(
                state,
                "p1",
                new NobAction(NobAction.TIMEOUT, "lo-to", null, null, null, List.of(), null),
                new SeededRandomSource(11)
        );
        assertThat(state.player("p1").moonMarkCount()).isEqualTo(before + 2);
        assertThat(state.getPhase()).isEqualTo(NobPhase.DRAFT_PICK_1);
    }

    @Test
    void summaryTimeoutAwardsRemainingTokensThenStartsNextRound() {
        NobGameState state = summaryState();
        state.setPhaseDeadline(Instant.now().minusSeconds(1));
        NobRulesEngine.apply(
                state,
                "p1",
                new NobAction(NobAction.TIMEOUT, "sum-to", null, null, null, List.of(), null),
                new SeededRandomSource(11)
        );
        assertThat(state.player("p1").moonMarkCount()).isGreaterThanOrEqualTo(1);
        assertThat(state.getPhase()).isEqualTo(NobPhase.DRAFT_PICK_1);
        assertThat(state.getRoundNumber()).isEqualTo(2);
    }

    private static NobGameState summaryState() {
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
        List<String> rewards = NobScoringService.rewardPlayerIds(state, result);
        if (rewards.isEmpty()) {
            rewards = List.of("p1");
        }
        state.setLastRoundResult(new com.partygameonline.game.nob.domain.NobRoundResult(result.name(), "VAMPIRE", false));
        state.beginRoundSummary(List.of(rewards.getFirst()), new SeededRandomSource(4));
        return state;
    }

    private static NobGameState lastOfferingSummary(boolean factionWins) {
        NobGameState state = NobTestSupport.fourPlayers(9);
        state.getDraftHands().clear();
        state.getPlayers().forEach(player -> {
            player.getHand().clear();
            player.getMoonMarks().clear();
            player.getRevealedCards().clear();
            player.setAlive(true);
        });
        state.player("p1").setCurrentBloodline(NobBloodline.vampire(2));
        state.player("p1").setAlive(false);
        state.player("p1").getRevealedCards().add(NobCardInstance.from(NobCardCatalog.require("NOB-SP-LAST-OFFERING")));
        if (factionWins) {
            state.player("p2").setCurrentBloodline(NobBloodline.vampire(1));
            state.player("p3").setCurrentBloodline(NobBloodline.werewolf(3));
            state.player("p4").setCurrentBloodline(NobBloodline.werewolf(4));
        } else {
            state.player("p2").setCurrentBloodline(NobBloodline.werewolf(1));
            state.player("p3").setCurrentBloodline(NobBloodline.werewolf(2));
            state.player("p4").setCurrentBloodline(NobBloodline.vampire(5));
        }
        var result = NobScoringService.compareSurvivors(state);
        List<String> rewards = NobScoringService.rewardPlayerIds(state, result);
        String winning = switch (result) {
            case VAMPIRE -> "VAMPIRE";
            case WEREWOLF -> "WEREWOLF";
            default -> null;
        };
        state.setLastRoundResult(new com.partygameonline.game.nob.domain.NobRoundResult(result.name(), winning, false));
        state.beginRoundSummary(rewards, new SeededRandomSource(8));
        return state;
    }
}
