package com.partygameonline.game.nob;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.game.nob.api.dto.NobView;
import com.partygameonline.game.nob.catalog.NobCardCatalog;
import com.partygameonline.game.nob.domain.NobAction;
import com.partygameonline.game.nob.domain.NobBloodline;
import com.partygameonline.game.nob.domain.NobBloodlineKnowledge;
import com.partygameonline.game.nob.domain.NobCardInstance;
import com.partygameonline.game.nob.domain.NobGameState;
import com.partygameonline.game.nob.domain.NobPhase;
import com.partygameonline.game.nob.domain.NobPhaseState;
import org.junit.jupiter.api.Test;

class NobCriticalFixTests {

    @Test
    void nextHunterIsNotActiveUntilResultDisplayExpires() {
        NobGameState state = prepared(NobPhase.HUNTER);
        give(state, "p1", "NOB-HU-01");
        give(state, "p2", "NOB-HU-02");
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p2", NobTestSupport.submit(hand(state, "p2").instanceId()));
        assertThat(state.getPendingDecision().actorId()).isEqualTo("p1");
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p3"));
        NobTestSupport.apply(state, "p1", NobTestSupport.hunter("SPARE"));
        assertThat(state.getPhaseState()).isEqualTo(NobPhaseState.RESOLUTION_RESULT_DISPLAY);
        assertThat(state.getPendingDecision()).isNull();
        assertThat(state.getResolutionDisplayExpiresAt()).isNotNull();
        NobTestSupport.flushPresentation(state);
        assertThat(state.getPendingDecision()).isNotNull();
        assertThat(state.getPendingDecision().actorId()).isEqualTo("p2");
    }

    @Test
    void swapClearsPreviousPublicBloodlineKnowledge() {
        NobGameState state = prepared(NobPhase.SHAPESHIFTER);
        give(state, "p1", "NOB-SH-01");
        state.player("p2").setCurrentBloodline(NobBloodline.vampire(1));
        state.player("p3").setCurrentBloodline(NobBloodline.werewolf(2));
        state.player("p2").setKnowledgeState(NobBloodlineKnowledge.PUBLICLY_REVEALED);
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2", "p3"));
        NobTestSupport.apply(state, "p1", NobTestSupport.option("SWAP"));
        assertThat(state.player("p2").getCurrentBloodline().type().name()).isEqualTo("WEREWOLF");
        assertThat(state.player("p2").getKnowledgeState()).isEqualTo(NobBloodlineKnowledge.UNKNOWN_AFTER_SWAP);
        assertThat(state.player("p2").knowsOwnBloodline()).isFalse();
        NobView observer = new NobGameProjector().project(state, NobTestSupport.viewer("p4"));
        assertThat(observer.players().stream().filter(p -> "p2".equals(p.playerId())).findFirst().orElseThrow()
                .publiclyRevealedBloodline()).isNull();
    }

    @Test
    void staleDecisionIdIsRejected() {
        NobGameState state = prepared(NobPhase.HUNTER);
        give(state, "p1", "NOB-HU-01");
        NobTestSupport.apply(state, "p1", NobTestSupport.submit(hand(state, "p1").instanceId()));
        NobTestSupport.apply(state, "p1", NobTestSupport.target("p2"));
        String live = state.getPendingDecision().decisionId();
        NobAction stale = new NobAction(
                NobAction.HUNTER_DECISION, "x", null, null, null, java.util.List.of(), "ELIMINATE", "old-decision"
        );
        assertThat(NobTestSupport.validate(state, "p1", stale).errorCode()).isEqualTo("STALE_DECISION");
        assertThat(live).isNotBlank();
    }

    @Test
    void nextRoundClearsLastRoundResult() {
        NobGameState state = NobTestSupport.fourPlayers(2);
        state.setLastRoundResult(new com.partygameonline.game.nob.domain.NobRoundResult("VAMPIRE", "VAMPIRE", true));
        state.startNextRound(new com.partygameonline.game.core.SeededRandomSource(2));
        assertThat(state.getLastRoundResult()).isNull();
        assertThat(state.getRoundNumber()).isEqualTo(2);
        assertThat(state.getPublicLog()).isEmpty();
        assertThat(state.getPlayers()).allMatch(player -> player.getObservations().isEmpty());
        assertThat(state.getPlayers()).allMatch(player -> player.getInspectReveal() == null);
    }

    private static NobGameState prepared(NobPhase phase) {
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
        return state;
    }

    private static void give(NobGameState state, String playerId, String cardCode) {
        state.requirePlayer(playerId).getHand().add(NobCardInstance.from(NobCardCatalog.require(cardCode)));
    }

    private static NobCardInstance hand(NobGameState state, String playerId) {
        return state.requirePlayer(playerId).getHand().getFirst();
    }
}
