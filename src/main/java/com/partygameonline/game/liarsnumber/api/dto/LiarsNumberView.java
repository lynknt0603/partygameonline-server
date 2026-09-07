package com.partygameonline.game.liarsnumber.api.dto;

import java.time.Instant;
import java.util.List;

public record LiarsNumberView(
        String gameType,
        String roomId,
        String you,
        String phase,
        int stateVersion,
        Instant serverTime,
        int turnSeconds,
        Instant turnDeadline,
        boolean finished,
        int playerCount,
        int lossThreshold,
        int roundNumber,
        String currentRoundStarterId,
        LiarsNumberActiveRoundView activeRound,
        List<LiarsNumberPlayerView> players,
        List<LiarsNumberCardView> myHand,
        List<LiarsNumberCardView> myPenaltyCards,
        int removedCardCount,
        String loserId,
        List<String> winnerPlayerIds,
        List<LiarsNumberEventView> publicEvents,
        List<String> legalActions,
        List<String> availableTargetPlayerIds,
        List<String> availablePassTargetPlayerIds,
        LiarsNumberCardView lastResolvedCard,
        String lastPenaltyPlayerId,
        Integer lastPenaltyType,
        Integer lastPenaltyScore,
        Integer lastPenaltyThreshold,
        Boolean lastClaimIsTrue,
        Boolean lastReceiverCorrect,
        String gameOverReason
) {
}
