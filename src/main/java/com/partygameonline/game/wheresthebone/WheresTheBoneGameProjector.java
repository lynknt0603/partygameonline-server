package com.partygameonline.game.wheresthebone;

import com.partygameonline.game.core.GameEloChange;
import com.partygameonline.game.core.GameStateProjector;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.ViewerKind;
import com.partygameonline.game.wheresthebone.api.dto.WheresTheBoneCoAwakeRecord;
import com.partygameonline.game.wheresthebone.api.dto.WheresTheBoneEventView;
import com.partygameonline.game.wheresthebone.api.dto.WheresTheBonePlayerView;
import com.partygameonline.game.wheresthebone.api.dto.WheresTheBoneView;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneActionType;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneGameState;
import com.partygameonline.game.wheresthebone.domain.WheresTheBonePackSelectionMode;
import com.partygameonline.game.wheresthebone.domain.WheresTheBonePeek;
import com.partygameonline.game.wheresthebone.domain.WheresTheBonePhase;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneRole;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Projects the authoritative state without leaking secret dice, roles, or night activity. */
@Component
public class WheresTheBoneGameProjector implements GameStateProjector<WheresTheBoneGameState, WheresTheBoneView> {

    @Override
    public String gameType() {
        return WheresTheBoneGameManifest.ID;
    }

