package com.partygameonline.game.nob;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.game.nob.api.dto.NobView;
import com.partygameonline.game.nob.catalog.NobCardCatalog;
import com.partygameonline.game.nob.domain.NobBloodline;
import com.partygameonline.game.nob.domain.NobBloodlineKnowledge;
import com.partygameonline.game.nob.domain.NobCardInstance;
import com.partygameonline.game.nob.domain.NobDecisionType;
import com.partygameonline.game.nob.domain.NobGameState;
import com.partygameonline.game.nob.domain.NobMoonMark;
import com.partygameonline.game.nob.domain.NobPhase;
import java.util.List;
import org.junit.jupiter.api.Test;

class NobNightAndEffectsTests {

    @Test
    void nightPhasesResolveNumberOneBeforeSixAndSkipEmptyPhases() {
        NobGameState state = preparedNight(NobPhase.SHADOW_STALKER);
        give(state, "p1", "NOB-SS-06");
        give(state, "p2", "NOB-SS-01");
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p2", NobTestSupport.submit(hand(state, "p2").instanceId()));
        assertThat(state.player("p2").getRevealedCards()).extracting(NobCardInstance::cardCode).contains("NOB-SS-01");
        assertThat(state.getPendingDecision().actorId()).isEqualTo("p2");
    }

    @Test
    void deadOwnerCardIsCancelledBeforeResolution() {
        NobGameState state = preparedNight(NobPhase.FERAL_KILLER);
        give(state, "p1", "NOB-FK-01");
        give(state, "p2", "NOB-FK-06");
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p2", NobTestSupport.submit(hand(state, "p2").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        assertThat(state.player("p2").isAlive()).isFalse();
        NobTestSupport.flushPresentation(state);
        assertThat(state.player("p2").getRevealedCards()).extracting(NobCardInstance::cardCode)
                .doesNotContain("NOB-FK-06");
        assertThat(state.getPublicLog()).anyMatch(entry -> "NOB_CARD_CANCELLED_OWNER_DEAD".equals(entry.type()));
    }

    @Test
    void shadowStalkerInspectionIsPrivate() {
        NobGameState state = preparedNight(NobPhase.SHADOW_STALKER);
        give(state, "p1", "NOB-SS-01");
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        assertThat(state.player("p1").getObservations()).hasSize(1);
        assertThat(state.player("p1").getObservations().getFirst().bloodline())
                .isEqualTo(state.player("p2").getCurrentBloodline());
        assertThat(state.player("p3").getObservations()).isEmpty();
        assertThat(state.player("p2").getKnowledgeState()).isEqualTo(NobBloodlineKnowledge.KNOWN);
        assertThat(state.getPublicLog()).anyMatch(entry ->
                "NOB_INSPECTED".equals(entry.type())
                        && "p1".equals(entry.actorPlayerId())
                        && "p2".equals(entry.targetPlayerId()));
        assertThat(state.getPublicLog()).noneMatch(entry ->
                entry.text() != null && entry.text().contains(state.player("p2").getCurrentBloodline().type().name()));
        assertThat(state.player("p1").getInspectReveal()).isNotNull();
        assertThat(state.player("p1").getInspectReveal().bloodline())
                .isEqualTo(state.player("p2").getCurrentBloodline());
        assertThat(state.player("p3").getInspectReveal()).isNull();
    }

    @Test
    void bloodSeerSeesBloodlineAndOneHiddenCard() {
        NobGameState state = preparedNight(NobPhase.BLOOD_SEER);
        give(state, "p1", "NOB-BS-01");
        give(state, "p2", "NOB-HU-03");
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        assertThat(state.player("p1").getObservations()).extracting(obs -> obs.kind())
                .contains("BLOODLINE", "CARD");
        assertThat(state.player("p1").getInspectReveal().cardCode()).isEqualTo("NOB-HU-03");
        assertThat(state.getPublicLog()).filteredOn(entry -> "NOB_INSPECTED".equals(entry.type())).hasSize(1);
    }

