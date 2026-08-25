package com.partygameonline.user.infrastructure;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByUsername(String username);

    List<UserEntity> findByUserKeyIn(Iterable<String> userKeys);

    boolean existsByUsername(String username);
}
