package com.partygameonline.history.application;

import com.partygameonline.common.error.ResourceNotFoundException;
import com.partygameonline.game.core.GameEloChange;
import com.partygameonline.game.core.GameEloChangeSink;
import com.partygameonline.game.core.GameOutcomeState;
import com.partygameonline.game.core.GamePlayerOutcome;
import com.partygameonline.game.nob.domain.NobGameState;
import com.partygameonline.game.nob.infrastructure.NobGameRoundEntity;
import com.partygameonline.game.nob.infrastructure.NobGameRoundJpaRepository;
import com.partygameonline.game.core.GameRegistry;
import com.partygameonline.history.api.dto.MatchHistoryItemResponse;
import com.partygameonline.history.api.dto.MatchHistoryPlayerResponse;
import com.partygameonline.game.runtime.GameSession;
import com.partygameonline.history.api.dto.MatchPlayerResponse;
import com.partygameonline.history.api.dto.MatchResponse;
import com.partygameonline.history.api.dto.PageResponse;
import com.partygameonline.history.infrastructure.MatchEntity;
import com.partygameonline.history.infrastructure.MatchJpaRepository;
import com.partygameonline.history.infrastructure.MatchPlayerEntity;
import com.partygameonline.history.infrastructure.MatchPlayerJpaRepository;
import com.partygameonline.ranking.application.EloRatingService;
import com.partygameonline.room.domain.GameRoom;
import com.partygameonline.room.domain.RoomPlayer;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchHistoryService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final MatchJpaRepository matchJpaRepository;
    private final MatchPlayerJpaRepository matchPlayerJpaRepository;
    private final GameRegistry gameRegistry;
    private final NobGameRoundJpaRepository nobGameRoundJpaRepository;
    private final EloRatingService eloRatingService;

    @Autowired
    public MatchHistoryService(
            MatchJpaRepository matchJpaRepository,
            MatchPlayerJpaRepository matchPlayerJpaRepository,
            GameRegistry gameRegistry,
            NobGameRoundJpaRepository nobGameRoundJpaRepository,
            EloRatingService eloRatingService
    ) {
        this.matchJpaRepository = matchJpaRepository;
        this.matchPlayerJpaRepository = matchPlayerJpaRepository;
        this.gameRegistry = gameRegistry;
        this.nobGameRoundJpaRepository = nobGameRoundJpaRepository;
        this.eloRatingService = eloRatingService;
    }

    public MatchHistoryService(
            MatchJpaRepository matchJpaRepository,
            MatchPlayerJpaRepository matchPlayerJpaRepository,
            GameRegistry gameRegistry
    ) {
        this(matchJpaRepository, matchPlayerJpaRepository, gameRegistry, null, null);
    }

    public MatchHistoryService(
            MatchJpaRepository matchJpaRepository,
            MatchPlayerJpaRepository matchPlayerJpaRepository,
            GameRegistry gameRegistry,
            NobGameRoundJpaRepository nobGameRoundJpaRepository
    ) {
        this(matchJpaRepository, matchPlayerJpaRepository, gameRegistry, nobGameRoundJpaRepository, null);
    }

    @Transactional
    public void recordIfFinished(GameRoom room, GameSession session) {
        if (session == null) {
            return;
        }
        synchronized (session) {
            if (!session.isFinished() || session.getPersistedMatchId() != null) {
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
            Set<String> winners = winners(session);
            int seat = 0;
            for (RoomPlayer player : room.getPlayers()) {
                boolean winner = winners.contains(player.getPlayerId());
                PlayerStatistics statistics = playerStatistics(session, player.getPlayerId());
                matchPlayerJpaRepository.save(MatchPlayerEntity.newPlayer(
                        match.getId(),
                        null,
                        player.getPlayerId(),
                        player.getDisplayName(),
                        seat,
                        winner ? "WIN" : "LOSS",
                        statistics.score(),
                        statistics.role(),
                        statistics.bloodline()
                ));
                seat += 1;
            }
            persistNobRounds(match.getId(), session);
            applyElo(match, session, winners);
            session.markPersisted(match.getId());
        }
    }

    /**
     * Records an explicit leave from a running game immediately as one loss.
     * The live session continues for the remaining players; the forfeiting
     * player is excluded from the eventual completed-match ELO settlement.
     */
    @Transactional
    public void recordForfeit(GameRoom room, GameSession session, String playerId, String displayName) {
        if (room == null || session == null || session.isFinished()
                || playerId == null || !session.getConfig().playerIds().contains(playerId)
                || session.isForfeited(playerId)) {
            return;
        }
        synchronized (session) {
            if (session.isForfeited(playerId)) {
                return;
            }
            Instant forfeitedAt = Instant.now();
            MatchEntity match = matchJpaRepository.save(MatchEntity.completed(
                    session.getGameId(),
                    room.getId().value(),
                    null,
                    "FORFEIT",
                    session.getStartedAt(),
                    forfeitedAt
            ));
            PlayerStatistics statistics = playerStatistics(session, playerId);
            matchPlayerJpaRepository.save(MatchPlayerEntity.newPlayer(
                    match.getId(),
                    null,
                    playerId,
                    displayName == null || displayName.isBlank() ? playerId : displayName,
                    session.getConfig().playerIds().indexOf(playerId),
                    "LOSS",
                    statistics.score(),
                    statistics.role(),
                    statistics.bloodline()
            ));
            if (eloRatingService != null) {
                eloRatingService.applyForfeit(session.getGameId(), playerId);
            }
            match.markEloProcessed();
            matchJpaRepository.save(match);
            session.markForfeited(playerId);
        }
    }

    private Set<String> winners(GameSession session) {
        Set<String> winners = new LinkedHashSet<>();
        if (session.getState() instanceof GameOutcomeState outcome && !outcome.winnerPlayerIds().isEmpty()) {
            winners.addAll(outcome.winnerPlayerIds());
        } else if (session.getWinnerPlayerId() != null) {
            winners.add(session.getWinnerPlayerId());
        }
        return winners;
    }

    private void applyElo(MatchEntity match, GameSession session, Set<String> winners) {
        if (eloRatingService == null || match.isEloProcessed()) {
            return;
        }
        List<String> playerIds = session.getConfig().playerIds().stream()
                .filter(playerId -> !session.isForfeited(playerId))
                .toList();
        EloRatingService.EloMatchResult result = eloRatingService.completeMatch(
                session.getGameId(),
                playerIds,
                winners,
                session.getState()
        );
        if (session.getState() instanceof GameEloChangeSink sink) {
            Map<String, GameEloChange> changes = new LinkedHashMap<>();
            result.changes().forEach((playerId, change) -> changes.put(
                    playerId,
                    new GameEloChange(
                            change.playerId(),
                            change.winner(),
                            change.oldElo(),
                            change.eloDelta(),
                            change.newElo()
                    )
            ));
            sink.recordEloChanges(changes);
        }
        match.markEloProcessed();
        matchJpaRepository.save(match);
    }

    private void persistNobRounds(UUID gameId, GameSession session) {
        if (nobGameRoundJpaRepository == null || !(session.getState() instanceof NobGameState nob)) {
            return;
        }
        List<NobGameRoundEntity> rounds = nob.getCompletedRounds().stream()
                .flatMap(round -> round.players().stream()
                        .map(player -> NobGameRoundEntity.from(gameId, round, player)))
                .toList();
        if (!rounds.isEmpty()) {
            nobGameRoundJpaRepository.saveAll(rounds);
        }
    }

    private static PlayerStatistics playerStatistics(GameSession session, String playerId) {
        if (session.getState() instanceof GameOutcomeState outcome) {
            GamePlayerOutcome player = outcome.playerOutcome(playerId);
            return player == null
                    ? PlayerStatistics.EMPTY
                    : new PlayerStatistics(player.score(), player.role(), player.bloodline());
        }
        return PlayerStatistics.EMPTY;
    }

    private record PlayerStatistics(Integer score, String role, String bloodline) {
        private static final PlayerStatistics EMPTY = new PlayerStatistics(null, null, null);
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

    @Transactional(readOnly = true)
    public PageResponse<MatchHistoryItemResponse> listHistoryForPlayer(
            String playerId,
            Integer page,
            Integer size,
            String gameId
    ) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null ? DEFAULT_SIZE : Math.min(Math.max(size, 1), MAX_SIZE);
        Page<MatchEntity> matches = gameId == null || gameId.isBlank()
                ? matchJpaRepository.findFinishedForPlayer(
                        playerId,
                        PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "finishedAt"))
                )
                : matchJpaRepository.findFinishedForPlayerAndGame(
                        playerId,
                        gameId,
                        PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "finishedAt"))
                );
        Map<UUID, List<MatchPlayerEntity>> playersByMatch = playersByMatch(matches.getContent());
        List<MatchHistoryItemResponse> content = matches.getContent().stream()
                .map(match -> toHistoryItem(
                        match,
                        playersByMatch.getOrDefault(match.getId(), List.of()),
                        playerId
                ))
                .toList();
        return new PageResponse<>(
                content,
                matches.getNumber(),
                matches.getSize(),
                matches.getTotalElements(),
                matches.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public MatchHistoryItemResponse getHistoryForPlayer(String playerId, UUID matchId) {
        MatchEntity match = matchJpaRepository.findById(matchId)
                .filter(item -> item.getFinishedAt() != null)
                .orElseThrow(() -> new ResourceNotFoundException("MATCH_NOT_FOUND", "The match was not found"));
        List<MatchPlayerEntity> players = matchPlayerJpaRepository.findByMatchIdOrderBySeatAscIdAsc(matchId);
        if (players.stream().noneMatch(player -> player.getPlayerId().equals(playerId))) {
            throw new ResourceNotFoundException("MATCH_NOT_FOUND", "The match was not found");
        }
        return toHistoryItem(match, players, playerId);
    }

    private Map<UUID, List<MatchPlayerEntity>> playersByMatch(List<MatchEntity> matches) {
        if (matches.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<MatchPlayerEntity>> grouped = new HashMap<>();
        matchPlayerJpaRepository.findByMatchIdInOrderByMatchIdAscSeatAscIdAsc(
                        matches.stream().map(MatchEntity::getId).toList()
                )
                .forEach(player -> grouped.computeIfAbsent(player.getMatchId(), ignored -> new ArrayList<>()).add(player));
        return grouped;
    }

    private MatchHistoryItemResponse toHistoryItem(
            MatchEntity match,
            List<MatchPlayerEntity> players,
            String viewerPlayerId
    ) {
        MatchPlayerEntity viewer = players.stream()
                .filter(player -> player.getPlayerId().equals(viewerPlayerId))
                .findFirst()
                .orElse(null);
        String viewerResult = viewer == null ? resultFor(match, viewerPlayerId) : resultFor(viewer);
        return new MatchHistoryItemResponse(
                match.getId(),
                match.getGameId(),
                gameRegistry.findById(match.getGameId()).map(game -> game.name()).orElse(match.getGameId()),
                match.getRoomId(),
                match.getFinishedAt(),
                durationSeconds(match),
                viewerResult,
                viewer == null ? null : viewer.getScore(),
                viewer == null ? null : viewer.getRole(),
                viewer == null ? null : viewer.getBloodline(),
                players.stream().map(this::toHistoryPlayer).toList()
        );
    }

    private MatchHistoryPlayerResponse toHistoryPlayer(MatchPlayerEntity player) {
        return new MatchHistoryPlayerResponse(
                player.getPlayerId(),
                player.getDisplayName(),
                resultFor(player),
                player.getScore(),
                player.getRole(),
                player.getBloodline()
        );
    }

    private String resultFor(MatchPlayerEntity player) {
        return player.getResult() == null ? "LOSS" : player.getResult();
    }

    private String resultFor(MatchEntity match, String playerId) {
        if (match.getWinnerPlayerId() == null) {
            return "DRAW";
        }
        return playerId.equals(match.getWinnerPlayerId()) ? "WIN" : "LOSS";
    }

    private long durationSeconds(MatchEntity match) {
        if (match.getStartedAt() == null || match.getFinishedAt() == null) {
            return 0;
        }
        return Math.max(0, Duration.between(match.getStartedAt(), match.getFinishedAt()).getSeconds());
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
                                "WIN".equals(player.getResult()),
                                player.getResult() == null ? "LOSS" : player.getResult(),
                                player.getScore(),
                                player.getRole(),
                                player.getBloodline()
                        ))
                        .toList()
        );
    }
}
