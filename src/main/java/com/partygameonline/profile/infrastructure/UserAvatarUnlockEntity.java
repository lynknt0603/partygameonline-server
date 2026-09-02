package com.partygameonline.profile.infrastructure;

import com.partygameonline.user.infrastructure.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_avatar_unlock", uniqueConstraints = @UniqueConstraint(
        name = "uq_user_avatar_unlock_user_avatar", columnNames = {"user_id", "avatar_key"}
))
public class UserAvatarUnlockEntity {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserEntity user;

    @Column(name = "avatar_key", nullable = false, length = 128, updatable = false)
    private String avatarKey;

    @Column(nullable = false, length = 64, updatable = false)
    private String source;

    @Column(name = "unlocked_at", nullable = false, updatable = false)
    private Instant unlockedAt;

    protected UserAvatarUnlockEntity() {
    }

    public static UserAvatarUnlockEntity create(UserEntity user, String avatarKey, String source) {
        UserAvatarUnlockEntity entity = new UserAvatarUnlockEntity();
        entity.id = UUID.randomUUID();
        entity.user = user;
        entity.avatarKey = avatarKey;
        entity.source = source;
        entity.unlockedAt = Instant.now();
        return entity;
    }

    public String getAvatarKey() { return avatarKey; }
    public String getSource() { return source; }
    public Instant getUnlockedAt() { return unlockedAt; }
}
