package com.partygameonline.game.nob.scoring;

import com.partygameonline.game.core.RandomSource;
import com.partygameonline.game.nob.domain.NobBloodlineType;
import com.partygameonline.game.nob.domain.NobEffectCode;
import com.partygameonline.game.nob.domain.NobGameState;
import com.partygameonline.game.nob.domain.NobPlayerState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class NobScoringService {

    public enum MainResult {
        VAMPIRE,
        WEREWOLF,
        TOTAL_TIE,
        LAST_HOPE_HALFBLOOD
    }

    private NobScoringService() {
    }

    public static MainResult compareSurvivors(NobGameState state) {
        NobPlayerState lastHope = livingLastHope(state);
        if (lastHope != null && lastHope.getCurrentBloodline() != null) {
            if (lastHope.getCurrentBloodline().type() == NobBloodlineType.HALFBLOOD) {
                return MainResult.LAST_HOPE_HALFBLOOD;
            }
            return lastHope.getCurrentBloodline().type() == NobBloodlineType.VAMPIRE
                    ? MainResult.VAMPIRE
                    : MainResult.WEREWOLF;
        }
        List<Integer> vampires = ranks(state, NobBloodlineType.VAMPIRE);
        List<Integer> wolves = ranks(state, NobBloodlineType.WEREWOLF);
        int max = Math.max(vampires.size(), wolves.size());
        if (vampires.isEmpty() && wolves.isEmpty()) {
            return MainResult.TOTAL_TIE;
        }
        for (int i = 0; i < max; i++) {
            if (i >= vampires.size()) {
                return MainResult.WEREWOLF;
            }
            if (i >= wolves.size()) {
                return MainResult.VAMPIRE;
            }
            int cmp = Integer.compare(vampires.get(i), wolves.get(i));
            if (cmp < 0) {
                return MainResult.VAMPIRE;
            }
            if (cmp > 0) {
                return MainResult.WEREWOLF;
            }
        }
        return MainResult.TOTAL_TIE;
    }

    public static List<String> rewardPlayerIds(NobGameState state, MainResult result) {
        List<String> ids = new ArrayList<>();
        if (result == MainResult.LAST_HOPE_HALFBLOOD) {
            state.alivePlayers().stream()
                    .filter(player -> player.getCurrentBloodline() != null
                            && player.getCurrentBloodline().type() == NobBloodlineType.HALFBLOOD)
                    .map(NobPlayerState::getPlayerId)
                    .forEach(ids::add);
            return includeLastOffering(state, ids);
        }
        if (result == MainResult.TOTAL_TIE) {
            state.alivePlayers().stream().map(NobPlayerState::getPlayerId).forEach(ids::add);
            return includeLastOffering(state, ids);
        }
        NobBloodlineType winner = result == MainResult.VAMPIRE ? NobBloodlineType.VAMPIRE : NobBloodlineType.WEREWOLF;
        for (NobPlayerState player : state.getPlayers()) {
            if (player.getCurrentBloodline() != null && player.getCurrentBloodline().type() == winner) {
                ids.add(player.getPlayerId());
            } else if (player.isAlive()
                    && player.getCurrentBloodline() != null
                    && player.getCurrentBloodline().type() == NobBloodlineType.HALFBLOOD) {
                ids.add(player.getPlayerId());
            }
        }
        return includeLastOffering(state, ids);
    }

    private static List<String> includeLastOffering(NobGameState state, List<String> ids) {
        for (NobPlayerState player : state.getPlayers()) {
            if (usedLastOffering(player) && !ids.contains(player.getPlayerId())) {
                ids.add(player.getPlayerId());
            }
        }
        return ids;
    }

    public static boolean usedLastOffering(NobPlayerState player) {
        return player.getRevealedCards().stream().anyMatch(card -> card.effectCode() == NobEffectCode.LAST_OFFERING)
                || player.getUsedCards().stream().anyMatch(card -> card.effectCode() == NobEffectCode.LAST_OFFERING);
    }

    public static int moonPicksNeeded(NobGameState state, String playerId) {
        NobPlayerState player = state.requirePlayer(playerId);
        if (!usedLastOffering(player) || player.getCurrentBloodline() == null) {
            return 1;
        }
        NobBloodlineType type = player.getCurrentBloodline().type();
        return switch (compareSurvivors(state)) {
            case VAMPIRE -> type == NobBloodlineType.VAMPIRE ? 2 : 1;
            case WEREWOLF -> type == NobBloodlineType.WEREWOLF ? 2 : 1;
            case LAST_HOPE_HALFBLOOD -> type == NobBloodlineType.HALFBLOOD ? 2 : 1;
            case TOTAL_TIE -> 1;
        };
    }

    public static void applyRoundRewards(NobGameState state, MainResult result, RandomSource random) {
        for (String playerId : rewardPlayerIds(state, result)) {
            state.awardMoonMark(state.requirePlayer(playerId), random);
        }
    }

    public static List<String> winnersAtOrOverTarget(NobGameState state) {
        int best = state.getPlayers().stream().mapToInt(NobPlayerState::score).max().orElse(0);
        if (best < state.getTargetScore()) {
            return List.of();
        }
        return state.getPlayers().stream()
                .filter(player -> player.score() == best)
                .map(NobPlayerState::getPlayerId)
                .toList();
    }

    private static List<Integer> ranks(NobGameState state, NobBloodlineType type) {
        List<Integer> ranks = new ArrayList<>();
        for (NobPlayerState player : state.alivePlayers()) {
            if (player.getCurrentBloodline() != null
                    && player.getCurrentBloodline().type() == type
                    && player.getCurrentBloodline().rank() != null) {
                ranks.add(player.getCurrentBloodline().rank());
            }
        }
        ranks.sort(Comparator.naturalOrder());
        return ranks;
    }

    private static NobPlayerState livingLastHope(NobGameState state) {
        for (NobPlayerState player : state.alivePlayers()) {
            boolean holds = player.getHand().stream().anyMatch(card -> card.effectCode() == NobEffectCode.LAST_HOPE)
                    || player.getRevealedCards().stream().anyMatch(card -> card.effectCode() == NobEffectCode.LAST_HOPE);
            if (holds) {
                return player;
            }
        }
        return null;
    }
}
