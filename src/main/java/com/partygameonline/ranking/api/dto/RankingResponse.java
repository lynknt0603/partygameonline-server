package com.partygameonline.ranking.api.dto;

import java.util.List;

public record RankingResponse(
        String gameId,
        String sort,
        String bloodline,
        String role,
        List<RankingEntry> podium,
        List<RankingEntry> entries,
        RankingEntry me,
        int page,
        int size,
        long totalPlayers,
        int totalPages
) {

    public RankingResponse(
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
        this(gameId, sort, bloodline, null, podium, entries, me, page, size, totalPlayers, totalPages);
    }

    public record RankingEntry(
            int rank,
            String playerId,
            String username,
            String displayName,
            String avatarUrl,
            int elo,
            int highestElo,
            int totalWins,
            int totalMatches,
            String favoriteBloodline,
            int bloodlineWins,
            String favoriteRole,
            int roleWins,
            int vegetarianMatches,
            int vegetarianWins,
            double vegetarianWinRate,
            int meatEaterMatches,
            int meatEaterWins,
            double meatEaterWinRate
    ) {
        public RankingEntry(
                int rank,
                String playerId,
                String username,
                String displayName,
                String avatarUrl,
                int elo,
                int highestElo,
                int totalWins,
                int totalMatches,
                String favoriteBloodline,
                int bloodlineWins
        ) {
            this(
                    rank,
                    playerId,
                    username,
                    displayName,
                    avatarUrl,
                    elo,
                    highestElo,
                    totalWins,
                    totalMatches,
                    favoriteBloodline,
                    bloodlineWins,
                    null,
                    0,
                    0,
                    0,
                    0.0,
                    0,
                    0,
                    0.0
            );
        }
    }
}
