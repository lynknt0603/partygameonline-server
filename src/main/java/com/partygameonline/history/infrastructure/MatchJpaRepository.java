package com.partygameonline.history.infrastructure;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

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

    @Query("""
            select m from MatchEntity m
            where m.finishedAt is not null
              and m.gameId = :gameId
              and exists (
                select 1 from MatchPlayerEntity p
                where p.matchId = m.id and p.playerId = :playerId
              )
            """)
            Page<MatchEntity> findFinishedForPlayerAndGame(
            @Param("playerId") String playerId,
            @Param("gameId") String gameId,
            Pageable pageable
    );

    @Query("""
            select m from MatchEntity m
            where m.finishedAt is not null
              and m.gameId = :gameId
              and exists (
                select 1 from MatchPlayerEntity p
                where p.matchId = m.id and p.playerId = :playerId
              )
            order by m.finishedAt asc
            """)
    List<MatchEntity> findAllFinishedForPlayerAndGame(
            @Param("playerId") String playerId,
            @Param("gameId") String gameId
    );

    @Query("""
            select m from MatchEntity m
            where m.finishedAt is not null
              and exists (
                select 1 from MatchPlayerEntity p
                where p.matchId = m.id and p.playerId = :playerId
              )
            order by m.finishedAt asc
            """)
    List<MatchEntity> findAllFinishedForPlayer(@Param("playerId") String playerId);
}
