package com.partygameonline.user.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserJpaRepositoryTests {

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Test
    void savesAndReadsUser() {
        UserEntity saved = userJpaRepository.saveAndFlush(UserEntity.newUser("Linh"));

        UserEntity found = userJpaRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getDisplayName()).isEqualTo("Linh");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void renameUpdatesDisplayNameAndTimestamp() {
        UserEntity user = userJpaRepository.saveAndFlush(UserEntity.newUser("Linh"));
        user.rename("Linh Nguyen");
        userJpaRepository.saveAndFlush(user);

        UserEntity found = userJpaRepository.findById(user.getId()).orElseThrow();
        assertThat(found.getDisplayName()).isEqualTo("Linh Nguyen");
        assertThat(found.getUpdatedAt()).isAfterOrEqualTo(found.getCreatedAt());
    }
}
