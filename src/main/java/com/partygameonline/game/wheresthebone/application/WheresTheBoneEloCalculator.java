package com.partygameonline.game.wheresthebone.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure zero-sum ELO calculation for a completed Where's the Bone match.
 *
 * <p>The calculator has no persistence or game-rule dependency. The caller
 * supplies an immutable pre-match rating snapshot and the official winner
 * ids produced by the game engine.</p>
 */
public final class WheresTheBoneEloCalculator {

    public static final double BASE_WIN = 50.0;
    public static final double BASE_LOSS = 76.0;
    public static final double MAX_LOSS = 100.0;
    public static final double WIN_FACTOR_MIN = 0.60;
    public static final double WIN_FACTOR_MAX = 1.50;
    public static final double LOSS_FACTOR_MIN = 0.60;
    public static final double LOSS_FACTOR_MAX = 1.35;
    public static final double ELO_FACTOR_DIVISOR = 2_000.0;

    private WheresTheBoneEloCalculator() {
    }

    public static List<EloChange> calculate(
            List<PlayerRating> players,
            Set<String> winnerPlayerIds
    ) {
        List<PlayerRating> checkedPlayers = validatePlayers(players);
        Set<String> winners = validateWinners(winnerPlayerIds, checkedPlayers);
        List<PlayerRating> winnerRatings = checkedPlayers.stream()
                .filter(player -> winners.contains(player.playerId()))
                .toList();
        List<PlayerRating> loserRatings = checkedPlayers.stream()
                .filter(player -> !winners.contains(player.playerId()))
                .toList();
        if (winnerRatings.isEmpty() || loserRatings.isEmpty()) {
            throw new IllegalArgumentException("A completed match needs at least one winner and one loser");
        }

        double loserAverage = average(loserRatings);
        double winnerAverage = average(winnerRatings);
        List<DesiredChange> desiredWinners = winnerRatings.stream()
                .map(player -> desiredWinner(player, loserAverage))
                .toList();
        List<DesiredChange> desiredLosers = loserRatings.stream()
                .map(player -> desiredLoser(player, winnerAverage))
                .toList();

        double totalDesiredGain = desiredWinners.stream()
                .mapToDouble(DesiredChange::desired)
                .sum();
        double totalDesiredLoss = desiredLosers.stream()
                .mapToDouble(DesiredChange::desired)
                .sum();
        int pool = integerPool(Math.min(totalDesiredGain, totalDesiredLoss));
        long loserCapacity = loserRatings.stream().mapToLong(PlayerRating::elo).sum();
        pool = (int) Math.min(pool, loserCapacity);
        if (pool <= 0) {
            throw new IllegalArgumentException("The ELO pool cannot produce a positive change for every player");
        }

        int[] winnerAllocations = allocate(pool, desiredWinners, null);
        int[] loserAllocations = allocate(pool, desiredLosers, desiredLosers.stream()
                .mapToInt(change -> change.player().elo())
                .toArray());

        List<EloChange> result = new ArrayList<>(checkedPlayers.size());
        for (int index = 0; index < desiredWinners.size(); index++) {
            DesiredChange change = desiredWinners.get(index);
            result.add(change.withActualDelta(winnerAllocations[index]));
        }
        for (int index = 0; index < desiredLosers.size(); index++) {
            DesiredChange change = desiredLosers.get(index);
            result.add(change.withActualDelta(-loserAllocations[index]));
        }
        validateResult(result, pool);
        return List.copyOf(result);
    }

    private static DesiredChange desiredWinner(PlayerRating player, double opponentAverage) {
        double factor = clamp(
                1.0 + (opponentAverage - player.elo()) / ELO_FACTOR_DIVISOR,
                WIN_FACTOR_MIN,
                WIN_FACTOR_MAX
        );
        return new DesiredChange(player, true, opponentAverage, factor, BASE_WIN * factor);
    }

