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
import com.partygameonline.game.notinmypot.NotInMyPotGameManifest;
import com.partygameonline.game.wheresthebone.WheresTheBoneGameManifest;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneGameState;
import com.partygameonline.history.api.dto.MatchHistoryItemResponse;
import com.partygameonline.history.api.dto.MatchHistoryPlayerResponse;
import com.partygameonline.game.runtime.GameSession;
import com.partygameonline.profile.application.PlayerProgressService;
import com.partygameonline.history.api.dto.MatchPlayerResponse;
import com.partygameonline.history.api.dto.MatchResponse;
import com.partygameonline.history.api.dto.PageResponse;
import com.partygameonline.history.infrastructure.MatchEntity;
import com.partygameonline.history.infrastructure.MatchJpaRepository;
import com.partygameonline.history.infrastructure.MatchPlayerEntity;
import com.partygameonline.history.infrastructure.MatchPlayerJpaRepository;
import com.partygameonline.ranking.application.EloRatingService;
import com.partygameonline.room.domain.GameRoom;
import com.partygameonline.room.domain.PlayerLobbyState;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchHistoryService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final Logger log = LoggerFactory.getLogger(MatchHistoryService.class);

    private final MatchJpaRepository matchJpaRepository;
    private final MatchPlayerJpaRepository matchPlayerJpaRepository;
    private final GameRegistry gameRegistry;
    private final NobGameRoundJpaRepository nobGameRoundJpaRepository;
    private final EloRatingService eloRatingService;
    private final PlayerProgressService playerProgressService;

    @Autowired
    public MatchHistoryService(
            MatchJpaRepository matchJpaRepository,
            MatchPlayerJpaRepository matchPlayerJpaRepository,
            GameRegistry gameRegistry,
            NobGameRoundJpaRepository nobGameRoundJpaRepository,
            EloRatingService eloRatingService,
            PlayerProgressService playerProgressService
    ) {
        this.matchJpaRepository = matchJpaRepository;
        this.matchPlayerJpaRepository = matchPlayerJpaRepository;
        this.gameRegistry = gameRegistry;
        this.nobGameRoundJpaRepository = nobGameRoundJpaRepository;
        this.eloRatingService = eloRatingService;
        this.playerProgressService = playerProgressService;
    }

    public MatchHistoryService(
            MatchJpaRepository matchJpaRepository,
            MatchPlayerJpaRepository matchPlayerJpaRepository,
            GameRegistry gameRegistry
    ) {
        this(matchJpaRepository, matchPlayerJpaRepository, gameRegistry, null, null, null);
    }

    public MatchHistoryService(
            MatchJpaRepository matchJpaRepository,
            MatchPlayerJpaRepository matchPlayerJpaRepository,
            GameRegistry gameRegistry,
            NobGameRoundJpaRepository nobGameRoundJpaRepository
    ) {
        this(matchJpaRepository, matchPlayerJpaRepository, gameRegistry, nobGameRoundJpaRepository, null, null);
    }

    public MatchHistoryService(
            MatchJpaRepository matchJpaRepository,
            MatchPlayerJpaRepository matchPlayerJpaRepository,
            GameRegistry gameRegistry,
            NobGameRoundJpaRepository nobGameRoundJpaRepository,
            EloRatingService eloRatingService
    ) {
        this(matchJpaRepository, matchPlayerJpaRepository, gameRegistry,
                nobGameRoundJpaRepository, eloRatingService, null);
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
            recordDisconnectedNotInMyPotForfeits(room, session);
            boolean unrankedForfeit = isUnrankedForfeit(session);
            Instant finishedAt = session.getFinishedAt() == null ? Instant.now() : session.getFinishedAt();
            MatchEntity match = matchJpaRepository.save(MatchEntity.completed(
                    session.getGameId(),
                    room.getId().value(),
                    unrankedForfeit ? null : session.getWinnerPlayerId(),
                    unrankedForfeit ? "UNRANKED_FORFEIT"
                            : session.getResult() == null ? "COMPLETED" : session.getResult(),
                    session.getStartedAt(),
                    finishedAt
            ));
            Set<String> winners = unrankedForfeit ? Set.of() : winners(session);
            List<String> participantIds = persistedParticipantIds(room, session);
            for (int seat = 0; seat < participantIds.size(); seat++) {
                String playerId = participantIds.get(seat);
                RoomPlayer roomPlayer = room.findPlayer(playerId).orElse(null);
                String displayName = roomPlayer == null
                        ? session.getConfig().displayName(playerId)
                        : roomPlayer.getDisplayName();
                boolean winner = winners.contains(playerId);
                PlayerStatistics statistics = playerStatistics(session, playerId);
                matchPlayerJpaRepository.save(MatchPlayerEntity.newPlayer(
                        match.getId(),
                        null,
                        playerId,
                        displayName,
                        seat,
                        winner ? "WIN" : "LOSS",
                        statistics.score(),
                        statistics.role(),
                        statistics.bloodline()
                ));
            }
            persistNobRounds(match.getId(), session);
            applyElo(match, session, winners);
            if (playerProgressService != null && !unrankedForfeit) {
                playerProgressService.recordFinishedGame(session.getState(), winners, participantIds);
            }
            session.markPersisted(match.getId());
        }
    }

    /**
     * Records a leave or expired disconnect from a running game immediately as one loss.
     * The live session continues for the remaining players; the forfeiting
     * player is excluded from the eventual completed-match ELO settlement.
     */
    @Transactional
    public void recordForfeit(GameRoom room, GameSession session, String playerId, String displayName) {
        if (room == null || session == null
                || (session.isFinished() && !NotInMyPotGameManifest.ID.equals(session.getGameId()))
                || WheresTheBoneGameManifest.ID.equals(session.getGameId())
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
                EloRatingService.EloMatchResult result = eloRatingService.applyForfeit(
                        session.getGameId(),
                        playerId
                );
                if (NotInMyPotGameManifest.ID.equals(session.getGameId())) {
                    recordEloChanges(session, result);
                }
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
        boolean wheresTheBone = WheresTheBoneGameManifest.ID.equals(session.getGameId());
        List<String> playerIds = wheresTheBone
                ? session.getConfig().playerIds()
                : session.getConfig().playerIds().stream()
                        .filter(playerId -> !session.isForfeited(playerId))
                        .toList();
        // A match with fewer than two eligible players is an unranked
        // mass-forfeit result. Awarding a sole survivor a winner reward mints
        // Elo and is trivially boostable with alternate accounts.
        if (NotInMyPotGameManifest.ID.equals(session.getGameId()) && playerIds.size() < 2) {
            match.markEloProcessed();
            matchJpaRepository.save(match);
            return;
        }
        Set<String> eloWinners = winners;
        if (wheresTheBone && !validWheresTheBoneOutcome(match, session, winners, playerIds)) {
            match.markEloProcessed();
            matchJpaRepository.save(match);
            return;
        }
        EloRatingService.EloMatchResult result = eloRatingService.completeMatch(
                session.getGameId(),
                playerIds,
                eloWinners,
                session.getState()
        );
        if (session.getState() instanceof GameEloChangeSink sink) {
            sink.recordEloChanges(toGameEloChanges(result));
        }
        if (wheresTheBone) {
            int pool = result.changes().values().stream()
                    .filter(change -> change.eloDelta() > 0)
                    .mapToInt(EloRatingService.EloChange::eloDelta)
                    .sum();
            log.info(
                    "ELO matchId={} gameCode={} pool={} winnerCount={} loserCount={}",
                    match.getId(),
                    session.getGameId(),
                    pool,
                    winners.size(),
                    playerIds.size() - winners.size()
            );
        }
        match.markEloProcessed();
        matchJpaRepository.save(match);
    }

    private static boolean isUnrankedForfeit(GameSession session) {
        if (!NotInMyPotGameManifest.ID.equals(session.getGameId())
                || session.getConfig().playerIds().size() < 2) {
            return false;
        }
        long eligible = session.getConfig().playerIds().stream()
                .filter(playerId -> !session.isForfeited(playerId))
                .count();
        return eligible < 2;
    }

    private static void recordEloChanges(GameSession session, EloRatingService.EloMatchResult result) {
        if (session.getState() instanceof GameEloChangeSink sink) {
            sink.recordEloChanges(toGameEloChanges(result));
        }
    }

    private static Map<String, GameEloChange> toGameEloChanges(EloRatingService.EloMatchResult result) {
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
        return changes;
    }

    private static List<String> persistedParticipantIds(GameRoom room, GameSession session) {
        if (WheresTheBoneGameManifest.ID.equals(session.getGameId())) {
            return session.getConfig().playerIds();
        }
        return room.getPlayers().stream()
                .map(RoomPlayer::getPlayerId)
                .filter(playerId -> !NotInMyPotGameManifest.ID.equals(session.getGameId())
                        || !session.isForfeited(playerId))
                .toList();
    }

    private void recordDisconnectedNotInMyPotForfeits(GameRoom room, GameSession session) {
        if (!NotInMyPotGameManifest.ID.equals(session.getGameId())) {
            return;
        }
        for (RoomPlayer player : room.getPlayers()) {
            if (player.getState() == PlayerLobbyState.DISCONNECTED) {
                recordForfeit(room, session, player.getPlayerId(), player.getDisplayName());
            }
        }
    }

    private static boolean validWheresTheBoneOutcome(
            MatchEntity match,
            GameSession session,
            Set<String> winners,
            List<String> playerIds
    ) {
        if (!(session.getState() instanceof WheresTheBoneGameState state) || !state.isFinished()) {
            return false;
        }
        String result = match.getResult();
        if (result != null && Set.of("CANCELLED", "ABORTED", "INVALID")
                .contains(result.trim().toUpperCase(java.util.Locale.ROOT))) {
            return false;
        }
        return !winners.isEmpty()
                && winners.size() < playerIds.size()
                && winners.stream().allMatch(playerIds::contains);
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
