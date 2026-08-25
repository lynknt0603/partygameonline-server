package com.partygameonline.game.nob;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.game.nob.api.dto.NobView;
import com.partygameonline.game.nob.catalog.NobCardCatalog;
import com.partygameonline.game.nob.domain.NobBloodline;
import com.partygameonline.game.nob.domain.NobBloodlineKnowledge;
import com.partygameonline.game.nob.domain.NobCardInstance;
import com.partygameonline.game.nob.domain.NobDecisionType;
import com.partygameonline.game.nob.application.NobRulesEngine;
import com.partygameonline.game.nob.domain.NobAction;
import com.partygameonline.game.nob.domain.NobGameState;
import com.partygameonline.game.nob.domain.NobInspectReveal;
import com.partygameonline.game.nob.domain.NobMoonMark;
import com.partygameonline.game.nob.domain.NobObservation;
import com.partygameonline.game.nob.domain.NobPhase;
import com.partygameonline.game.nob.domain.NobPhaseState;
import com.partygameonline.game.core.SeededRandomSource;
import java.time.Instant;
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
    void shapeshifterExchangeCannotTargetSelfAndShowsBothBeforeSwap() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-01");
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        assertThat(state.getPendingDecision().allowedTargetIds()).doesNotContain("p1");
        assertThat(NobTestSupport.validate(state, "p1", NobTestSupport.target("p1")).errorCode())
                .isEqualTo("INVALID_TARGET");
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        assertThat(state.getPendingDecision().type()).isEqualTo(NobDecisionType.CHOOSE_TARGET);
        assertThat(state.getPendingDecision().allowedTargetIds()).containsExactlyInAnyOrder("p3", "p4");
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p3"));
        assertThat(state.getPendingDecision().type()).isEqualTo(NobDecisionType.SHAPE_SWAP);
        assertThat(state.getPendingDecision().allowedOptions()).containsExactly("SWAP", "KEEP");
        assertThat(state.getPendingDecision().allowedTargetIds()).containsExactly("p2", "p3");
        assertThat(state.player("p1").getObservations()).extracting(obs -> obs.targetPlayerId())
                .contains("p2", "p3");
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
    void shapeshifterSwapInvalidatesPreviouslySeenBloodlinesForEveryViewer() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-01");
        NobBloodline oldP2Bloodline = state.player("p2").getCurrentBloodline();
        state.player("p4").getObservations().add(
                new NobObservation("BLOODLINE", "p2", oldP2Bloodline, null, null));
        state.player("p4").getObservations().add(
                new NobObservation("CARD", "p2", null, "NOB-HU-06", null));
        state.player("p4").setInspectReveal(
                new NobInspectReveal("p2", oldP2Bloodline, null, Instant.now().plusSeconds(3)));

        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2", "p3"));
        NobTestSupport.apply(state, "p1", NobTestSupport.option("SWAP"));

        assertThat(state.player("p1").getObservations()).noneMatch(observation ->
                "BLOODLINE".equals(observation.kind())
                        && List.of("p2", "p3").contains(observation.targetPlayerId()));
        assertThat(state.player("p4").getObservations()).noneMatch(observation ->
                "BLOODLINE".equals(observation.kind())
                        && List.of("p2", "p3").contains(observation.targetPlayerId()));
        assertThat(state.player("p4").getObservations()).anyMatch(observation ->
                "CARD".equals(observation.kind()) && "p2".equals(observation.targetPlayerId()));
        assertThat(state.player("p4").getInspectReveal()).isNull();

        NobView observer = new NobGameProjector().project(state, NobTestSupport.viewer("p4"));
        assertThat(observer.myObservations()).noneMatch(observation ->
                "BLOODLINE".equals(observation.kind())
                        && List.of("p2", "p3").contains(observation.targetPlayerId()));
        assertThat(observer.players().stream()
                .filter(player -> List.of("p2", "p3").contains(player.playerId())))
                .allMatch(player -> player.publiclyRevealedBloodline() == null);
    }

    @Test
    void shapeshifterKeepStillHidesIdentityCardsFromTheTwoTargets() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-01");
        NobBloodline a = state.player("p2").getCurrentBloodline();
        NobBloodline b = state.player("p3").getCurrentBloodline();
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2", "p3"));
        NobTestSupport.apply(state, "p1", NobTestSupport.option("KEEP"));
        assertThat(state.player("p2").getCurrentBloodline()).isEqualTo(a);
        assertThat(state.player("p3").getCurrentBloodline()).isEqualTo(b);
        assertThat(state.player("p2").getKnowledgeState()).isEqualTo(NobBloodlineKnowledge.UNKNOWN_AFTER_SWAP);
        assertThat(state.player("p3").getKnowledgeState()).isEqualTo(NobBloodlineKnowledge.UNKNOWN_AFTER_SWAP);
        assertThat(state.player("p2").knowsOwnBloodline()).isFalse();
        assertThat(state.player("p3").knowsOwnBloodline()).isFalse();
        assertThat(state.player("p1").getObservations()).filteredOn(observation ->
                "BLOODLINE".equals(observation.kind()))
                .extracting(observation -> observation.targetPlayerId())
                .containsExactlyInAnyOrder("p2", "p3");
        NobView p2 = new NobGameProjector().project(state, NobTestSupport.viewer("p2"));
        assertThat(p2.myBloodline()).isNull();
        assertThat(p2.myBloodlineKnowledge()).isEqualTo("UNKNOWN_AFTER_SWAP");
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
        assertThat(state.getEchoSource()).isNotNull();
        assertThat(state.getEchoSource().cardCode()).isEqualTo("NOB-SH-02");
        assertThat(state.getEchoPicked()).isNotNull();
        assertThat(state.getEchoPicked().cardCode()).isEqualTo("NOB-SS-03");
        NobView afterKeep = new NobGameProjector().project(state, NobTestSupport.viewer("p1"));
        assertThat(afterKeep.echoSourceCard()).isNotNull();
        assertThat(afterKeep.echoSourceCard().cardCode()).isEqualTo("NOB-SH-02");
        assertThat(afterKeep.echoCards()).extracting(card -> card.cardCode()).contains("NOB-SS-03");
    }

    @Test
    void shapeshifterEchoCardsStayPrivateUntilActorChooses() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-02");
        state.getDiscardPile().add(NobCardInstance.from(NobCardCatalog.require("NOB-SS-01")));
        state.getDiscardPile().add(NobCardInstance.from(NobCardCatalog.require("NOB-SS-03")));

        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        assertThat(state.getPendingDecision().type()).isEqualTo(NobDecisionType.ECHO_CHOOSE);
        assertThat(state.getEchoHold()).hasSize(2);

        NobView actor = new NobGameProjector().project(state, NobTestSupport.viewer("p1"));
        NobView observer = new NobGameProjector().project(state, NobTestSupport.viewer("p2"));
        assertThat(actor.echoCardCount()).isEqualTo(2);
        assertThat(actor.echoCards()).hasSize(2);
        assertThat(observer.echoCardCount()).isEqualTo(2);
        assertThat(observer.echoCards()).isEmpty();

        NobCardInstance chosen = state.getEchoHold().getFirst();
        NobTestSupport.apply(state, "p1", new NobAction(
                NobAction.CHOOSE_OPTION,
                null, null, chosen.instanceId(), null, List.of(), "KEEP_FOR_LATER"
        ));

        NobView afterChoice = new NobGameProjector().project(state, NobTestSupport.viewer("p2"));
        assertThat(afterChoice.echoCardCount()).isEqualTo(2);
        assertThat(afterChoice.echoCards()).extracting(card -> card.cardCode()).containsExactly(chosen.cardCode());
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
        assertThat(state.getEchoSource().cardCode()).isEqualTo("NOB-SH-02");
        assertThat(state.getEchoPicked().cardCode()).isEqualTo("NOB-SS-01");
        NobView afterPlay = new NobGameProjector().project(state, NobTestSupport.viewer("p2"));
        assertThat(afterPlay.echoSourceCard().cardCode()).isEqualTo("NOB-SH-02");
        assertThat(afterPlay.echoCards()).extracting(card -> card.cardCode()).contains("NOB-SS-01");
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
    void moonBrokerHidesInspectTokenWhenTargetHasNoMarks() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-04");
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        assertThat(state.getPendingDecision().allowedOptions()).containsExactlyInAnyOrder("INSPECT_BLOODLINE", "SKIP");
        assertThat(state.getPendingDecision().allowedOptions()).doesNotContain("INSPECT_TOKEN", "SWAP");
    }

    @Test
    void moonBrokerInspectsOneOfTwoTokensThenOffersSwapOnlyIfActorHasAMark() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-04");
        state.player("p2").getMoonMarks().add(NobMoonMark.of(3));
        state.player("p2").getMoonMarks().add(NobMoonMark.of(4));
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        assertThat(state.getPendingDecision().allowedOptions()).contains("INSPECT_TOKEN");
        assertThat(state.getPendingDecision().allowedOptions()).doesNotContain("SWAP");
        NobTestSupport.apply(state, "p1", NobTestSupport.option("INSPECT_TOKEN"));
        assertThat(state.getPendingDecision().type()).isEqualTo(NobDecisionType.CHOOSE_MOON_TOKEN);
        assertThat(state.getPendingDecision().allowedOptions()).hasSize(2);
        assertThat(state.getPendingDecision().allowedOptions()).doesNotContain("INSPECT_TOKEN", "SWAP", "3", "4");
        String firstId = state.getPendingDecision().allowedOptions().getFirst();
        NobTestSupport.apply(state, "p1", NobTestSupport.option(firstId));
        assertThat(state.player("p1").getObservations()).extracting(obs -> obs.kind()).contains("MOON");
        assertThat(state.getPendingDecision().type()).isEqualTo(NobDecisionType.MOON_BROKER);
        assertThat(state.getPendingDecision().allowedOptions()).containsExactly("KEEP");
    }

    @Test
    void moonBrokerSwapAfterInspectExchangesTheSeenToken() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-04");
        state.player("p1").getMoonMarks().add(NobMoonMark.of(2));
        NobMoonMark seen = NobMoonMark.of(4);
        state.player("p2").getMoonMarks().add(NobMoonMark.of(3));
        state.player("p2").getMoonMarks().add(seen);
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        NobTestSupport.apply(state, "p1", NobTestSupport.option("INSPECT_TOKEN"));
        NobTestSupport.apply(state, "p1", NobTestSupport.option(seen.tokenId()));
        assertThat(state.getPendingDecision().allowedOptions()).contains("SWAP", "KEEP");
        NobTestSupport.apply(state, "p1", NobTestSupport.option("SWAP"));
        assertThat(state.player("p1").getMoonMarks()).extracting(NobMoonMark::value).containsExactly(4);
        assertThat(state.player("p2").getMoonMarks()).extracting(NobMoonMark::value).containsExactly(3, 2);
    }

    @Test
    void moonBrokerCanInspectBloodlineWithoutLeakingTokenValues() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-04");
        state.player("p2").getMoonMarks().add(NobMoonMark.of(4));
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        assertThat(state.getPendingDecision().allowedOptions()).contains("INSPECT_BLOODLINE", "INSPECT_TOKEN", "SKIP");
        assertThat(state.getPendingDecision().allowedOptions()).doesNotContain("SWAP");
        NobTestSupport.apply(state, "p1", NobTestSupport.option("INSPECT_BLOODLINE"));
        assertThat(state.player("p1").getObservations()).extracting(obs -> obs.kind()).contains("BLOODLINE");
        assertThat(state.player("p2").getMoonMarks()).extracting(NobMoonMark::value).containsExactly(4);
    }

    @Test
    void moonThiefOnlyTargetsPlayersWithMoreTokensAndDoesNotEndGame() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-05");
        state.player("p1").getMoonMarks().add(NobMoonMark.of(2));
        NobMoonMark richerFirst = NobMoonMark.of(3);
        NobMoonMark richerSecond = NobMoonMark.of(4);
        state.player("p2").getMoonMarks().add(richerFirst);
        state.player("p2").getMoonMarks().add(richerSecond);
        state.player("p3").getMoonMarks().add(NobMoonMark.of(2));
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        assertThat(state.getPendingDecision().allowedTargetIds()).containsExactly("p2");
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        assertThat(state.getPendingDecision().type()).isEqualTo(NobDecisionType.CHOOSE_MOON_TOKEN);
        NobTestSupport.apply(state, "p1", NobTestSupport.option(richerFirst.tokenId()));
        assertThat(state.player("p1").moonMarkCount()).isEqualTo(2);
        assertThat(state.player("p1").getMoonMarks()).extracting(NobMoonMark::value).containsExactly(2, 3);
        assertThat(state.player("p2").getMoonMarks()).extracting(NobMoonMark::value).containsExactly(4);
        assertThat(state.player("p1").getKnowledgeState()).isEqualTo(NobBloodlineKnowledge.PUBLICLY_REVEALED);
        assertThat(state.isFinished()).isFalse();
    }

    @Test
    void moonThiefWithTwoTokensLetsActorPickAnActualToken() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-05");
        NobMoonMark first = NobMoonMark.of(2);
        NobMoonMark second = NobMoonMark.of(4);
        state.player("p2").getMoonMarks().add(first);
        state.player("p2").getMoonMarks().add(second);
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        assertThat(state.getPendingDecision().type()).isEqualTo(NobDecisionType.CHOOSE_MOON_TOKEN);
        assertThat(state.getPendingDecision().allowedOptions()).containsExactlyInAnyOrder(first.tokenId(), second.tokenId());
        NobView thief = new NobGameProjector().project(state, NobTestSupport.viewer("p1"));
        assertThat(thief.myPendingDecision().optionValues()).hasSize(2).allMatch(value -> value == null);
        assertThat(new NobGameProjector().project(state, NobTestSupport.viewer("p3")).myPendingDecision()).isNull();
        assertThat(state.player("p1").getKnowledgeState()).isNotEqualTo(NobBloodlineKnowledge.PUBLICLY_REVEALED);
        NobTestSupport.apply(state, "p1", NobTestSupport.option(second.tokenId()));
        assertThat(state.player("p1").getMoonMarks()).extracting(NobMoonMark::value).containsExactly(4);
        assertThat(state.player("p2").getMoonMarks()).extracting(NobMoonMark::value).containsExactly(2);
        assertThat(state.player("p1").getKnowledgeState()).isEqualTo(NobBloodlineKnowledge.PUBLICLY_REVEALED);
    }

    @Test
    void moonThiefOffersEveryMoonMarkWhenTargetHasMoreThanTwo() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-05");
        state.player("p1").getMoonMarks().add(NobMoonMark.of(2));
        NobMoonMark first = NobMoonMark.of(3);
        NobMoonMark second = NobMoonMark.of(4);
        NobMoonMark third = NobMoonMark.of(2);
        state.player("p2").getMoonMarks().add(first);
        state.player("p2").getMoonMarks().add(second);
        state.player("p2").getMoonMarks().add(third);

        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        assertThat(state.getPendingDecision().type()).isEqualTo(NobDecisionType.CHOOSE_MOON_TOKEN);
        assertThat(state.getPendingDecision().allowedOptions())
                .containsExactlyInAnyOrder(first.tokenId(), second.tokenId(), third.tokenId());

        NobView thief = new NobGameProjector().project(state, NobTestSupport.viewer("p1"));
        assertThat(thief.myPendingDecision().optionValues()).hasSize(3).allMatch(value -> value == null);
        NobTestSupport.apply(state, "p1", NobTestSupport.option(second.tokenId()));
        assertThat(state.player("p1").getMoonMarks()).extracting(NobMoonMark::value).containsExactly(2, 4);
        assertThat(state.player("p2").getMoonMarks()).extracting(NobMoonMark::value)
                .containsExactlyInAnyOrder(3, 2);
    }

    @Test
    void moonThiefWithNoRicherTargetCompletesAndOpensFeralKiller() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-05");
        give(state, "p1", "NOB-FK-06");
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(
                state.player("p1").getHand().stream()
                        .filter(card -> "NOB-SH-05".equals(card.cardCode()))
                        .findFirst()
                        .orElseThrow()
                        .instanceId()));
        assertThat(state.getPendingDecision()).isNull();
        assertThat(state.getAnnouncement().messageKey()).isEqualTo("nob.moonThief.alreadyRichest");
        assertThat(state.player("p1").getKnowledgeState()).isNotEqualTo(NobBloodlineKnowledge.PUBLICLY_REVEALED);
        assertThat(state.getResolutionDisplayExpiresAt()).isNotNull();
        assertThat(state.getPhaseState()).isEqualTo(NobPhaseState.RESOLUTION_RESULT_DISPLAY);
        NobTestSupport.flushPresentation(state);
        assertThat(state.getPhase()).isEqualTo(NobPhase.FERAL_KILLER);
        assertThat(state.getPhaseState()).isEqualTo(NobPhaseState.WAITING_FOR_PHASE_SUBMISSIONS);
        assertThat(state.player("p1").getHand()).extracting(NobCardInstance::cardCode).contains("NOB-FK-06");
        assertThat(state.getCurrentResolvingCard()).isNull();
        assertThat(state.getPendingDecision()).isNull();
        assertThat(state.player("p1").getKnowledgeState()).isNotEqualTo(NobBloodlineKnowledge.PUBLICLY_REVEALED);
    }

    @Test
    void echoesWithEmptyDiscardCompletesResolution() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-02");
        give(state, "p1", "NOB-FK-01");
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(
                state.player("p1").getHand().stream()
                        .filter(card -> "NOB-SH-02".equals(card.cardCode()))
                        .findFirst()
                        .orElseThrow()
                        .instanceId()));
        assertThat(state.getPendingDecision()).isNull();
        assertThat(state.getResolutionDisplayExpiresAt()).isNotNull();
        NobTestSupport.flushPresentation(state);
        assertThat(state.getPhase()).isEqualTo(NobPhase.FERAL_KILLER);
        assertThat(state.player("p1").getHand()).extracting(NobCardInstance::cardCode).contains("NOB-FK-01");
    }

    @Test
    void stuckResolvingCardWithoutPendingTimesOutAndAdvances() {
        NobGameState state = preparedNight(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-FK-06");
        NobCardInstance leftover = NobCardInstance.from(NobCardCatalog.require("NOB-SH-05"));
        state.setCurrentResolvingCard(leftover);
        state.setCurrentActorPlayerId("p1");
        state.setPhaseState(NobPhaseState.WAITING_FOR_TARGET);
        state.setPendingDecision(null);
        state.setResolutionDisplayExpiresAt(null);
        assertThat(state.timeoutIsDue(Instant.now())).isTrue();
        NobRulesEngine.apply(
                state,
                "p1",
                new NobAction(NobAction.TIMEOUT, "unstick-1", null, null, null, List.of(), null),
                new SeededRandomSource(7)
        );
        assertThat(state.getResolutionDisplayExpiresAt()).isNotNull();
        NobTestSupport.flushPresentation(state);
        assertThat(state.getPhase()).isEqualTo(NobPhase.FERAL_KILLER);
        assertThat(state.player("p1").getHand()).extracting(NobCardInstance::cardCode).contains("NOB-FK-06");
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
    void feralKillerAutoActivatesVeilReversal() {
        NobGameState state = preparedNight(NobPhase.FERAL_KILLER);
        give(state, "p1", "NOB-FK-02");
        give(state, "p2", "NOB-SP-VEIL-REVERSAL");
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        assertThat(state.getPendingDecision()).isNull();
        assertThat(state.player("p2").isAlive()).isTrue();
        assertThat(state.player("p1").isAlive()).isFalse();
        assertThat(state.getCurrentResolvingCard().cardCode()).isEqualTo("NOB-SP-VEIL-REVERSAL");
        assertThat(state.player("p2").getRevealedCards()).extracting(NobCardInstance::cardCode)
                .contains("NOB-SP-VEIL-REVERSAL");
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
