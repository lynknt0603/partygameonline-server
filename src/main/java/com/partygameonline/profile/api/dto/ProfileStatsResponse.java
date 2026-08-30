package com.partygameonline.profile.api.dto;

public record ProfileStatsResponse(
        Player player,
        NobStats nobStats,
        WheresTheBoneStats wheresTheBoneStats
) {

    /** Keeps the pre-Where's-the-Bone constructor source-compatible for callers that only expose NOB. */
    public ProfileStatsResponse(Player player, NobStats nobStats) {
        this(player, nobStats, WheresTheBoneStats.empty());
    }

    public record Player(
            String playerId,
            String username,
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

    public record WheresTheBoneStats(
            long totalMatches,
            long matchesWon,
            double winRate,
            FactionStats boneThief,
            FactionStats yardDog,
            FactionStats whiteDog,
            FactionStats packmate,
            int elo,
            int highestElo
    ) {
        public static WheresTheBoneStats empty() {
            FactionStats emptyFaction = new FactionStats(0, 0, 0.0);
            return new WheresTheBoneStats(0, 0, 0.0, emptyFaction, emptyFaction, emptyFaction, emptyFaction, 5000, 5000);
        }
    }
}
