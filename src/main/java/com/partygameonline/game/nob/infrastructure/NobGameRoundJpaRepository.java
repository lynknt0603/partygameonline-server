package com.partygameonline.game.nob.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NobGameRoundJpaRepository extends JpaRepository<NobGameRoundEntity, UUID> {

    List<NobGameRoundEntity> findByGameIdInAndPlayerIdOrderByGameIdAscRoundNumberAscIdAsc(
            Iterable<UUID> gameIds,
            String playerId
    );
}
