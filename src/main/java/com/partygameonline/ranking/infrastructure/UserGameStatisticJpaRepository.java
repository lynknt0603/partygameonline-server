package com.partygameonline.ranking.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface UserGameStatisticJpaRepository extends JpaRepository<UserGameStatisticEntity, UUID> {

    Optional<UserGameStatisticEntity> findByUserIdAndGameCode(String userId, String gameCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select statistic from UserGameStatisticEntity statistic
            where statistic.userId = :userId and statistic.gameCode = :gameCode
            """)
    Optional<UserGameStatisticEntity> findByUserIdAndGameCodeForUpdate(
            @Param("userId") String userId,
            @Param("gameCode") String gameCode
    );

    List<UserGameStatisticEntity> findByUserIdOrderByGameCodeAsc(String userId);
}
