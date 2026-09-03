package com.partygameonline.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.partygameonline.user.infrastructure.UserEntity;
import com.partygameonline.user.infrastructure.UserJpaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LegacyPasswordMigrationTests {

    @Test
    void replacesDecryptableLegacyRowsWithBcryptHashes() {
        UserJpaRepository users = mock(UserJpaRepository.class);
        AesPasswordCipher cipher = mock(AesPasswordCipher.class);
        UserEntity user = UserEntity.newMember("linh", "legacy-aes", "Linh");
        when(users.findAll()).thenReturn(List.of(user));
        when(cipher.needsUpgrade("legacy-aes")).thenReturn(true);
        when(cipher.decryptLegacy("legacy-aes")).thenReturn(Optional.of("Secret123!"));
        when(cipher.encrypt("Secret123!")).thenReturn("$2a$secure-hash");

        new LegacyPasswordMigration(users, cipher).migrate();

        assertThat(user.getPasswordAes()).isEqualTo("$2a$secure-hash");
        verify(users).saveAll(anyList());
    }
}
