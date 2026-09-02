package com.partygameonline.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class AesPasswordCipherTests {

    private final AesPasswordCipher cipher = new AesPasswordCipher("DEV_AES_KEY");

    @Test
    void hashesWithBcryptAndMatchesOriginalPassword() {
        String first = cipher.encrypt("Secret123!");
        String second = cipher.encrypt("Secret123!");

        assertThat(first).isNotEqualTo(second);
        assertThat(first).startsWith("$2a$");
        assertThat(cipher.needsUpgrade(first)).isFalse();
        assertThat(cipher.matches("Secret123!", first)).isTrue();
        assertThat(cipher.matches("Wrong123!", first)).isFalse();
    }

    @Test
    void rejectsMalformedCiphertext() {
        assertThat(cipher.matches("Secret123!", "not-base64"))
                .isFalse();
        assertThat(cipher.needsUpgrade("legacy-ciphertext")).isTrue();
    }

    @Test
    void explicitLegacyKeyCanMigrateOldRowsButBlankKeyCannot() throws Exception {
        String legacy = legacyEncrypt("DEV_AES_KEY", "Secret123!");

        assertThat(cipher.matches("Secret123!", legacy)).isTrue();
        assertThat(cipher.decryptLegacy(legacy)).contains("Secret123!");
        AesPasswordCipher withoutLegacyKey = new AesPasswordCipher("");
        assertThat(withoutLegacyKey.matches("Secret123!", legacy)).isFalse();
    }

    private static String legacyEncrypt(String configuredKey, String rawPassword) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        SecretKeySpec key = new SecretKeySpec(
                MessageDigest.getInstance("SHA-256").digest(configuredKey.getBytes(StandardCharsets.UTF_8)),
                "AES"
        );
        Cipher legacy = Cipher.getInstance("AES/GCM/NoPadding");
        legacy.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] encrypted = legacy.doFinal(rawPassword.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(
                ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array()
        );
    }
}
