package com.partygameonline.game.bloodbound.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure zero-sum team ELO calculation for a completed Blood Bound (Huyết Thệ) match.
 *
 * <p>Invariants:
 * <ul>
 *   <li>Default ELO rating is 5000.</li>
 *   <li>Total positive delta gained by winning team equals total negative delta lost by losing team:
 *       {@code sum(winnerDeltas) + sum(loserDeltas) == 0}.</li>
 *   <li>Every winning player receives a positive rating delta.</li>
 *   <li>Every losing player receives a negative rating delta.</li>
 *   <li>No rating ever drops below 0.</li>
 *   <li>Ratings scale smoothly according to opponent team average ELO.</li>
 * </ul>
 * </p>
 */
public final class BloodBoundEloCalculator {

    public static final int DEFAULT_ELO = 5000;
    public static final int MIN_ELO = 0;
    public static final double BASE_WIN_PER_PLAYER = 50.0;
    public static final double SOLO_INQUISITOR_BASE_WIN = 120.0;
    public static final double FACTOR_MIN = 0.50;
    public static final double FACTOR_MAX = 1.60;
    public static final double ELO_FACTOR_DIVISOR = 2_000.0;

    private BloodBoundEloCalculator() {
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

        double winnerAverage = average(winnerRatings);
        double loserAverage = average(loserRatings);

        int winnerCount = winnerRatings.size();
        int loserCount = loserRatings.size();

        double baseTeamGain = winnerCount == 1 ? SOLO_INQUISITOR_BASE_WIN : winnerCount * BASE_WIN_PER_PLAYER;
        double baseGainPerWinner = baseTeamGain / winnerCount;
        double baseLossPerLoser = baseTeamGain / loserCount;

        List<DesiredChange> desiredWinners = winnerRatings.stream()
                .map(player -> desiredWinner(player, loserAverage, baseGainPerWinner))
                .toList();
        List<DesiredChange> desiredLosers = loserRatings.stream()
                .map(player -> desiredLoser(player, winnerAverage, baseLossPerLoser))
                .toList();

        double totalDesiredGain = desiredWinners.stream().mapToDouble(DesiredChange::desired).sum();
        double totalDesiredLoss = desiredLosers.stream().mapToDouble(DesiredChange::desired).sum();
        int targetPool = (int) Math.round((totalDesiredGain + totalDesiredLoss) / 2.0);

        long loserCapacity = loserRatings.stream().mapToLong(PlayerRating::elo).sum();
        int pool = (int) Math.min(targetPool, loserCapacity);
        pool = Math.max(pool, Math.max(winnerCount, loserCount));

        int[] winnerAllocations = allocate(pool, desiredWinners, null);
        int[] loserCaps = desiredLosers.stream().mapToInt(c -> c.player().elo()).toArray();
        int[] loserAllocations = allocate(pool, desiredLosers, loserCaps);

        List<EloChange> result = new ArrayList<>(checkedPlayers.size());
        for (int i = 0; i < desiredWinners.size(); i++) {
            DesiredChange change = desiredWinners.get(i);
            int delta = winnerAllocations[i];
            int newElo = change.player().elo() + delta;
            result.add(new EloChange(change.player().playerId(), true, change.player().elo(), delta, newElo));
        }
        for (int i = 0; i < desiredLosers.size(); i++) {
            DesiredChange change = desiredLosers.get(i);
            int delta = -loserAllocations[i];
            int newElo = Math.max(MIN_ELO, change.player().elo() + delta);
            result.add(new EloChange(change.player().playerId(), false, change.player().elo(), delta, newElo));
        }

        validateResult(result, pool);
        return List.copyOf(result);
    }

    private static DesiredChange desiredWinner(PlayerRating player, double opponentAverage, double baseGain) {
        double factor = clamp(
                1.0 + (opponentAverage - player.elo()) / ELO_FACTOR_DIVISOR,
                FACTOR_MIN,
                FACTOR_MAX
        );
        return new DesiredChange(player, true, opponentAverage, factor, baseGain * factor);
    }

    private static DesiredChange desiredLoser(PlayerRating player, double opponentAverage, double baseLoss) {
        double factor = clamp(
                1.0 + (player.elo() - opponentAverage) / ELO_FACTOR_DIVISOR,
                FACTOR_MIN,
                FACTOR_MAX
        );
        return new DesiredChange(player, false, opponentAverage, factor, baseLoss * factor);
    }

