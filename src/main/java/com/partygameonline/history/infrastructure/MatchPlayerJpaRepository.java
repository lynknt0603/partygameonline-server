package com.partygameonline.history.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchPlayerJpaRepository extends JpaRepository<MatchPlayerEntity, UUID> {

    List<MatchPlayerEntity> findByMatchIdOrderBySeatAscIdAsc(UUID matchId);

    List<MatchPlayerEntity> findByMatchIdInOrderByMatchIdAscSeatAscIdAsc(Iterable<UUID> matchIds);

    List<MatchPlayerEntity> findByPlayerIdInOrderByCreatedAtDescIdAsc(Iterable<String> playerIds);
}
