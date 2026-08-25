package com.partygameonline.profile.api.dto;

public record ProfileStatsResponse(
        Player player,
        NobStats nobStats
) {

    public record Player(
            String playerId,
            String displayName,
            String avatarUrl,
            String joinedAt,
            String role,
            String platform
    ) {
    }

    public record NobStats(
            long totalMatches,
            long matchesWon,
            double winRate,
            FactionStats vampire,
            FactionStats werewolf,
            FactionStats halfblood,
            int elo,
            int highestElo
    ) {
    }

    /** NOB faction counters are round-based; the JSON names remain compatible with the existing client. */
    public record FactionStats(
            long matchesPlayed,
            long matchesWon,
            double winRate
    ) {
    }
}
