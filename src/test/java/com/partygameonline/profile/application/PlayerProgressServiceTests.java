package com.partygameonline.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.partygameonline.game.nob.domain.NobCompletedRound;
import com.partygameonline.game.nob.domain.NobGameState;
import com.partygameonline.game.nob.domain.NobRoundPlayerSnapshot;
import com.partygameonline.game.nob.domain.NobRoundResult;
import com.partygameonline.game.notinmypot.domain.NotInMyPotGameState;
import com.partygameonline.game.notinmypot.domain.NotInMyPotIngredientType;
import com.partygameonline.game.notinmypot.domain.NotInMyPotPlayerState;
import com.partygameonline.game.notinmypot.domain.NotInMyPotRole;
import com.partygameonline.profile.domain.AchievementDefinition;
import com.partygameonline.user.infrastructure.UserEntity;
import com.partygameonline.user.infrastructure.UserJpaRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PlayerProgressServiceTests {

    @Autowired
    private PlayerProgressService service;

    @Autowired
    private UserJpaRepository users;

    @Test
    void masterAchievementGrantsBothMasterAvatars() {
        assertThat(AchievementDefinition.ACHIEVEMENT_MASTER.avatarKeys())
                .containsExactly("master.png", "master_girl.png");
    }

    @Test
    void notInMyPotCountersUnlockTheirRewardAvatars() {
        UserEntity user = member("nimpachievement");
        NotInMyPotGameState state = new NotInMyPotGameState("ACHIEVEMENT_ROOM");
        state.addPlayer(new NotInMyPotPlayerState(user.getUserKey(), user.getDisplayName(), 0, NotInMyPotRole.VEGETARIAN));
        for (int i = 0; i < 20; i++) {
            state.recordIngredientPlayed(user.getUserKey(), NotInMyPotIngredientType.VEGETABLE);
            state.recordIngredientPlayed(user.getUserKey(), NotInMyPotIngredientType.SALT);
            state.recordIngredientPlayed(user.getUserKey(), NotInMyPotIngredientType.MEAT);
        }
        for (int i = 0; i < 10; i++) {
            state.recordPotReveal(user.getUserKey());
        }

        service.recordFinishedGame(state, Set.of(user.getUserKey()), List.of(user.getUserKey()));

        PlayerProgressService.MemberProgress progress = service.memberProgress(user);
        assertThat(progress.avatarSources()).containsKeys(
                "23_pot.png", "20_tofu.png", "21_meat.png", "17_broccoli.png"
        );
        assertThat(progress.achievements())
                .filteredOn(item -> item.definition() == AchievementDefinition.NIMP_VEGETARIAN_WINS)
                .singleElement()
                .extracting(PlayerProgressService.AchievementProgress::progress)
                .isEqualTo(1);
    }

    @Test
    void nobRoundPlayAndWinThresholdsUnlockSeparateAvatars() {
        UserEntity user = member("nobachievement");
        NobGameState state = mock(NobGameState.class);
        List<NobCompletedRound> rounds = new ArrayList<>();
        for (int round = 1; round <= 20; round++) {
            rounds.add(new NobCompletedRound(
                    round,
                    new NobRoundResult("VAMPIRE", "VAMPIRE", false),
                    List.of(new NobRoundPlayerSnapshot(user.getUserKey(), "VAMPIRE", "WIN", 1))
            ));
        }
        when(state.getCompletedRounds()).thenReturn(rounds);

        service.recordFinishedGame(state, Set.of(user.getUserKey()), List.of(user.getUserKey()));

        PlayerProgressService.MemberProgress progress = service.memberProgress(user);
        assertThat(progress.avatarSources()).containsKeys("vampire_2.png", "vampire.png");
    }

    private UserEntity member(String prefix) {
        String username = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UserEntity user = users.saveAndFlush(UserEntity.newMember(username, "encrypted"));
        service.initializeMember(user);
        return user;
    }
}
