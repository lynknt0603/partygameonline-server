package com.partygameonline.game.nob;

import com.partygameonline.game.core.GameStateProjector;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.ViewerKind;
import com.partygameonline.game.nob.api.dto.NobAnnouncementView;
import com.partygameonline.game.nob.api.dto.NobBloodlineView;
import com.partygameonline.game.nob.api.dto.NobCardView;
import com.partygameonline.game.nob.api.dto.NobInspectRevealView;
import com.partygameonline.game.nob.api.dto.NobObservationView;
import com.partygameonline.game.nob.api.dto.NobPendingDecisionView;
import com.partygameonline.game.nob.api.dto.NobPublicLogView;
import com.partygameonline.game.nob.api.dto.NobPublicPlayerView;
import com.partygameonline.game.nob.api.dto.NobRoundResultView;
import com.partygameonline.game.nob.api.dto.NobView;
import com.partygameonline.game.nob.domain.NobAnnouncement;
import com.partygameonline.game.nob.domain.NobBloodline;
import com.partygameonline.game.nob.domain.NobBloodlineKnowledge;
import com.partygameonline.game.nob.domain.NobCardInstance;
import com.partygameonline.game.nob.domain.NobDecisionType;
import com.partygameonline.game.nob.domain.NobEloChange;
import com.partygameonline.game.nob.domain.NobGameState;
import com.partygameonline.game.nob.domain.NobInspectReveal;
import com.partygameonline.game.nob.domain.NobMoonMark;
import com.partygameonline.game.nob.domain.NobObservation;
import com.partygameonline.game.nob.domain.NobPendingDecision;
import com.partygameonline.game.nob.domain.NobPhase;
import com.partygameonline.game.nob.domain.NobPhaseState;
import com.partygameonline.game.nob.domain.NobPlayerState;
import com.partygameonline.game.nob.domain.NobRoundResult;
import com.partygameonline.game.nob.domain.NobResolutionItem;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NobGameProjector implements GameStateProjector<NobGameState, NobView> {

    @Override
    public String gameType() {
        return NobGameManifest.ID;
    }

    @Override
    public NobView project(NobGameState state, PlayerContext viewer) {
        String you = viewer.playerId();
        NobPlayerState self = state.player(you);
        boolean inGame = self != null && viewer.kind() == ViewerKind.PLAYER;
        List<NobPublicPlayerView> players = new ArrayList<>();
        boolean showRoundElo = state.getPhase() == NobPhase.ROUND_SUMMARY || state.getPhase() == NobPhase.GAME_OVER;
        int lastRoundNumber = state.getCompletedRounds().isEmpty()
                ? -1
                : state.getCompletedRounds().getLast().roundNumber();
        java.util.Map<String, NobEloChange> roundElo = showRoundElo
                ? state.getRoundEloChanges(lastRoundNumber)
                : java.util.Map.of();
        java.util.Map<String, NobEloChange> finalElo = state.getFinalEloChanges();
        for (NobPlayerState player : state.getPlayers()) {
            boolean revealBloodline = player.getKnowledgeState() == NobBloodlineKnowledge.PUBLICLY_REVEALED
                    || state.getPhase() == NobPhase.BLOODLINE_REVEAL
                    || state.getPhase() == NobPhase.SCORING
                    || state.getPhase() == NobPhase.ROUND_SUMMARY
                    || state.getPhase() == NobPhase.GAME_OVER;
            Integer score = state.isFinished() ? player.score() : null;
            NobEloChange eloChange = state.isFinished()
                    ? finalElo.get(player.getPlayerId())
                    : roundElo.get(player.getPlayerId());
            // Final-match Elo shown to players is a presentation value. Keep
            // the domain/ranking Elo unchanged, but expose the final delta
            // divided by ten and rounded down. Round-summary deltas remain
            // untouched.
            Integer displayedEloDelta = eloChange == null
                    ? null
                    : state.isFinished()
                    ? Math.floorDiv(eloChange.eloDelta(), 10)
                    : eloChange.eloDelta();
            players.add(new NobPublicPlayerView(
                    player.getPlayerId(),
                    player.getDisplayName(),
                    player.getSeat(),
                    player.isAlive(),
                    player.isConnected(),
                    player.getPlayerId().equals(you),
                    player.moonMarkCount(),
                    score,
                    revealBloodline ? bloodlineView(player.getCurrentBloodline()) : null,
                    player.getRevealedCards().stream().map(NobGameProjector::cardView).toList(),
                    player.getHand().size(),
                    eloChange == null ? null : eloChange.oldElo(),
                    displayedEloDelta,
                    eloChange == null ? null : eloChange.newElo()
            ));
        }
        List<NobCardView> myHand = inGame ? self.getHand().stream().map(NobGameProjector::cardView).toList() : List.of();
        NobBloodlineView myBloodline = null;
        String knowledge = null;
        if (inGame) {
            knowledge = self.getKnowledgeState().name();
            if (self.knowsOwnBloodline()) {
                myBloodline = bloodlineView(self.getCurrentBloodline());
            }
        }
        List<Integer> myMarks = inGame
                ? self.getMoonMarks().stream().map(NobMoonMark::value).toList()
                : List.of();
        List<NobObservationView> observations = inGame
                ? self.getObservations().stream().map(NobGameProjector::observationView).toList()
                : List.of();
        NobInspectRevealView inspectReveal = inGame ? inspectRevealView(self.getInspectReveal()) : null;
        NobPendingDecisionView pendingView = null;
        if (inGame && state.getPendingDecision() != null && you.equals(state.getPendingDecision().actorId())) {
            pendingView = pendingView(state.getPendingDecision());
        } else if (inGame && state.hasUnclaimedMoonPick(you)) {
            pendingView = moonPickView(state, you);
        }
        boolean waitingForSecretSubmissions = state.getPhaseState() == NobPhaseState.WAITING_FOR_PHASE_SUBMISSIONS;
        List<String> unclaimedMoon = state.unclaimedMoonPlayerIds();
        String actorId = waitingForSecretSubmissions
                ? null
                : state.getPendingDecision() != null
                ? state.getPendingDecision().actorId()
                : (!unclaimedMoon.isEmpty() ? unclaimedMoon.getFirst() : state.getCurrentActorPlayerId());
        String decisionType = state.getPendingDecision() != null
                ? state.getPendingDecision().type().name()
                : (!unclaimedMoon.isEmpty() ? "MOON_MARK_PICK" : null);
        List<NobCardView> draftHand = List.of();
        if (inGame) {
            List<NobCardInstance> draft = state.getDraftHands().get(you);
            if (draft != null) {
                draftHand = draft.stream().map(NobGameProjector::cardView).toList();
            }
        }
        List<NobCardView> echoCards = List.of();
        int echoCardCount = state.getEchoCardCount();
        if (!state.getEchoHold().isEmpty()) {
            echoCardCount = state.getEchoHold().size();
            boolean echoActor = inGame
                    && state.getPendingDecision() != null
                    && state.getPendingDecision().type() == NobDecisionType.ECHO_CHOOSE
                    && you.equals(state.getPendingDecision().actorId());
            if (echoActor) {
                echoCards = state.getEchoHold().stream().map(NobGameProjector::cardView).toList();
            }
        } else if (state.getEchoPicked() != null) {
            echoCards = List.of(cardView(state.getEchoPicked()));
            echoCardCount = Math.max(echoCardCount, 1);
        }
        NobCardView echoSourceCard = state.getEchoSource() == null ? null : cardView(state.getEchoSource());
        List<NobCardView> resolving = state.getResolutionQueue().stream()
                .map(NobResolutionItem::card)
                .map(NobGameProjector::cardView)
                .toList();
        List<NobPublicLogView> log = state.getPublicLog().stream()
                .map(entry -> new NobPublicLogView(
                        entry.type(),
                        entry.text(),
                        entry.actorPlayerId(),
                        entry.targetPlayerId(),
                        entry.extraTargetPlayerId(),
                        entry.cardCode()
                ))
                .toList();
        Instant serverTime = Instant.now();
        Instant deadline = state.getPendingDecision() != null
                ? state.getPendingDecision().expiresAt()
                : (state.getResolutionDisplayExpiresAt() != null
                        ? state.getResolutionDisplayExpiresAt()
                        : state.getPhaseDeadline());
        Instant started = state.getPendingDecision() != null
                ? state.getPendingDecision().startedAt()
                : state.getWindowStartedAt();
        return new NobView(
                NobGameManifest.ID,
                state.getRoomId(),
                you,
                state.getRoundNumber(),
                state.getPhase().name(),
                state.getPhaseState().name(),
                state.getVersion(),
                serverTime,
                started,
                deadline,
                state.isFinished(),
                state.getTargetScore(),
                List.copyOf(state.getWinnerPlayerIds()),
                players,
                myHand,
                myBloodline,
                knowledge,
                myMarks,
                observations,
                inspectReveal,
                pendingView,
                draftHand,
                echoCards,
                echoCardCount,
                echoSourceCard,
                log,
                resolving,
                state.getCurrentResolvingCard() == null ? null : cardView(state.getCurrentResolvingCard()),
                actorId,
                decisionType,
                viewerSubmittedPlayerIds(state, you),
                announcementView(state.getAnnouncement(), waitingForSecretSubmissions),
                roundResultView(state.getLastRoundResult()),
                List.copyOf(state.getRoundRewardPlayerIds()),
                state.getTiming().toMap(),
                state.getDiscardPile().size(),
                state.getUndealt().size()
        );
    }

    private static NobCardView cardView(NobCardInstance card) {
        return new NobCardView(
                card.instanceId(),
                card.cardCode(),
                card.roleType().name(),
                card.number(),
                card.effectCode().name()
        );
    }

    private static NobBloodlineView bloodlineView(NobBloodline bloodline) {
        if (bloodline == null) {
            return null;
        }
        return new NobBloodlineView(bloodline.type().name(), bloodline.rank());
    }

    private static NobInspectRevealView inspectRevealView(NobInspectReveal reveal) {
        if (reveal == null) {
            return null;
        }
        return new NobInspectRevealView(
                reveal.targetPlayerId(),
                bloodlineView(reveal.bloodline()),
                reveal.cardCode(),
                reveal.displayUntil()
        );
    }

    private static NobObservationView observationView(NobObservation observation) {
        return new NobObservationView(
                observation.kind(),
                observation.targetPlayerId(),
                bloodlineView(observation.bloodline()),
                observation.cardCode(),
                observation.moonMarkValue()
        );
    }

    private static NobPendingDecisionView moonPickView(NobGameState state, String playerId) {
        List<String> options = state.getMoonTokenOffers().getOrDefault(playerId, List.of()).stream()
                .map(com.partygameonline.game.nob.domain.NobMoonTokenOption::optionId)
                .toList();
        return new NobPendingDecisionView(
                "moon-" + playerId,
                "MOON_MARK_PICK",
                playerId,
                null,
                null,
                options,
                List.of(),
                null,
                state.getWindowStartedAt(),
                state.getPhaseDeadline(),
                null
        );
    }

    private static NobPendingDecisionView pendingView(NobPendingDecision pending) {
        Object target = pending.context() == null ? null : pending.context().get("targetId");
        if (target == null && pending.context() != null && pending.context().get("a") != null) {
            target = pending.context().get("a");
        }
        List<Integer> optionValues = null;
        if (pending.context() != null && "STEAL".equals(pending.context().get("mode"))) {
            // Keep the steal choices face-down. The selected Moon Mark value is
            // revealed naturally after it is transferred to the actor.
            optionValues = pending.allowedOptions().stream().map(id -> (Integer) null).toList();
        }
        return new NobPendingDecisionView(
                pending.decisionId(),
                pending.type().name(),
                pending.actorId(),
                target instanceof String id ? id : null,
                null,
                pending.allowedOptions(),
                pending.allowedTargetIds(),
                pending.sourceCardInstanceId(),
                pending.startedAt(),
                pending.expiresAt(),
                optionValues
        );
    }

    private static List<String> viewerSubmittedPlayerIds(NobGameState state, String viewerId) {
        return state.submittedPlayerIds().contains(viewerId) ? List.of(viewerId) : List.of();
    }

    private static NobAnnouncementView announcementView(NobAnnouncement announcement, boolean hideActor) {
        if (announcement == null) {
            return null;
        }
        return new NobAnnouncementView(
                announcement.id(),
                announcement.type(),
                hideActor ? null : announcement.actorPlayerId(),
                announcement.targetPlayerId(),
                announcement.cardCode(),
                announcement.reactionCardCode(),
                announcement.messageKey(),
                announcement.createdAt(),
                announcement.displayUntil()
        );
    }

    private static NobRoundResultView roundResultView(NobRoundResult result) {
        if (result == null) {
            return null;
        }
        return new NobRoundResultView(result.result(), result.winningBloodline(), result.lastHopeTriggered());
    }
}