    @Override
    public WheresTheBoneView project(WheresTheBoneGameState state, PlayerContext viewer) {
        String viewerId = viewer.playerId();
        boolean inGame = viewer.kind() == ViewerKind.PLAYER && state.getPlayerIds().contains(viewerId);
        WheresTheBoneRole viewerRole = state.getRoles().get(viewerId);
        boolean viewerAwake = inGame && state.isAwakeNow(viewerId);
        boolean witnessSelection = state.getPhase() == WheresTheBonePhase.PACK_SELECTION
                && state.getPackSelectionMode() == WheresTheBonePackSelectionMode.WITNESS;
        boolean publicPhase = state.getPhase() == WheresTheBonePhase.DISCUSSION
                || state.getPhase() == WheresTheBonePhase.VOTING
                || state.isFinished();
        String phase = witnessSelection && viewerRole != WheresTheBoneRole.BONE_THIEF
                ? WheresTheBonePhase.NIGHT_HOUR.name()
                : state.getPhase().name();
        boolean witnessed = inGame && !state.getWitnessedBoneTakenHours().getOrDefault(viewerId, List.of()).isEmpty();
        boolean visibleBone = state.isBoneTaken()
                && (publicPhase || viewerRole == WheresTheBoneRole.BONE_THIEF || viewerAwake || witnessed);
        boolean exposeDetails = state.isFinished() || publicPhase;

        // Witnessing the theft does not grant visibility into later wake calls.
        // A player may only see the other dogs while that player is awake now.
        Set<String> visibleAwake = viewerAwake ? state.awakePlayerIds() : Set.of();
        List<String> currentAwake = visibleAwake.stream().filter(id -> !id.equals(viewerId)).toList();
        List<WheresTheBonePlayerView> players = state.getPlayerIds().stream()
                .map(id -> playerView(state, id, viewerId, visibleAwake, state.isFinished(), publicPhase))
                .toList();

        List<String> knownPackmates = knownPackmates(state, viewerId, viewerRole);
        String knownThief = knownBoneThief(state, viewerId, viewerRole);
        List<String> candidates = inGame && viewerRole == WheresTheBoneRole.BONE_THIEF
                && state.getPhase() == WheresTheBonePhase.PACK_SELECTION
                ? List.copyOf(state.getPendingPackCandidates()) : List.of();
        List<WheresTheBonePeek> viewerPeeks = inGame
                ? state.getPeekResults().getOrDefault(viewerId, List.of()) : List.of();
        Map<String, List<Integer>> peekResults = inGame
                ? viewerPeeks.stream()
                .collect(Collectors.toMap(WheresTheBonePeek::targetPlayerId, WheresTheBonePeek::wakeHours,
                        (left, right) -> right, LinkedHashMap::new))
                : Map.of();
        List<String> clues = clues(state, viewerId, witnessed);
        List<WheresTheBoneCoAwakeRecord> coAwake = inGame
                ? state.getCoAwakeRecords().getOrDefault(viewerId, Map.of()).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new WheresTheBoneCoAwakeRecord(entry.getKey(), List.copyOf(entry.getValue())))
                .toList() : List.of();
        List<WheresTheBoneEventView> events = state.getSettings().showActionHistory() && exposeDetails
                ? state.getEvents().stream()
                .filter(event -> isPublicHistoryEvent(event.type()))
                .map(event -> new WheresTheBoneEventView(event.type(), Map.of()))
                .toList()
                : List.of();
        Map<String, Integer> elo = new LinkedHashMap<>();
        for (Map.Entry<String, GameEloChange> entry : state.getEloChanges().entrySet()) {
            elo.put(entry.getKey(), entry.getValue().eloDelta());
        }
        // Ballot targets and totals stay secret until voting is resolved. During
        // voting, player rows only expose whether each player has submitted.
        Map<String, Integer> voteCounts = voteCounts(state, state.isFinished());
        Map<String, String> votes = state.isFinished() ? Map.copyOf(state.getVotes()) : Map.of();
        Instant deadline = witnessSelection && viewerRole != WheresTheBoneRole.BONE_THIEF
                ? state.getSuspendedNightDeadline() : state.getDeadline();
        String faction = winnerFaction(state);
        boolean knowsTheftIdentity = state.isFinished() || viewerRole == WheresTheBoneRole.BONE_THIEF
                || witnessed || knownThief != null;
        String boneBy = visibleBone && knowsTheftIdentity ? state.getBoneTakenBy() : null;
        Integer boneHour = visibleBone && (state.isFinished() || knowsTheftIdentity)
                ? state.getBoneTakenHour() : null;
        int hour = WheresTheBonePhase.NIGHT_HOUR.name().equals(phase) ? state.getCurrentHour() : 0;
        boolean discussionSkipPending = state.getPhase() == WheresTheBonePhase.DISCUSSION
                && state.getDiscussionSkipRequesterId() != null;
        int discussionSkipAgreeCount = discussionSkipPending
                ? (int) state.activePlayerIds().stream()
                .filter(id -> Boolean.TRUE.equals(state.getDiscussionSkipResponses().get(id)))
                .count() : 0;
        int discussionSkipResponseCount = discussionSkipPending
                ? (int) state.activePlayerIds().stream()
                .filter(state.getDiscussionSkipResponses()::containsKey)
                .count() : 0;
        int discussionSkipRequiredAgreeCount = discussionSkipPending
                ? state.activePlayerIds().size() / 2 + 1 : 0;

