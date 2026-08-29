package com.partygameonline.game.notinmypot.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record NotInMyPotView(
        String gameType,
        String roomId,
        String you,
        String phase,
        int stateVersion,
        Instant serverTime,
        boolean finished,
        String currentPlayerId,
        int turnNumber,
        Instant turnDeadline,
        boolean actionHistoryVisible,
        int targetScore,
        String winnerFaction,
        List<String> winnerPlayerIds,
        List<NotInMyPotPublicPlayerView> players,
        String myRole,
        List<NotInMyPotCardView> myHand,
        int drawPileCount,
        int potCardCount,
        int discardPileCount,
        Map<String, String> publicRoles,
        List<NotInMyPotEventView> publicEvents,
        NotInMyPotPendingActionView pendingAction,
        List<NotInMyPotCardView> privateInspectedCards,
        Integer finalPotScore,
        List<NotInMyPotCardView> finalPot,
        boolean canDeclarePotReady,
        boolean canAct
) {
}
