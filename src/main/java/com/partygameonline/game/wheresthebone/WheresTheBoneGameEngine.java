package com.partygameonline.game.wheresthebone;

import com.partygameonline.common.UniqueDisplayNames;
import com.partygameonline.game.core.GameActionFormatException;
import com.partygameonline.game.core.GameConfig;
import com.partygameonline.game.core.GameEngine;
import com.partygameonline.game.core.GameResult;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.RandomSource;
import com.partygameonline.game.core.ValidationResult;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneAction;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneActionType;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneEvent;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneGameState;
import com.partygameonline.game.wheresthebone.domain.WheresTheBonePackSelectionMode;
import com.partygameonline.game.wheresthebone.domain.WheresTheBonePeek;
import com.partygameonline.game.wheresthebone.domain.WheresTheBonePhase;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneRole;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneSettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public final class WheresTheBoneGameEngine implements GameEngine<WheresTheBoneGameState, WheresTheBoneAction, WheresTheBoneEvent> {

    private static final int MAX_DIE = 6;

    @Override
    public String gameType() { return WheresTheBoneGameManifest.ID; }

    @Override
    public WheresTheBoneGameState createGame(GameConfig config, RandomSource random) {
        int count = config.playerIds().size();
        if (count < WheresTheBoneGameState.MIN_PLAYERS || count > WheresTheBoneGameState.MAX_PLAYERS) {
            throw new IllegalArgumentException("Where's the Bone requires 4-8 players");
        }
        Map<String, String> names = UniqueDisplayNames.uniquifyAll(config.playerIds(), config.displayNames());
        WheresTheBoneSettings settings = WheresTheBoneSettings.fromRoomSettings(config.settings());
        WheresTheBoneGameState state = new WheresTheBoneGameState(
                config.roomId(), config.playerIds().get(0), config.playerIds(), names, settings
        );
        List<String> shuffled = new ArrayList<>(config.playerIds());
        random.shuffle(shuffled);
        String thief = shuffled.get(0);
        // White Dog is an optional special role and may also appear in the
        // four-player setup; it still participates in the normal wake choice.
        String whiteDog = settings.whiteDogEnabled() && count > 1 ? shuffled.get(1) : null;
        Map<String, List<Integer>> dice = randomWakeSchedule(count, shuffled, random);
        for (String playerId : config.playerIds()) {
            WheresTheBoneRole role = playerId.equals(thief)
                    ? WheresTheBoneRole.BONE_THIEF
                    : playerId.equals(whiteDog) ? WheresTheBoneRole.WHITE_DOG : WheresTheBoneRole.YARD_DOG;
            state.getRoles().put(playerId, role);
            state.getDiceRolls().put(playerId, List.copyOf(dice.get(playerId)));
            state.getWakeHours().put(playerId,
                    count == 4 && role != WheresTheBoneRole.BONE_THIEF
                            ? List.of()
                            : effective(dice.get(playerId)));
        }
        if (count == 4) {
            state.setPhase(WheresTheBonePhase.WAKE_SELECTION);
            state.setDeadline(null);
        } else {
            state.setPhase(WheresTheBonePhase.NIGHT_HOUR);
            // The standalone game gives the first night hour a short grace
            // window while the role cards settle and clients join the table.
            advanceNight(state, 10);
        }
        state.addEvent(WheresTheBoneEvent.of("GAME_STARTED", Map.of("playerCount", count)));
        return state;
    }

    @Override
    public WheresTheBoneAction decodeAction(Map<String, Object> payload) {
        Object rawType = payload.get("type");
        if (!(rawType instanceof String type) || type.isBlank()) {
            throw new GameActionFormatException("type is required");
        }
        WheresTheBoneActionType actionType;
        try {
            actionType = WheresTheBoneActionType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new GameActionFormatException("unknown action type");
        }
        return new WheresTheBoneAction(
                actionType,
                stringValue(payload.get("commandId")),
                integerValue(payload.get("expectedVersion")),
                integerValue(payload.containsKey("hour") ? payload.get("hour") : payload.get("selectedWakeTime")),
                stringValue(payload.get("targetPlayerId")),
                stringList(payload, "targetPlayerIds", "targetIds")
        );
    }

    @Override
    public ValidationResult validate(WheresTheBoneGameState state, PlayerContext actor, WheresTheBoneAction action) {
        if (state.isFinished()) return ValidationResult.reject("GAME_FINISHED", "The game has finished");
        if (action.expectedVersion() != null && action.expectedVersion() != state.getVersion()) {
            return ValidationResult.reject("STALE_VERSION", "The game state has changed; refresh and try again");
        }
        if (action.type() == WheresTheBoneActionType.TIMEOUT) {
            return state.getDeadline() != null && !state.getDeadline().isAfter(Instant.now())
                    ? ValidationResult.ok()
                    : ValidationResult.reject("ACTION_NOT_ALLOWED", "This phase has not timed out");
        }
        String playerId = actor.playerId();
        if (!state.getPlayerIds().contains(playerId)) return ValidationResult.reject("NOT_ROOM_MEMBER", "You are not in this game");
        if (!state.isActive(playerId)) return ValidationResult.reject("ACTION_NOT_ALLOWED", "You have left this game");
        if (state.isProcessed(action.commandId())) return ValidationResult.reject("DUPLICATE_REQUEST", "This request was already processed");

        return switch (action.type()) {
            case SELECT_WAKE_TIME -> validateWakeSelection(state, playerId, action.hour());
            case TAKE_BONE -> validateNightAction(state, playerId, true, false);
            case PEEK_WAKE_TIME -> validatePeek(state, playerId, action.targetPlayerId());
            case WAIT -> validateNightAction(state, playerId, false, true);
            case SELECT_PACKMATE -> validatePackSelection(state, playerId, action.targetPlayerIds(), action.targetPlayerId());
            case START_VOTE -> state.getPhase() == WheresTheBonePhase.DISCUSSION && state.getHostPlayerId().equals(playerId)
                    ? ValidationResult.ok() : ValidationResult.reject("ACTION_NOT_ALLOWED", "Only the host can start voting");
            case VOTE -> validateVote(state, playerId, action.targetPlayerId());
            case TIMEOUT -> ValidationResult.ok();
        };
    }

    @Override
    public GameResult<WheresTheBoneGameState, WheresTheBoneEvent> apply(
            WheresTheBoneGameState state, PlayerContext actor, WheresTheBoneAction action, RandomSource random
    ) {
        if (action.type() != WheresTheBoneActionType.TIMEOUT && state.isProcessed(action.commandId())) {
            return GameResult.of(state, List.of());
        }
        if (action.commandId() != null) state.markProcessed(action.commandId());
        state.bumpVersion();
        List<WheresTheBoneEvent> events = new ArrayList<>();
        switch (action.type()) {
            case SELECT_WAKE_TIME -> selectWakeTime(state, actor.playerId(), action.hour(), events);
            case TAKE_BONE -> takeBone(state, actor.playerId(), events);
            case PEEK_WAKE_TIME -> peek(state, actor.playerId(), action.targetPlayerId(), events);
            case WAIT -> waitForHour(state, actor.playerId(), events);
            case SELECT_PACKMATE -> selectPackmates(state, action, events);
            case START_VOTE -> startVoting(state, events);
            case VOTE -> vote(state, actor.playerId(), action.targetPlayerId(), events);
            case TIMEOUT -> timeout(state, random, events);
        }
        events.forEach(state::addEvent);
        return state.isFinished()
                ? GameResult.finished(state, events, state.getWinnerPlayerIds().stream().findFirst().orElse(null))
                : GameResult.of(state, events);
    }

    @Override
    public GameResult<WheresTheBoneGameState, WheresTheBoneEvent> onPlayerAbandoned(
            WheresTheBoneGameState state, PlayerContext player, RandomSource random
    ) {
        String playerId = player.playerId();
        if (state.isFinished() || !state.isActive(playerId)) return GameResult.of(state, List.of());
        state.abandon(playerId);
        state.bumpVersion();
        List<WheresTheBoneEvent> events = new ArrayList<>();
        events.add(WheresTheBoneEvent.of("PLAYER_ABANDONED", Map.of("playerId", playerId)));

        // Wake selection has no timer in the source game. Choose one of the
        // abandoned Yard-side player's dice so the remaining table cannot hang.
        if (state.getPhase() == WheresTheBonePhase.WAKE_SELECTION
                && isYardSide(state.getRoles().get(playerId))
                && !state.hasSelectedWake(playerId)
                && !state.diceFor(playerId).isEmpty()) {
            List<Integer> dice = state.diceFor(playerId);
            selectWakeTime(state, playerId, dice.get(random.nextInt(dice.size())), events);
        }
        state.getPendingPackCandidates().remove(playerId);
        state.setPendingPackCount(Math.min(state.getPendingPackCount(), state.getPendingPackCandidates().size()));
        if (state.getPhase() == WheresTheBonePhase.VOTING && allActivePlayersVoted(state)) {
            resolveVotes(state, events);
        }
        events.forEach(state::addEvent);
        return state.isFinished()
                ? GameResult.finished(state, events, state.getWinnerPlayerIds().stream().findFirst().orElse(null))
                : GameResult.of(state, events);
    }

    private static ValidationResult validateWakeSelection(WheresTheBoneGameState state, String playerId, Integer hour) {
        WheresTheBoneRole role = state.getRoles().get(playerId);
        if (state.getPhase() != WheresTheBonePhase.WAKE_SELECTION || !isYardSide(role)) {
            return ValidationResult.reject("ACTION_NOT_ALLOWED", "Only Yard Dogs choose a wake time");
        }
        if (state.hasSelectedWake(playerId)) return ValidationResult.reject("ACTION_NOT_ALLOWED", "You already chose a wake time");
        if (hour == null || !state.diceFor(playerId).contains(hour)) {
            return ValidationResult.reject("INVALID_WAKE_TIME", "Choose one of your secret dice");
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validateNightAction(WheresTheBoneGameState state, String playerId, boolean take, boolean wait) {
        if (state.getPhase() != WheresTheBonePhase.NIGHT_HOUR || !state.isAwakeNow(playerId)) {
            return ValidationResult.reject("ACTION_NOT_ALLOWED", "You are asleep at this hour");
        }
        if (state.isDone(playerId)) return ValidationResult.reject("ACTION_NOT_ALLOWED", "You already acted this hour");
        WheresTheBoneRole role = state.getRoles().get(playerId);
        if (take && role != WheresTheBoneRole.BONE_THIEF) return ValidationResult.reject("ACTION_NOT_ALLOWED", "Only the Bone Thief can take the bone");
        if (take && state.isBoneTaken()) return ValidationResult.reject("BONE_ALREADY_TAKEN", "The bone has already been taken");
        if (wait && role == WheresTheBoneRole.BONE_THIEF && !state.isBoneTaken()
                && !canThiefDelay(state)) return ValidationResult.reject("MUST_TAKE_BONE", "The Bone Thief must take the bone now");
        return ValidationResult.ok();
    }

    private static ValidationResult validatePeek(WheresTheBoneGameState state, String playerId, String target) {
        ValidationResult base = validateNightAction(state, playerId, false, false);
        if (!base.valid()) return base;
        if (!canPeek(state, playerId)) return ValidationResult.reject("ACTION_NOT_ALLOWED", "You cannot peek at this hour");
        if (target == null || !state.getPlayerIds().contains(target) || target.equals(playerId)) {
            return ValidationResult.reject("INVALID_TARGET", "Choose another player");
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validatePackSelection(WheresTheBoneGameState state, String playerId, List<String> ids, String single) {
        if (state.getPhase() != WheresTheBonePhase.PACK_SELECTION || state.getRoles().get(playerId) != WheresTheBoneRole.BONE_THIEF) {
            return ValidationResult.reject("ACTION_NOT_ALLOWED", "Only the Bone Thief can choose Packmates");
        }
        List<String> selected = normalizeTargets(ids, single);
        if (selected.size() != state.getPendingPackCount() || new LinkedHashSet<>(selected).size() != selected.size()) {
            return ValidationResult.reject("INVALID_PACK_SELECTION", "Choose exactly " + state.getPendingPackCount() + " different players");
        }
        if (selected.stream().anyMatch(id -> !state.getPendingPackCandidates().contains(id))) {
            return ValidationResult.reject("INVALID_PACK_SELECTION", "One or more selected players are not eligible");
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validateVote(WheresTheBoneGameState state, String playerId, String target) {
        if (state.getPhase() != WheresTheBonePhase.VOTING || state.getVotes().containsKey(playerId)) {
            return ValidationResult.reject("ACTION_NOT_ALLOWED", "You cannot vote now");
        }
        if (target == null || !state.getPlayerIds().contains(target) || target.equals(playerId)) {
            return ValidationResult.reject("INVALID_TARGET", "Vote for another player");
        }
        return ValidationResult.ok();
    }

    private static void selectWakeTime(WheresTheBoneGameState state, String playerId, Integer hour, List<WheresTheBoneEvent> events) {
        state.getWakeHours().put(playerId, List.of(hour));
        events.add(WheresTheBoneEvent.of("WAKE_TIME_SELECTED", Map.of("playerId", playerId)));
        boolean allSelected = state.getRoles().entrySet().stream().filter(entry -> isYardSide(entry.getValue())).allMatch(entry -> state.hasSelectedWake(entry.getKey()));
        if (allSelected) {
            state.setPhase(WheresTheBonePhase.NIGHT_HOUR);
            state.setCurrentHour(0);
            advanceNight(state, 0);
        }
    }

    private static void takeBone(WheresTheBoneGameState state, String thiefId, List<WheresTheBoneEvent> events) {
        state.setBoneTaken(true);
        state.setBoneTakenHour(state.getCurrentHour());
        state.setBoneTakenBy(thiefId);
        Set<String> awake = state.awakePlayerIds();
        state.markDone(thiefId);
        for (String playerId : awake) {
            if (!playerId.equals(thiefId)) state.witnessedFor(playerId).add(state.getCurrentHour());
        }
        events.add(WheresTheBoneEvent.of("BONE_TAKEN", Map.of("hour", state.getCurrentHour())));
        if (state.getPlayerIds().size() == 5) {
            List<String> witnesses = awake.stream().filter(id -> !id.equals(thiefId)).filter(id -> state.getRoles().get(id) != WheresTheBoneRole.BONE_THIEF).toList();
            if (witnesses.size() == 1) applyPackmates(state, witnesses);
            else if (witnesses.size() > 1) openPackSelection(state, witnesses, 1, WheresTheBonePackSelectionMode.WITNESS);
        }
    }

    private static void peek(WheresTheBoneGameState state, String playerId, String target, List<WheresTheBoneEvent> events) {
        state.peekFor(playerId).add(new WheresTheBonePeek(target, state.diceFor(target)));
        state.markDone(playerId);
        events.add(WheresTheBoneEvent.of("PEEK_RECORDED", Map.of("playerId", playerId)));
    }

    private static void waitForHour(WheresTheBoneGameState state, String playerId, List<WheresTheBoneEvent> events) {
        state.markDone(playerId);
        events.add(WheresTheBoneEvent.of("PLAYER_WAITED", Map.of("playerId", playerId)));
    }

    private static void selectPackmates(WheresTheBoneGameState state, WheresTheBoneAction action, List<WheresTheBoneEvent> events) {
        List<String> selected = normalizeTargets(action.targetPlayerIds(), action.targetPlayerId());
        applyPackmates(state, selected);
        WheresTheBonePackSelectionMode mode = state.getPackSelectionMode();
        state.getPendingPackCandidates().clear();
        state.setPendingPackCount(0);
        state.setPackSelectionMode(null);
        if (mode == WheresTheBonePackSelectionMode.WITNESS) resumeWitnessSelection(state);
        else enterDiscussion(state);
        events.add(WheresTheBoneEvent.of("PACKMATES_SELECTED", Map.of("count", selected.size())));
    }

    private static void startVoting(WheresTheBoneGameState state, List<WheresTheBoneEvent> events) {
        state.getVotes().clear();
        enterVoting(state);
        events.add(WheresTheBoneEvent.of("VOTING_STARTED", Map.of()));
    }

    private static void vote(WheresTheBoneGameState state, String playerId, String target, List<WheresTheBoneEvent> events) {
        state.getVotes().put(playerId, target);
        events.add(WheresTheBoneEvent.of("VOTE_CAST", Map.of("playerId", playerId)));
        if (allActivePlayersVoted(state)) resolveVotes(state, events);
    }

    private static void timeout(WheresTheBoneGameState state, RandomSource random, List<WheresTheBoneEvent> events) {
        switch (state.getPhase()) {
            case NIGHT_HOUR -> {
                String thief = state.getRoles().entrySet().stream().filter(entry -> entry.getValue() == WheresTheBoneRole.BONE_THIEF && state.isAwakeNow(entry.getKey())).map(Map.Entry::getKey).findFirst().orElse(null);
                if (thief != null && !state.isBoneTaken() && !canThiefDelay(state)) takeBone(state, thief, events);
                // A five-player theft can open a private witness selection. Do not advance
                // the hour until that selection is resolved or times out.
                if (state.getPhase() != WheresTheBonePhase.NIGHT_HOUR) return;
                state.clearDone();
                advanceNight(state, 0);
            }
            case PACK_SELECTION -> {
                List<String> candidates = new ArrayList<>(state.getPendingPackCandidates());
                random.shuffle(candidates);
                List<String> selected = candidates.stream().limit(state.getPendingPackCount()).toList();
                applyPackmates(state, selected);
                WheresTheBonePackSelectionMode mode = state.getPackSelectionMode();
                state.getPendingPackCandidates().clear();
                state.setPendingPackCount(0);
                state.setPackSelectionMode(null);
                if (mode == WheresTheBonePackSelectionMode.WITNESS) resumeWitnessSelection(state);
                else enterDiscussion(state);
                events.add(WheresTheBoneEvent.of("PACKMATES_AUTO_SELECTED", Map.of("count", selected.size())));
            }
            case DISCUSSION -> {
                state.getVotes().clear();
                enterVoting(state);
                events.add(WheresTheBoneEvent.of("VOTING_STARTED", Map.of()));
            }
            case VOTING -> resolveVotes(state, events);
            default -> { }
        }
    }

    private static void resolveVotes(WheresTheBoneGameState state, List<WheresTheBoneEvent> events) {
        Map<String, Long> counts = state.getVotes().values().stream().collect(Collectors.groupingBy(id -> id, LinkedHashMap::new, Collectors.counting()));
        long max = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        Set<String> revealed = counts.entrySet().stream().filter(entry -> entry.getValue() == max).map(Map.Entry::getKey).collect(Collectors.toCollection(LinkedHashSet::new));
        String thief = state.getRoles().entrySet().stream().filter(entry -> entry.getValue() == WheresTheBoneRole.BONE_THIEF).map(Map.Entry::getKey).findFirst().orElse(null);
        String white = state.getRoles().entrySet().stream().filter(entry -> entry.getValue() == WheresTheBoneRole.WHITE_DOG).map(Map.Entry::getKey).findFirst().orElse(null);
        WheresTheBoneRole faction = white != null && state.isActive(white) && revealed.contains(white)
                ? WheresTheBoneRole.WHITE_DOG
                : revealed.contains(thief) ? WheresTheBoneRole.YARD_DOG : WheresTheBoneRole.PACKMATE;
        state.setWinnerFaction(faction);
        state.setRevealedPlayerIds(revealed);
        if (faction == WheresTheBoneRole.WHITE_DOG) state.setWinnerPlayerIds(List.of(white));
        else if (faction == WheresTheBoneRole.YARD_DOG) state.setWinnerPlayerIds(state.getRoles().entrySet().stream()
                .filter(entry -> entry.getValue() == WheresTheBoneRole.YARD_DOG && state.isActive(entry.getKey()))
                .map(Map.Entry::getKey).toList());
        else {
            List<String> winners = new ArrayList<>();
            if (thief != null && state.isActive(thief)) winners.add(thief);
            state.getPackmates().stream().filter(state::isActive).forEach(winners::add);
            state.setWinnerPlayerIds(winners);
        }
        state.setPhase(WheresTheBonePhase.RESULT);
        state.setCurrentHour(0);
        state.setDeadline(null);
        state.setPhaseStartedAt(Instant.now());
        events.add(WheresTheBoneEvent.of("GAME_FINISHED", Map.of("revealedCount", revealed.size())));
    }

    private static void advanceNight(WheresTheBoneGameState state, int extraSeconds) {
        state.clearDone();
        state.setCurrentHour(state.getCurrentHour() + 1);
        if (state.getCurrentHour() > WheresTheBoneGameState.MAX_HOUR) {
            finishNight(state);
            return;
        }
        recordObservers(state);
        recordCoAwake(state);
        state.setPhaseStartedAt(Instant.now());
        state.setDeadline(Instant.now().plusSeconds(state.getSettings().nightSeconds() + extraSeconds));
    }

    private static void finishNight(WheresTheBoneGameState state) {
        state.setCurrentHour(0);
        if (!state.isBoneTaken()) {
            state.setBoneTaken(true);
            state.setBoneTakenHour(WheresTheBoneGameState.MAX_HOUR);
            state.getRoles().entrySet().stream().filter(entry -> entry.getValue() == WheresTheBoneRole.BONE_THIEF).map(Map.Entry::getKey).findFirst().ifPresent(state::setBoneTakenBy);
        }
        int needed = plannedPackmateCount(state.getPlayerIds().size()) - state.getPackmates().size();
        if (state.getPlayerIds().size() >= 6 && needed > 0) {
            String thief = state.getRoles().entrySet().stream().filter(entry -> entry.getValue() == WheresTheBoneRole.BONE_THIEF).map(Map.Entry::getKey).findFirst().orElse(null);
            List<String> candidates = state.activePlayerIds().stream()
                    .filter(id -> !id.equals(thief) && !state.getPackmates().contains(id))
                    .toList();
            openPackSelection(state, candidates, needed, WheresTheBonePackSelectionMode.POST_NIGHT);
        } else enterDiscussion(state);
    }

    private static void openPackSelection(WheresTheBoneGameState state, List<String> candidates, int count, WheresTheBonePackSelectionMode mode) {
        Instant previousDeadline = state.getDeadline();
        state.getPendingPackCandidates().clear();
        state.getPendingPackCandidates().addAll(candidates);
        state.setPendingPackCount(Math.min(count, candidates.size()));
        state.setPackSelectionMode(mode);
        state.setPhase(WheresTheBonePhase.PACK_SELECTION);
        state.setPhaseStartedAt(Instant.now());
        if (mode == WheresTheBonePackSelectionMode.WITNESS) {
            // Witness recruitment occupies the remainder of the current hour;
            // it must not extend the night timer. If no timer exists, fall back
            // to the configured pack-selection timeout.
            state.setSuspendedNightDeadline(previousDeadline);
            state.setDeadline(previousDeadline == null
                    ? Instant.now().plusSeconds(state.getSettings().packSelectionSeconds())
                    : previousDeadline);
        } else {
            state.setDeadline(Instant.now().plusSeconds(state.getSettings().packSelectionSeconds()));
        }
    }

    private static void resumeWitnessSelection(WheresTheBoneGameState state) {
        Instant nightDeadline = state.getSuspendedNightDeadline();
        state.setSuspendedNightDeadline(null);
        state.setPhase(WheresTheBonePhase.NIGHT_HOUR);
        state.setPhaseStartedAt(Instant.now());
        if (nightDeadline == null || !nightDeadline.isAfter(Instant.now())) advanceNight(state, 0);
        else state.setDeadline(nightDeadline);
    }

    private static void enterDiscussion(WheresTheBoneGameState state) {
        state.setPhase(WheresTheBonePhase.DISCUSSION);
        state.setCurrentHour(0);
        state.setPhaseStartedAt(Instant.now());
        state.setDeadline(Instant.now().plusSeconds(Math.max(1, state.getPlayerIds().size() - 1) * WheresTheBoneGameState.DISCUSSION_SECONDS_PER_PLAYER));
    }

    private static void enterVoting(WheresTheBoneGameState state) {
        state.setPhase(WheresTheBonePhase.VOTING);
        state.setPhaseStartedAt(Instant.now());
        state.setDeadline(Instant.now().plusSeconds(WheresTheBoneGameState.VOTING_SECONDS));
    }

    private static void applyPackmates(WheresTheBoneGameState state, List<String> selected) {
        for (String id : selected) {
            state.getPackmates().add(id);
            if (state.getRoles().get(id) != WheresTheBoneRole.WHITE_DOG) state.getRoles().put(id, WheresTheBoneRole.PACKMATE);
        }
    }

    private static boolean canPeek(WheresTheBoneGameState state, String playerId) {
        return state.awakePlayerIds().size() == 1 && state.isAwakeNow(playerId)
                && !state.getPackmates().contains(playerId) && isYardSide(state.getRoles().get(playerId));
    }

    private static boolean canThiefDelay(WheresTheBoneGameState state) {
        if (state.getPlayerIds().size() != 4) return false;
        String thief = state.getRoles().entrySet().stream().filter(entry -> entry.getValue() == WheresTheBoneRole.BONE_THIEF).map(Map.Entry::getKey).findFirst().orElse(null);
        return thief != null && state.wakeFor(thief).stream().anyMatch(hour -> hour > state.getCurrentHour());
    }

    private static boolean allActivePlayersVoted(WheresTheBoneGameState state) {
        List<String> active = state.activePlayerIds();
        return !active.isEmpty() && active.stream().allMatch(state.getVotes()::containsKey);
    }

    private static void recordCoAwake(WheresTheBoneGameState state) {
        Set<String> awake = state.awakePlayerIds();
        if (awake.size() < 2) return;
        for (String playerId : awake) {
            state.coAwakeFor(playerId).put(state.getCurrentHour(), awake.stream().filter(id -> !id.equals(playerId)).collect(Collectors.toCollection(LinkedHashSet::new)));
        }
    }

    private static void recordObservers(WheresTheBoneGameState state) {
        for (String playerId : state.awakePlayerIds()) {
            if (state.getRoles().get(playerId) == WheresTheBoneRole.BONE_THIEF) continue;
            if (!state.isBoneTaken()) state.observedPresentFor(playerId).add(state.getCurrentHour());
            else if (state.getBoneTakenHour() != null && state.getBoneTakenHour() < state.getCurrentHour()) state.observedMissingFor(playerId).add(state.getCurrentHour());
        }
    }

    private static Map<String, List<Integer>> randomWakeSchedule(int count, List<String> playerIds, RandomSource random) {
        int cap = count - plannedPackmateCount(count) - 1;
        int diceCount = count == 4 ? 2 : 1;
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        Map<Integer, Integer> possible = new LinkedHashMap<>();
        for (String playerId : playerIds) {
            List<Integer> dice = new ArrayList<>();
            for (int i = 0; i < diceCount; i++) {
                List<Integer> candidates = new ArrayList<>();
                for (int hour = 1; hour <= MAX_DIE; hour++) {
                    if (dice.contains(hour) || possible.getOrDefault(hour, 0) < cap) candidates.add(hour);
                }
                int hour = candidates.get(random.nextInt(candidates.size()));
                dice.add(hour);
                if (!dice.subList(0, dice.size() - 1).contains(hour)) possible.merge(hour, 1, Integer::sum);
            }
            result.put(playerId, dice);
        }
        return result;
    }

    private static List<Integer> effective(List<Integer> dice) { return dice.stream().distinct().sorted().toList(); }
    private static boolean isYardSide(WheresTheBoneRole role) { return role == WheresTheBoneRole.YARD_DOG || role == WheresTheBoneRole.WHITE_DOG; }
    private static int plannedPackmateCount(int players) { return switch (players) { case 5, 6 -> 1; case 7, 8 -> 2; default -> 0; }; }

    private static List<String> normalizeTargets(List<String> ids, String single) {
        if (ids != null && !ids.isEmpty()) return ids.stream().filter(id -> id != null && !id.isBlank()).toList();
        return single == null || single.isBlank() ? List.of() : List.of(single);
    }

    private static String stringValue(Object value) { return value instanceof String text && !text.isBlank() ? text : null; }
    private static Integer integerValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) try { return Integer.valueOf(text.trim()); } catch (NumberFormatException ignored) { }
        return null;
    }
    private static List<String> stringList(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object raw = payload.get(key);
            if (!(raw instanceof List<?> values)) continue;
            return values.stream().filter(String.class::isInstance).map(String.class::cast).filter(value -> !value.isBlank()).toList();
        }
        return List.of();
    }
}
