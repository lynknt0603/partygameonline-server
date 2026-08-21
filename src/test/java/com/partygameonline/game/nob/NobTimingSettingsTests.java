package com.partygameonline.game.nob;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.game.nob.api.dto.NobView;
import com.partygameonline.game.nob.domain.NobAction;
import com.partygameonline.game.nob.domain.NobEvent;
import com.partygameonline.game.nob.domain.NobGameState;
import com.partygameonline.game.nob.domain.NobPhase;
import com.partygameonline.game.nob.domain.NobTimingSettings;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NobTimingSettingsTests {

    @Test
    void clampsOutOfRangeValuesToDefaults() {
        NobTimingSettings settings = new NobTimingSettings(3, 200, 30, 30, 30, 2, 100, 20_000, 3);
        assertThat(settings.draftPickSeconds()).isEqualTo(30);
        assertThat(settings.phaseSubmitSeconds()).isEqualTo(30);
        assertThat(settings.reactionDecisionSeconds()).isEqualTo(10);
        assertThat(settings.resolutionCardDisplayMs()).isEqualTo(2500);
        assertThat(settings.announcementDisplayMs()).isEqualTo(3000);
    }

    @Test
    void projectedViewIncludesServerTimeDeadlineAndTiming() {
        NobGameState state = NobTestSupport.fourPlayers(3);
        NobView view = new NobGameProjector().project(state, NobTestSupport.viewer("p1"));
        assertThat(view.serverTime()).isBeforeOrEqualTo(Instant.now().plusSeconds(1));
        assertThat(view.deadline()).isAfter(view.serverTime());
        assertThat(view.windowStartedAt()).isNotNull();
        assertThat(view.timing()).containsEntry("draftPickSeconds", 30);
        assertThat(view.submittedPlayerIds()).isEmpty();
        assertThat(view.currentResolvingCard()).isNull();
    }

    @Test
    void draftTimeoutPicksRandomCardAndEmitsPublicAutoActionWithoutCardId() {
        NobGameState state = NobTestSupport.fourPlayers(4);
        state.setPhaseDeadline(Instant.now().minusSeconds(1));
        String before = state.getDraftHands().get("p1").getFirst().instanceId();
        List<NobEvent> events = com.partygameonline.game.nob.application.NobRulesEngine.apply(
                state,
                "p1",
                new NobAction(NobAction.TIMEOUT, "to-1", null, null, null, List.of(), null),
                new com.partygameonline.game.core.SeededRandomSource(99)
        );
        assertThat(state.getPhase()).isNotEqualTo(NobPhase.DRAFT_PICK_1);
        assertThat(events).anyMatch(event -> "NOB_PLAYER_AUTO_ACTION".equals(event.type())
                && "DRAFT_PICK".equals(event.payload().get("actionType"))
                && !event.payload().containsKey("cardInstanceId")
                && !event.payload().containsKey("cardCode"));
        assertThat(state.getAnnouncement()).isNotNull();
        assertThat(state.getAnnouncement().type()).isEqualTo("PLAYER_AUTO_ACTION");
        assertThat(before).isNotBlank();
    }
}
