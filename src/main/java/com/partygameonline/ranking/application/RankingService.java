package com.partygameonline.ranking.application;

import com.partygameonline.game.nob.NobGameManifest;
import com.partygameonline.game.nob.infrastructure.NobGameRoundEntity;
import com.partygameonline.game.nob.infrastructure.NobGameRoundJpaRepository;
import com.partygameonline.history.infrastructure.MatchPlayerEntity;
import com.partygameonline.history.infrastructure.MatchPlayerJpaRepository;
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
    public RankingResponse getRanking(
            String gameId,
            String sort,
            String bloodline,
            Integer page,
            Integer size,
            PlayerPrincipal principal
    ) {
        String normalizedGame = gameId == null || gameId.isBlank() ? DEFAULT_GAME : gameId.trim();
        String normalizedSort = normalizeSort(sort);
        String normalizedBloodline = normalizeBloodline(bloodline);
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null ? DEFAULT_SIZE : Math.min(Math.max(size, 1), MAX_SIZE);

        List<UserGameStatisticEntity> statistics = statisticRepository
                .findByGameCodeOrderByHighestEloDescEloNobDescTotalWinDescUserIdAsc(normalizedGame);
        if (statistics.isEmpty()) {
            return new RankingResponse(
                    normalizedGame,
                    normalizedSort,
                    normalizedBloodline,
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
        Map<String, BloodlineSummary> bloodlineSummaries = bloodlineSummaries(playerIds);
        List<RankedPlayer> ranked = statistics.stream()
                .map(statistic -> toRankedPlayer(statistic, displayNames, usernames, bloodlineSummaries))
                .filter(player -> normalizedBloodline == null || player.bloodlineSummary().played(normalizedBloodline) > 0)
                .sorted(comparator(normalizedSort, normalizedBloodline))
                .toList();

        List<RankingResponse.RankingEntry> allEntries = new ArrayList<>(ranked.size());
        for (int index = 0; index < ranked.size(); index++) {
            allEntries.add(ranked.get(index).toResponse(index + 1, normalizedBloodline));
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

    private RankedPlayer toRankedPlayer(
            UserGameStatisticEntity statistic,
            Map<String, String> displayNames,
            Map<String, String> usernames,
            Map<String, BloodlineSummary> bloodlineSummaries
    ) {
        return new RankedPlayer(
                statistic.getUserId(),
                usernames.get(statistic.getUserId()),
                displayNames.getOrDefault(statistic.getUserId(), statistic.getUserId()),
                statistic.getEloNob(),
                statistic.getHighestElo(),
                statistic.getTotalWin(),
                statistic.getTotalMatch(),
                bloodlineSummaries.getOrDefault(statistic.getUserId(), new BloodlineSummary())
        );
    }

    private static Comparator<RankedPlayer> comparator(String sort, String bloodline) {
        Comparator<RankedPlayer> comparator;
        if ("wins".equals(sort)) {
            comparator = Comparator.comparingInt((RankedPlayer player) -> player.totalWins()).reversed()
                    .thenComparing(Comparator.comparingInt(RankedPlayer::highestElo).reversed());
        } else if ("bloodlineWins".equals(sort)) {
            comparator = Comparator.comparingInt((RankedPlayer player) -> player.bloodlineSummary().wins(bloodline))
                    .reversed()
                    .thenComparing(Comparator.comparingInt(RankedPlayer::highestElo).reversed());
        } else {
            comparator = Comparator.comparingInt(RankedPlayer::highestElo).reversed()
                    .thenComparing(Comparator.comparingInt(RankedPlayer::elo).reversed());
        }
        return comparator.thenComparing(Comparator.comparingInt(RankedPlayer::totalWins).reversed())
                .thenComparing(RankedPlayer::playerId);
    }

    private static String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_SORT;
        }
        return switch (sort.trim().toLowerCase(Locale.ROOT)) {
            case "wins", "totalwins", "matcheswon" -> "wins";
            case "bloodlinewins", "rolewins", "roundwins" -> "bloodlineWins";
            default -> DEFAULT_SORT;
        };
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

    private record RankedPlayer(
            String playerId,
            String username,
            String displayName,
            int elo,
            int highestElo,
            int totalWins,
            int totalMatches,
            BloodlineSummary bloodlineSummary
    ) {
        private RankingResponse.RankingEntry toResponse(int rank, String bloodline) {
            String favorite = bloodline == null ? bloodlineSummary.favorite() : bloodline;
            return new RankingResponse.RankingEntry(
                    rank,
                    playerId,
                    username,
                    displayName,
                    elo,
                    highestElo,
                    totalWins,
                    totalMatches,
                    favorite,
                    bloodlineSummary.wins(bloodline)
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
            return wins.values().stream().mapToInt(Integer::intValue).sum();
        }

        private String favorite() {
            return wins.entrySet().stream()
                    .max(Map.Entry.<String, Integer>comparingByValue()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }
    }
}
