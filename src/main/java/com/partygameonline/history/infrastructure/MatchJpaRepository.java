package com.partygameonline.history.infrastructure;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchJpaRepository extends JpaRepository<MatchEntity, UUID> {

    @Query("""
            select m from MatchEntity m
            where m.finishedAt is not null
              and exists (
                select 1 from MatchPlayerEntity p
                where p.matchId = m.id and p.playerId = :playerId
              )
            """)
    Page<MatchEntity> findFinishedForPlayer(@Param("playerId") String playerId, Pageable pageable);
}
