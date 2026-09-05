package com.partygameonline.game.bloodbound.api.dto;

import com.partygameonline.game.bloodbound.domain.BloodBoundPhase;
import com.partygameonline.game.bloodbound.domain.BloodClan;
import java.util.List;

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
        List<BloodBoundPublicPlayerView> players,
        BloodBoundSecretCardView mySecretCard,
        BloodBoundClueView leftNeighborClue,
        BloodClan winnerClan,
        String capturedPlayerId,
        List<BloodBoundLogView> publicLog
) {}