    private static int[] allocate(int pool, List<DesiredChange> changes, int[] caps) {
        if (changes.isEmpty() || pool < changes.size()) {
            throw new IllegalArgumentException("The ELO pool is too small to give every player a signed delta");
        }
        int[] allocation = new int[changes.size()];
        int[] remainingCaps = new int[changes.size()];
        for (int i = 0; i < changes.size(); i++) {
            int cap = caps == null ? Integer.MAX_VALUE : caps[i];
            remainingCaps[i] = Math.max(1, cap);
        }

        int remaining = pool;
        while (remaining > 0) {
            List<Integer> active = new ArrayList<>();
            for (int i = 0; i < changes.size(); i++) {
                if (remainingCaps[i] > 0) {
                    active.add(i);
                }
            }
            if (active.isEmpty()) {
                break;
            }

            double totalWeight = active.stream().mapToDouble(i -> changes.get(i).desired()).sum();
            boolean saturated = false;
            for (int i : active) {
                double exact = remaining * changes.get(i).desired() / totalWeight;
                if (exact >= remainingCaps[i] - 1.0e-9) {
                    allocation[i] += remainingCaps[i];
                    remaining -= remainingCaps[i];
                    remainingCaps[i] = 0;
                    saturated = true;
                    break;
                }
            }
            if (saturated) {
                continue;
            }

            int assigned = 0;
            List<Remainder> remainders = new ArrayList<>(active.size());
            for (int i : active) {
                double exact = remaining * changes.get(i).desired() / totalWeight;
                int base = Math.min((int) Math.floor(exact), remainingCaps[i]);
                allocation[i] += base;
                remainingCaps[i] -= base;
                assigned += base;
                remainders.add(new Remainder(i, exact - base));
            }
            int pointsLeft = remaining - assigned;
            remainders.sort(Comparator
                    .comparingDouble(Remainder::fraction).reversed()
                    .thenComparing(remainder -> changes.get(remainder.index()).player().playerId()));

            while (pointsLeft > 0) {
                boolean gavePoint = false;
                for (Remainder remainder : remainders) {
                    int i = remainder.index();
                    if (remainingCaps[i] > 0 && pointsLeft > 0) {
                        allocation[i]++;
                        remainingCaps[i]--;
                        pointsLeft--;
                        gavePoint = true;
                    }
                }
                if (!gavePoint) {
                    break;
                }
            }
            remaining = 0;
        }

        // Ensure every participant gets at least 1 point
        for (int i = 0; i < allocation.length; i++) {
            if (allocation[i] > 0) {
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
            if (donor >= 0) {
                allocation[donor]--;
                allocation[i]++;
            }
        }

        return allocation;
    }

    private static void validateResult(List<EloChange> result, int pool) {
        int winnerSum = 0;
        int loserSum = 0;
        for (EloChange change : result) {
            if (change.winner()) {
                if (change.actualDelta() <= 0) {
                    throw new IllegalStateException("Winner did not gain ELO: " + change.playerId());
                }
                winnerSum += change.actualDelta();
            } else {
                if (change.actualDelta() >= 0) {
                    throw new IllegalStateException("Loser did not lose ELO: " + change.playerId());
                }
                loserSum += -change.actualDelta();
            }
        }
        if (winnerSum != loserSum) {
            throw new IllegalStateException("Zero-sum violated: winners gained " + winnerSum
                    + " but losers lost " + loserSum);
        }
    }

    private static List<PlayerRating> validatePlayers(List<PlayerRating> players) {
        if (players == null || players.size() < 2) {
            throw new IllegalArgumentException("A match requires at least two players");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (PlayerRating player : players) {
            if (player == null || player.playerId() == null || player.playerId().isBlank()) {
                throw new IllegalArgumentException("Player id is required");
            }
            if (!seen.add(player.playerId())) {
                throw new IllegalArgumentException("Duplicate player in match: " + player.playerId());
            }
            if (player.elo() < 0) {
                throw new IllegalArgumentException("Player rating cannot be negative: " + player.playerId());
            }
        }
        return List.copyOf(players);
    }

    private static Set<String> validateWinners(Set<String> winners, List<PlayerRating> players) {
        if (winners == null || winners.isEmpty()) {
            throw new IllegalArgumentException("Winner set cannot be empty");
        }
        Set<String> known = players.stream().map(PlayerRating::playerId).collect(java.util.stream.Collectors.toSet());
        for (String winner : winners) {
            if (!known.contains(winner)) {
                throw new IllegalArgumentException("Winner is not a match participant: " + winner);
            }
        }
        if (winners.size() >= players.size()) {
            throw new IllegalArgumentException("All participants cannot be winners in a competitive match");
        }
        return Set.copyOf(winners);
    }

    private static double average(List<PlayerRating> players) {
        return players.stream().mapToInt(PlayerRating::elo).average().orElse(DEFAULT_ELO);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record PlayerRating(String playerId, int elo) {
    }

    public record EloChange(
            String playerId,
            boolean winner,
            int oldElo,
            int actualDelta,
            int newElo
    ) {
    }

    private record DesiredChange(
            PlayerRating player,
            boolean winner,
            double opponentAverage,
            double factor,
            double desired
    ) {
    }

    private record Remainder(int index, double fraction) {
    }
}
