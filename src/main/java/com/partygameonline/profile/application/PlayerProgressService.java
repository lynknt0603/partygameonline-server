package com.partygameonline.profile.application;

import com.partygameonline.game.nob.domain.NobGameState;
import com.partygameonline.game.nob.domain.NobRoundPlayerSnapshot;
import com.partygameonline.game.notinmypot.domain.NotInMyPotGameState;
import com.partygameonline.game.notinmypot.domain.NotInMyPotIngredientType;
import com.partygameonline.game.notinmypot.domain.NotInMyPotRole;
import com.partygameonline.profile.domain.AchievementDefinition;
import com.partygameonline.common.avatar.AvatarCatalog;
import com.partygameonline.profile.infrastructure.UserAchievementEntity;
import com.partygameonline.profile.infrastructure.UserAchievementJpaRepository;
import com.partygameonline.profile.infrastructure.UserAvatarUnlockEntity;
import com.partygameonline.profile.infrastructure.UserAvatarUnlockJpaRepository;
import com.partygameonline.ranking.application.RankingService;
import com.partygameonline.user.infrastructure.UserEntity;
import com.partygameonline.user.infrastructure.UserJpaRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerProgressService {

    private final UserJpaRepository users;
    private final UserAchievementJpaRepository achievements;
    private final UserAvatarUnlockJpaRepository avatars;
    private final RankingService rankingService;

    public PlayerProgressService(
            UserJpaRepository users,
            UserAchievementJpaRepository achievements,
            UserAvatarUnlockJpaRepository avatars,
            RankingService rankingService
    ) {
        this.users = users;
        this.achievements = achievements;
        this.avatars = avatars;
        this.rankingService = rankingService;
    }

    @Transactional
    public void initializeMember(UserEntity user) {
        ensureRows(user);
    }

    @Transactional
    public MemberProgress memberProgress(UserEntity user) {
        ensureRows(user);
        Map<String, UserAchievementEntity> byCode = new HashMap<>();
        achievements.findByUserIdOrderByAchievementCodeAsc(user.getId())
                .forEach(row -> byCode.put(row.getAchievementCode(), row));
        List<AchievementProgress> progress = new ArrayList<>();
        for (AchievementDefinition definition : AchievementDefinition.values()) {
            UserAchievementEntity row = byCode.get(definition.name());
            progress.add(new AchievementProgress(
                    definition,
                    row == null ? 0 : row.getProgress(),
                    definition.target(),
                    row != null && row.isUnlocked(),
                    row == null ? null : row.getUnlockedAt()
            ));
        }
        Map<String, String> sources = new LinkedHashMap<>();
        avatars.findByUserIdOrderByAvatarKeyAsc(user.getId())
                .forEach(row -> sources.put(row.getAvatarKey(), row.getSource()));
        return new MemberProgress(progress, sources);
    }

    @Transactional
    public void recordFinishedGame(Object state, Set<String> winners, List<String> playerIds) {
        if (state instanceof NobGameState nob) {
            recordNob(nob);
        } else if (state instanceof NotInMyPotGameState pot) {
            recordNotInMyPot(pot, winners);
        }
        for (String playerId : playerIds) {
            if (users.findByUserKey(playerId).isPresent() && rankingService.isTopOne(playerId)) {
                increment(playerId, AchievementDefinition.RANKING_TOP_ONE, 1);
            }
        }
    }

    private void recordNob(NobGameState state) {
        Map<String, Map<AchievementDefinition, Integer>> increments = new LinkedHashMap<>();
        state.getCompletedRounds().forEach(round -> round.players().forEach(snapshot ->
                collectNobIncrement(increments, snapshot)));
        applyIncrements(increments);
    }

    private void collectNobIncrement(
            Map<String, Map<AchievementDefinition, Integer>> increments,
            NobRoundPlayerSnapshot snapshot
    ) {
        AchievementDefinition played;
        AchievementDefinition wins;
        String bloodline = normalizeBloodline(snapshot.bloodline());
        if (bloodline == null) {
            return;
        }
        switch (bloodline) {
            case "VAMPIRE" -> {
                played = AchievementDefinition.NOB_VAMPIRE_PLAYED;
                wins = AchievementDefinition.NOB_VAMPIRE_WINS;
            }
            case "WEREWOLF" -> {
                played = AchievementDefinition.NOB_WEREWOLF_PLAYED;
                wins = AchievementDefinition.NOB_WEREWOLF_WINS;
            }
            default -> {
                played = AchievementDefinition.NOB_HALFBLOOD_PLAYED;
                wins = AchievementDefinition.NOB_HALFBLOOD_WINS;
            }
        }
        increments.computeIfAbsent(snapshot.playerId(), ignored -> new LinkedHashMap<>())
                .merge(played, 1, Integer::sum);
        if ("WIN".equalsIgnoreCase(snapshot.result())) {
            increments.get(snapshot.playerId()).merge(wins, 1, Integer::sum);
        }
    }

    private void recordNotInMyPot(NotInMyPotGameState state, Set<String> winners) {
        Map<String, Map<AchievementDefinition, Integer>> increments = new LinkedHashMap<>();
        state.getPlayers().forEach(player -> {
            Map<AchievementDefinition, Integer> playerProgress = increments.computeIfAbsent(
                    player.getPlayerId(), ignored -> new LinkedHashMap<>()
            );
            putIfPositive(playerProgress, AchievementDefinition.NIMP_POT_REVEALED,
                    state.potRevealCount(player.getPlayerId()));
            putIfPositive(playerProgress, AchievementDefinition.NIMP_VEGETABLE_PLAYED,
                    state.ingredientPlayCount(player.getPlayerId(), NotInMyPotIngredientType.VEGETABLE));
            putIfPositive(playerProgress, AchievementDefinition.NIMP_TOFU_PLAYED,
                    state.ingredientPlayCount(player.getPlayerId(), NotInMyPotIngredientType.SALT));
            putIfPositive(playerProgress, AchievementDefinition.NIMP_MEAT_PLAYED,
                    state.ingredientPlayCount(player.getPlayerId(), NotInMyPotIngredientType.MEAT));
            if (winners.contains(player.getPlayerId())) {
                AchievementDefinition win = player.getRole() == NotInMyPotRole.VEGETARIAN
                        ? AchievementDefinition.NIMP_VEGETARIAN_WINS
                        : AchievementDefinition.NIMP_MEAT_EATER_WINS;
                playerProgress.merge(win, 1, Integer::sum);
            }
        });
        applyIncrements(increments);
    }

    private void applyIncrements(Map<String, Map<AchievementDefinition, Integer>> increments) {
        increments.forEach((playerId, values) -> values.forEach((definition, amount) ->
                increment(playerId, definition, amount)));
    }

    private void increment(String playerId, AchievementDefinition definition, int amount) {
        if (amount <= 0 || definition.isMaster()) {
            return;
        }
        UserEntity user = users.findByUserKey(playerId).orElse(null);
        if (user == null) {
            return;
        }
        ensureRows(user);
        UserAchievementEntity row = achievements.findForUpdate(user.getId(), definition.name()).orElseThrow();
        if (row.addProgress(amount)) {
            unlockRewards(user, definition);
        }
        synchronizeMaster(user);
    }

    private void synchronizeMaster(UserEntity user) {
        List<UserAchievementEntity> rows = achievements.findByUserIdOrderByAchievementCodeAsc(user.getId());
        int completed = (int) rows.stream()
                .filter(row -> !AchievementDefinition.ACHIEVEMENT_MASTER.name().equals(row.getAchievementCode()))
                .filter(UserAchievementEntity::isUnlocked)
                .count();
        UserAchievementEntity master = achievements.findForUpdate(
                user.getId(), AchievementDefinition.ACHIEVEMENT_MASTER.name()
        ).orElseThrow();
        if (master.setProgress(completed)) {
            unlockRewards(user, AchievementDefinition.ACHIEVEMENT_MASTER);
        }
    }

    private void unlockRewards(UserEntity user, AchievementDefinition definition) {
        definition.avatarKeys().forEach(key -> unlockAvatar(user, key, "ACHIEVEMENT:" + definition.name()));
    }

    private void ensureRows(UserEntity user) {
        Map<String, UserAchievementEntity> existing = new HashMap<>();
        achievements.findByUserIdOrderByAchievementCodeAsc(user.getId())
                .forEach(row -> existing.put(row.getAchievementCode(), row));
        List<UserAchievementEntity> missing = java.util.Arrays.stream(AchievementDefinition.values())
                .filter(definition -> !existing.containsKey(definition.name()))
                .map(definition -> UserAchievementEntity.create(user, definition))
                .toList();
        if (!missing.isEmpty()) {
            achievements.saveAllAndFlush(missing);
        }
        AvatarCatalog.freeKeys().forEach(key -> unlockAvatar(user, key, "FREE"));
    }

    private void unlockAvatar(UserEntity user, String avatarKey, String source) {
        if (!avatars.existsByUserIdAndAvatarKey(user.getId(), avatarKey)) {
            avatars.save(UserAvatarUnlockEntity.create(user, avatarKey, source));
        }
    }

    private static void putIfPositive(
            Map<AchievementDefinition, Integer> values,
            AchievementDefinition definition,
            int amount
    ) {
        if (amount > 0) {
            values.put(definition, amount);
        }
    }

    private static String normalizeBloodline(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.toUpperCase(java.util.Locale.ROOT)) {
            case "VAMPIRE" -> "VAMPIRE";
            case "WEREWOLF" -> "WEREWOLF";
            case "HALFBLOOD", "HALF_BLOOD", "SORCERESS" -> "HALFBLOOD";
            default -> null;
        };
    }

    public record AchievementProgress(
            AchievementDefinition definition,
            int progress,
            int target,
            boolean unlocked,
            Instant unlockedAt
    ) {
    }

    public record MemberProgress(
            List<AchievementProgress> achievements,
            Map<String, String> avatarSources
    ) {
        public MemberProgress {
            achievements = List.copyOf(achievements);
            avatarSources = Map.copyOf(avatarSources);
        }
    }
}
