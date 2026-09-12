package com.partygameonline.game.bloodbound;

import com.partygameonline.game.core.GameStateProjector;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.ViewerKind;
import com.partygameonline.game.bloodbound.api.dto.BloodBoundClueView;
import com.partygameonline.game.bloodbound.api.dto.BloodBoundLogView;
import com.partygameonline.game.bloodbound.api.dto.BloodBoundPublicPlayerView;
import com.partygameonline.game.bloodbound.api.dto.BloodBoundSecretCardView;
import com.partygameonline.game.bloodbound.api.dto.BloodBoundView;
import com.partygameonline.game.bloodbound.domain.BloodBoundEvent;
import com.partygameonline.game.bloodbound.domain.BloodBoundGameState;
import com.partygameonline.game.bloodbound.domain.BloodBoundPhase;
import com.partygameonline.game.bloodbound.domain.BloodBoundPlayerState;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class BloodBoundGameStateProjector implements GameStateProjector<BloodBoundGameState, BloodBoundView> {

    @Override
    public String gameType() {
        return BloodBoundGameManifest.ID;
    }

    @Override
    public BloodBoundView project(BloodBoundGameState state, PlayerContext viewer) {
        String viewerId = viewer.playerId();
        BloodBoundPlayerState self = state.player(viewerId);
        boolean inGame = self != null && viewer.kind() == ViewerKind.PLAYER;

        List<BloodBoundPublicPlayerView> playersView = new ArrayList<>();
        int count = state.getPlayers().size();

        for (BloodBoundPlayerState p : state.getPlayers()) {
            boolean isDagger = p.getPlayerId().equals(state.getDaggerPlayerId());
            playersView.add(new BloodBoundPublicPlayerView(
                    p.getPlayerId(),
                    p.getDisplayName(),
                    p.getSeat(),
                    p.getWounds(),
                    List.copyOf(p.getRevealedTokens()),
                    p.isHasRevealedRank(),
                    p.isHasUsedAbility(),
                    p.isShielded(),
                    isDagger,
                    p.isConnected()
            ));
        }

        BloodBoundSecretCardView mySecretCard = null;
        if (inGame) {
            mySecretCard = new BloodBoundSecretCardView(self.getClan(), self.getRank());
        }

        // Left neighbor clue calculation
        BloodBoundClueView leftNeighborClue = null;
        if (inGame && count > 1) {
            int leftSeat = (self.getSeat() + 1) % count;
            for (BloodBoundPlayerState p : state.getPlayers()) {
                if (p.getSeat() == leftSeat) {
                    leftNeighborClue = new BloodBoundClueView(p.getClan(), p.getClan().name() + "-CREST");
                    break;
                }
            }
        }

        List<BloodBoundLogView> logsView = new ArrayList<>();
        for (BloodBoundEvent ev : state.getLogs()) {
            logsView.add(new BloodBoundLogView(ev.text(), ev.textVi()));
        }

        boolean gameOver = state.getPhase() == BloodBoundPhase.GAME_OVER;
        Set<String> winnerPlayerIds = gameOver ? state.winnerPlayerIds() : Set.of();
        Map<String, BloodBoundSecretCardView> finalSecretCards = new LinkedHashMap<>();
        if (gameOver) {
            for (BloodBoundPlayerState p : state.getPlayers()) {
                finalSecretCards.put(p.getPlayerId(), new BloodBoundSecretCardView(p.getClan(), p.getRank()));
            }
        }

        return new BloodBoundView(
                BloodBoundGameManifest.ID,
                state.getRoomId(),
                state.getVersion(),
                viewerId,
                state.getPhase(),
                state.getRoundNumber(),
                state.getDaggerPlayerId(),
                state.getTargetPlayerId(),
                state.getIntervenerPlayerId(),
                state.getForcedAttackTargetId(),
                state.getLastAttackerPlayerId(),
                playersView,
                mySecretCard,
                leftNeighborClue,
                state.getWinnerClan(),
                state.getCapturedPlayerId(),
                logsView,
                state.getPhaseDeadline(),
                state.getSettings().turnSeconds(),
                state.getSettings().interventionSeconds(),
                winnerPlayerIds,
                finalSecretCards
        );
    }
}
