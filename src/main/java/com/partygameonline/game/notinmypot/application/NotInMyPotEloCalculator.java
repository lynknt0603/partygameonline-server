package com.partygameonline.game.notinmypot.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Calculates the zero-sum ELO transfer for a completed Not In My Pot match.
 *
 * <p>This calculator deliberately has no persistence dependency. The caller
 * supplies the ratings captured for this match and decides how to store the
 * returned integer changes.</p>
 */
public final class NotInMyPotEloCalculator {

    public static final int BASE_REWARD = 50;
    private static final double ADJUSTMENT_DIVISOR = 10_000.0;
    private static final double MAX_ADJUSTMENT = 0.10;

    private NotInMyPotEloCalculator() {
    }

    /**
     * Calculates winner rewards and loser deductions for one two-faction
     * match. Every winner receives {@link #BASE_REWARD}; the loser pool is
     * weighted by rating, rounded to integers, and then balanced exactly.
     */
    public static List<EloChange> calculateEloChanges(
            List<PlayerRating> winners,
            List<PlayerRating> losers
    ) {
        List<PlayerRating> checkedWinners = validateTeam(winners, "winners");
        List<PlayerRating> checkedLosers = validateTeam(losers, "losers");
        Set<String> ids = new HashSet<>();
        checkedWinners.forEach(player -> addUniqueId(ids, player));
        checkedLosers.forEach(player -> addUniqueId(ids, player));

        long totalReward = Math.multiplyExact((long) checkedWinners.size(), BASE_REWARD);
        double averageLoserElo = checkedLosers.stream()
                .mapToDouble(PlayerRating::elo)
                .average()
                .orElseThrow();
        List<WeightedLoser> weightedLosers = new ArrayList<>(checkedLosers.size());
        double totalWeight = 0.0;
        int highestEloIndex = 0;
        for (int index = 0; index < checkedLosers.size(); index++) {
            PlayerRating loser = checkedLosers.get(index);
            double adjustment = clamp(
                    (loser.elo() - averageLoserElo) / ADJUSTMENT_DIVISOR,
                    -MAX_ADJUSTMENT,
                    MAX_ADJUSTMENT
            );
            double weight = 1.0 + adjustment;
            weightedLosers.add(new WeightedLoser(weight));
            totalWeight += weight;
            if (loser.elo() > checkedLosers.get(highestEloIndex).elo()) {
                highestEloIndex = index;
            }
        }

        List<Long> losses = new ArrayList<>(checkedLosers.size());
        long roundedLosses = 0;
        for (WeightedLoser weighted : weightedLosers) {
            long loss = Math.round(totalReward * weighted.weight() / totalWeight);
            losses.add(loss);
            roundedLosses = Math.addExact(roundedLosses, loss);
        }
        long roundingRemainder = totalReward - roundedLosses;
        losses.set(
                highestEloIndex,
                Math.addExact(losses.get(highestEloIndex), roundingRemainder)
        );
        if (losses.stream().anyMatch(loss -> loss <= 0)
                || losses.stream().mapToLong(Long::longValue).sum() != totalReward) {
            throw new IllegalArgumentException("The ELO reward pool cannot be distributed to every loser");
        }

        List<EloChange> result = new ArrayList<>(checkedWinners.size() + checkedLosers.size());
        for (PlayerRating winner : checkedWinners) {
            result.add(change(winner, BASE_REWARD));
        }
        for (int index = 0; index < checkedLosers.size(); index++) {
            result.add(change(checkedLosers.get(index), -Math.toIntExact(losses.get(index))));
        }
        return List.copyOf(result);
    }

    private static List<PlayerRating> validateTeam(List<PlayerRating> players, String teamName) {
        if (players == null || players.isEmpty()) {
            throw new IllegalArgumentException(teamName + " must contain at least one player");
        }
        List<PlayerRating> checked = new ArrayList<>(players.size());
        for (PlayerRating player : players) {
            if (player == null || player.id() == null || player.id().isBlank()) {
                throw new IllegalArgumentException(teamName + " contains a player without an id");
            }
            checked.add(player);
        }
        return List.copyOf(checked);
    }

    private static void addUniqueId(Set<String> ids, PlayerRating player) {
        if (!ids.add(player.id())) {
            throw new IllegalArgumentException("A player cannot appear in both teams or more than once: " + player.id());
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static EloChange change(PlayerRating player, int eloChange) {
        return new EloChange(
                player.id(),
                player.elo(),
                eloChange,
                Math.toIntExact((long) player.elo() + eloChange)
        );
    }

    private record WeightedLoser(double weight) {
    }

    public record PlayerRating(String id, int elo) {
    }

    public record EloChange(String id, int oldElo, int eloChange, int newElo) {
    }
}
