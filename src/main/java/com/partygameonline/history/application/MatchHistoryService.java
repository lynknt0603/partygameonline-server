package com.partygameonline.history.application;

import com.partygameonline.common.error.ResourceNotFoundException;
import com.partygameonline.game.nob.domain.NobGameState;
import com.partygameonline.game.runtime.GameSession;
import com.partygameonline.history.api.dto.MatchPlayerResponse;
import com.partygameonline.history.api.dto.MatchResponse;
import com.partygameonline.history.api.dto.PageResponse;
import com.partygameonline.history.infrastructure.MatchEntity;
import com.partygameonline.history.infrastructure.MatchJpaRepository;
import com.partygameonline.history.infrastructure.MatchPlayerEntity;
import com.partygameonline.history.infrastructure.MatchPlayerJpaRepository;
import com.partygameonline.room.domain.GameRoom;
import com.partygameonline.room.domain.RoomPlayer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchHistoryService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final MatchJpaRepository matchJpaRepository;
    private final MatchPlayerJpaRepository matchPlayerJpaRepository;

    public MatchHistoryService(
            MatchJpaRepository matchJpaRepository,
            MatchPlayerJpaRepository matchPlayerJpaRepository
    ) {
        this.matchJpaRepository = matchJpaRepository;
        this.matchPlayerJpaRepository = matchPlayerJpaRepository;
    }

    @Transactional
    public void recordIfFinished(GameRoom room, GameSession session) {
        if (session == null || !session.isFinished() || session.getPersistedMatchId() != null) {
            return;
        }
        Instant finishedAt = session.getFinishedAt() == null ? Instant.now() : session.getFinishedAt();
        MatchEntity match = matchJpaRepository.save(MatchEntity.completed(
                session.getGameId(),
                room.getId().value(),
                session.getWinnerPlayerId(),
                session.getResult() == null ? "COMPLETED" : session.getResult(),
                session.getStartedAt(),
                finishedAt
        ));
        Set<String> winners = new LinkedHashSet<>();
        if (session.getState() instanceof NobGameState nob && !nob.getWinnerPlayerIds().isEmpty()) {
            winners.addAll(nob.getWinnerPlayerIds());
        } else if (session.getWinnerPlayerId() != null) {
            winners.add(session.getWinnerPlayerId());
        }
        int seat = 0;
        for (RoomPlayer player : room.getPlayers()) {
            boolean winner = winners.contains(player.getPlayerId());
            matchPlayerJpaRepository.save(MatchPlayerEntity.newPlayer(
                    match.getId(),
                    null,
                    player.getPlayerId(),
                    player.getDisplayName(),
                    seat,
                    winner ? "WIN" : "LOSS"
            ));
            seat += 1;
        }
        session.markPersisted(match.getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<MatchResponse> listForPlayer(String playerId, Integer page, Integer size) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null ? DEFAULT_SIZE : Math.min(Math.max(size, 1), MAX_SIZE);
        Page<MatchEntity> matches = matchJpaRepository.findFinishedForPlayer(
                playerId,
                PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "finishedAt"))
        );
        List<MatchResponse> content = new ArrayList<>();
        for (MatchEntity match : matches.getContent()) {
            content.add(toResponse(match));
        }
        return new PageResponse<>(
                content,
                matches.getNumber(),
                matches.getSize(),
                matches.getTotalElements(),
                matches.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public MatchResponse getForPlayer(String playerId, UUID matchId) {
        MatchEntity match = matchJpaRepository.findById(matchId)
                .filter(item -> item.getFinishedAt() != null)
                .orElseThrow(() -> new ResourceNotFoundException("MATCH_NOT_FOUND", "The match was not found"));
        List<MatchPlayerEntity> players = matchPlayerJpaRepository.findByMatchIdOrderBySeatAscIdAsc(matchId);
        boolean participant = players.stream().anyMatch(player -> player.getPlayerId().equals(playerId));
        if (!participant) {
            throw new ResourceNotFoundException("MATCH_NOT_FOUND", "The match was not found");
        }
        return toResponse(match, players);
    }

    private MatchResponse toResponse(MatchEntity match) {
        return toResponse(match, matchPlayerJpaRepository.findByMatchIdOrderBySeatAscIdAsc(match.getId()));
    }

    private static MatchResponse toResponse(MatchEntity match, List<MatchPlayerEntity> players) {
        return new MatchResponse(
                match.getId(),
                match.getGameId(),
                match.getRoomId(),
                match.getStartedAt(),
                match.getFinishedAt(),
                match.getWinnerPlayerId(),
                match.getResult(),
                players.stream()
                        .map(player -> new MatchPlayerResponse(
                                player.getPlayerId(),
                                player.getDisplayName(),
                                player.getSeat() == null ? null : player.getSeat().intValue(),
                                "WIN".equals(player.getResult())
                        ))
                        .toList()
        );
    }
}
