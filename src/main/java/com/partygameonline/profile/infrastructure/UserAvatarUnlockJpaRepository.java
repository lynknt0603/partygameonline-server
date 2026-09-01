package com.partygameonline.profile.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAvatarUnlockJpaRepository extends JpaRepository<UserAvatarUnlockEntity, UUID> {
    List<UserAvatarUnlockEntity> findByUserIdOrderByAvatarKeyAsc(UUID userId);
    boolean existsByUserIdAndAvatarKey(UUID userId, String avatarKey);
}
