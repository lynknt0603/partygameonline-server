package com.partygameonline.ranking.application;

import com.partygameonline.ranking.infrastructure.UserGameStatisticEntity;
import com.partygameonline.ranking.infrastructure.UserGameStatisticJpaRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final UserGameStatisticJpaRepository statisticRepository;
    private final Map<String, GameEloPolicy> policies;
    private final GameEloPolicy fallbackPolicy;

    /** Convenience constructor retained for unit tests and lightweight callers. */
    public EloRatingService(UserGameStatisticJpaRepository statisticRepository) {
        this(statisticRepository, List.of(
                new NobGameEloPolicy(),
                new NotInMyPotGameEloPolicy(),
                new WheresTheBoneGameEloPolicy(),
                new DefaultGameEloPolicy()
        ));
    }

    @Autowired
    public EloRatingService(
            UserGameStatisticJpaRepository statisticRepository,
            List<GameEloPolicy> gamePolicies
    ) {
        this.statisticRepository = statisticRepository;
        Map<String, GameEloPolicy> indexed = new HashMap<>();
        GameEloPolicy fallback = null;
        for (GameEloPolicy policy : gamePolicies == null ? List.<GameEloPolicy>of() : gamePolicies) {
            if (policy == null) {
                continue;
            }
            if (policy.gameCode() == null || policy.gameCode().isBlank()) {
                if (fallback != null) {
                    throw new IllegalStateException("Only one fallback ELO policy may be registered");
                }
                fallback = policy;
            } else {
                if (indexed.put(policy.gameCode(), policy) != null) {
                    throw new IllegalStateException("Duplicate ELO policy for game: " + policy.gameCode());
                }
            }
        }
        this.policies = Map.copyOf(indexed);
        this.fallbackPolicy = fallback == null ? new DefaultGameEloPolicy() : fallback;
    }

    public int calculateEloDelta(int playerElo, double roomAverageElo, boolean winner) {
        return DefaultGameEloPolicy.calculateEloDelta(playerElo, roomAverageElo, winner);
    }

    @Transactional
    public EloMatchResult applyRound(String gameCode, List<PlayerOutcome> outcomes) {
        List<PlayerOutcome> distinct = distinctOutcomes(outcomes);
        if (distinct.isEmpty()) {
            return new EloMatchResult(Map.of(), DEFAULT_ELO);
        }
        Map<String, UserGameStatisticEntity> stats = loadLocked(
                gameCode,
                distinct.stream().map(PlayerOutcome::playerId).toList()
        );
        EloMatchResult result = policyFor(gameCode).calculateRound(distinct, ratings(stats));
        applyResult(gameCode, stats, result, false);
        return result;
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
                            .map(UserGameStatisticEntity::getEloForGame)
                            .orElse(DEFAULT_ELO);
            ratings.put(outcome.playerId(), Math.max(MIN_ELO, rating));
        }
        return policyFor(gameCode).calculateRound(distinct, ratings);
    }

    /**
     * Applies one complete match through the policy registered for its game.
     */
    @Transactional
    public EloMatchResult applyMatch(String gameCode, List<PlayerOutcome> outcomes) {
        List<PlayerOutcome> distinct = distinctOutcomes(outcomes);
        if (distinct.isEmpty()) {
            return new EloMatchResult(Map.of(), DEFAULT_ELO);
        }
        return completeMatch(gameCode, distinct.stream().map(PlayerOutcome::playerId).toList(),
                distinct.stream().filter(PlayerOutcome::winner).map(PlayerOutcome::playerId)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)), null);
    }

    /** Completes a match through the policy registered for its game id. */
    @Transactional
    public EloMatchResult completeMatch(
            String gameCode,
            List<String> playerIds,
            Set<String> winners,
            Object gameState
    ) {
        List<String> distinctPlayerIds = distinctIds(playerIds);
        if (distinctPlayerIds.isEmpty()) {
            return new EloMatchResult(Map.of(), DEFAULT_ELO);
        }
        Map<String, UserGameStatisticEntity> stats = loadLocked(gameCode, distinctPlayerIds);
        Map<String, Integer> ratings = ratings(stats);
        List<PlayerOutcome> outcomes = distinctPlayerIds.stream()
                .map(playerId -> new PlayerOutcome(
                        playerId,
                        winners != null && winners.contains(playerId)
                ))
                .toList();
        EloMatchResult result = policyFor(gameCode).calculateMatch(outcomes, ratings, gameState);
        applyResult(gameCode, stats, result, true);
        return result;
    }

    /**
     * Applies one game-specific forfeit penalty without counting it as a
     * completed match. Match totals are reserved for games that reached a
     * valid terminal outcome and were persisted by MatchHistoryService.
     */
    @Transactional
    public EloMatchResult applyForfeit(String gameCode, String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return new EloMatchResult(Map.of(), DEFAULT_ELO);
        }
        Map<String, UserGameStatisticEntity> stats = loadLocked(gameCode, List.of(playerId));
        UserGameStatisticEntity statistic = stats.get(playerId);
        EloMatchResult result = policyFor(gameCode).calculateForfeit(playerId, statistic.getEloForGame());
        applyResult(gameCode, stats, result, false);
        return result;
    }

    /** Compatibility wrapper for existing callers and tests. */
    @Transactional
    public EloMatchResult completeNotInMyPotMatch(List<String> playerIds, Set<String> winners) {
        return completeMatch(
                "not-in-my-pot",
                playerIds,
                winners,
                null
        );
    }

    /** Compatibility wrapper for existing callers and tests. */
    @Transactional
    public EloMatchResult completeNobMatch(
            List<String> playerIds,
            Set<String> winners,
            Object state
    ) {
        return completeMatch("night-of-bloodlines", playerIds, winners, state);
    }

    private void applyResult(
            String gameCode,
            Map<String, UserGameStatisticEntity> stats,
            EloMatchResult result,
            boolean completeMatch
    ) {
        GameEloPolicy policy = policyFor(gameCode);
        for (EloChange change : result.changes().values()) {
            UserGameStatisticEntity statistic = stats.get(change.playerId());
            if (statistic == null) {
                continue;
            }
            policy.applyDelta(statistic, change.eloDelta());
            if (completeMatch) {
                statistic.completeMatch(change.winner());
            }
        }
        statisticRepository.saveAll(stats.values());
        log.info(
                "ELO {} gameCode={} players={} roomAverage={}",
                completeMatch ? "match" : "round",
                gameCode,
                result.changes().size(),
                result.roomAverageElo()
        );
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

    private GameEloPolicy policyFor(String gameCode) {
        return policies.getOrDefault(gameCode, fallbackPolicy);
    }

    private static Map<String, Integer> ratings(Map<String, UserGameStatisticEntity> stats) {
        Map<String, Integer> ratings = new LinkedHashMap<>();
        stats.forEach((playerId, statistic) -> ratings.put(playerId, statistic.getEloForGame()));
        return ratings;
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

    public record PlayerOutcome(String playerId, boolean winner, Integer score) {

        public PlayerOutcome(String playerId, boolean winner) {
            this(playerId, winner, null);
        }
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
