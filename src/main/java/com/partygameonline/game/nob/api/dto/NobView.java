package com.partygameonline.game.nob.api.dto;

import java.time.Instant;
import java.util.List;

public record NobView(
        String gameType,
        String roomId,
        String you,
        int roundNumber,
        String phase,
        String phaseState,
        int version,
        Instant serverTime,
        Instant windowStartedAt,
        Instant deadline,
        boolean finished,
        int targetScore,
        List<String> winnerPlayerIds,
        List<NobPublicPlayerView> players,
        List<NobCardView> myHand,
        NobBloodlineView myBloodline,
        String myBloodlineKnowledge,
        List<Integer> myMoonMarkValues,
        List<NobObservationView> myObservations,
        NobInspectRevealView inspectReveal,
        NobPendingDecisionView myPendingDecision,
        List<NobCardView> myDraftHand,
        List<NobCardView> echoCards,
        List<NobPublicLogView> publicLog,
        List<NobCardView> resolving,
        NobCardView currentResolvingCard,
        String currentActorPlayerId,
        String currentDecisionType,
        List<String> submittedPlayerIds,
        NobAnnouncementView announcement,
        NobRoundResultView lastRoundResult,
        List<String> roundRewardPlayerIds,
        java.util.Map<String, Object> timing,
        int discardCount,
        int undealtCount
) {
}
