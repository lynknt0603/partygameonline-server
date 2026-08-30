package com.partygameonline.game.notinmypot;

import com.partygameonline.game.core.GameEloChange;
import com.partygameonline.game.core.GameStateProjector;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.ViewerKind;
import com.partygameonline.game.notinmypot.api.dto.NotInMyPotCardView;
import com.partygameonline.game.notinmypot.api.dto.NotInMyPotEventView;
import com.partygameonline.game.notinmypot.api.dto.NotInMyPotPendingActionView;
import com.partygameonline.game.notinmypot.api.dto.NotInMyPotPublicPlayerView;
import com.partygameonline.game.notinmypot.api.dto.NotInMyPotView;
import com.partygameonline.game.notinmypot.application.NotInMyPotRules;
import com.partygameonline.game.notinmypot.domain.NotInMyPotCard;
import com.partygameonline.game.notinmypot.domain.NotInMyPotGameState;
import com.partygameonline.game.notinmypot.domain.NotInMyPotPendingAction;
import com.partygameonline.game.notinmypot.domain.NotInMyPotPendingType;
import com.partygameonline.game.notinmypot.domain.NotInMyPotPhase;
import com.partygameonline.game.notinmypot.domain.NotInMyPotPlayerState;
import com.partygameonline.game.notinmypot.domain.NotInMyPotRole;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NotInMyPotGameProjector implements GameStateProjector<NotInMyPotGameState, NotInMyPotView> {

    @Override
    public String gameType() {
        return NotInMyPotGameManifest.ID;
    }

    @Override
    public NotInMyPotView project(NotInMyPotGameState state, PlayerContext viewer) {
        String playerId = viewer.playerId();
        NotInMyPotPlayerState self = state.player(playerId);
        boolean inGame = self != null && viewer.kind() == ViewerKind.PLAYER;
        List<NotInMyPotPublicPlayerView> players = new ArrayList<>();
        Map<String, NotInMyPotRole> publicRoles = state.getPublicRoles();
        Map<String, GameEloChange> eloChanges = state.getEloChanges();
        for (NotInMyPotPlayerState player : state.getPlayers()) {
            String visibleRole = publicRoles.containsKey(player.getPlayerId())
                    ? publicRoles.get(player.getPlayerId()).name()
                    : inGame && player.getPlayerId().equals(playerId)
                    ? player.getRole().name()
                    : null;
            GameEloChange elo = eloChanges.get(player.getPlayerId());
            players.add(new NotInMyPotPublicPlayerView(
                    player.getPlayerId(),
                    player.getDisplayName(),
                    player.getSeat(),
                    player.isActive(),
                    player.isExpelled(),
                    player.isConnected(),
                    player.getPlayerId().equals(playerId),
                    state.doorCount(player.getPlayerId()),
                    player.getHand().size(),
                    visibleRole,
                    state.getWinnerPlayerIds().contains(player.getPlayerId()),
                    elo == null ? null : elo.oldElo(),
                    elo == null ? null : elo.eloDelta(),
                    elo == null ? null : elo.newElo()
            ));
        }

        NotInMyPotPendingAction pending = state.getPendingAction();
        List<String> privateAllowedCardIds = inGame
                && pending != null
                && playerId.equals(pending.actorPlayerId())
                ? pending.type() == NotInMyPotPendingType.RETURN_SHOPPING_CARDS
                ? self.getHand().stream().map(NotInMyPotCard::cardId).toList()
                : pending.allowedCardIds()
                : List.of();
        NotInMyPotPendingActionView pendingView = pending == null
                ? null
                : new NotInMyPotPendingActionView(
                        pending.type().name(),
                        pending.actorPlayerId(),
                        pending.requiredCardCount(),
                        pending.allowedTargetPlayerIds(),
                        privateAllowedCardIds,
                        pending.startedAt(),
                        pending.expiresAt()
                );
        List<NotInMyPotCardView> inspected = inGame
                && pending != null
                && pending.type() == NotInMyPotPendingType.INSPECT_SHUFFLED_POT
                && playerId.equals(pending.actorPlayerId())
                ? pending.inspectedCards().stream().map(NotInMyPotGameProjector::cardView).toList()
                : List.of();
        List<NotInMyPotCardView> finalPot = state.isFinished() && state.getFinalPotScore() != null
                ? state.getPotBottomToTop().stream().map(NotInMyPotGameProjector::cardView).toList()
                : List.of();
        Map<String, String> publicRoleView = new LinkedHashMap<>();
        publicRoles.forEach((id, role) -> publicRoleView.put(id, role.name()));
        boolean actionHistoryVisible = state.getSettings().showActionHistory();
        List<NotInMyPotEventView> eventView = actionHistoryVisible
                ? state.getPublicEvents().stream()
                .map(event -> new NotInMyPotEventView(event.type(), event.payload()))
                .toList()
                : List.of();
        boolean canAct = inGame
                && self.isActive()
                && state.getPhase() == NotInMyPotPhase.PLAYING
                && pending == null
                && playerId.equals(state.getCurrentPlayerId());
        boolean canDeclare = canAct
                && state.isTurnBeginning()
                && self.getRole() == NotInMyPotRole.VEGETARIAN;
        return new NotInMyPotView(
                NotInMyPotGameManifest.ID,
                state.getRoomId(),
                playerId,
                state.getPhase().name(),
                state.getStateVersion(),
                Instant.now(),
                state.isFinished(),
                state.getCurrentPlayerId(),
                state.getTurnNumber(),
                state.getTurnDeadline(),
                actionHistoryVisible,
                state.getTargetScore(),
                state.getWinnerFaction() == null ? null : state.getWinnerFaction().name(),
                state.getWinnerPlayerIds(),
                players,
                inGame ? self.getRole().name() : null,
                inGame ? self.getHand().stream().map(NotInMyPotGameProjector::cardView).toList() : List.of(),
                state.getDrawPile().size(),
                state.getPot().size(),
                state.getDiscardPile().size(),
                Map.copyOf(publicRoleView),
                eventView,
                pendingView,
                inspected,
                state.getFinalPotScore(),
                finalPot,
                canDeclare,
                canAct
        );
    }

    private static NotInMyPotCardView cardView(NotInMyPotCard card) {
        return new NotInMyPotCardView(
                card.cardId(),
                card.category().name(),
                card.type(),
                card.isIngredient() ? card.score() : null
        );
    }

}