    private static DesiredChange desiredLoser(PlayerRating player, double opponentAverage) {
        double factor = clamp(
                1.0 + (player.elo() - opponentAverage) / ELO_FACTOR_DIVISOR,
                LOSS_FACTOR_MIN,
                LOSS_FACTOR_MAX
        );
        return new DesiredChange(player, false, opponentAverage, factor, Math.min(MAX_LOSS, BASE_LOSS * factor));
    }

    /**
     * Allocates integer points with largest-remainder rounding. The optional
     * cap is the maximum number of points a participant may lose without going
     * below zero ELO.
     */
    private static int[] allocate(int pool, List<DesiredChange> changes, int[] caps) {
        if (changes.isEmpty() || pool < changes.size()) {
            throw new IllegalArgumentException("The ELO pool is too small to give every player a signed delta");
        }
        int[] allocation = new int[changes.size()];
        int[] remainingCaps = new int[changes.size()];
        for (int index = 0; index < changes.size(); index++) {
            int cap = caps == null ? Integer.MAX_VALUE : caps[index];
            if (cap < 1) {
                throw new IllegalArgumentException("A loser must have positive ELO to receive a loss");
            }
            remainingCaps[index] = cap;
        }

        int remaining = pool;
        while (remaining > 0) {
            List<Integer> active = new ArrayList<>();
            for (int index = 0; index < changes.size(); index++) {
                if (remainingCaps[index] > 0) {
                    active.add(index);
                }
            }
            if (active.isEmpty()) {
                throw new IllegalArgumentException("The ELO pool exceeds the available loser ELO");
            }

            double totalWeight = active.stream()
                    .mapToDouble(index -> changes.get(index).desired())
                    .sum();
            boolean saturated = false;
            for (int index : active) {
                double exact = remaining * changes.get(index).desired() / totalWeight;
                if (exact >= remainingCaps[index] - 1.0e-9) {
                    allocation[index] += remainingCaps[index];
                    remaining -= remainingCaps[index];
                    remainingCaps[index] = 0;
                    saturated = true;
                    break;
                }
            }
            if (saturated) {
                continue;
            }

            int assigned = 0;
            List<Remainder> remainders = new ArrayList<>(active.size());
            for (int index : active) {
                double exact = remaining * changes.get(index).desired() / totalWeight;
                int base = Math.min((int) Math.floor(exact), remainingCaps[index]);
                allocation[index] += base;
                remainingCaps[index] -= base;
                assigned += base;
                remainders.add(new Remainder(index, exact - base));
            }
            int pointsLeft = remaining - assigned;
            remainders.sort(Comparator
                    .comparingDouble(Remainder::fraction).reversed()
                    .thenComparing(remainder -> changes.get(remainder.index()).player().playerId()));
            while (pointsLeft > 0) {
                boolean gavePoint = false;
                for (Remainder remainder : remainders) {
                    int index = remainder.index();
                    if (remainingCaps[index] > 0 && pointsLeft > 0) {
                        allocation[index]++;
                        remainingCaps[index]--;
                        pointsLeft--;
                        gavePoint = true;
                    }
                }
                if (!gavePoint) {
                    throw new IllegalArgumentException("The ELO pool cannot be rounded within player caps");
                }
            }
            remaining = 0;
        }

        // The requested invariants require a signed delta for every player.
        // With the bounded factors and normal pools Hamilton allocation already
        // satisfies this; this deterministic repair handles a narrow capped
        // edge case without changing the pool total.
        for (int index = 0; index < allocation.length; index++) {
            if (allocation[index] > 0) {
                continue;
            }
            int donor = -1;
            for (int candidate = 0; candidate < allocation.length; candidate++) {
                if (allocation[candidate] > 1
                        && (donor < 0 || allocation[candidate] > allocation[donor]
                        || (allocation[candidate] == allocation[donor]
                        && changes.get(candidate).player().playerId()
                        .compareTo(changes.get(donor).player().playerId()) < 0))) {
                    donor = candidate;
                }
            }
            if (donor < 0) {
                throw new IllegalArgumentException("The ELO pool is too small to give every player a signed delta");
            }
            allocation[donor]--;
            allocation[index]++;
        }
        return allocation;
    }

