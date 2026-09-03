package com.partygameonline.user.infrastructure;

import com.partygameonline.common.avatar.AvatarCatalog;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "display_name", nullable = false, length = 32)
    private String displayName;

    @Column(length = 32, unique = true)
    private String username;

    @Column(name = "password_aes", length = 512)
    private String passwordAes;

    @Column(name = "user_key", nullable = false, length = 64, unique = true)
    private String userKey;

    @Column(name = "avatar_key", nullable = false, length = 64)
    private String avatarKey;

    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "update_date", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {
    }

    public UserEntity(UUID id, String displayName, String username, String passwordAes,
                      String userKey, Instant createdAt, Instant updatedAt) {
        this(id, displayName, username, passwordAes, userKey, AvatarCatalog.DEFAULT_KEY, createdAt, updatedAt);
    }

    public UserEntity(UUID id, String displayName, String username, String passwordAes,
                      String userKey, String avatarKey, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.displayName = displayName;
        this.username = username;
        this.passwordAes = passwordAes;
        this.userKey = userKey;
        this.avatarKey = AvatarCatalog.normalizeKey(avatarKey);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static UserEntity newUser(String displayName) {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        return new UserEntity(id, displayName, null, null, id.toString(), now, now);
    }

    public static UserEntity newMember(String username, String passwordAes) {
        return newMember(username, passwordAes, username);
    }

    public static UserEntity newMember(String username, String passwordAes, String displayName) {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        return new UserEntity(id, displayName, username, passwordAes, UUID.randomUUID().toString(), now, now);
    }

    public void rename(String displayName) {
        this.displayName = displayName;
        this.updatedAt = Instant.now();
    }

    public void selectAvatar(String avatarKey) {
        this.avatarKey = AvatarCatalog.normalizeKey(avatarKey);
        this.updatedAt = Instant.now();
    }

    public void upgradePassword(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash must not be blank");
        }
        this.passwordAes = passwordHash;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordAes() {
        return passwordAes;
    }

    public String getUserKey() {
        return userKey;
    }

    public String getAvatarKey() {
        return avatarKey;
    }
}
