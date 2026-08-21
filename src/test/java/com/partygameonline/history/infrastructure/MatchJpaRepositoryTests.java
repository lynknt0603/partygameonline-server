package com.partygameonline.history.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.partygameonline.user.infrastructure.UserEntity;
import com.partygameonline.user.infrastructure.UserJpaRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MatchJpaRepositoryTests {

    @Autowired
    private MatchJpaRepository matchJpaRepository;

    @Autowired
    private MatchPlayerJpaRepository matchPlayerJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Test
    void savesCompletedMatchWithPlayers() {
        UserEntity user = userJpaRepository.saveAndFlush(UserEntity.newUser("Linh"));
        MatchEntity match = matchJpaRepository.saveAndFlush(MatchEntity.newMatch("demo-card-game"));
        match.finish(Instant.now());
        matchJpaRepository.saveAndFlush(match);

        matchPlayerJpaRepository.saveAndFlush(
                MatchPlayerEntity.newPlayer(match.getId(), user.getId(), "P1", "Linh", 0)
        );
        matchPlayerJpaRepository.saveAndFlush(
                MatchPlayerEntity.newPlayer(match.getId(), null, "P2", "Guest", 1)
        );

        MatchEntity found = matchJpaRepository.findById(match.getId()).orElseThrow();
        List<MatchPlayerEntity> players = matchPlayerJpaRepository.findByMatchIdOrderBySeatAscIdAsc(match.getId());

        assertThat(found.getGameId()).isEqualTo("demo-card-game");
        assertThat(found.getFinishedAt()).isNotNull();
        assertThat(players).hasSize(2);
        assertThat(players.get(0).getPlayerId()).isEqualTo("P1");
        assertThat(players.get(0).getUserId()).isEqualTo(user.getId());
        assertThat(players.get(1).getUserId()).isNull();
        assertThat(players).extracting(MatchPlayerEntity::getDisplayName).containsExactly("Linh", "Guest");
    }

    @Test
    void rejectsDuplicatePlayerInSameMatch() {
        MatchEntity match = matchJpaRepository.saveAndFlush(MatchEntity.newMatch("demo-card-game"));
        matchPlayerJpaRepository.saveAndFlush(
                MatchPlayerEntity.newPlayer(match.getId(), null, "P1", "Linh", 0)
        );

        assertThatThrownBy(() -> matchPlayerJpaRepository.saveAndFlush(
                MatchPlayerEntity.newPlayer(match.getId(), null, "P1", "Linh", 1)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
