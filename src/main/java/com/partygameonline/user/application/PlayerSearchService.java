package com.partygameonline.user.application;

import com.partygameonline.history.infrastructure.MatchPlayerEntity;
import com.partygameonline.history.infrastructure.MatchPlayerJpaRepository;
import com.partygameonline.user.api.dto.PlayerSearchResponse;
import com.partygameonline.user.infrastructure.UserEntity;
import com.partygameonline.user.infrastructure.UserJpaRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerSearchService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final int MIN_QUERY_LENGTH = 2;

    private final UserJpaRepository userRepository;
    private final MatchPlayerJpaRepository matchPlayerRepository;

    public PlayerSearchService(
            UserJpaRepository userRepository,
            MatchPlayerJpaRepository matchPlayerRepository
    ) {
        this.userRepository = userRepository;
        this.matchPlayerRepository = matchPlayerRepository;
    }

    @Transactional(readOnly = true)
    public List<PlayerSearchResponse> search(String query, Integer limit) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }

        int resultLimit = limit == null
                ? DEFAULT_LIMIT
                : Math.min(Math.max(limit, 1), MAX_LIMIT);
        Map<String, PlayerSearchResponse> results = new LinkedHashMap<>();
        PageRequest pageRequest = PageRequest.of(0, resultLimit);

        userRepository.findByUsernameContainingIgnoreCaseOrUserKeyContainingIgnoreCase(
                        normalizedQuery,
                        normalizedQuery,
                        pageRequest
                )
                .forEach(user -> results.put(user.getUserKey(), toResponse(user)));

        if (results.size() < resultLimit) {
            matchPlayerRepository.findByPlayerIdContainingIgnoreCaseOrderByCreatedAtDescIdAsc(
                            normalizedQuery,
                            pageRequest
                    )
                    .forEach(player -> results.putIfAbsent(
                            player.getPlayerId(),
                            toGuestResponse(player)
                    ));
        }

        return new ArrayList<>(results.values()).stream()
                .limit(resultLimit)
                .toList();
    }

    private static PlayerSearchResponse toResponse(UserEntity user) {
        return new PlayerSearchResponse(
                user.getUserKey(),
                user.getUsername(),
                user.getDisplayName()
        );
    }

    private static PlayerSearchResponse toGuestResponse(MatchPlayerEntity player) {
        return new PlayerSearchResponse(
                player.getPlayerId(),
                null,
                player.getDisplayName()
        );
    }
}
