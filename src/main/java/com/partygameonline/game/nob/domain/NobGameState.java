package com.partygameonline.game.nob.domain;

import com.partygameonline.game.core.RandomSource;
import com.partygameonline.game.core.GameEloChange;
import com.partygameonline.game.core.GameEloRound;
import com.partygameonline.game.core.GameEloRoundPlayer;
import com.partygameonline.game.core.GameEloChangeSink;
import com.partygameonline.game.core.GameOutcomeState;
import com.partygameonline.game.core.GamePlayerOutcome;
import com.partygameonline.game.core.GameRoundEloSource;
import com.partygameonline.game.nob.catalog.NobCardCatalog;
import com.partygameonline.game.nob.catalog.NobCardDef;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NobGameState implements GameRoundEloSource, GameEloChangeSink, GameOutcomeState {

    public static final int MIN_PLAYERS = 4;
    public static final int MAX_PLAYERS = 11;
    public static final int TARGET_SCORE = 10;
    public static final int HURRY_UP_SECONDS = 5;

    private final String roomId;
    private int roundNumber = 1;
    private NobPhase phase = NobPhase.BLOODLINE_ASSIGNMENT;
    private NobPhaseState phaseState = NobPhaseState.IDLE;
    private int version = 1;
    private int targetScore = TARGET_SCORE;
    private NobTimingSettings timing = NobTimingSettings.defaults();
    private boolean finished;
    private NobAnnouncement announcement;
    private NobCardInstance currentResolvingCard;
    private String currentActorPlayerId;
    private NobRoundResult lastRoundResult;
    private Instant windowStartedAt;
    private Instant resolutionDisplayExpiresAt;
    private final Deque<NobAnnouncement> presentationQueue = new ArrayDeque<>();
    private final List<String> roundRewardPlayerIds = new ArrayList<>();
    private final Map<String, List<NobMoonTokenOption>> moonTokenOffers = new LinkedHashMap<>();
    private final Set<String> moonTokenClaimed = new HashSet<>();
    private final Map<String, Integer> moonPickNeed = new LinkedHashMap<>();
    private final Map<String, Integer> moonPicksTaken = new LinkedHashMap<>();
    private final List<String> winnerPlayerIds = new ArrayList<>();
    private final List<NobCompletedRound> completedRounds = new ArrayList<>();
    private final Map<Integer, Map<String, NobEloChange>> roundEloChanges = new LinkedHashMap<>();
    private final Map<String, Integer> eloSimulation = new LinkedHashMap<>();
    private final Map<String, NobEloChange> finalEloChanges = new LinkedHashMap<>();
    private final List<NobPlayerState> players = new ArrayList<>();
    private final List<NobCardInstance> discardPile = new ArrayList<>();
    private final List<NobCardInstance> undealt = new ArrayList<>();
    private final List<NobMoonMark> moonMarkPool = new ArrayList<>();
    private final Deque<NobResolutionItem> resolutionQueue = new ArrayDeque<>();
    private NobPendingDecision pendingDecision;
    private final List<NobPublicLogEntry> publicLog = new ArrayList<>();
    private final Map<String, List<NobCardInstance>> draftHands = new LinkedHashMap<>();
    private final Map<String, String> draftPicks = new LinkedHashMap<>();
    private final Map<String, List<String>> phaseSubmissions = new LinkedHashMap<>();
    private final Set<String> processedCommandIds = new HashSet<>();
    private final List<NobCardInstance> echoHold = new ArrayList<>();
    private int echoCardCount;
    private NobCardInstance echoSource;
    private NobCardInstance echoPicked;
    private Instant phaseDeadline;
    private NobKillAttempt activeKill;

    public NobGameState(String roomId) {
        this.roomId = roomId;
    }

    public String getRoomId() {
        return roomId;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public NobPhase getPhase() {
        return phase;
    }

    public void setPhase(NobPhase phase) {
        this.phase = phase;
    }

    public NobPhaseState getPhaseState() {
        return phaseState;
    }

    public void setPhaseState(NobPhaseState phaseState) {
        this.phaseState = phaseState;
    }

    public int getVersion() {
        return version;
    }

    public void bumpVersion() {
        version += 1;
    }

    public int getTargetScore() {
        return targetScore;
    }

    public NobTimingSettings getTiming() {
        return timing;
    }

    public int getDraftSeconds() {
        return timing.draftPickSeconds();
    }

    public int getPhaseSubmitSeconds() {
        return timing.phaseSubmitSeconds();
    }

    public int getDecisionSeconds() {
        return timing.optionDecisionSeconds();
    }

    public int getTargetDecisionSeconds() {
        return timing.targetDecisionSeconds();
    }

    public int getHunterDecisionSeconds() {
        return timing.hunterDecisionSeconds();
    }

    public int getReactionSeconds() {
        return timing.reactionDecisionSeconds();
    }

    public int timeoutSecondsFor(NobDecisionType type) {
        return timing.secondsFor(type);
    }

    public NobAnnouncement getAnnouncement() {
        return announcement;
    }

    public void setAnnouncement(NobAnnouncement announcement) {
        this.announcement = announcement;
    }

    public Instant getResolutionDisplayExpiresAt() {
        return resolutionDisplayExpiresAt;
    }

    public void setResolutionDisplayExpiresAt(Instant resolutionDisplayExpiresAt) {
        this.resolutionDisplayExpiresAt = resolutionDisplayExpiresAt;
    }

    public Deque<NobAnnouncement> getPresentationQueue() {
        return presentationQueue;
    }

    public void announce(
            String type,
            String actorPlayerId,
            String targetPlayerId,
            String cardCode,
            String reactionCardCode,
            String messageKey
    ) {
        NobAnnouncement next = NobAnnouncement.of(
                type,
                actorPlayerId,
                targetPlayerId,
                cardCode,
                reactionCardCode,
                messageKey,
                timing.announcementDisplayMs()
        );
        if (announcement != null && resolutionDisplayExpiresAt != null) {
            presentationQueue.addLast(next);
            return;
        }
        this.announcement = next;
    }

    public void holdResultDisplay() {
        holdResultDisplay(timing.resolutionCardDisplayMs());
    }

    public void holdResultDisplay(int millis) {
        phaseState = NobPhaseState.RESOLUTION_RESULT_DISPLAY;
        pendingDecision = null;
        if (resolutionDisplayExpiresAt == null) {
            resolutionDisplayExpiresAt = Instant.now().plusMillis(Math.max(millis, 1));
        }
    }

    public void holdResultDisplayFor(int millis) {
        phaseState = NobPhaseState.RESOLUTION_RESULT_DISPLAY;
        pendingDecision = null;
        resolutionDisplayExpiresAt = Instant.now().plusMillis(Math.max(millis, 1));
    }

    public NobCardInstance getCurrentResolvingCard() {
        return currentResolvingCard;
    }

    public void setCurrentResolvingCard(NobCardInstance currentResolvingCard) {
        this.currentResolvingCard = currentResolvingCard;
    }

    public String getCurrentActorPlayerId() {
        return currentActorPlayerId;
    }

    public void setCurrentActorPlayerId(String currentActorPlayerId) {
        this.currentActorPlayerId = currentActorPlayerId;
    }

    public NobRoundResult getLastRoundResult() {
        return lastRoundResult;
    }

    public void setLastRoundResult(NobRoundResult lastRoundResult) {
        this.lastRoundResult = lastRoundResult;
    }

    public Instant getWindowStartedAt() {
        return windowStartedAt;
    }

    public void setWindowStartedAt(Instant windowStartedAt) {
        this.windowStartedAt = windowStartedAt;
    }

    public List<String> submittedPlayerIds() {
        if (phase == NobPhase.DRAFT_PICK_1 || phase == NobPhase.DRAFT_PICK_2) {
            return List.copyOf(draftPicks.keySet());
        }
        if (phaseState == NobPhaseState.WAITING_FOR_PHASE_SUBMISSIONS) {
            return List.copyOf(phaseSubmissions.keySet());
        }
        return List.of();
    }

    public List<NobCardInstance> getEchoHold() {
        return echoHold;
    }

    public int getEchoCardCount() {
        return echoCardCount;
    }

    public void setEchoCardCount(int echoCardCount) {
        this.echoCardCount = Math.max(0, echoCardCount);
    }

    public NobCardInstance getEchoSource() {
        return echoSource;
    }

    public void setEchoSource(NobCardInstance echoSource) {
        this.echoSource = echoSource;
    }

    public NobCardInstance getEchoPicked() {
        return echoPicked;
    }

    public void setEchoPicked(NobCardInstance echoPicked) {
        this.echoPicked = echoPicked;
    }

    public void clearEchoShowcase() {
        echoHold.clear();
        echoCardCount = 0;
        echoSource = null;
        echoPicked = null;
    }

    public void configure(int targetScore, int draftSeconds, int phaseSubmitSeconds, int decisionSeconds, int reactionSeconds) {
        configure(targetScore, new NobTimingSettings(
                draftSeconds,
                phaseSubmitSeconds,
                decisionSeconds,
                decisionSeconds,
                decisionSeconds,
                reactionSeconds,
                2500,
                3000,
                30
        ));
    }

    public void configure(int targetScore, NobTimingSettings timing) {
        this.targetScore = targetScore > 0 ? targetScore : TARGET_SCORE;
        this.timing = timing == null ? NobTimingSettings.defaults() : timing;
    }

    public boolean isDuplicateCommand(String commandId) {
        return commandId != null && processedCommandIds.contains(commandId);
    }

    public boolean timeoutIsDue(Instant now) {
        if (pendingDecision != null && pendingDecision.expiresAt() != null && !pendingDecision.expiresAt().isAfter(now)) {
            return true;
        }
        if (resolutionDisplayExpiresAt != null && !resolutionDisplayExpiresAt.isAfter(now)) {
            return true;
        }
        if (phase == NobPhase.ROUND_SUMMARY
                && phaseDeadline != null
                && !phaseDeadline.isAfter(now)) {
            return true;
        }
        if (pendingDecision == null
                && phaseDeadline != null
                && !phaseDeadline.isAfter(now)
                && (phase == NobPhase.DRAFT_PICK_1
                || phase == NobPhase.DRAFT_PICK_2
                || (phaseState == NobPhaseState.WAITING_FOR_PHASE_SUBMISSIONS && currentResolvingCard == null))) {
            return true;
        }
        return pendingDecision == null
                && activeKill == null
                && currentResolvingCard != null
                && resolutionDisplayExpiresAt == null;
    }

    public List<String> getRoundRewardPlayerIds() {
        return roundRewardPlayerIds;
    }

    public Map<String, List<NobMoonTokenOption>> getMoonTokenOffers() {
        return moonTokenOffers;
    }

    public Set<String> getMoonTokenClaimed() {
        return moonTokenClaimed;
    }

    public boolean hasUnclaimedMoonPick(String playerId) {
        if (!moonTokenOffers.containsKey(playerId) || moonTokenClaimed.contains(playerId)) {
            return false;
        }
        return moonPicksTaken.getOrDefault(playerId, 0) < moonPickNeed.getOrDefault(playerId, 1);
    }

    public int moonPicksNeeded(String playerId) {
        return moonPickNeed.getOrDefault(playerId, 1);
    }

    public int moonPicksTaken(String playerId) {
        return moonPicksTaken.getOrDefault(playerId, 0);
    }

    public void recordMoonPick(String playerId) {
        moonPicksTaken.put(playerId, moonPicksTaken.getOrDefault(playerId, 0) + 1);
        if (moonPicksTaken.getOrDefault(playerId, 0) >= moonPickNeed.getOrDefault(playerId, 1)) {
            moonTokenClaimed.add(playerId);
        }
    }

    public List<String> unclaimedMoonPlayerIds() {
        return roundRewardPlayerIds.stream().filter(this::hasUnclaimedMoonPick).toList();
    }

    public void beginRoundSummary(List<String> rewardIds, RandomSource random) {
        phase = NobPhase.ROUND_SUMMARY;
        phaseState = NobPhaseState.WAITING_FOR_OPTION;
        pendingDecision = null;
        activeKill = null;
        currentResolvingCard = null;
        resolutionDisplayExpiresAt = null;
        presentationQueue.clear();
        currentActorPlayerId = rewardIds.isEmpty() ? null : rewardIds.getFirst();
        roundRewardPlayerIds.clear();
        roundRewardPlayerIds.addAll(rewardIds);
        moonTokenOffers.clear();
        moonTokenClaimed.clear();
        moonPickNeed.clear();
        moonPicksTaken.clear();
        // Reserve every rewarded player's required pick(s) before adding
        // optional choices. Otherwise, earlier players can lock three marks
        // each and leave later players with no pick when the pool gets low.
        for (String playerId : rewardIds) {
            int required = com.partygameonline.game.nob.scoring.NobScoringService.moonPicksNeeded(this, playerId);
            List<NobMoonTokenOption> options = new ArrayList<>(3);
            for (int i = 0; i < required; i++) {
                NobMoonMark mark = drawMoonMark(random);
                if (mark == null) {
                    break;
                }
                options.add(new NobMoonTokenOption(UUID.randomUUID().toString(), mark));
            }
            if (options.isEmpty()) {
                continue;
            }
            int need = Math.min(
                    com.partygameonline.game.nob.scoring.NobScoringService.moonPicksNeeded(this, playerId),
                    options.size()
            );
            moonTokenOffers.put(playerId, options);
            moonPicksTaken.put(playerId, 0);
            moonPickNeed.put(playerId, need);
        }
        boolean addedChoice = true;
        while (addedChoice && !moonMarkPool.isEmpty()) {
            addedChoice = false;
            for (String playerId : rewardIds) {
                List<NobMoonTokenOption> current = moonTokenOffers.get(playerId);
                if (current == null || current.size() >= 3) {
                    continue;
                }
                NobMoonMark mark = drawMoonMark(random);
                if (mark == null) {
                    break;
                }
                List<NobMoonTokenOption> expanded = new ArrayList<>(current);
                expanded.add(new NobMoonTokenOption(UUID.randomUUID().toString(), mark));
                moonTokenOffers.put(playerId, List.copyOf(expanded));
                addedChoice = true;
            }
        }
        Instant now = Instant.now();
        windowStartedAt = now;
        setPhaseDeadline(now.plusSeconds(timing.roundSummarySeconds()));
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public List<String> getWinnerPlayerIds() {
        return winnerPlayerIds;
    }

    @Override
    public Set<String> winnerPlayerIds() {
        return Set.copyOf(winnerPlayerIds);
    }

    @Override
    public GamePlayerOutcome playerOutcome(String playerId) {
        NobPlayerState player = player(playerId);
        if (player == null) {
            return null;
        }
        String role = java.util.stream.Stream.of(player.getUsedCards(), player.getRevealedCards(), player.getHand())
                .flatMap(List::stream)
                .map(card -> card.roleType().name())
                .filter(value -> !"SPECIAL".equals(value))
                .findFirst()
                .orElse(null);
        String bloodline = player.getCurrentBloodline() == null
                ? null
                : player.getCurrentBloodline().type().name();
        return new GamePlayerOutcome(player.score(), role, bloodline);
    }

    public List<NobCompletedRound> getCompletedRounds() {
        return List.copyOf(completedRounds);
    }

    public void recordCompletedRound(NobRoundResult roundResult, List<String> rewardedPlayerIds) {
        if (roundResult == null) {
            return;
        }
        Set<String> winners = rewardedPlayerIds == null
                ? Set.of()
                : new HashSet<>(rewardedPlayerIds);
        List<NobRoundPlayerSnapshot> playerSnapshots = players.stream()
                .map(player -> new NobRoundPlayerSnapshot(
                        player.getPlayerId(),
                        player.getCurrentBloodline() == null
                                ? null
                                : player.getCurrentBloodline().type().name(),
                        winners.contains(player.getPlayerId()) ? "WIN" : "LOSS",
                        player.score()
                ))
                .toList();
        completedRounds.add(new NobCompletedRound(roundNumber, roundResult, playerSnapshots));
    }

    public Map<String, NobEloChange> getRoundEloChanges(int roundNumber) {
        return Map.copyOf(roundEloChanges.getOrDefault(roundNumber, Map.of()));
    }

    public Map<String, NobEloChange> getFinalEloChanges() {
        return Map.copyOf(finalEloChanges);
    }

    public Map<String, Integer> getEloSimulation() {
        return Map.copyOf(eloSimulation);
    }

    @Override
    public List<GameEloRound> completedEloRounds() {
        return completedRounds.stream()
                .map(round -> new GameEloRound(
                        round.roundNumber(),
                        round.players().stream()
                                .map(player -> new GameEloRoundPlayer(
                                        player.playerId(),
                                        "WIN".equalsIgnoreCase(player.result()),
                                        player.score()
                                ))
                                .toList()
                ))
                .toList();
    }

    @Override
    public Map<String, Integer> eloSimulation() {
        return getEloSimulation();
    }

    @Override
    public void recordGameEloRoundChanges(int roundNumber, Map<String, GameEloChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        Map<String, NobEloChange> translated = new LinkedHashMap<>();
        changes.forEach((playerId, change) -> translated.put(
                playerId,
                new NobEloChange(change.oldElo(), change.eloDelta(), change.newElo())
        ));
        recordRoundEloChanges(roundNumber, translated);
    }

    public void recordRoundEloChanges(int roundNumber, Map<String, NobEloChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        Map<String, NobEloChange> copy = new LinkedHashMap<>(changes);
        roundEloChanges.put(roundNumber, Map.copyOf(copy));
        copy.forEach((playerId, change) -> eloSimulation.put(playerId, change.newElo()));
        for (int i = 0; i < completedRounds.size(); i++) {
            NobCompletedRound round = completedRounds.get(i);
            if (round.roundNumber() != roundNumber) {
                continue;
            }
            List<NobRoundPlayerSnapshot> snapshots = round.players().stream()
                    .map(snapshot -> {
                        NobEloChange change = copy.get(snapshot.playerId());
                        return change == null
                                ? snapshot
                                : new NobRoundPlayerSnapshot(
                                        snapshot.playerId(),
                                        snapshot.bloodline(),
                                        snapshot.result(),
                                        snapshot.score(),
                                        change.eloDelta()
                                );
                    })
                    .toList();
            completedRounds.set(i, new NobCompletedRound(round.roundNumber(), round.roundResult(), snapshots));
            break;
        }
    }

    public void recordFinalEloChanges(Map<String, NobEloChange> changes) {
        finalEloChanges.clear();
        if (changes != null) {
            finalEloChanges.putAll(changes);
        }
    }

    @Override
    public void recordEloChanges(Map<String, GameEloChange> changes) {
        Map<String, NobEloChange> translated = new LinkedHashMap<>();
        if (changes != null) {
            changes.forEach((playerId, change) -> translated.put(
                    playerId,
                    new NobEloChange(change.oldElo(), change.eloDelta(), change.newElo())
            ));
        }
        recordFinalEloChanges(translated);
    }

    public List<NobPlayerState> getPlayers() {
        return players;
    }

    public List<NobPlayerState> alivePlayers() {
        return players.stream().filter(NobPlayerState::isAlive).toList();
    }

    public NobPlayerState player(String playerId) {
        return players.stream().filter(player -> player.getPlayerId().equals(playerId)).findFirst().orElse(null);
    }

    public NobPlayerState requirePlayer(String playerId) {
        NobPlayerState player = player(playerId);
        if (player == null) {
            throw new IllegalArgumentException("Unknown player");
        }
        return player;
    }

    public List<NobCardInstance> getDiscardPile() {
        return discardPile;
    }

    public List<NobCardInstance> getUndealt() {
        return undealt;
    }

    public List<NobMoonMark> getMoonMarkPool() {
        return moonMarkPool;
    }

    public Deque<NobResolutionItem> getResolutionQueue() {
        return resolutionQueue;
    }

    public NobPendingDecision getPendingDecision() {
        return pendingDecision;
    }

    public void setPendingDecision(NobPendingDecision pendingDecision) {
        this.pendingDecision = pendingDecision;
    }

    public List<NobPublicLogEntry> getPublicLog() {
        return publicLog;
    }

    public Map<String, List<NobCardInstance>> getDraftHands() {
        return draftHands;
    }

    public Map<String, String> getDraftPicks() {
        return draftPicks;
    }

    public Map<String, List<String>> getPhaseSubmissions() {
        return phaseSubmissions;
    }

    public Set<String> getProcessedCommandIds() {
        return processedCommandIds;
    }

    public Instant getPhaseDeadline() {
        return phaseDeadline;
    }

    public void setPhaseDeadline(Instant phaseDeadline) {
        this.phaseDeadline = phaseDeadline;
    }

    public void hurrySharedDeadline() {
        Instant cap = Instant.now().plusSeconds(HURRY_UP_SECONDS);
        if (phaseDeadline == null || phaseDeadline.isAfter(cap)) {
            setPhaseDeadline(cap);
        }
    }

    public NobKillAttempt getActiveKill() {
        return activeKill;
    }

    public void setActiveKill(NobKillAttempt activeKill) {
        this.activeKill = activeKill;
    }

    public void log(String type, String text) {
        log(type, text, null, null, null, null);
    }

    public void log(String type, String text, String actorPlayerId, String targetPlayerId) {
        log(type, text, actorPlayerId, targetPlayerId, null, null);
    }

    public void log(
            String type,
            String text,
            String actorPlayerId,
            String targetPlayerId,
            String extraTargetPlayerId,
            String cardCode
    ) {
        publicLog.add(new NobPublicLogEntry(type, text, actorPlayerId, targetPlayerId, extraTargetPlayerId, cardCode));
    }

    public static List<NobBloodline> bloodlinePool(int playerCount) {
        if (playerCount < MIN_PLAYERS || playerCount > MAX_PLAYERS) {
            throw new IllegalArgumentException("NOB requires 4-11 players");
        }
        int ranks = playerCount / 2;
        List<NobBloodline> pool = new ArrayList<>();
        for (int rank = 1; rank <= ranks; rank++) {
            pool.add(NobBloodline.vampire(rank));
            pool.add(NobBloodline.werewolf(rank));
        }
        if (playerCount % 2 == 1) {
            pool.add(NobBloodline.halfblood());
        }
        return pool;
    }

    public void seedMoonMarks(int twos, int threes, int fours) {
        moonMarkPool.clear();
        for (int i = 0; i < twos; i++) {
            moonMarkPool.add(NobMoonMark.of(2));
        }
        for (int i = 0; i < threes; i++) {
            moonMarkPool.add(NobMoonMark.of(3));
        }
        for (int i = 0; i < fours; i++) {
            moonMarkPool.add(NobMoonMark.of(4));
        }
    }

    public int moonMarkPoolCount(int value) {
        return (int) moonMarkPool.stream().filter(mark -> mark.value() == value).count();
    }

    public NobMoonMark drawMoonMark(RandomSource random) {
        if (moonMarkPool.isEmpty()) {
            return null;
        }
        int index = random.nextInt(moonMarkPool.size());
        return moonMarkPool.remove(index);
    }

    public void assignBloodlines(RandomSource random) {
        List<NobBloodline> pool = bloodlinePool(players.size());
        random.shuffle(pool);
        for (int i = 0; i < players.size(); i++) {
            NobPlayerState player = players.get(i);
            player.setCurrentBloodline(pool.get(i));
            player.setKnowledgeState(NobBloodlineKnowledge.KNOWN);
        }
    }

    public void dealDraft(RandomSource random) {
        List<NobCardInstance> deck = new ArrayList<>();
        for (NobCardDef def : NobCardCatalog.all()) {
            deck.add(NobCardInstance.from(def));
        }
        random.shuffle(deck);
        draftHands.clear();
        draftPicks.clear();
        for (NobPlayerState player : players) {
            List<NobCardInstance> hand = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                hand.add(deck.removeFirst());
            }
            draftHands.put(player.getPlayerId(), hand);
        }
        undealt.clear();
        undealt.addAll(deck);
        discardPile.clear();
        clearEchoShowcase();
        phase = NobPhase.DRAFT_PICK_1;
        phaseState = NobPhaseState.WAITING_FOR_PHASE_SUBMISSIONS;
        currentResolvingCard = null;
        currentActorPlayerId = null;
        Instant now = Instant.now();
        windowStartedAt = now;
        setPhaseDeadline(now.plusSeconds(getDraftSeconds()));
    }

    public void beginNightPhase(NobPhase nightPhase) {
        phase = nightPhase;
        phaseState = NobPhaseState.WAITING_FOR_PHASE_SUBMISSIONS;
        phaseSubmissions.clear();
        resolutionQueue.clear();
        pendingDecision = null;
        activeKill = null;
        clearEchoShowcase();
        currentResolvingCard = null;
        currentActorPlayerId = null;
        Instant now = Instant.now();
        windowStartedAt = now;
        setPhaseDeadline(now.plusSeconds(getPhaseSubmitSeconds()));
    }

    public List<NobPlayerState> playersWhoMustSubmit() {
        return alivePlayers().stream()
                .filter(player -> player.getHand().stream().anyMatch(card ->
                        card.matchesPhase(phase) && !player.getPassedInstanceIds().contains(card.instanceId())))
                .toList();
    }

    public void closePhaseSubmissions() {
        List<NobResolutionItem> items = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : phaseSubmissions.entrySet()) {
            List<String> submitted = entry.getValue() == null ? List.of() : entry.getValue();
            if (submitted.stream().anyMatch(value -> "PASS".equalsIgnoreCase(value))) {
                NobPlayerState player = requirePlayer(entry.getKey());
                player.getHand().stream()
                        .filter(card -> card.matchesPhase(phase))
                        .forEach(card -> player.getPassedInstanceIds().add(card.instanceId()));
                continue;
            }
            NobPlayerState player = requirePlayer(entry.getKey());
            for (String cardId : submitted) {
                NobCardInstance card = player.findHand(cardId);
                if (card != null && card.matchesPhase(phase)) {
                    items.add(new NobResolutionItem(player.getPlayerId(), card));
                }
            }
        }
        items.sort(Comparator
                .comparing((NobResolutionItem item) -> item.card().number() == null ? 99 : item.card().number())
                .thenComparing(item -> requirePlayer(item.ownerPlayerId()).getSeat()));
        resolutionQueue.clear();
        resolutionQueue.addAll(items);
        phaseSubmissions.clear();
    }

    public NobResolutionItem pollNextLiveCard() {
        while (!resolutionQueue.isEmpty()) {
            NobResolutionItem item = resolutionQueue.poll();
            NobPlayerState owner = player(item.ownerPlayerId());
            if (owner != null && owner.isAlive() && owner.findHand(item.card().instanceId()) != null) {
                return item;
            }
            log("NOB_CARD_CANCELLED_OWNER_DEAD", "A queued card was cancelled");
        }
        return null;
    }

    public void revealCard(NobPlayerState owner, NobCardInstance card) {
        owner.getHand().removeIf(item -> item.instanceId().equals(card.instanceId()));
        owner.getRevealedCards().add(card);
        owner.getUsedCards().add(card);
        log(
                "NOB_ROLE_REVEALED",
                owner.getDisplayName() + " revealed " + card.cardCode(),
                owner.getPlayerId(),
                null,
                null,
                card.cardCode()
        );
    }

    public void awardMoonMark(NobPlayerState player, RandomSource random) {
        NobMoonMark mark = drawMoonMark(random);
        if (mark == null) {
            log("NOB_MOON_MARK_POOL_EMPTY", "Moon Mark pool is empty");
            return;
        }
        player.getMoonMarks().add(mark);
        log("NOB_MOON_MARK_COUNT_CHANGED", player.getDisplayName() + " now has " + player.moonMarkCount() + " Moon Marks");
    }

    public void startNextRound(RandomSource random) {
        roundNumber += 1;
        for (NobPlayerState player : players) {
            player.setAlive(true);
            player.getHand().clear();
            player.getRevealedCards().clear();
            player.getUsedCards().clear();
            player.getPassedInstanceIds().clear();
            player.getObservations().clear();
            player.setInspectReveal(null);
            player.setKnowledgeState(NobBloodlineKnowledge.KNOWN);
        }
        publicLog.clear();
        discardPile.clear();
        undealt.clear();
        resolutionQueue.clear();
        pendingDecision = null;
        activeKill = null;
        currentResolvingCard = null;
        currentActorPlayerId = null;
        announcement = null;
        lastRoundResult = null;
        resolutionDisplayExpiresAt = null;
        presentationQueue.clear();
        roundRewardPlayerIds.clear();
        for (List<NobMoonTokenOption> leftover : moonTokenOffers.values()) {
            for (NobMoonTokenOption option : leftover) {
                moonMarkPool.add(option.mark());
            }
        }
        moonTokenOffers.clear();
        moonTokenClaimed.clear();
        moonPickNeed.clear();
        moonPicksTaken.clear();
        assignBloodlines(random);
        dealDraft(random);
    }

    public static Instant nowPlusSeconds(int seconds) {
        return Instant.now().plusSeconds(seconds);
    }

    public NobPendingDecision newDecision(
            String actorId,
            NobDecisionType type,
            List<String> options,
            List<String> targets,
            String cardInstanceId,
            int timeoutSeconds,
            Map<String, Object> context
    ) {
        Instant now = Instant.now();
        this.windowStartedAt = now;
        this.currentActorPlayerId = actorId;
        return new NobPendingDecision(
                UUID.randomUUID().toString(),
                actorId,
                type,
                List.copyOf(options),
                List.copyOf(targets),
                cardInstanceId,
                now,
                now.plusSeconds(timeoutSeconds),
                context == null ? Map.of() : Map.copyOf(context)
        );
    }

    public record NobKillAttempt(
            String attackerId,
            String targetId,
            NobKillSource source,
            String sourceCardInstanceId
    ) {
    }
}
