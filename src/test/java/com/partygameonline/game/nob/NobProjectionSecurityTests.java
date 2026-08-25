package com.partygameonline.game.nob;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.nob.api.dto.NobView;
import com.partygameonline.game.nob.catalog.NobCardCatalog;
import com.partygameonline.game.nob.domain.NobBloodlineKnowledge;
import com.partygameonline.game.nob.domain.NobCardInstance;
import com.partygameonline.game.nob.domain.NobGameState;
import com.partygameonline.game.nob.domain.NobMoonMark;
import com.partygameonline.game.nob.domain.NobObservation;
import com.partygameonline.game.nob.domain.NobPhase;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class NobProjectionSecurityTests {

    private final NobGameProjector projector = new NobGameProjector();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void privateSnapshotContainsOnlyTheViewerSecrets() {
        NobGameState state = NobTestSupport.fourPlayers(8);
        NobView p1 = projector.project(state, NobTestSupport.viewer("p1"));
        NobView p2 = projector.project(state, NobTestSupport.viewer("p2"));

        assertThat(p1.myDraftHand()).isNotEmpty();
        assertThat(p1.myBloodline()).isNotNull();
        assertThat(p1.myBloodlineKnowledge()).isEqualTo(NobBloodlineKnowledge.KNOWN.name());
        assertThat(p2.myDraftHand().stream().map(card -> card.instanceId()).toList())
                .doesNotContainAnyElementsOf(p1.myDraftHand().stream().map(card -> card.instanceId()).toList());

        String p1Json = json.writeValueAsString(p1);
        for (var card : state.getDraftHands().get("p2")) {
            assertThat(p1Json).doesNotContain(card.instanceId());
        }
        assertThat(p1.players()).allMatch(player -> player.publiclyRevealedBloodline() == null);
        assertThat(p1.players().stream().filter(player -> "p2".equals(player.playerId())).findFirst().orElseThrow()
                .publiclyRevealedBloodline()).isNull();
        assertThat(p1.discardCount()).isZero();
        assertThat(p1.undealtCount()).isEqualTo(21);
    }

    @Test
    void inspectionAndMoonValuesDoNotLeakToOtherViewers() {
        NobGameState state = NobTestSupport.fourPlayers(8);
        state.getDraftHands().clear();
        state.beginNightPhase(NobPhase.SHADOW_STALKER);
        state.player("p2").getMoonMarks().add(NobMoonMark.of(4));
        state.player("p1").getObservations().add(new NobObservation(
                "BLOODLINE", "p2", state.player("p2").getCurrentBloodline(), null, null
        ));
        state.player("p1").getObservations().add(new NobObservation("MOON", "p2", null, null, 4));
        state.player("p1").getHand().add(NobCardInstance.from(NobCardCatalog.require("NOB-SS-01")));

        NobView p1 = projector.project(state, NobTestSupport.viewer("p1"));
        NobView p3 = projector.project(state, NobTestSupport.viewer("p3"));
        Map<String, Object> p3Json = json.convertValue(p3, Map.class);

        assertThat(p1.myObservations()).hasSize(2);
        assertThat(p3.myObservations()).isEmpty();
        assertThat(p3.myHand()).isEmpty();
        assertThat(p3.players().stream().filter(player -> "p2".equals(player.playerId())).findFirst().orElseThrow()
                .moonMarkCount()).isEqualTo(1);
        assertThat(p3.players().stream().filter(player -> "p2".equals(player.playerId())).findFirst().orElseThrow()
                .score()).isNull();
        assertThat(json.writeValueAsString(p3)).doesNotContain("\"moonMarkValue\":4");
        assertThat(p3Json.get("myMoonMarkValues")).isEqualTo(java.util.List.of());
        assertThat(p3.players()).allMatch(player -> player.publiclyRevealedBloodline() == null);
        assertThat(json.writeValueAsString(p3)).doesNotContain(state.player("p1").getHand().getFirst().instanceId());
    }

    @Test
    void spectatorDoesNotReceiveHandsOrBloodlines() {
        NobGameState state = NobTestSupport.fourPlayers(2);
        NobView view = projector.project(state, PlayerContext.spectator("spec", "SPEC"));
        assertThat(view.myHand()).isEmpty();
        assertThat(view.myDraftHand()).isEmpty();
        assertThat(view.myBloodline()).isNull();
        assertThat(view.myMoonMarkValues()).isEmpty();
        assertThat(view.myPendingDecision()).isNull();
        assertThat(view.myObservations()).isEmpty();
        assertThat(view.inspectReveal()).isNull();
    }

    @Test
    void inspectRevealDoesNotLeakToOtherViewers() {
        NobGameState state = NobTestSupport.fourPlayers(8);
        state.getDraftHands().clear();
        state.beginNightPhase(NobPhase.BLOOD_SEER);
        state.player("p1").setInspectReveal(new com.partygameonline.game.nob.domain.NobInspectReveal(
                "p2",
                state.player("p2").getCurrentBloodline(),
                "NOB-HU-03",
                java.time.Instant.now().plusSeconds(3)
        ));
        state.log("NOB_INSPECTED", "P1 inspected P2", "p1", "p2");

        NobView p1 = projector.project(state, NobTestSupport.viewer("p1"));
        NobView p3 = projector.project(state, NobTestSupport.viewer("p3"));

        assertThat(p1.inspectReveal()).isNotNull();
        assertThat(p1.inspectReveal().cardCode()).isEqualTo("NOB-HU-03");
        assertThat(p3.inspectReveal()).isNull();
        assertThat(json.writeValueAsString(p3)).doesNotContain("NOB-HU-03");
        assertThat(p3.publicLog()).anyMatch(entry ->
                "NOB_INSPECTED".equals(entry.type())
                        && "p1".equals(entry.actorPlayerId())
                && "p2".equals(entry.targetPlayerId()));
    }

    @Test
    void secretPhaseDoesNotRevealWhichOtherPlayersSubmitted() {
        NobGameState state = NobTestSupport.fourPlayers(8);
        state.getDraftHands().clear();
        state.beginNightPhase(NobPhase.SHADOW_STALKER);
        state.getPhaseSubmissions().put("p1", List.of("card-p1"));
        state.announce("PLAYER_AUTO_ACTION", "p1", null, null, null, "nob.timeout.autoAction");

        NobView submitter = projector.project(state, NobTestSupport.viewer("p1"));
        NobView observer = projector.project(state, NobTestSupport.viewer("p2"));

        assertThat(submitter.submittedPlayerIds()).containsExactly("p1");
        assertThat(observer.submittedPlayerIds()).isEmpty();
        assertThat(submitter.currentActorPlayerId()).isNull();
        assertThat(observer.currentActorPlayerId()).isNull();
        assertThat(submitter.announcement().actorPlayerId()).isNull();
        assertThat(observer.announcement().actorPlayerId()).isNull();
    }
}
