package com.partygameonline.ranking.api.dto;

import java.util.List;

public record RankingResponse(
        String gameId,
        String sort,
        String bloodline,
        List<RankingEntry> podium,
        List<RankingEntry> entries,
        RankingEntry me,
        int page,
        int size,
        long totalPlayers,
        int totalPages
) {

    public record RankingEntry(
            int rank,
            String playerId,
            String username,
            String displayName,
            int elo,
            int highestElo,
            int totalWins,
            int totalMatches,
            String favoriteBloodline,
            int bloodlineWins
    ) {
    }
}
