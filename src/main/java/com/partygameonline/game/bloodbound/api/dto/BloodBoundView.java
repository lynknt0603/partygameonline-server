package com.partygameonline.game.bloodbound.api.dto;

import com.partygameonline.game.bloodbound.domain.BloodBoundPhase;
import com.partygameonline.game.bloodbound.domain.BloodClan;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record BloodBoundView(
        String gameId,
        String roomId,
        int version,
        String you,
        BloodBoundPhase phase,
        int roundNumber,
        String daggerHolderPlayerId,
        String currentTargetPlayerId,
        String intervenedByPlayerId,
        String forcedAttackTargetId,
        String lastAttackerPlayerId,
        List<BloodBoundPublicPlayerView> players,
        BloodBoundSecretCardView mySecretCard,
        BloodBoundClueView leftNeighborClue,
        BloodClan winnerClan,
        String capturedPlayerId,
        List<BloodBoundLogView> publicLog,
        Instant phaseDeadline,
        int turnSeconds,
        int interventionSeconds,
        Set<String> winnerPlayerIds,
        Map<String, BloodBoundSecretCardView> finalSecretCards
) {}