    @Test
    void bloodSeerWithTwoHiddenCardsLetsActorPickWhichToFlip() {
        NobGameState state = preparedNight(NobPhase.BLOOD_SEER);
        give(state, "p1", "NOB-BS-01");
        give(state, "p2", "NOB-HU-03");
        give(state, "p2", "NOB-SH-04");
        String hunterId = state.player("p2").getHand().getFirst().instanceId();
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        assertThat(state.getPendingDecision().type()).isEqualTo(NobDecisionType.CHOOSE_HIDDEN_CARD);
        assertThat(state.getPendingDecision().allowedOptions()).hasSize(2);
        assertThat(state.player("p1").getObservations()).extracting(obs -> obs.kind()).containsExactly("BLOODLINE");
        NobTestSupport.apply(state, "p1", new com.partygameonline.game.nob.domain.NobAction(
                com.partygameonline.game.nob.domain.NobAction.CHOOSE_OPTION,
                null, null, hunterId, null, List.of(), hunterId
        ));
        assertThat(state.player("p1").getObservations()).extracting(obs -> obs.kind())
                .contains("BLOODLINE", "CARD");
        assertThat(state.player("p1").getInspectReveal().cardCode()).isEqualTo("NOB-HU-03");
        assertThat(state.player("p2").getHand()).hasSize(2);
    }

