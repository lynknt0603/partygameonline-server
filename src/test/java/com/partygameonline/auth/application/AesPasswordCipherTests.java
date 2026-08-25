package com.partygameonline.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AesPasswordCipherTests {

    private final AesPasswordCipher cipher = new AesPasswordCipher("DEV_AES_KEY");

    @Test
    void encryptsWithRandomIvAndMatchesOriginalPassword() {
        String first = cipher.encrypt("Secret123!");
        String second = cipher.encrypt("Secret123!");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.matches("Secret123!", first)).isTrue();
        assertThat(cipher.matches("Wrong123!", first)).isFalse();
    }

    @Test
    void rejectsMalformedCiphertext() {
        assertThat(cipher.matches("Secret123!", "not-base64"))
                .isFalse();
    }
}
