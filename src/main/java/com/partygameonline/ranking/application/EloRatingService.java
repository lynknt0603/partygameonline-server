package com.partygameonline.ranking.application;

import com.partygameonline.game.nob.domain.NobGameState;
import com.partygameonline.ranking.infrastructure.UserGameStatisticEntity;
import com.partygameonline.ranking.infrastructure.UserGameStatisticJpaRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Custom multiplayer ELO rules used by the games platform. */
@Service
public class EloRatingService {

    public static final int DEFAULT_ELO = 5000;
    public static final int MIN_ELO = 0;

    private static final Logger log = LoggerFactory.getLogger(EloRatingService.class);
    private static final String NOB_GAME = "night-of-bloodlines";

    private final UserGameStatisticJpaRepository statisticRepository;

    public EloRatingService(UserGameStatisticJpaRepository statisticRepository) {
        this.statisticRepository = statisticRepository;
    }

    public int calculateEloDelta(int playerElo, double roomAverageElo, boolean winner) {
        double difference = Math.abs(playerElo - roomAverageElo);
        boolean belowAverage = playerElo < roomAverageElo;
        if (difference <= 100) {
            return winner ? 50 : -50;
        }
        if (difference <= 300) {
            return winner ? (belowAverage ? 55 : 45) : (belowAverage ? -45 : -55);
        }
        if (difference <= 500) {
            return winner ? (belowAverage ? 60 : 40) : (belowAverage ? -40 : -60);
        }
        if (difference <= 800) {
            return winner ? (belowAverage ? 65 : 35) : (belowAverage ? -35 : -65);
        }
        if (difference <= 1200) {
            return winner ? (belowAverage ? 70 : 30) : (belowAverage ? -30 : -70);
        }
        if (difference <= 1800) {
            return winner ? (belowAverage ? 75 : 25) : (belowAverage ? -25 : -75);
        }
        return winner ? (belowAverage ? 80 : 20) : (belowAverage ? -20 : -80);
    }

    @Transactional
    public EloMatchResult applyRound(String gameCode, List<PlayerOutcome> outcomes) {
        return applyRatings(gameCode, outcomes, false);
    }

    /**
     * Calculates the round change without writing the database. NOB keeps this
     * ledger in its live game state so every round can show a signed change,
     * while the completed match still commits all rating changes atomically.
     */
    @Transactional(readOnly = true)
    public EloMatchResult previewRound(
            String gameCode,
            List<PlayerOutcome> outcomes,
            Map<String, Integer> simulatedRatings
    ) {
        List<PlayerOutcome> distinct = distinctOutcomes(outcomes);
        if (distinct.isEmpty()) {
            return new EloMatchResult(Map.of(), 0);
        }
        Map<String, Integer> ratings = new LinkedHashMap<>();
        for (PlayerOutcome outcome : distinct) {
            int rating = simulatedRatings != null && simulatedRatings.containsKey(outcome.playerId())
                    ? simulatedRatings.get(outcome.playerId())
                    : statisticRepository.findByUserIdAndGameCode(outcome.playerId(), gameCode)
                            .map(UserGameStatisticEntity::getElo)
                            .orElse(DEFAULT_ELO);
            ratings.put(outcome.playerId(), Math.max(MIN_ELO, rating));
        }
        double roomAverage = ratings.values().stream().mapToInt(Integer::intValue).average().orElse(DEFAULT_ELO);
        Map<String, EloChange> changes = new LinkedHashMap<>();
        for (PlayerOutcome outcome : distinct) {
            int oldElo = ratings.get(outcome.playerId());
            int requestedDelta = calculateEloDelta(oldElo, roomAverage, outcome.winner());
            int newElo = Math.max(MIN_ELO, oldElo + requestedDelta);
            changes.put(outcome.playerId(), new EloChange(
                    outcome.playerId(),
                    outcome.winner(),
                    oldElo,
                    newElo - oldElo,
                    newElo
            ));
        }
        return new EloMatchResult(changes, roomAverage);
    }

    /**
     * Applies one complete match for games that do not expose round results.
     * NOB uses {@link #completeNobMatch(List, Set, NobGameState)} so totalMatch
     * is still incremented once even though its rating is shown round-by-round.
     */
    @Transactional
    public EloMatchResult applyMatch(String gameCode, List<PlayerOutcome> outcomes) {
        return applyRatings(gameCode, outcomes, true);
    }

