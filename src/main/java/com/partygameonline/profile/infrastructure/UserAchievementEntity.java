package com.partygameonline.profile.infrastructure;

import com.partygameonline.profile.domain.AchievementDefinition;
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
@Table(name = "user_achievement", uniqueConstraints = @UniqueConstraint(
        name = "uq_user_achievement_user_code", columnNames = {"user_id", "achievement_code"}
))
public class UserAchievementEntity {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserEntity user;

    @Column(name = "achievement_code", nullable = false, length = 64, updatable = false)
    private String achievementCode;

    @Column(nullable = false)
    private int progress;

    @Column(nullable = false)
    private int target;

    @Column(name = "unlocked_at")
    private Instant unlockedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAchievementEntity() {
    }

    public static UserAchievementEntity create(UserEntity user, AchievementDefinition definition) {
        Instant now = Instant.now();
        UserAchievementEntity entity = new UserAchievementEntity();
        entity.id = UUID.randomUUID();
        entity.user = user;
        entity.achievementCode = definition.name();
        entity.target = definition.target();
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public boolean addProgress(int amount) {
        if (amount <= 0 || isUnlocked()) {
            return false;
        }
        return setProgress(progress + amount);
    }

    public boolean setProgress(int value) {
        int next = Math.min(Math.max(value, 0), target);
        boolean newlyUnlocked = unlockedAt == null && next >= target;
        progress = next;
        updatedAt = Instant.now();
        if (newlyUnlocked) {
            unlockedAt = updatedAt;
        }
        return newlyUnlocked;
    }

    public boolean isUnlocked() {
        return unlockedAt != null;
    }

    public String getAchievementCode() { return achievementCode; }
    public int getProgress() { return progress; }
    public int getTarget() { return target; }
    public Instant getUnlockedAt() { return unlockedAt; }
}
