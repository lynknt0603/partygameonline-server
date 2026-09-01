package com.partygameonline.ranking.application;

import com.partygameonline.game.nob.NobGameManifest;
import com.partygameonline.game.nob.infrastructure.NobGameRoundEntity;
import com.partygameonline.game.nob.infrastructure.NobGameRoundJpaRepository;
import com.partygameonline.game.notinmypot.NotInMyPotGameManifest;
import com.partygameonline.game.notinmypot.domain.NotInMyPotRole;
import com.partygameonline.game.wheresthebone.WheresTheBoneGameManifest;
import com.partygameonline.history.infrastructure.MatchPlayerEntity;
import com.partygameonline.history.infrastructure.MatchPlayerJpaRepository;
import com.partygameonline.common.avatar.AvatarCatalog;
import com.partygameonline.ranking.api.dto.RankingResponse;
import com.partygameonline.ranking.infrastructure.UserGameStatisticEntity;
import com.partygameonline.ranking.infrastructure.UserGameStatisticJpaRepository;
import com.partygameonline.session.domain.PlayerPrincipal;
import com.partygameonline.user.infrastructure.UserJpaRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RankingService {

    private static final String DEFAULT_GAME = NobGameManifest.ID;
    private static final String DEFAULT_SORT = "highestElo";
    private static final String WHITE_DOG_GROUP = "WHITE_DOG";
    private static final String YARD_TEAM_GROUP = "YARD_TEAM";
    private static final String BONE_THIEF_TEAM_GROUP = "BONE_THIEF_TEAM";
    private static final int DEFAULT_SIZE = 7;
    private static final int MAX_SIZE = 50;

    private final UserGameStatisticJpaRepository statisticRepository;
    private final MatchPlayerJpaRepository matchPlayerRepository;
    private final NobGameRoundJpaRepository roundRepository;
    private final UserJpaRepository userRepository;

    public RankingService(
            UserGameStatisticJpaRepository statisticRepository,
            MatchPlayerJpaRepository matchPlayerRepository,
            NobGameRoundJpaRepository roundRepository,
            UserJpaRepository userRepository
    ) {
        this.statisticRepository = statisticRepository;
        this.matchPlayerRepository = matchPlayerRepository;
        this.roundRepository = roundRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public boolean isTopOne(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return false;
        }
        Map<String, List<UserGameStatisticEntity>> byGame = statisticRepository.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(UserGameStatisticEntity::getGameCode));
        return byGame.values().stream().anyMatch(statistics -> {
            int bestElo = statistics.stream()
                    .mapToInt(UserGameStatisticEntity::getHighestEloForGame)
                    .max().orElse(Integer.MIN_VALUE);
            int bestWins = statistics.stream()
                    .mapToInt(UserGameStatisticEntity::getTotalWin)
                    .max().orElse(Integer.MIN_VALUE);
            return statistics.stream().anyMatch(statistic -> playerId.equals(statistic.getUserId())
                    && (statistic.getHighestEloForGame() == bestElo || statistic.getTotalWin() == bestWins));
        });
    }

    @Transactional(readOnly = true)
    public RankingResponse getRanking(
            String gameId,
            String sort,
            String bloodline,
            Integer page,
            Integer size,
            PlayerPrincipal principal
    ) {
        return getRanking(gameId, sort, bloodline, null, page, size, principal);
    }

    @Transactional(readOnly = true)
    public RankingResponse getRanking(
            String gameId,
            String sort,
            String bloodline,
            String role,
            Integer page,
            Integer size,
            PlayerPrincipal principal
    ) {
        String normalizedGame = gameId == null || gameId.isBlank() ? DEFAULT_GAME : gameId.trim();
        boolean nobRanking = NobGameManifest.ID.equals(normalizedGame);
        boolean notInMyPotRanking = NotInMyPotGameManifest.ID.equals(normalizedGame);
        boolean wheresTheBoneRanking = WheresTheBoneGameManifest.ID.equals(normalizedGame);
        String normalizedSort = normalizeSort(sort);
        if (nobRanking && "roleWins".equals(normalizedSort)) {
            normalizedSort = "bloodlineWins";
        }
        if (!nobRanking && "bloodlineWins".equals(normalizedSort)) {
            normalizedSort = DEFAULT_SORT;
        }
        if (!wheresTheBoneRanking && "roleWins".equals(normalizedSort)) {
            normalizedSort = DEFAULT_SORT;
        }
        if (!notInMyPotRanking && isNotInMyPotFactionSort(normalizedSort)) {
            normalizedSort = DEFAULT_SORT;
        }
        String normalizedBloodline = nobRanking ? normalizeBloodline(bloodline) : null;
        String normalizedRole = wheresTheBoneRanking ? normalizeRole(role) : null;
        boolean vegetarianRanking = "vegetarianWins".equals(normalizedSort);
        boolean meatEaterRanking = "meatEaterWins".equals(normalizedSort);
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null ? DEFAULT_SIZE : Math.min(Math.max(size, 1), MAX_SIZE);

        List<UserGameStatisticEntity> statistics = statisticRepository.findByGameCode(normalizedGame);
        if (statistics.isEmpty()) {
            return new RankingResponse(
                    normalizedGame,
                    normalizedSort,
                    normalizedBloodline,
                    normalizedRole,
                    List.of(),
                    List.of(),
                    null,
                    pageNumber,
                    pageSize,
                    0,
                    0
            );
        }

        List<String> playerIds = statistics.stream().map(UserGameStatisticEntity::getUserId).toList();
        Map<String, String> displayNames = displayNames(playerIds);
        Map<String, String> usernames = usernames(playerIds);
        Map<String, String> avatarUrls = avatarUrls(playerIds);
        Map<String, BloodlineSummary> bloodlineSummaries = nobRanking
                ? bloodlineSummaries(playerIds)
                : Map.of();
        Map<String, RoleSummary> roleSummaries = wheresTheBoneRanking
                ? roleSummaries(normalizedGame, playerIds)
                : Map.of();
        Map<String, NotInMyPotFactionSummary> notInMyPotFactionSummaries = notInMyPotRanking
                ? notInMyPotFactionSummaries(normalizedGame, playerIds)
                : Map.of();
        List<RankedPlayer> ranked = statistics.stream()
                .map(statistic -> toRankedPlayer(
                        statistic,
                        displayNames,
                        usernames,
                        avatarUrls,
                        bloodlineSummaries,
                        roleSummaries,
                        notInMyPotFactionSummaries
                ))
                .filter(player -> normalizedBloodline == null || player.bloodlineSummary().played(normalizedBloodline) > 0)
                .filter(player -> normalizedRole == null || player.roleSummary().played(normalizedRole) > 0)
                .filter(player -> !vegetarianRanking
                        || player.notInMyPotFactionSummary().played(NotInMyPotRole.VEGETARIAN) > 0)
                .filter(player -> !meatEaterRanking
                        || player.notInMyPotFactionSummary().played(NotInMyPotRole.MEAT_EATER) > 0)
                .sorted(comparator(normalizedSort, normalizedBloodline, normalizedRole))
                .toList();

        List<RankingResponse.RankingEntry> allEntries = new ArrayList<>(ranked.size());
        for (int index = 0; index < ranked.size(); index++) {
            allEntries.add(ranked.get(index).toResponse(index + 1, normalizedBloodline, normalizedRole));
        }

        List<RankingResponse.RankingEntry> podium = allEntries.stream().limit(3).toList();
        int start = 3 + pageNumber * pageSize;
        int end = Math.min(start + pageSize, allEntries.size());
        List<RankingResponse.RankingEntry> entries = start >= allEntries.size()
                ? List.of()
                : allEntries.subList(start, end);
        RankingResponse.RankingEntry me = principal == null
                ? null
                : allEntries.stream()
                        .filter(entry -> principal.playerId().equals(entry.playerId()))
                        .findFirst()
                        .orElse(null);
        int totalPages = ranked.size() <= 3
                ? 0
                : (int) Math.ceil((ranked.size() - 3) / (double) pageSize);
        return new RankingResponse(
                normalizedGame,
                normalizedSort,
                normalizedBloodline,
                normalizedRole,
                podium,
                entries,
                me,
                pageNumber,
                pageSize,
                ranked.size(),
                totalPages
        );
    }

    private Map<String, String> displayNames(List<String> playerIds) {
        Map<String, String> result = new HashMap<>();
        matchPlayerRepository.findByPlayerIdInOrderByCreatedAtDescIdAsc(playerIds)
                .forEach(player -> result.putIfAbsent(player.getPlayerId(), player.getDisplayName()));
        playerIds.forEach(playerId -> result.putIfAbsent(playerId, playerId));
        return result;
    }

    private Map<String, String> usernames(List<String> playerIds) {
        if (userRepository == null) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        userRepository.findByUserKeyIn(playerIds).forEach(user -> {
            if (user.getUsername() != null && !user.getUsername().isBlank()) {
                result.put(user.getUserKey(), user.getUsername());
            }
        });
        return result;
    }

    private Map<String, String> avatarUrls(List<String> playerIds) {
        Map<String, String> result = new HashMap<>();
        playerIds.forEach(playerId -> result.put(playerId, AvatarCatalog.DEFAULT_URL));
        if (userRepository != null) {
            userRepository.findByUserKeyIn(playerIds).forEach(user ->
                    result.put(user.getUserKey(), AvatarCatalog.urlForKey(user.getAvatarKey()))
            );
        }
        return result;
    }

    private Map<String, BloodlineSummary> bloodlineSummaries(List<String> playerIds) {
        Map<String, BloodlineSummary> result = new HashMap<>();
        for (String playerId : playerIds) {
            result.put(playerId, new BloodlineSummary());
        }
        roundRepository.findByPlayerIdInOrderByCreatedAtDescIdAsc(playerIds)
                .forEach(round -> result.computeIfAbsent(round.getPlayerId(), ignored -> new BloodlineSummary())
                        .record(round));
        return result;
    }

    private Map<String, RoleSummary> roleSummaries(String gameId, List<String> playerIds) {
        Map<String, RoleSummary> result = new HashMap<>();
        for (String playerId : playerIds) {
            result.put(playerId, new RoleSummary());
        }
        matchPlayerRepository.findByGameIdAndPlayerIdInOrderByCreatedAtDescIdAsc(gameId, playerIds)
                .forEach(player -> result.computeIfAbsent(player.getPlayerId(), ignored -> new RoleSummary())
                        .record(player));
        return result;
    }

    private Map<String, NotInMyPotFactionSummary> notInMyPotFactionSummaries(
            String gameId,
            List<String> playerIds
    ) {
        Map<String, NotInMyPotFactionSummary> result = new HashMap<>();
        for (String playerId : playerIds) {
            result.put(playerId, new NotInMyPotFactionSummary());
        }
        matchPlayerRepository.findByGameIdAndPlayerIdInOrderByCreatedAtDescIdAsc(gameId, playerIds)
                .forEach(player -> result
                        .computeIfAbsent(player.getPlayerId(), ignored -> new NotInMyPotFactionSummary())
                        .record(player));
        return result;
    }

    private RankedPlayer toRankedPlayer(
            UserGameStatisticEntity statistic,
            Map<String, String> displayNames,
            Map<String, String> usernames,
            Map<String, String> avatarUrls,
            Map<String, BloodlineSummary> bloodlineSummaries,
            Map<String, RoleSummary> roleSummaries,
            Map<String, NotInMyPotFactionSummary> notInMyPotFactionSummaries
    ) {
        return new RankedPlayer(
                statistic.getUserId(),
                usernames.get(statistic.getUserId()),
                displayNames.getOrDefault(statistic.getUserId(), statistic.getUserId()),
                avatarUrls.getOrDefault(statistic.getUserId(), AvatarCatalog.DEFAULT_URL),
                statistic.getEloForGame(),
                statistic.getHighestEloForGame(),
                statistic.getTotalWin(),
                statistic.getTotalMatch(),
                bloodlineSummaries.getOrDefault(statistic.getUserId(), new BloodlineSummary()),
                roleSummaries.getOrDefault(statistic.getUserId(), new RoleSummary()),
                notInMyPotFactionSummaries.getOrDefault(statistic.getUserId(), new NotInMyPotFactionSummary())
        );
    }

    private static Comparator<RankedPlayer> comparator(String sort, String bloodline, String role) {
        Comparator<RankedPlayer> comparator;
        if ("wins".equals(sort)) {
            comparator = Comparator.comparingInt((RankedPlayer player) -> player.totalWins()).reversed()
                    .thenComparing(Comparator.comparingInt(RankedPlayer::highestElo).reversed());
        } else if ("bloodlineWins".equals(sort)) {
            comparator = Comparator.comparingInt((RankedPlayer player) -> player.bloodlineSummary().wins(bloodline))
                    .reversed()
                    .thenComparing(Comparator.comparingInt(RankedPlayer::highestElo).reversed());
        } else if ("roleWins".equals(sort)) {
            comparator = Comparator.comparingInt((RankedPlayer player) -> player.roleSummary().wins(role))
                    .reversed()
                    .thenComparing(Comparator.comparingInt(RankedPlayer::highestElo).reversed());
        } else if ("vegetarianWins".equals(sort)) {
            comparator = notInMyPotFactionComparator(NotInMyPotRole.VEGETARIAN);
        } else if ("meatEaterWins".equals(sort)) {
            comparator = notInMyPotFactionComparator(NotInMyPotRole.MEAT_EATER);
        } else {
            comparator = Comparator.comparingInt(RankedPlayer::highestElo).reversed()
                    .thenComparing(Comparator.comparingInt(RankedPlayer::elo).reversed());
        }
        return comparator.thenComparing(Comparator.comparingInt(RankedPlayer::totalWins).reversed())
                .thenComparing(RankedPlayer::playerId);
    }

    private static Comparator<RankedPlayer> notInMyPotFactionComparator(NotInMyPotRole faction) {
        return Comparator.comparingInt(
                        (RankedPlayer player) -> player.notInMyPotFactionSummary().wins(faction)
                ).reversed()
                .thenComparing(Comparator.comparingDouble(
                        (RankedPlayer player) -> player.notInMyPotFactionSummary().winRate(faction)
                ).reversed())
                .thenComparing(Comparator.comparingInt(RankedPlayer::highestElo).reversed());
    }

    private static String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_SORT;
        }
        return switch (sort.trim().toLowerCase(Locale.ROOT)) {
            case "wins", "totalwins", "matcheswon" -> "wins";
            case "bloodlinewins", "roundwins" -> "bloodlineWins";
            case "rolewins" -> "roleWins";
            case "vegetarianwins", "vegetarianwinrate" -> "vegetarianWins";
            case "meateaterwins", "meateaterwinrate" -> "meatEaterWins";
            default -> DEFAULT_SORT;
        };
    }

    private static boolean isNotInMyPotFactionSort(String sort) {
        return "vegetarianWins".equals(sort) || "meatEaterWins".equals(sort);
    }

    private static String normalizeBloodline(String bloodline) {
        if (bloodline == null || bloodline.isBlank() || "all".equalsIgnoreCase(bloodline)) {
            return null;
        }
        return switch (bloodline.trim().toUpperCase(Locale.ROOT)) {
            case "VAMPIRE" -> "VAMPIRE";
            case "WEREWOLF" -> "WEREWOLF";
            case "HALFBLOOD", "HALF_BLOOD", "SORCERESS" -> "HALFBLOOD";
            default -> null;
        };
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank() || "all".equalsIgnoreCase(role)) {
            return null;
        }
        return switch (role.trim().toUpperCase(Locale.ROOT)) {
            case "WHITE_DOG", "WHITEDOG" -> WHITE_DOG_GROUP;
            case "YARD_TEAM", "YARD_DOG", "YARDDOG" -> YARD_TEAM_GROUP;
            case "BONE_THIEF_TEAM", "BONE_THIEF", "BONETHIEF",
                    "PACKMATE", "CURSED_DOG", "CURSEDDOG" -> BONE_THIEF_TEAM_GROUP;
            default -> null;
        };
    }

    private record RankedPlayer(
            String playerId,
            String username,
            String displayName,
            String avatarUrl,
            int elo,
            int highestElo,
            int totalWins,
            int totalMatches,
            BloodlineSummary bloodlineSummary,
            RoleSummary roleSummary,
            NotInMyPotFactionSummary notInMyPotFactionSummary
    ) {
        private RankingResponse.RankingEntry toResponse(int rank, String bloodline, String role) {
            String favorite = bloodline == null ? bloodlineSummary.favorite() : bloodline;
            String favoriteRole = role == null ? roleSummary.favorite() : role;
            return new RankingResponse.RankingEntry(
                    rank,
                    playerId,
                    username,
                    displayName,
                    avatarUrl,
                    elo,
                    highestElo,
                    totalWins,
                    totalMatches,
                    favorite,
                    bloodlineSummary.wins(bloodline),
                    favoriteRole,
                    roleSummary.wins(role),
                    notInMyPotFactionSummary.played(NotInMyPotRole.VEGETARIAN),
                    notInMyPotFactionSummary.wins(NotInMyPotRole.VEGETARIAN),
                    notInMyPotFactionSummary.winRate(NotInMyPotRole.VEGETARIAN),
                    notInMyPotFactionSummary.played(NotInMyPotRole.MEAT_EATER),
                    notInMyPotFactionSummary.wins(NotInMyPotRole.MEAT_EATER),
                    notInMyPotFactionSummary.winRate(NotInMyPotRole.MEAT_EATER)
            );
        }
    }

    private static final class BloodlineSummary {
        private final Map<String, Integer> played = new LinkedHashMap<>();
        private final Map<String, Integer> wins = new LinkedHashMap<>();

        private void record(NobGameRoundEntity round) {
            String bloodline = normalizeBloodline(round.getBloodline());
            if (bloodline == null) {
                return;
            }
            played.merge(bloodline, 1, Integer::sum);
            if ("WIN".equalsIgnoreCase(round.getResult())) {
                wins.merge(bloodline, 1, Integer::sum);
            }
        }

        private int played(String bloodline) {
            return played.getOrDefault(bloodline, 0);
        }

        private int wins(String bloodline) {
            if (bloodline != null) {
                return wins.getOrDefault(bloodline, 0);
            }
            return wins.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        }

        private String favorite() {
            return wins.entrySet().stream()
                    .max(Map.Entry.<String, Integer>comparingByValue()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }
    }

    private static final class RoleSummary {
        private final Map<String, Integer> played = new LinkedHashMap<>();
        private final Map<String, Integer> wins = new LinkedHashMap<>();

        private void record(MatchPlayerEntity player) {
            String role = normalizeRole(player.getRole());
            if (role == null) {
                return;
            }
            played.merge(role, 1, Integer::sum);
            if ("WIN".equalsIgnoreCase(player.getResult())) {
                wins.merge(role, 1, Integer::sum);
            }
        }

        private int played(String role) {
            return played.getOrDefault(role, 0);
        }

        private int wins(String role) {
            if (role != null) {
                return wins.getOrDefault(role, 0);
            }
            return wins.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        }

        private String favorite() {
            return wins.entrySet().stream()
                    .max(Map.Entry.<String, Integer>comparingByValue()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }
    }

    private static final class NotInMyPotFactionSummary {
        private final Map<NotInMyPotRole, Integer> played = new LinkedHashMap<>();
        private final Map<NotInMyPotRole, Integer> wins = new LinkedHashMap<>();

        private void record(MatchPlayerEntity player) {
            NotInMyPotRole faction = normalizeNotInMyPotFaction(player.getRole());
            if (faction == null) {
                return;
            }
            played.merge(faction, 1, Integer::sum);
            if ("WIN".equalsIgnoreCase(player.getResult())) {
                wins.merge(faction, 1, Integer::sum);
            }
        }

        private int played(NotInMyPotRole faction) {
            return played.getOrDefault(faction, 0);
        }

        private int wins(NotInMyPotRole faction) {
            return wins.getOrDefault(faction, 0);
        }

        private double winRate(NotInMyPotRole faction) {
            int total = played(faction);
            return total == 0 ? 0.0 : Math.round(wins(faction) * 1000.0 / total) / 10.0;
        }
    }

    private static NotInMyPotRole normalizeNotInMyPotFaction(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        return switch (role.trim().toUpperCase(Locale.ROOT)) {
            case "VEGETARIAN" -> NotInMyPotRole.VEGETARIAN;
            case "MEAT_EATER", "MEATEATER" -> NotInMyPotRole.MEAT_EATER;
            default -> null;
        };
    }
}
