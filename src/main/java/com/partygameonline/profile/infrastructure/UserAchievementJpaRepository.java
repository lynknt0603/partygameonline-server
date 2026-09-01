package com.partygameonline.profile.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAchievementJpaRepository extends JpaRepository<UserAchievementEntity, UUID> {
    List<UserAchievementEntity> findByUserIdOrderByAchievementCodeAsc(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from UserAchievementEntity a where a.user.id = :userId and a.achievementCode = :code")
    Optional<UserAchievementEntity> findForUpdate(@Param("userId") UUID userId, @Param("code") String code);
}
