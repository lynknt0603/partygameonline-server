package com.partygameonline.user.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByUsername(String username);

    Optional<UserEntity> findByUserKey(String userKey);

    List<UserEntity> findByUsernameContainingIgnoreCaseOrUserKeyContainingIgnoreCase(
            String username,
            String userKey,
            Pageable pageable
    );

    List<UserEntity> findByUserKeyIn(Iterable<String> userKeys);

    boolean existsByUsername(String username);
}
