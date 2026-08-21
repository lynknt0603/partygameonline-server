package com.partygameonline.game.nob;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.game.core.SeededRandomSource;
import com.partygameonline.game.nob.catalog.NobCardCatalog;
import com.partygameonline.game.nob.domain.NobBloodline;
import com.partygameonline.game.nob.domain.NobCardInstance;
import com.partygameonline.game.nob.domain.NobDecisionType;
import com.partygameonline.game.nob.domain.NobGameState;
import com.partygameonline.game.nob.domain.NobMoonMark;
import com.partygameonline.game.nob.domain.NobPhase;
import com.partygameonline.game.nob.domain.NobPlayerState;
import com.partygameonline.game.nob.scoring.NobScoringService;
import org.junit.jupiter.api.Test;

class NobScoringAndSpecialsTests {

    @Test
    void veilReversalSavesTargetAndKillsAttackerWithoutChain() {
        NobGameState state = killSetup();
        state.player("p2").getHand().add(NobCardInstance.from(NobCardCatalog.require("NOB-SP-VEIL-REVERSAL")));
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(state.player("p1").getHand().getFirst().instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        NobTestSupport.apply(state, "p2", NobTestSupport.reaction("VEIL_REVERSAL"));
        assertThat(state.player("p2").isAlive()).isTrue();
        assertThat(state.player("p1").isAlive()).isFalse();
        assertThat(state.getActiveKill()).isNull();
        assertThat(state.getPendingDecision()).isNull();
    }

    @Test
    void lastOfferingAwardsOneTokenThenTargetDies() {
        NobGameState state = killSetup();
        state.player("p2").getHand().add(NobCardInstance.from(NobCardCatalog.require("NOB-SP-LAST-OFFERING")));
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(state.player("p1").getHand().getFirst().instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        NobTestSupport.apply(state, "p2", NobTestSupport.reaction("LAST_OFFERING"));
        assertThat(state.player("p2").isAlive()).isFalse();
        assertThat(state.player("p2").moonMarkCount()).isEqualTo(1);
        assertThat(state.player("p1").isAlive()).isTrue();
    }

    @Test
    void lastHopeForcesHolderFactionAndDeadLastHopeDoesNothing() {
        NobGameState state = scoredState();
        state.player("p1").setCurrentBloodline(NobBloodline.vampire(5));
        state.player("p2").setCurrentBloodline(NobBloodline.werewolf(1));
        state.player("p3").setCurrentBloodline(NobBloodline.werewolf(2));
        state.player("p4").setCurrentBloodline(NobBloodline.vampire(4));
        state.player("p1").getHand().add(NobCardInstance.from(NobCardCatalog.require("NOB-SP-LAST-HOPE")));
        assertThat(NobScoringService.compareSurvivors(state)).isEqualTo(NobScoringService.MainResult.VAMPIRE);

        state.player("p1").setAlive(false);
        assertThat(NobScoringService.compareSurvivors(state)).isEqualTo(NobScoringService.MainResult.WEREWOLF);
    }

    @Test
    void lastHopeHalfbloodBlocksMainFactionReward() {
        NobGameState state = scoredState();
        state.player("p1").setCurrentBloodline(NobBloodline.halfblood());
        state.player("p1").getHand().add(NobCardInstance.from(NobCardCatalog.require("NOB-SP-LAST-HOPE")));
        state.player("p2").setCurrentBloodline(NobBloodline.vampire(1));
        assertThat(NobScoringService.compareSurvivors(state)).isEqualTo(NobScoringService.MainResult.LAST_HOPE_HALFBLOOD);
        NobScoringService.applyRoundRewards(state, NobScoringService.MainResult.LAST_HOPE_HALFBLOOD, new SeededRandomSource(1));
        assertThat(state.player("p1").moonMarkCount()).isEqualTo(1);
        assertThat(state.player("p2").moonMarkCount()).isZero();
    }

    @Test
    void rankOneBeatsRankTwoRegardlessOfSurvivorCount() {
        NobGameState state = scoredState();
        state.player("p1").setCurrentBloodline(NobBloodline.vampire(1));
        state.player("p2").setCurrentBloodline(NobBloodline.werewolf(2));
        state.player("p3").setCurrentBloodline(NobBloodline.werewolf(3));
        state.player("p4").setCurrentBloodline(NobBloodline.werewolf(4));
        assertThat(NobScoringService.compareSurvivors(state)).isEqualTo(NobScoringService.MainResult.VAMPIRE);
    }

    @Test
    void tiedFirstRankComparesNextAndTotalTieAwardsEachSurvivorOnce() {
        NobGameState state = scoredState();
        state.player("p1").setCurrentBloodline(NobBloodline.vampire(1));
        state.player("p2").setCurrentBloodline(NobBloodline.werewolf(1));
        state.player("p3").setCurrentBloodline(NobBloodline.vampire(3));
        state.player("p4").setCurrentBloodline(NobBloodline.werewolf(2));
        assertThat(NobScoringService.compareSurvivors(state)).isEqualTo(NobScoringService.MainResult.WEREWOLF);

        state.player("p3").setCurrentBloodline(NobBloodline.vampire(2));
        assertThat(NobScoringService.compareSurvivors(state)).isEqualTo(NobScoringService.MainResult.TOTAL_TIE);
        NobScoringService.applyRoundRewards(state, NobScoringService.MainResult.TOTAL_TIE, new SeededRandomSource(2));
        assertThat(state.alivePlayers()).allMatch(player -> player.moonMarkCount() == 1);
    }

    @Test
    void winnerFactionIncludesDeadMembersAndSurvivingHalfblood() {
        NobGameState state = scoredState();
        state.player("p1").setCurrentBloodline(NobBloodline.vampire(1));
        state.player("p2").setCurrentBloodline(NobBloodline.vampire(2));
        state.player("p2").setAlive(false);
        state.player("p3").setCurrentBloodline(NobBloodline.werewolf(3));
        state.player("p4").setCurrentBloodline(NobBloodline.halfblood());
        NobScoringService.applyRoundRewards(state, NobScoringService.MainResult.VAMPIRE, new SeededRandomSource(3));
        assertThat(state.player("p1").moonMarkCount()).isEqualTo(1);
        assertThat(state.player("p2").moonMarkCount()).isEqualTo(1);
        assertThat(state.player("p3").moonMarkCount()).isZero();
        assertThat(state.player("p4").moonMarkCount()).isEqualTo(1);
    }

    @Test
    void victoryChecksOnlyHighestScoreAtOrOverTargetAndAllowsSharedWinners() {
        NobGameState state = scoredState();
        state.player("p1").getMoonMarks().add(NobMoonMark.of(4));
        state.player("p1").getMoonMarks().add(NobMoonMark.of(4));
        state.player("p1").getMoonMarks().add(NobMoonMark.of(2));
        state.player("p2").getMoonMarks().add(NobMoonMark.of(4));
        state.player("p2").getMoonMarks().add(NobMoonMark.of(4));
        state.player("p2").getMoonMarks().add(NobMoonMark.of(2));
        state.player("p3").getMoonMarks().add(NobMoonMark.of(4));
        assertThat(NobScoringService.winnersAtOrOverTarget(state)).containsExactly("p1", "p2");
        state.player("p1").getMoonMarks().clear();
        state.player("p2").getMoonMarks().clear();
        assertThat(NobScoringService.winnersAtOrOverTarget(state)).isEmpty();
    }

    @Test
    void hunterEliminateUsesTheSameReactionPipeline() {
        NobGameState state = prepared(NobPhase.HUNTER);
        state.player("p1").getHand().add(NobCardInstance.from(NobCardCatalog.require("NOB-HU-02")));
        state.player("p2").getHand().add(NobCardInstance.from(NobCardCatalog.require("NOB-SP-LAST-OFFERING")));
        state.player("p3").getMoonMarks().add(NobMoonMark.of(4));
        state.player("p3").getMoonMarks().add(NobMoonMark.of(4));
        state.player("p3").getMoonMarks().add(NobMoonMark.of(2));
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(state.player("p1").getHand().getFirst().instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        NobTestSupport.apply(state, "p1", NobTestSupport.hunter("ELIMINATE"));
        assertThat(state.getPendingDecision().type()).isEqualTo(NobDecisionType.REACTION);
        NobTestSupport.apply(state, "p2", NobTestSupport.reaction("DECLINE"));
        assertThat(state.player("p2").isAlive()).isFalse();
    }

    private static NobGameState killSetup() {
        NobGameState state = prepared(NobPhase.FERAL_KILLER);
        state.player("p1").getHand().add(NobCardInstance.from(NobCardCatalog.require("NOB-FK-01")));
        state.player("p4").getHand().add(NobCardInstance.from(NobCardCatalog.require("NOB-HU-06")));
        return state;
    }

    private static NobGameState scoredState() {
        return prepared(NobPhase.SCORING);
    }

    private static NobGameState prepared(NobPhase phase) {
        NobGameState state = NobTestSupport.fourPlayers(5);
        state.getDraftHands().clear();
        state.getPlayers().forEach(player -> {
            player.getHand().clear();
            player.getRevealedCards().clear();
            player.getMoonMarks().clear();
            player.setAlive(true);
        });
        state.beginNightPhase(phase == NobPhase.SCORING ? NobPhase.HUNTER : phase);
        if (phase == NobPhase.SCORING) {
            state.setPhase(NobPhase.SCORING);
        }
        return state;
    }
}