    @Transactional
    public EloMatchResult completeNobMatch(
            List<String> playerIds,
            Set<String> winners,
            NobGameState state
    ) {
        List<String> distinctPlayerIds = distinctIds(playerIds);
        Set<String> winnerIds = winners == null ? Set.of() : Set.copyOf(winners);
        if (state == null || state.getCompletedRounds().isEmpty()) {
            return applyMatch(
                    NOB_GAME,
                    distinctPlayerIds.stream().map(id -> new PlayerOutcome(id, winnerIds.contains(id))).toList()
            );
        }

        Map<String, UserGameStatisticEntity> stats = loadLocked(NOB_GAME, distinctPlayerIds);
        double roomAverage = roomAverage(stats);
        Map<String, Integer> firstRoundElo = new LinkedHashMap<>();
        for (var round : state.getCompletedRounds()) {
            state.getRoundEloChanges(round.roundNumber()).forEach((playerId, change) ->
                    firstRoundElo.putIfAbsent(playerId, change.oldElo())
            );
        }
        int loserLossTotal = state.getCompletedRounds().stream()
                .flatMap(round -> round.players().stream())
                .filter(snapshot -> !winnerIds.contains(snapshot.playerId()))
                .mapToInt(snapshot -> Math.max(0, -nullToZero(snapshot.eloDelta())))
                .sum();
        Map<String, Integer> targetRatings = new LinkedHashMap<>();
        for (String playerId : distinctPlayerIds) {
            int target = stats.get(playerId).getElo();
            for (var round : state.getCompletedRounds()) {
                for (var snapshot : round.players()) {
                    if (playerId.equals(snapshot.playerId())) {
                        target = Math.max(MIN_ELO, target + nullToZero(snapshot.eloDelta()));
                    }
                }
            }
            if (winnerIds.contains(playerId)) {
                target = Math.max(MIN_ELO, target + loserLossTotal);
            }
            targetRatings.put(playerId, target);
        }
        Map<String, EloChange> changes = new LinkedHashMap<>();
        for (String playerId : distinctPlayerIds) {
            UserGameStatisticEntity statistic = stats.get(playerId);
            int oldElo = statistic.getElo();
            int finalElo = targetRatings.getOrDefault(playerId, oldElo);
            if (finalElo != oldElo) {
                statistic.applyRatingDelta(finalElo - oldElo);
            }
            statistic.completeMatch(winnerIds.contains(playerId));
            int aggregateOldElo = firstRoundElo.getOrDefault(playerId, oldElo);
            changes.put(playerId, new EloChange(
                    playerId,
                    winnerIds.contains(playerId),
                    aggregateOldElo,
                    statistic.getElo() - aggregateOldElo,
                    statistic.getElo()
            ));
        }
        statisticRepository.saveAll(stats.values());
        log.info(
                "NOB ELO completed players={} winners={} loserLossBonus={}",
                distinctPlayerIds.size(),
                winnerIds.size(),
                loserLossTotal
        );
        return new EloMatchResult(changes, roomAverage);
    }

    private EloMatchResult applyRatings(
            String gameCode,
            List<PlayerOutcome> rawOutcomes,
            boolean completeMatch
    ) {
        List<PlayerOutcome> outcomes = distinctOutcomes(rawOutcomes);
        if (outcomes.isEmpty()) {
            return new EloMatchResult(Map.of(), 0);
        }
        Map<String, UserGameStatisticEntity> stats = loadLocked(
                gameCode,
                outcomes.stream().map(PlayerOutcome::playerId).toList()
        );
        double roomAverage = roomAverage(stats);
        Map<String, EloChange> changes = new LinkedHashMap<>();
        for (PlayerOutcome outcome : outcomes) {
            UserGameStatisticEntity statistic = stats.get(outcome.playerId());
            int oldElo = statistic.getElo();
            int delta = calculateEloDelta(oldElo, roomAverage, outcome.winner());
            statistic.applyRatingDelta(delta);
            if (completeMatch) {
                statistic.completeMatch(outcome.winner());
            }
            changes.put(outcome.playerId(), new EloChange(
                    outcome.playerId(),
                    outcome.winner(),
                    oldElo,
                    statistic.getElo() - oldElo,
                    statistic.getElo()
            ));
        }
        statisticRepository.saveAll(stats.values());
        log.info(
                "ELO {} gameCode={} players={} roomAverage={}",
                completeMatch ? "match" : "round",
                gameCode,
                outcomes.size(),
                roomAverage
        );
        return new EloMatchResult(changes, roomAverage);
    }

    private Map<String, UserGameStatisticEntity> loadLocked(String gameCode, List<String> playerIds) {
        Map<String, UserGameStatisticEntity> result = new LinkedHashMap<>();
        for (String playerId : distinctIds(playerIds)) {
            UserGameStatisticEntity statistic = statisticRepository
                    .findByUserIdAndGameCodeForUpdate(playerId, gameCode)
                    .orElseGet(() -> UserGameStatisticEntity.newStatistic(playerId, gameCode));
            result.put(playerId, statistic);
        }
        return result;
    }

    private static double roomAverage(Map<String, UserGameStatisticEntity> stats) {
        return stats.values().stream()
                .mapToInt(UserGameStatisticEntity::getElo)
                .average()
                .orElse(DEFAULT_ELO);
    }

    private static List<PlayerOutcome> distinctOutcomes(List<PlayerOutcome> rawOutcomes) {
        if (rawOutcomes == null) {
            return List.of();
        }
        Map<String, PlayerOutcome> unique = new LinkedHashMap<>();
        for (PlayerOutcome outcome : rawOutcomes) {
            if (outcome != null && outcome.playerId() != null && !outcome.playerId().isBlank()) {
                unique.putIfAbsent(outcome.playerId(), outcome);
            }
        }
        return List.copyOf(unique.values());
    }

    private static List<String> distinctIds(List<String> rawIds) {
        if (rawIds == null) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(rawIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .toList()));
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    public record PlayerOutcome(String playerId, boolean winner) {
    }

    public record EloChange(
            String playerId,
            boolean winner,
            int oldElo,
            int eloDelta,
            int newElo
    ) {
    }

    public record EloMatchResult(Map<String, EloChange> changes, double roomAverageElo) {
        public EloMatchResult {
            changes = changes == null ? Map.of() : Map.copyOf(changes);
        }
    }
}
