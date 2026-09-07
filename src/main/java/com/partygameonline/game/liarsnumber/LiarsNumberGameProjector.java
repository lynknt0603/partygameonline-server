package com.partygameonline.game.liarsnumber;

import com.partygameonline.game.core.GameEloChange;
import com.partygameonline.game.core.GameStateProjector;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.liarsnumber.api.dto.LiarsNumberActiveRoundView;
import com.partygameonline.game.liarsnumber.api.dto.LiarsNumberCardView;
import com.partygameonline.game.liarsnumber.api.dto.LiarsNumberClaimView;
import com.partygameonline.game.liarsnumber.api.dto.LiarsNumberEventView;
import com.partygameonline.game.liarsnumber.api.dto.LiarsNumberPlayerView;
import com.partygameonline.game.liarsnumber.api.dto.LiarsNumberView;
import com.partygameonline.game.liarsnumber.application.LiarsNumberRules;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberActiveRound;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberCard;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberClaim;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberEvent;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberGameState;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberPhase;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberPlayerState;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class LiarsNumberGameProjector implements GameStateProjector<LiarsNumberGameState, LiarsNumberView> {

    @Override
    public String gameType() {
        return LiarsNumberGameManifest.ID;
    }

    @Override
    public LiarsNumberView project(LiarsNumberGameState state, PlayerContext viewer) {
        String viewerId = viewer.playerId();
        LiarsNumberPlayerState self = state.findPlayer(viewerId);
        LiarsNumberActiveRound round = state.getActiveRound();
        LiarsNumberActiveRoundView active = round == null ? null : new LiarsNumberActiveRoundView(
                cardView(round.getCard(), round.getSeenBy().contains(viewerId)),
                round.getOriginalSenderId(),
                round.getCurrentSenderId(),
                round.getCurrentReceiverId(),
                round.getDeclaredType(),
                round.getHistory().stream().map(LiarsNumberGameProjector::claimView).toList(),
                Set.copyOf(round.getSeenBy())
        );
        List<LiarsNumberPlayerView> players = state.getPlayers().stream()
                .map(player -> playerView(state, player, viewerId))
                .toList();
        List<String> availableTargets = round == null ? List.of() : state.getPlayers().stream()
                .map(LiarsNumberPlayerState::getPlayerId)
                .filter(id -> !id.equals(round.getCurrentSenderId()))
                .toList();
        List<String> availablePassTargets = LiarsNumberGameEngine.availablePassTargets(
                state, viewerId, round
        );
        return new LiarsNumberView(
                LiarsNumberGameManifest.ID,
                state.getRoomId(),
                viewerId,
                state.getPhase().name(),
                state.getStateVersion(),
                Instant.now(),
                state.getTurnSeconds(),
                state.getTurnDeadline(),
                state.isFinished(),
                state.getPlayerCount(),
                state.threshold(),
                state.getRoundNumber(),
                state.getCurrentRoundStarterId(),
                active,
                players,
                self == null ? List.of() : self.getHand().stream().map(card -> cardView(card, true)).toList(),
                self == null ? List.of() : self.getPenaltyCards().stream().map(card -> cardView(card, true)).toList(),
                state.getRemovedCards().size(),
                state.getLoserId(),
                state.getWinnerPlayerIds(),
                state.getPublicEvents().stream().map(event -> new LiarsNumberEventView(event.type(), event.payload())).toList(),
                legalActions(state, viewerId, round),
                availableTargets,
                availablePassTargets,
                state.getLastResolvedCard() == null ? null : cardView(state.getLastResolvedCard(), true),
                state.getLastPenaltyPlayerId(),
                state.getLastPenaltyType(),
                state.getLastPenaltyScore(),
                state.getLastPenaltyThreshold(),
                state.getLastClaimIsTrue(),
                state.getLastReceiverCorrect(),
                state.getGameOverReason()
        );
    }

    private static LiarsNumberPlayerView playerView(
            LiarsNumberGameState state, LiarsNumberPlayerState player, String viewerId
    ) {
        GameEloChange elo = state.getEloChanges().get(player.getPlayerId());
        Map<Integer, Integer> scores = new LinkedHashMap<>();
        for (int type = 1; type <= 8; type++) {
            int score = LiarsNumberRules.getPenaltyScoreForType(player, type);
            if (score > 0) {
                scores.put(type, score);
            }
        }
        return new LiarsNumberPlayerView(
                player.getPlayerId(),
                player.getDisplayName(),
                player.getSeat(),
                player.getPlayerId().equals(viewerId),
                player.getHand().size(),
                player.getPenaltyCards().stream().map(card -> cardView(card, true)).toList(),
                Map.copyOf(scores),
                state.getWinnerPlayerIds().contains(player.getPlayerId()),
                player.getPlayerId().equals(state.getLoserId()),
                elo == null ? null : elo.oldElo(),
                elo == null ? null : elo.eloDelta(),
                elo == null ? null : elo.newElo()
        );
    }

    private static List<String> legalActions(
            LiarsNumberGameState state, String viewerId, LiarsNumberActiveRound round
    ) {
        if (state.isFinished()) {
            return List.of();
        }
        return switch (state.getPhase()) {
            case SELECT_CARD -> viewerId.equals(state.getCurrentRoundStarterId()) ? List.of("SELECT_CARD") : List.of();
            case SELECT_TARGET -> round != null && viewerId.equals(round.getCurrentSenderId()) ? List.of("SELECT_TARGET") : List.of();
            case DECLARE_TYPE -> round != null && viewerId.equals(round.getCurrentSenderId()) ? List.of("DECLARE_TYPE") : List.of();
            case RECEIVER_DECISION -> {
                if (round == null || !viewerId.equals(round.getCurrentReceiverId())) {
                    yield List.of();
                }
                List<String> actions = new java.util.ArrayList<>(List.of("GUESS"));
                if (!LiarsNumberGameEngine.availablePassTargets(state, viewerId, round).isEmpty()) {
                    actions.add("PEEK_AND_PASS");
                }
                yield List.copyOf(actions);
            }
            case SELECT_PASS_TARGET -> round != null && viewerId.equals(round.getCurrentSenderId())
                    ? List.of("SELECT_PASS_TARGET") : List.of();
            case PASS_DECLARE_TYPE -> round != null && viewerId.equals(round.getCurrentSenderId())
                    ? List.of("PASS_DECLARE_TYPE") : List.of();
            case GAME_OVER -> List.of();
        };
    }

    private static LiarsNumberClaimView claimView(LiarsNumberClaim claim) {
        return new LiarsNumberClaimView(claim.senderId(), claim.receiverId(), claim.declaredType());
    }

    private static LiarsNumberCardView cardView(LiarsNumberCard card, boolean faceUp) {
        if (!faceUp) {
            return new LiarsNumberCardView(null, null, null, "CARD BACK", false, 0);
        }
        return new LiarsNumberCardView(
                card.cardId(), card.typeId(), card.variant().name().toLowerCase(), card.label(), true,
                card.penaltyWeight()
        );
    }
}