    @Test
    void shapeshifterSwapChangesBloodlinesAndHidesThemFromOwners() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-01");
        NobBloodline a = state.player("p2").getCurrentBloodline();
        NobBloodline b = state.player("p3").getCurrentBloodline();
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2", "p3"));
        NobTestSupport.apply(state, "p1", NobTestSupport.option("SWAP"));
        assertThat(state.player("p2").getCurrentBloodline()).isEqualTo(b);
        assertThat(state.player("p3").getCurrentBloodline()).isEqualTo(a);
        assertThat(state.player("p2").getKnowledgeState()).isEqualTo(NobBloodlineKnowledge.UNKNOWN_AFTER_SWAP);
        assertThat(state.player("p3").getKnowledgeState()).isEqualTo(NobBloodlineKnowledge.UNKNOWN_AFTER_SWAP);
        assertThat(state.player("p2").knowsOwnBloodline()).isFalse();
    }

    @Test
    void shapeshifterEchoKeepDoesNotDuplicateAndPlayNowResolves() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-02");
        NobCardInstance discarded = NobCardInstance.from(NobCardCatalog.require("NOB-SS-03"));
        state.getDiscardPile().add(discarded);
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        assertThat(state.getPendingDecision().type()).isEqualTo(NobDecisionType.ECHO_CHOOSE);
        assertThat(state.getEchoHold()).hasSize(1);
        NobTestSupport.apply(state, "p1", new com.partygameonline.game.nob.domain.NobAction(
                com.partygameonline.game.nob.domain.NobAction.CHOOSE_OPTION,
                null, null, discarded.instanceId(), null, List.of(), "KEEP_FOR_LATER"
        ));
        assertThat(state.player("p1").getHand()).extracting(NobCardInstance::cardCode).contains("NOB-SS-03");
        assertThat(state.getDiscardPile()).extracting(NobCardInstance::instanceId).doesNotContain(discarded.instanceId());
        assertThat(state.getEchoHold()).isEmpty();
    }

    @Test
    void shapeshifterEchoPlayNowStartsTheChosenCard() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-02");
        NobCardInstance discarded = NobCardInstance.from(NobCardCatalog.require("NOB-SS-01"));
        state.getDiscardPile().add(discarded);
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        assertThat(state.getPendingDecision().type()).isEqualTo(NobDecisionType.ECHO_CHOOSE);
        NobTestSupport.apply(state, "p1", new com.partygameonline.game.nob.domain.NobAction(
                com.partygameonline.game.nob.domain.NobAction.CHOOSE_OPTION,
                null, null, discarded.instanceId(), null, List.of(), "PLAY_NOW"
        ));
        assertThat(state.getEchoHold()).isEmpty();
        assertThat(state.getPendingDecision()).isNotNull();
        assertThat(state.getPendingDecision().type()).isEqualTo(NobDecisionType.CHOOSE_TARGET);
        assertThat(state.getPendingDecision().actorId()).isEqualTo("p1");
        assertThat(state.player("p1").getRevealedCards()).extracting(NobCardInstance::cardCode)
                .contains("NOB-SS-01");
    }

    @Test
    void shapeshifterUnmaskCanRevealPublicly() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-03");
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p4"));
        NobTestSupport.apply(state, "p1", NobTestSupport.option("REVEAL_PUBLIC"));
        assertThat(state.player("p4").getKnowledgeState()).isEqualTo(NobBloodlineKnowledge.PUBLICLY_REVEALED);

        NobView observer = new NobGameProjector().project(state, NobTestSupport.viewer("p2"));
        var p4 = observer.players().stream().filter(player -> "p4".equals(player.playerId())).findFirst().orElseThrow();
        assertThat(p4.publiclyRevealedBloodline()).isNotNull();
        assertThat(p4.publiclyRevealedBloodline().type()).isEqualTo(state.player("p4").getCurrentBloodline().type().name());
        assertThat(p4.publiclyRevealedBloodline().rank()).isEqualTo(state.player("p4").getCurrentBloodline().rank());
        assertThat(observer.myObservations()).anyMatch(obs ->
                "BLOODLINE".equals(obs.kind())
                        && "p4".equals(obs.targetPlayerId())
                        && obs.bloodline() != null);
        assertThat(observer.announcement()).isNotNull();
        assertThat(observer.announcement().type()).isEqualTo("BLOODLINE_PUBLICLY_REVEALED");
        assertThat(observer.announcement().targetPlayerId()).isEqualTo("p4");
        assertThat(observer.publicLog()).anyMatch(entry ->
                "NOB_BLOODLINE_PUBLICLY_REVEALED".equals(entry.type())
                        && "p4".equals(entry.targetPlayerId())
                        && entry.text() != null
                        && entry.text().contains(p4.publiclyRevealedBloodline().type()));
    }

    @Test
    void shapeshifterUnmaskKeepSecretDoesNotPublishBloodline() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-03");
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p4"));
        NobTestSupport.apply(state, "p1", NobTestSupport.option("KEEP_SECRET"));
        assertThat(state.player("p4").getKnowledgeState()).isNotEqualTo(NobBloodlineKnowledge.PUBLICLY_REVEALED);

        NobView observer = new NobGameProjector().project(state, NobTestSupport.viewer("p2"));
        var p4 = observer.players().stream().filter(player -> "p4".equals(player.playerId())).findFirst().orElseThrow();
        assertThat(p4.publiclyRevealedBloodline()).isNull();
        assertThat(observer.myObservations()).noneMatch(obs ->
                "BLOODLINE".equals(obs.kind()) && "p4".equals(obs.targetPlayerId()));
    }

    @Test
    void moonBrokerCanInspectBloodlineWithoutLeakingTokenValues() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-04");
        state.player("p2").getMoonMarks().add(NobMoonMark.of(4));
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        assertThat(state.getPendingDecision().allowedOptions()).contains("INSPECT_BLOODLINE", "INSPECT_TOKEN", "SKIP");
        NobTestSupport.apply(state, "p1", NobTestSupport.option("INSPECT_BLOODLINE"));
        assertThat(state.player("p1").getObservations()).extracting(obs -> obs.kind()).contains("BLOODLINE");
        assertThat(state.player("p2").getMoonMarks()).extracting(NobMoonMark::value).containsExactly(4);
    }

    @Test
    void moonThiefOnlyTargetsPlayersWithMoreTokensAndDoesNotEndGame() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-05");
        state.player("p2").getMoonMarks().add(NobMoonMark.of(3));
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        assertThat(state.getPendingDecision().allowedTargetIds()).containsExactly("p2");
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        assertThat(state.player("p1").moonMarkCount()).isEqualTo(1);
        assertThat(state.player("p2").moonMarkCount()).isZero();
        assertThat(state.isFinished()).isFalse();
    }

    @Test
    void finalJudgementEliminatesWithoutReaction() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-06");
        give(state, "p2", "NOB-SP-VEIL-REVERSAL");
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        assertThat(state.player("p2").isAlive()).isFalse();
        assertThat(state.getPendingDecision()).isNull();
        assertThat(state.getActiveKill()).isNull();
    }

    @Test
    void hunterInspectsThenSpareLeavesTargetAlive() {
        NobGameState state = preparedNight(NobPhase.HUNTER);
        give(state, "p1", "NOB-HU-01");
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p3"));
        assertThat(state.player("p1").getObservations()).isNotEmpty();
        NobTestSupport.apply(state, "p1", NobTestSupport.hunter("SPARE"));
        assertThat(state.player("p3").isAlive()).isTrue();
    }

    @Test
    void feralKillerOpensReactionWindow() {
        NobGameState state = preparedNight(NobPhase.FERAL_KILLER);
        give(state, "p1", "NOB-FK-02");
        give(state, "p2", "NOB-SP-VEIL-REVERSAL");
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        assertThat(state.getPendingDecision().type()).isEqualTo(NobDecisionType.REACTION);
        assertThat(state.getPendingDecision().allowedOptions()).contains("VEIL_REVERSAL", "DECLINE");
        assertThat(state.player("p2").isAlive()).isTrue();
    }

    @Test
    void skippedCardIsNotReplayedInTheSamePhase() {
        NobGameState state = preparedNight(NobPhase.SHADOW_STALKER);
        give(state, "p1", "NOB-SS-01");
        NobTestSupport.apply(state, "p1", NobTestSupport.pass());
        assertThat(state.player("p1").getPassedInstanceIds()).isNotEmpty();
        assertThat(state.getPhase()).isNotEqualTo(NobPhase.SHADOW_STALKER);
    }

    @Test
    void bloodSeerCanPlayBothCardsInNumberOrder() {
        NobGameState state = preparedNight(NobPhase.BLOOD_SEER);
        give(state, "p1", "NOB-BS-02");
        give(state, "p1", "NOB-BS-01");
        NobTestSupport.apply(state, "p1", NobTestSupport.submitBoth());
        assertThat(state.player("p1").getRevealedCards()).extracting(NobCardInstance::cardCode)
                .containsExactly("NOB-BS-01");
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        NobTestSupport.flushPresentation(state);
        assertThat(state.player("p1").getRevealedCards()).extracting(NobCardInstance::cardCode)
                .containsExactly("NOB-BS-01", "NOB-BS-02");
        assertThat(state.getPendingDecision()).isNotNull();
        assertThat(state.getPendingDecision().actorId()).isEqualTo("p1");
    }

    @Test
    void hunterCanPlayBothCardsSequentially() {
        NobGameState state = preparedNight(NobPhase.HUNTER);
        give(state, "p1", "NOB-HU-02");
        give(state, "p1", "NOB-HU-01");
        NobTestSupport.apply(state, "p1", NobTestSupport.submitBoth());
        assertThat(state.player("p1").getRevealedCards()).extracting(NobCardInstance::cardCode).contains("NOB-HU-01");
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p3"));
        NobTestSupport.apply(state, "p1", NobTestSupport.hunter("SPARE"));
        NobTestSupport.flushPresentation(state);
        assertThat(state.player("p1").getRevealedCards()).extracting(NobCardInstance::cardCode)
                .contains("NOB-HU-01", "NOB-HU-02");
        assertThat(state.getPendingDecision()).isNotNull();
        assertThat(state.getPendingDecision().actorId()).isEqualTo("p1");
    }

    @Test
    void playBothRequiresTwoMatchingRoleCards() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-01");
        assertThat(NobTestSupport.validate(state, "p1", NobTestSupport.submitBoth()).errorCode())
                .isEqualTo("INVALID_CARD");
    }

    @Test
    void specialCardsCannotBeBundledWithPlayBoth() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-01");
        give(state, "p1", "NOB-SP-LAST-HOPE");
        assertThat(NobTestSupport.validate(state, "p1", NobTestSupport.submitBoth()).errorCode())
                .isEqualTo("INVALID_CARD");
    }

    private static NobGameState preparedNight(NobPhase phase) {
        NobGameState state = NobTestSupport.fourPlayers(11);
        state.getDraftHands().clear();
        state.getDraftPicks().clear();
        state.getPlayers().forEach(player -> {
            player.getHand().clear();
            player.getRevealedCards().clear();
            player.getUsedCards().clear();
            player.getPassedInstanceIds().clear();
        });
        state.getDiscardPile().clear();
        state.beginNightPhase(phase);
        if (phase != NobPhase.HUNTER) {
            give(state, "p4", "NOB-HU-06");
        }
        return state;
    }

    private static void give(NobGameState state, String playerId, String cardCode) {
        state.requirePlayer(playerId).getHand().add(NobCardInstance.from(NobCardCatalog.require(cardCode)));
    }

    private static NobCardInstance hand(NobGameState state, String playerId) {
        return state.requirePlayer(playerId).getHand().getFirst();
    }
}
