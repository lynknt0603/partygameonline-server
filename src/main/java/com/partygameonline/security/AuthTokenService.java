package com.partygameonline.security;

import com.partygameonline.session.domain.PlayerPrincipal;
import com.partygameonline.session.domain.SessionKind;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public final class AuthTokenService {

    private static final String PREFIX = "pgo1.";
    private static final byte[] AAD = "BoardVerse-auth-token-v1".getBytes(StandardCharsets.UTF_8);
    private static final int FORMAT_VERSION = 1;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MAX_TOKEN_LENGTH = 4096;
    private static final Duration MIN_TTL = Duration.ofMinutes(5);
    private static final Duration MAX_TTL = Duration.ofDays(30);

    private final SecretKeySpec key;
    private final Duration ttl;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public AuthTokenService(SecurityProperties properties) {
        this(properties.token().secret(), properties.token().ttl(), Clock.systemUTC());
    }

    AuthTokenService(String configuredSecret, Duration ttl, Clock clock) {
        if (configuredSecret == null || configuredSecret.length() < 32) {
            throw new IllegalStateException("AUTH_TOKEN_KEY must contain at least 32 characters");
        }
        if (ttl == null || ttl.compareTo(MIN_TTL) < 0 || ttl.compareTo(MAX_TTL) > 0) {
            throw new IllegalStateException("AUTH_TOKEN_TTL must be between 5 minutes and 30 days");
        }
        try {
            this.key = new SecretKeySpec(
                    MessageDigest.getInstance("SHA-256")
                            .digest(configuredSecret.getBytes(StandardCharsets.UTF_8)),
                    "AES"
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot initialize authentication token key", exception);
        }
        this.ttl = ttl;
        this.clock = clock;
    }

    public String issue(PlayerPrincipal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("Player principal is required");
        }
        Instant expiresAt = clock.instant().plus(ttl);
        byte[] plaintext = encode(principal, expiresAt);
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(AAD);
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] payload = ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot issue authentication token", exception);
        }
    }

    public Optional<PlayerPrincipal> verify(String token) {
        if (token == null || !token.startsWith(PREFIX) || token.length() > MAX_TOKEN_LENGTH) {
            return Optional.empty();
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(token.substring(PREFIX.length()));
            if (payload.length <= IV_BYTES + 16) {
                return Optional.empty();
            }
            byte[] iv = new byte[IV_BYTES];
            byte[] ciphertext = new byte[payload.length - IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            System.arraycopy(payload, IV_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(AAD);
            return decode(cipher.doFinal(ciphertext));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private byte[] encode(PlayerPrincipal principal, Instant expiresAt) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(FORMAT_VERSION);
                output.writeLong(expiresAt.toEpochMilli());
                output.writeUTF(principal.kind().name());
                output.writeUTF(principal.playerId());
                output.writeUTF(principal.displayName());
                output.writeLong(principal.createdAt().toEpochMilli());
                output.writeUTF(principal.avatarUrl() == null ? "" : principal.avatarUrl());
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot encode authentication token", exception);
        }
    }

    private Optional<PlayerPrincipal> decode(byte[] plaintext) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(plaintext))) {
            if (input.readUnsignedByte() != FORMAT_VERSION) {
                return Optional.empty();
            }
            Instant expiresAt = Instant.ofEpochMilli(input.readLong());
            SessionKind kind = SessionKind.valueOf(input.readUTF());
            String playerId = input.readUTF();
            String displayName = input.readUTF();
            Instant createdAt = Instant.ofEpochMilli(input.readLong());
            String avatarUrl = input.readUTF();
            if (input.available() != 0
                    || !expiresAt.isAfter(clock.instant())
                    || playerId.isBlank() || playerId.length() > 128
                    || displayName.isBlank() || displayName.length() > 32
                    || avatarUrl.length() > 512) {
                return Optional.empty();
            }
            return Optional.of(new PlayerPrincipal(
                    playerId,
                    displayName,
                    kind,
                    createdAt,
                    avatarUrl.isBlank() ? null : avatarUrl
            ));
        } catch (IOException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
