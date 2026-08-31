package com.partygameonline.history.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchPlayerJpaRepository extends JpaRepository<MatchPlayerEntity, UUID> {

    List<MatchPlayerEntity> findByMatchIdOrderBySeatAscIdAsc(UUID matchId);

    List<MatchPlayerEntity> findByMatchIdInOrderByMatchIdAscSeatAscIdAsc(Iterable<UUID> matchIds);

    List<MatchPlayerEntity> findByPlayerIdInOrderByCreatedAtDescIdAsc(Iterable<String> playerIds);

    @Query("""
            SELECT player
            FROM MatchPlayerEntity player
            JOIN MatchEntity match ON match.id = player.matchId
            WHERE match.gameId = :gameId
              AND player.playerId IN :playerIds
            ORDER BY player.createdAt DESC, player.id ASC
            """)
    List<MatchPlayerEntity> findByGameIdAndPlayerIdInOrderByCreatedAtDescIdAsc(
            @Param("gameId") String gameId,
            @Param("playerIds") List<String> playerIds
    );

    List<MatchPlayerEntity> findByPlayerIdContainingIgnoreCaseOrderByCreatedAtDescIdAsc(
            String playerId,
            Pageable pageable
    );
}