    private static void validateResult(List<EloChange> changes, int pool) {
        long sum = changes.stream().mapToLong(EloChange::actualDelta).sum();
        if (sum != 0) {
            throw new IllegalStateException("Where's the Bone ELO calculation is not zero-sum");
        }
        long gains = changes.stream().filter(EloChange::winner).mapToLong(EloChange::actualDelta).sum();
        long losses = changes.stream().filter(change -> !change.winner()).mapToLong(EloChange::actualDelta).sum();
        if (gains != pool || losses != -pool
                || changes.stream().anyMatch(change -> change.winner() && change.actualDelta() <= 0)
                || changes.stream().anyMatch(change -> !change.winner()
                && (change.actualDelta() >= 0 || change.actualDelta() < -MAX_LOSS))) {
            throw new IllegalStateException("Where's the Bone ELO invariants failed");
        }
        for (EloChange change : changes) {
            if (change.newElo() != Math.addExact(change.oldElo(), change.actualDelta())
                    || change.newElo() < 0) {
                throw new IllegalStateException("Where's the Bone ELO would leave the valid rating range");
            }
        }
    }

    private static List<PlayerRating> validatePlayers(List<PlayerRating> players) {
        if (players == null || players.isEmpty()) {
            throw new IllegalArgumentException("players must not be empty");
        }
        Set<String> ids = new LinkedHashSet<>();
        List<PlayerRating> checked = new ArrayList<>(players.size());
        for (PlayerRating player : players) {
            if (player == null || player.playerId() == null || player.playerId().isBlank()) {
                throw new IllegalArgumentException("players contains a player without an id");
            }
            if (player.elo() < 0) {
                throw new IllegalArgumentException("ELO cannot be negative");
            }
            if (!ids.add(player.playerId())) {
                throw new IllegalArgumentException("A player cannot appear more than once: " + player.playerId());
            }
            checked.add(player);
        }
        return List.copyOf(checked);
    }

    private static Set<String> validateWinners(Set<String> winnerPlayerIds, List<PlayerRating> players) {
        if (winnerPlayerIds == null || winnerPlayerIds.isEmpty()) {
            throw new IllegalArgumentException("winnerPlayerIds must not be empty");
        }
        Set<String> playerIds = players.stream().map(PlayerRating::playerId).collect(java.util.stream.Collectors.toSet());
        Set<String> winners = new LinkedHashSet<>();
        for (String winner : winnerPlayerIds) {
            if (winner == null || winner.isBlank() || !playerIds.contains(winner) || !winners.add(winner)) {
                throw new IllegalArgumentException("winnerPlayerIds contains an invalid or duplicate player");
            }
        }
        return Set.copyOf(winners);
    }

    private static double average(List<PlayerRating> players) {
        return players.stream().mapToInt(PlayerRating::elo).average().orElseThrow();
    }

    private static int integerPool(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException("The ELO pool must be positive");
        }
        return Math.toIntExact((long) Math.floor(value + 1.0e-9));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record DesiredChange(
            PlayerRating player,
            boolean winner,
            double opponentAverage,
            double factor,
            double desired
    ) {
        private EloChange withActualDelta(int actualDelta) {
            return new EloChange(
                    player.playerId(),
                    winner,
                    player.elo(),
                    opponentAverage,
                    factor,
                    desired,
                    actualDelta,
                    Math.addExact(player.elo(), actualDelta)
            );
        }
    }

    private record Remainder(int index, double fraction) {
    }

    public record PlayerRating(String playerId, int elo) {
    }

    public record EloChange(
            String playerId,
            boolean winner,
            int oldElo,
            double opponentAverage,
            double factor,
            double desired,
            int actualDelta,
            int newElo
    ) {
    }
}