        List<String> legal = legalActions(state, viewerId, viewerRole);
        return new WheresTheBoneView(
                WheresTheBoneGameManifest.ID,
                state.getRoomId(),
                viewerId,
                phase,
                state.getVersion(),
                Instant.now(),
                state.getPhaseStartedAt(),
                deadline,
                state.isFinished(),
                hour,
                visibleBone,
                boneHour,
                boneBy,
                currentAwake,
                players,
                inGame && viewerRole != null ? viewerRole.name() : null,
                inGame ? state.diceFor(viewerId) : List.of(),
                inGame ? state.wakeFor(viewerId) : List.of(),
                inGame && state.getPlayerIds().size() == 4 && state.hasSelectedWake(viewerId)
                        ? state.wakeFor(viewerId) : List.of(),
                peekResults,
                viewerPeeks.size(),
                clues,
                coAwake,
                inGame ? state.getWitnessedBoneTakenHours().getOrDefault(viewerId, List.of()) : List.of(),
                inGame ? state.getObservedBonePresentHours().getOrDefault(viewerId, List.of()) : List.of(),
                inGame ? state.getObservedBoneMissingHours().getOrDefault(viewerId, List.of()) : List.of(),
                knownPackmates,
                knownThief,
                inGame && viewerRole == WheresTheBoneRole.WHITE_DOG && state.getPackmates().contains(viewerId),
                candidates,
                inGame && viewerRole == WheresTheBoneRole.BONE_THIEF ? state.getPendingPackCount() : 0,
                discussionSkipPending ? state.getDiscussionSkipRequesterId() : null,
                discussionSkipAgreeCount,
                discussionSkipResponseCount,
                discussionSkipRequiredAgreeCount,
                discussionSkipPending && inGame ? state.getDiscussionSkipResponses().get(viewerId) : null,
                state.isFinished() ? state.getWinnerPlayerIds() : List.of(),
                state.isFinished() ? faction : null,
                votes,
                voteCounts,
                events,
                elo,
                legal,
                !legal.isEmpty()
        );
    }

    private static WheresTheBonePlayerView playerView(
            WheresTheBoneGameState state,
            String playerId,
            String viewerId,
            Set<String> visibleAwake,
            boolean revealAll,
            boolean showVotes
    ) {
        WheresTheBoneRole role = state.getRoles().get(playerId);
        boolean revealRole = revealAll || playerId.equals(viewerId);
        List<Integer> wake = revealAll || playerId.equals(viewerId) ? state.wakeFor(playerId) : List.of();
        GameEloChange elo = state.getEloChanges().get(playerId);
        return new WheresTheBonePlayerView(
                playerId,
                state.displayName(playerId),
                state.seat(playerId),
                state.isActive(playerId),
                playerId.equals(viewerId),
                revealRole && role != null ? role.name() : null,
                state.getWinnerPlayerIds().contains(playerId),
                visibleAwake.contains(playerId),
                (showVotes && state.getVotes().containsKey(playerId)),
                wake,
                elo == null ? null : elo.oldElo(),
                elo == null ? null : elo.eloDelta(),
                elo == null ? null : elo.newElo()
        );
    }

    private static List<String> knownPackmates(WheresTheBoneGameState state, String playerId, WheresTheBoneRole role) {
        if (role == WheresTheBoneRole.BONE_THIEF) return List.copyOf(state.getPackmates());
        if (isThiefAligned(state, playerId, role)) {
            return state.getPackmates().stream().filter(id -> !id.equals(playerId)).toList();
        }
        return List.of();
    }

    private static String knownBoneThief(WheresTheBoneGameState state, String playerId, WheresTheBoneRole role) {
        if (role == WheresTheBoneRole.BONE_THIEF || state.getPlayerIds().size() == 7
                || !isThiefAligned(state, playerId, role)) return null;
        return state.getRoles().entrySet().stream()
                .filter(entry -> entry.getValue() == WheresTheBoneRole.BONE_THIEF)
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    private static boolean isThiefAligned(WheresTheBoneGameState state, String playerId, WheresTheBoneRole role) {
        return role == WheresTheBoneRole.BONE_THIEF || state.getPackmates().contains(playerId);
    }

    private static List<String> clues(WheresTheBoneGameState state, String playerId, boolean witnessed) {
        List<String> result = new ArrayList<>();
        state.getObservedBonePresentHours().getOrDefault(playerId, List.of()).stream().sorted().forEach(hour -> result.add("Bone was still there at " + hour + ":00am"));
        state.getObservedBoneMissingHours().getOrDefault(playerId, List.of()).stream().sorted().forEach(hour -> result.add("Bone was already missing at " + hour + ":00am"));
        if (witnessed) {
            String thiefName = state.getBoneTakenBy() == null ? "Bone Thief" : state.displayName(state.getBoneTakenBy());
            state.getWitnessedBoneTakenHours().getOrDefault(playerId, List.of()).stream().sorted().forEach(hour -> result.add(thiefName + " took the bone at " + hour + ":00am"));
        }
        return List.copyOf(result);
    }

    private static Map<String, Integer> voteCounts(WheresTheBoneGameState state, boolean visible) {
        if (!visible) return Map.of();
        Map<String, Integer> counts = new LinkedHashMap<>();
        state.getVotes().values().forEach(target -> counts.merge(target, 1, Integer::sum));
        return Map.copyOf(counts);
    }

    private static boolean isPublicHistoryEvent(String type) {
        return switch (type) {
            case "GAME_STARTED", "BONE_TAKEN", "DISCUSSION_SKIP_REQUESTED",
                    "DISCUSSION_SKIP_APPROVED", "DISCUSSION_SKIP_REJECTED", "DISCUSSION_SKIP_CANCELLED",
                    "VOTING_STARTED", "VOTE_CAST",
                    "GAME_FINISHED", "PLAYER_ABANDONED" -> true;
            default -> false;
        };
    }

    private static String winnerFaction(WheresTheBoneGameState state) {
        if (state.getWinnerFaction() == null) return null;
        return switch (state.getWinnerFaction()) {
            case YARD_DOG -> "YARD_PACK";
            case WHITE_DOG -> "WHITE_DOG";
            case PACKMATE, BONE_THIEF -> "THIEF_PACK";
        };
    }

    private static List<String> legalActions(WheresTheBoneGameState state, String playerId, WheresTheBoneRole role) {
        if (role == null || state.isFinished() || !state.isActive(playerId)) return List.of();
        if (state.getPhase() == WheresTheBonePhase.WAKE_SELECTION && isYard(role) && !state.hasSelectedWake(playerId)) {
            return List.of(WheresTheBoneActionType.SELECT_WAKE_TIME.name());
        }
        if (state.getPhase() == WheresTheBonePhase.NIGHT_HOUR && state.isAwakeNow(playerId) && !state.isDone(playerId)) {
            if (role == WheresTheBoneRole.BONE_THIEF && !state.isBoneTaken()) {
                if (state.getPlayerIds().size() == 4 && state.wakeFor(playerId).stream().anyMatch(hour -> hour > state.getCurrentHour())) {
                    return List.of(WheresTheBoneActionType.TAKE_BONE.name(), WheresTheBoneActionType.WAIT.name());
                }
                return List.of(WheresTheBoneActionType.TAKE_BONE.name());
            }
            if (!state.getPackmates().contains(playerId) && state.awakePlayerIds().size() == 1) {
                return List.of(WheresTheBoneActionType.PEEK_WAKE_TIME.name(), WheresTheBoneActionType.WAIT.name());
            }
            return List.of(WheresTheBoneActionType.WAIT.name());
        }
        if (state.getPhase() == WheresTheBonePhase.PACK_SELECTION && role == WheresTheBoneRole.BONE_THIEF) {
            return List.of(WheresTheBoneActionType.SELECT_PACKMATE.name());
        }
        if (state.getPhase() == WheresTheBonePhase.DISCUSSION) {
            if (state.getDiscussionSkipRequesterId() != null) {
                return state.getDiscussionSkipResponses().containsKey(playerId)
                        ? List.of()
                        : List.of(WheresTheBoneActionType.RESPOND_SKIP_DISCUSSION.name());
            }
            if (!state.getDiscussionSkipRequesters().contains(playerId)) {
                return List.of(WheresTheBoneActionType.REQUEST_SKIP_DISCUSSION.name());
            }
        }
        if (state.getPhase() == WheresTheBonePhase.VOTING && !state.getVotes().containsKey(playerId)) {
            return List.of(WheresTheBoneActionType.VOTE.name());
        }
        return List.of();
    }

    private static boolean isYard(WheresTheBoneRole role) {
        return role == WheresTheBoneRole.YARD_DOG || role == WheresTheBoneRole.WHITE_DOG;
    }
}
