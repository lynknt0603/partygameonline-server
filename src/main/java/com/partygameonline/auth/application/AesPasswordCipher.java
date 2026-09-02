package com.partygameonline.auth.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AesPasswordCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int BCRYPT_STRENGTH = 12;
    private final SecretKeySpec key;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);

    public AesPasswordCipher(@Value("${app.encryption.key}") String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new IllegalArgumentException("app.encryption.key must not be blank");
        }
        try {
            this.key = new SecretKeySpec(
                    MessageDigest.getInstance("SHA-256").digest(configuredKey.getBytes(StandardCharsets.UTF_8)),
                    "AES"
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot initialize AES key", exception);
        }
    }

    public String encrypt(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("Password must not be blank");
        }
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Verifies both current BCrypt hashes and legacy AES ciphertexts so existing
     * accounts continue to work during the one-login migration window.
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        if (isBcrypt(encodedPassword)) {
            try {
                return passwordEncoder.matches(rawPassword, encodedPassword);
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
        return matchesLegacyAes(rawPassword, encodedPassword);
    }

    public boolean needsUpgrade(String encodedPassword) {
        return !isBcrypt(encodedPassword);
    }

    private boolean matchesLegacyAes(String rawPassword, String encodedPassword) {
        try {
            byte[] payload = Base64.getDecoder().decode(encodedPassword);
            if (payload.length <= IV_BYTES) {
                return false;
            }
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] actual = cipher.doFinal(encrypted);
            return MessageDigest.isEqual(actual, rawPassword.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isBcrypt(String encodedPassword) {
        return encodedPassword != null
                && (encodedPassword.startsWith("$2a$")
                || encodedPassword.startsWith("$2b$")
                || encodedPassword.startsWith("$2y$"));
    }
}
