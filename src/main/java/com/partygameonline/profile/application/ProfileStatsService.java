package com.partygameonline.profile.application;

import com.partygameonline.game.nob.NobGameManifest;
import com.partygameonline.game.nob.infrastructure.NobGameRoundEntity;
import com.partygameonline.game.nob.infrastructure.NobGameRoundJpaRepository;
import com.partygameonline.history.infrastructure.MatchEntity;
import com.partygameonline.history.infrastructure.MatchJpaRepository;
import com.partygameonline.history.infrastructure.MatchPlayerEntity;
import com.partygameonline.history.infrastructure.MatchPlayerJpaRepository;
import com.partygameonline.profile.api.dto.ProfileStatsResponse;
import com.partygameonline.ranking.infrastructure.UserGameStatisticEntity;
import com.partygameonline.ranking.infrastructure.UserGameStatisticJpaRepository;
import com.partygameonline.session.domain.PlayerPrincipal;
import com.partygameonline.session.domain.SessionKind;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileStatsService {

    private static final String DEFAULT_AVATAR = "/assets/avatar-default.png";
    private static final String PLATFORM = "Web";
    private static final DateTimeFormatter JOINED_AT_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneOffset.UTC);

    private final MatchJpaRepository matchRepository;
    private final MatchPlayerJpaRepository playerRepository;
    private final NobGameRoundJpaRepository roundRepository;
    private final UserGameStatisticJpaRepository statisticRepository;

    @Autowired
    public ProfileStatsService(
            MatchJpaRepository matchRepository,
            MatchPlayerJpaRepository playerRepository,
            NobGameRoundJpaRepository roundRepository,
            UserGameStatisticJpaRepository statisticRepository
    ) {
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.roundRepository = roundRepository;
        this.statisticRepository = statisticRepository;
    }

    public ProfileStatsService(
            MatchJpaRepository matchRepository,
            MatchPlayerJpaRepository playerRepository
    ) {
        this(matchRepository, playerRepository, null, null);
    }

    public ProfileStatsService(
            MatchJpaRepository matchRepository,
            MatchPlayerJpaRepository playerRepository,
            NobGameRoundJpaRepository roundRepository
    ) {
        this(matchRepository, playerRepository, roundRepository, null);
    }

    @Transactional(readOnly = true)
    public ProfileStatsResponse getStats(PlayerPrincipal principal) {
        List<MatchEntity> nobMatches = matchRepository.findAllFinishedForPlayerAndGame(
                principal.playerId(),
                NobGameManifest.ID
        );
        Map<UUID, MatchPlayerEntity> playerByMatch = playerByMatch(nobMatches, principal.playerId());
        List<NobGameRoundEntity> rounds = roundRows(nobMatches, principal.playerId());
        boolean legacyFactionFallback = roundRepository == null;

        FactionCounter vampire = new FactionCounter();
        FactionCounter werewolf = new FactionCounter();
        FactionCounter halfblood = new FactionCounter();
        long matchesWon = 0;
        for (MatchEntity match : nobMatches) {
            MatchPlayerEntity player = playerByMatch.get(match.getId());
            if (player == null) {
                continue;
            }
            boolean won = isWin(match, player, principal.playerId());
            if (won) {
                matchesWon++;
            }
            if (legacyFactionFallback) {
                FactionCounter faction = factionCounter(player.getBloodline(), vampire, werewolf, halfblood);
                if (faction != null) {
                    faction.record(won);
                }
            }
        }
        for (NobGameRoundEntity round : rounds) {
            FactionCounter faction = factionCounter(round.getBloodline(), vampire, werewolf, halfblood);
            if (faction != null) {
                faction.record(isRoundWin(round));
            }
        }

        Instant joinedAt = principal.createdAt() == null ? Instant.now() : principal.createdAt();
        UserGameStatisticEntity statistic = statisticRepository == null
                ? null
                : statisticRepository.findByUserIdAndGameCode(
                        principal.playerId(),
                        NobGameManifest.ID
                ).orElse(null);
        int elo = statistic == null ? UserGameStatisticEntity.DEFAULT_ELO : statistic.getEloNob();
        int highestElo = statistic == null ? UserGameStatisticEntity.DEFAULT_ELO : statistic.getHighestElo();
        return new ProfileStatsResponse(
                new ProfileStatsResponse.Player(
                        principal.playerId(),
                        principal.displayName(),
                        DEFAULT_AVATAR,
                        JOINED_AT_FORMAT.format(joinedAt),
                        principal.kind() == SessionKind.MEMBER ? "Member" : "Guest",
                        PLATFORM
                ),
                new ProfileStatsResponse.NobStats(
                        nobMatches.size(),
                        matchesWon,
                        rate(matchesWon, nobMatches.size()),
                        vampire.toResponse(),
                        werewolf.toResponse(),
                        halfblood.toResponse(),
                        elo,
                        highestElo
                )
        );
    }

    private List<NobGameRoundEntity> roundRows(List<MatchEntity> matches, String playerId) {
        if (roundRepository == null || matches.isEmpty()) {
            return List.of();
        }
        List<NobGameRoundEntity> result = roundRepository.findByGameIdInAndPlayerIdOrderByGameIdAscRoundNumberAscIdAsc(
                matches.stream().map(MatchEntity::getId).toList(),
                playerId
        );
        return result == null ? List.of() : result;
    }

    private Map<UUID, MatchPlayerEntity> playerByMatch(List<MatchEntity> matches, String playerId) {
        if (matches.isEmpty()) {
            return Map.of();
        }
        Map<UUID, MatchPlayerEntity> result = new HashMap<>();
        playerRepository.findByMatchIdInOrderByMatchIdAscSeatAscIdAsc(
                        matches.stream().map(MatchEntity::getId).toList()
                )
                .stream()
                .filter(player -> playerId.equals(player.getPlayerId()))
                .forEach(player -> result.put(player.getMatchId(), player));
        return result;
    }

    private static boolean isWin(MatchEntity match, MatchPlayerEntity player, String playerId) {
        if ("WIN".equalsIgnoreCase(player.getResult())) {
            return true;
        }
        return player.getResult() == null && playerId.equals(match.getWinnerPlayerId());
    }

    private static boolean isRoundWin(NobGameRoundEntity round) {
        return "WIN".equalsIgnoreCase(round.getResult());
    }

    private static FactionCounter factionCounter(
            String bloodline,
            FactionCounter vampire,
            FactionCounter werewolf,
            FactionCounter halfblood
    ) {
        if (bloodline == null) {
            return null;
        }
        return switch (bloodline.toUpperCase(java.util.Locale.ROOT)) {
            case "VAMPIRE" -> vampire;
            case "WEREWOLF" -> werewolf;
            case "HALFBLOOD", "HALF_BLOOD", "SORCERESS" -> halfblood;
            default -> null;
        };
    }

    private static double rate(long wins, long total) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round((wins * 1000.0 / total)) / 10.0;
    }

    private static final class FactionCounter {
        private long played;
        private long wins;

        private void record(boolean won) {
            played++;
            if (won) {
                wins++;
            }
        }

        private ProfileStatsResponse.FactionStats toResponse() {
            return new ProfileStatsResponse.FactionStats(played, wins, rate(wins, played));
        }
    }
}
