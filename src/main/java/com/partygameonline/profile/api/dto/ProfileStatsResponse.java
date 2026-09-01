package com.partygameonline.profile.api.dto;

import java.util.List;

public record ProfileStatsResponse(
        Player player,
        NobStats nobStats,
        WheresTheBoneStats wheresTheBoneStats,
        List<Achievement> achievements,
        List<Avatar> avatars
) {

    /** Keeps the pre-Where's-the-Bone constructor source-compatible for callers that only expose NOB. */
    public ProfileStatsResponse(Player player, NobStats nobStats) {
        this(player, nobStats, WheresTheBoneStats.empty(), List.of(), List.of());
    }

    public ProfileStatsResponse(Player player, NobStats nobStats, WheresTheBoneStats wheresTheBoneStats) {
        this(player, nobStats, wheresTheBoneStats, List.of(), List.of());
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

    public record Achievement(
            String code,
            int progress,
            int target,
            boolean unlocked,
            String unlockedAt,
            List<String> rewardAvatarUrls
    ) {
    }

    public record Avatar(
            String key,
            String url,
            boolean unlocked,
            boolean selected,
            String source,
            String achievementCode
    ) {
    }

    public record WheresTheBoneStats(
            long totalMatches,
            long matchesWon,
            double winRate,
            FactionStats whiteDog,
            FactionStats yardTeam,
            FactionStats boneThiefTeam,
            int elo,
            int highestElo
    ) {
        public static WheresTheBoneStats empty() {
            FactionStats emptyFaction = new FactionStats(0, 0, 0.0);
            return new WheresTheBoneStats(0, 0, 0.0, emptyFaction, emptyFaction, emptyFaction, 5000, 5000);
        }
    }
}
