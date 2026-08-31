package com.partygameonline.game.wheresthebone;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.game.core.GameConfig;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.RandomSource;
import com.partygameonline.game.core.SeededRandomSource;
import com.partygameonline.game.wheresthebone.api.dto.WheresTheBoneView;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneAction;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneActionType;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneEvent;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneGameState;
import com.partygameonline.game.wheresthebone.domain.WheresTheBonePackSelectionMode;
import com.partygameonline.game.wheresthebone.domain.WheresTheBonePeek;
import com.partygameonline.game.wheresthebone.domain.WheresTheBonePhase;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneRole;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WheresTheBoneGameEngineTests {

    private final WheresTheBoneGameEngine engine = new WheresTheBoneGameEngine();
    private final WheresTheBoneGameProjector projector = new WheresTheBoneGameProjector();

    @Test
    void fourPlayerSetupMakesOnlyYardSidePlayersChooseAndKeepsBothThiefDice() {
        WheresTheBoneGameState state = createGame(4, Map.of());
        String thief = rolePlayer(state, WheresTheBoneRole.BONE_THIEF);

        assertThat(state.getPhase()).isEqualTo(WheresTheBonePhase.ROLE_REVEAL);
        assertThat(Duration.between(state.getPhaseStartedAt(), state.getDeadline()).toSeconds())
                .isEqualTo(WheresTheBoneGameState.ROLE_REVEAL_SECONDS);
        assertThat(state.diceFor(thief)).hasSize(2);
        assertThat(state.wakeFor(thief)).containsExactlyElementsOf(
                state.diceFor(thief).stream().distinct().sorted().toList()
        );
        assertThat(state.getRoles().entrySet())
                .filteredOn(entry -> entry.getValue() != WheresTheBoneRole.BONE_THIEF)
                .allSatisfy(entry -> assertThat(state.wakeFor(entry.getKey())).isEmpty());

        state.setDeadline(Instant.now().minusMillis(1));
        engine.apply(state, player("p1"), action(WheresTheBoneActionType.TIMEOUT, null), new SeededRandomSource(8L));

        assertThat(state.getPhase()).isEqualTo(WheresTheBonePhase.WAKE_SELECTION);
        assertThat(Duration.between(state.getPhaseStartedAt(), state.getDeadline()).toSeconds())
                .isEqualTo(state.getSettings().nightSeconds());
    }

    @Test
    void fourPlayerWakeSelectionTimeoutChoosesOneOfEachPendingPlayersDice() {
        WheresTheBoneGameState state = createGame(4, Map.of(
                "wheresTheBone", Map.of("nightSeconds", 5)
        ));
        state.setDeadline(Instant.now().minusMillis(1));
        engine.apply(state, player("p1"), action(WheresTheBoneActionType.TIMEOUT, null), new SeededRandomSource(8L));
        assertThat(state.getPhase()).isEqualTo(WheresTheBonePhase.WAKE_SELECTION);

        RandomSource chooseLastDie = new RandomSource() {
            @Override
            public int nextInt(int bound) {
                return bound - 1;
            }

            @Override
            public long nextLong() {
                return 0;
            }
        };
        state.setDeadline(Instant.now().minusMillis(1));
        engine.apply(state, player("p1"), action(WheresTheBoneActionType.TIMEOUT, null), chooseLastDie);

        assertThat(state.getPhase()).isEqualTo(WheresTheBonePhase.NIGHT_HOUR);
        assertThat(state.getCurrentHour()).isEqualTo(1);
        assertThat(state.getRoles().entrySet())
                .filteredOn(entry -> entry.getValue() != WheresTheBoneRole.BONE_THIEF)
                .allSatisfy(entry -> assertThat(state.wakeFor(entry.getKey()))
                        .containsExactly(state.diceFor(entry.getKey()).getLast()));
    }

    @Test
    void roleRevealLastsFifteenSecondsThenFirstNightHourUsesConfiguredDuration() {
        WheresTheBoneGameState state = createGame(5, Map.of(
                "wheresTheBone", Map.of("nightSeconds", 5)
        ));

        assertThat(state.getPhase()).isEqualTo(WheresTheBonePhase.ROLE_REVEAL);
        assertThat(state.getCurrentHour()).isZero();
        assertThat(Duration.between(state.getPhaseStartedAt(), state.getDeadline()).toSeconds())
                .isEqualTo(WheresTheBoneGameState.ROLE_REVEAL_SECONDS);
        assertThat(projector.project(state, player("p1")).legalActions()).isEmpty();

        state.setDeadline(Instant.now().minusMillis(1));
        engine.apply(state, player("p1"), action(WheresTheBoneActionType.TIMEOUT, null), new SeededRandomSource(9L));

        assertThat(state.getPhase()).isEqualTo(WheresTheBonePhase.NIGHT_HOUR);
        assertThat(state.getCurrentHour()).isEqualTo(1);
        assertThat(Duration.between(state.getPhaseStartedAt(), state.getDeadline()).toMillis())
                .isBetween(5_000L, 5_100L);
    }

    @Test
    void playerCannotForceATimeoutBeforeTheAuthoritativeDeadline() {
        WheresTheBoneGameState state = state(5);
        state.setPhase(WheresTheBonePhase.NIGHT_HOUR);
        state.setDeadline(Instant.now().plusSeconds(30));
        WheresTheBoneAction timeout = action(WheresTheBoneActionType.TIMEOUT, null);

        assertThat(engine.validate(state, player("p1"), timeout).valid()).isFalse();

        state.setDeadline(Instant.now().minusMillis(1));
        assertThat(engine.validate(state, player("p1"), timeout).valid()).isTrue();
    }

    @Test
    void ballotsStaySecretUntilVotingHasResolved() {
        WheresTheBoneGameState state = votingState();
        state.getVotes().put("p1", "p2");
        WheresTheBoneView duringVote = projector.project(state, player("p3"));

        assertThat(duringVote.votes()).isEmpty();
        assertThat(duringVote.voteCounts()).isEmpty();
        assertThat(duringVote.players()).filteredOn(player -> player.playerId().equals("p1"))
                .allMatch(player -> player.voted());

        state.getVotes().put("p2", "p1");
        state.getVotes().put("p3", "p1");
        engine.apply(state, player("p4"), action(WheresTheBoneActionType.VOTE, "p3"), new SeededRandomSource(1));
        WheresTheBoneView result = projector.project(state, player("p3"));

        assertThat(result.finished()).isTrue();
        assertThat(result.votes()).hasSize(4);
        assertThat(result.voteCounts()).containsEntry("p1", 2);
    }

    @Test
    void whiteDogWinsTieWithTheBoneThiefByPriority() {
        WheresTheBoneGameState state = votingState();
        state.getVotes().put("p1", "p2");
        state.getVotes().put("p2", "p1");
        state.getVotes().put("p3", "p2");

        engine.apply(state, player("p4"), action(WheresTheBoneActionType.VOTE, "p1"), new SeededRandomSource(2));

        assertThat(state.getWinnerFaction()).isEqualTo(WheresTheBoneRole.WHITE_DOG);
        assertThat(state.getWinnerPlayerIds()).containsExactly("p2");
        assertThat(state.getRevealedPlayerIds()).containsExactlyInAnyOrder("p1", "p2");
    }

    @Test
    void aPastWitnessCannotSeeWhoWakesInLaterHours() {
        WheresTheBoneGameState state = state(5);
        assignRoles(state, false);
        state.getWakeHours().put("p1", List.of(2));
        state.getWakeHours().put("p2", List.of(3));
        state.getWakeHours().put("p3", List.of(3));
        state.getWakeHours().put("p4", List.of(4));
        state.getWakeHours().put("p5", List.of(2));
        state.setPhase(WheresTheBonePhase.NIGHT_HOUR);
        state.setCurrentHour(3);
        state.setBoneTaken(true);
        state.setBoneTakenBy("p1");
        state.setBoneTakenHour(2);
        state.witnessedFor("p5").add(2);

        WheresTheBoneView pastWitness = projector.project(state, player("p5"));
        WheresTheBoneView currentlyAwake = projector.project(state, player("p2"));

        assertThat(pastWitness.currentAwakePlayerIds()).isEmpty();
        assertThat(pastWitness.players()).noneMatch(player -> player.awake());
        assertThat(currentlyAwake.currentAwakePlayerIds()).containsExactly("p3");
    }

    @Test
    void witnessRecruitmentPhaseAndCandidatesStayHiddenFromSleepingPlayers() {
        WheresTheBoneGameState state = state(5);
        assignRoles(state, false);
        state.setPhase(WheresTheBonePhase.PACK_SELECTION);
        state.setPackSelectionMode(WheresTheBonePackSelectionMode.WITNESS);
        state.setCurrentHour(2);
        state.setDeadline(Instant.now().plusSeconds(5));
        state.setSuspendedNightDeadline(state.getDeadline());
        state.getPendingPackCandidates().addAll(List.of("p2", "p3"));
        state.setPendingPackCount(1);

        WheresTheBoneView sleeper = projector.project(state, player("p4"));
        WheresTheBoneView thief = projector.project(state, player("p1"));

        assertThat(sleeper.phase()).isEqualTo(WheresTheBonePhase.NIGHT_HOUR.name());
        assertThat(sleeper.packmateCandidateIds()).isEmpty();
        assertThat(sleeper.requiredPackmateCount()).isZero();
        assertThat(thief.phase()).isEqualTo(WheresTheBonePhase.PACK_SELECTION.name());
        assertThat(thief.packmateCandidateIds()).containsExactly("p2", "p3");
        assertThat(thief.requiredPackmateCount()).isEqualTo(1);
    }

    @Test
    void soleWitnessKeepsTheTheftClueAfterBecomingACursedPackmate() {
        WheresTheBoneGameState state = state(5);
        assignRoles(state, false);
        state.getWakeHours().put("p1", List.of(2));
        state.getWakeHours().put("p2", List.of(2));
        state.getWakeHours().put("p3", List.of(3));
        state.getWakeHours().put("p4", List.of(4));
        state.getWakeHours().put("p5", List.of(5));
        state.setPhase(WheresTheBonePhase.NIGHT_HOUR);
        state.setCurrentHour(2);
        state.setDeadline(Instant.now().plusSeconds(10));

        engine.apply(state, player("p1"), action(WheresTheBoneActionType.TAKE_BONE, null), new SeededRandomSource(15));
        WheresTheBoneView cursedWitness = projector.project(state, player("p2"));

        assertThat(cursedWitness.myRole()).isEqualTo(WheresTheBoneRole.PACKMATE.name());
        assertThat(cursedWitness.myWitnessedBoneTakenHours()).containsExactly(2);
        assertThat(cursedWitness.knownBoneThiefId()).isEqualTo("p1");
        assertThat(cursedWitness.boneTakenBy()).isEqualTo("p1");
    }

    @Test
    void wakingAloneIsRetainedInThePrivateNightJournal() {
        WheresTheBoneGameState state = state(5);
        assignRoles(state, false);
        state.getWakeHours().put("p1", List.of(3));
        state.getWakeHours().put("p2", List.of(2));
        state.getWakeHours().put("p3", List.of(4));
        state.getWakeHours().put("p4", List.of(5));
        state.getWakeHours().put("p5", List.of(6));
        state.setPhase(WheresTheBonePhase.NIGHT_HOUR);
        state.setCurrentHour(1);
        state.setDeadline(Instant.now().minusMillis(1));

        engine.apply(state, player("p1"), action(WheresTheBoneActionType.TIMEOUT, null), new SeededRandomSource(16));
        WheresTheBoneView loneDog = projector.project(state, player("p2"));

        assertThat(loneDog.myCoAwakeRecords()).hasSize(1);
        assertThat(loneDog.myCoAwakeRecords().getFirst().hour()).isEqualTo(2);
        assertThat(loneDog.myCoAwakeRecords().getFirst().playerIds()).isEmpty();
    }

    @Test
    void postNightPackmateCountsMatchSixSevenAndEightPlayerRules() {
        assertThat(requiredPackmatesAfterNight(6)).isEqualTo(1);
        assertThat(requiredPackmatesAfterNight(7)).isEqualTo(2);
        assertThat(requiredPackmatesAfterNight(8)).isEqualTo(2);
    }

    @Test
    void sevenPlayerPackmatesKnowEachOtherButNotTheThief() {
        WheresTheBoneGameState state = state(7);
        assignRoles(state, false);
        state.getRoles().put("p2", WheresTheBoneRole.PACKMATE);
        state.getRoles().put("p3", WheresTheBoneRole.PACKMATE);
        state.getPackmates().addAll(List.of("p2", "p3"));
        state.setPhase(WheresTheBonePhase.DISCUSSION);

        WheresTheBoneView packmate = projector.project(state, player("p2"));

        assertThat(packmate.knownPackmateIds()).containsExactly("p3");
        assertThat(packmate.knownBoneThiefId()).isNull();
    }

    @Test
    void disabledHistoryHidesPrivateNightDetailsAtDiscussionButKeepsPackKnowledge() {
        WheresTheBoneGameState state = state(8);
        assignRoles(state, false);
        state.setSettings(new WheresTheBoneSettings(5, 5, false, false));
        state.getRoles().put("p2", WheresTheBoneRole.PACKMATE);
        state.getRoles().put("p3", WheresTheBoneRole.PACKMATE);
        state.getPackmates().addAll(List.of("p2", "p3"));
        state.getDiceRolls().put("p2", List.of(2));
        state.getWakeHours().put("p2", List.of(2));
        state.peekFor("p2").add(new WheresTheBonePeek("p4", List.of(4)));
        state.coAwakeFor("p2").put(2, java.util.Set.of("p3"));
        state.witnessedFor("p2").add(2);
        state.observedPresentFor("p2").add(1);
        state.observedMissingFor("p2").add(3);
        state.setBoneTaken(true);
        state.setBoneTakenBy("p1");
        state.setBoneTakenHour(2);
        state.setPhase(WheresTheBonePhase.NIGHT_HOUR);
        state.setCurrentHour(2);

        WheresTheBoneView duringNight = projector.project(state, player("p2"));
        assertThat(duringNight.myDice()).containsExactly(2);
        assertThat(duringNight.myWakeHours()).containsExactly(2);
        assertThat(duringNight.myPeekResults()).containsKey("p4");
        assertThat(duringNight.myWitnessedBoneTakenHours()).containsExactly(2);
        assertThat(duringNight.boneTakenBy()).isEqualTo("p1");

        state.setPhase(WheresTheBonePhase.DISCUSSION);
        WheresTheBoneView discussion = projector.project(state, player("p2"));

        assertThat(discussion.myDice()).isEmpty();
        assertThat(discussion.myWakeHours()).isEmpty();
        assertThat(discussion.mySelectedWakeHours()).isEmpty();
        assertThat(discussion.myPeekResults()).isEmpty();
        assertThat(discussion.myPeekCount()).isZero();
        assertThat(discussion.myClues()).isEmpty();
        assertThat(discussion.myCoAwakeRecords()).isEmpty();
        assertThat(discussion.myWitnessedBoneTakenHours()).isEmpty();
        assertThat(discussion.myObservedBonePresentHours()).isEmpty();
        assertThat(discussion.myObservedBoneMissingHours()).isEmpty();
        assertThat(discussion.boneTakenBy()).isNull();
        assertThat(discussion.boneTakenHour()).isNull();
        assertThat(discussion.players()).filteredOn(player -> player.playerId().equals("p2"))
                .allMatch(player -> player.wakeHours().isEmpty());
        assertThat(discussion.knownPackmateIds()).containsExactly("p3");
        assertThat(discussion.knownBoneThiefId()).isEqualTo("p1");
    }

    @Test
    void privateNightActionsNeverAppearInPublicHistory() {
        WheresTheBoneGameState state = state(5);
        assignRoles(state, false);
        state.setPhase(WheresTheBonePhase.DISCUSSION);
        state.addEvent(WheresTheBoneEvent.of("PEEK_RECORDED", Map.of("playerId", "p2")));
        state.addEvent(WheresTheBoneEvent.of("PACKMATES_SELECTED", Map.of("count", 1)));
        state.addEvent(WheresTheBoneEvent.of("BONE_TAKEN", Map.of("hour", 3)));

        WheresTheBoneView view = projector.project(state, player("p4"));

        assertThat(view.events()).extracting(event -> event.type()).containsExactly("BONE_TAKEN");
        assertThat(view.events().getFirst().payload()).isEmpty();
    }

    @Test
    void abandoningWakeSelectionAutoSelectsAndRemovesThePlayerFromNightActions() {
        WheresTheBoneGameState state = createGame(4, Map.of());
        state.setDeadline(Instant.now().minusMillis(1));
        engine.apply(state, player("p1"), action(WheresTheBoneActionType.TIMEOUT, null), new SeededRandomSource(8));
        String abandoned = state.getRoles().entrySet().stream()
                .filter(entry -> entry.getValue() != WheresTheBoneRole.BONE_THIEF)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();

        engine.onPlayerAbandoned(state, player(abandoned), new SeededRandomSource(9));

        assertThat(state.isActive(abandoned)).isFalse();
        assertThat(state.wakeFor(abandoned)).hasSize(1);
        assertThat(state.diceFor(abandoned)).contains(state.wakeFor(abandoned).getFirst());
        assertThat(projector.project(state, player(abandoned)).legalActions()).isEmpty();
        assertThat(projector.project(state, player("p1")).players())
                .filteredOn(view -> view.playerId().equals(abandoned))
                .allMatch(view -> !view.connected());
    }

    @Test
    void abandoningTheLastNonVoterResolvesUsingSubmittedActiveBallots() {
        WheresTheBoneGameState state = votingState();
        state.getVotes().put("p1", "p2");
        state.getVotes().put("p2", "p1");
        state.getVotes().put("p3", "p1");

        var result = engine.onPlayerAbandoned(state, player("p4"), new SeededRandomSource(10));

        assertThat(result.finished()).isTrue();
        assertThat(state.getPhase()).isEqualTo(WheresTheBonePhase.RESULT);
        assertThat(state.getWinnerFaction()).isEqualTo(WheresTheBoneRole.YARD_DOG);
    }

    @Test
    void everyPlayerCanRequestDiscussionSkipAndRequesterAutomaticallyAgrees() {
        WheresTheBoneGameState state = discussionState(4);

        assertThat(projector.project(state, player("p1")).legalActions())
                .containsExactly(WheresTheBoneActionType.REQUEST_SKIP_DISCUSSION.name());
        assertThat(projector.project(state, player("p3")).legalActions())
                .containsExactly(WheresTheBoneActionType.REQUEST_SKIP_DISCUSSION.name());

        WheresTheBoneAction request = skipRequest("skip-request-p2");
        assertThat(engine.validate(state, player("p2"), request).valid()).isTrue();
        engine.apply(state, player("p2"), request, new SeededRandomSource(11));

        WheresTheBoneView requesterView = projector.project(state, player("p2"));
        WheresTheBoneView responderView = projector.project(state, player("p1"));
        assertThat(requesterView.discussionSkipRequesterId()).isEqualTo("p2");
        assertThat(requesterView.discussionSkipAgreeCount()).isEqualTo(1);
        assertThat(requesterView.discussionSkipResponseCount()).isEqualTo(1);
        assertThat(requesterView.discussionSkipRequiredAgreeCount()).isEqualTo(3);
        assertThat(requesterView.myDiscussionSkipResponse()).isTrue();
        assertThat(requesterView.legalActions()).isEmpty();
        assertThat(responderView.myDiscussionSkipResponse()).isNull();
        assertThat(responderView.legalActions())
                .containsExactly(WheresTheBoneActionType.RESPOND_SKIP_DISCUSSION.name());
    }

    @Test
    void discussionSkipNeedsStrictMajorityAndStartsVotingImmediately() {
        WheresTheBoneGameState state = discussionState(4);
        engine.apply(state, player("p1"), skipRequest("majority-request-p1"), new SeededRandomSource(12));
        engine.apply(state, player("p2"), skipResponse("skip-p2", false), new SeededRandomSource(12));
        engine.apply(state, player("p3"), skipResponse("skip-p3", true), new SeededRandomSource(12));

        assertThat(state.getPhase()).isEqualTo(WheresTheBonePhase.DISCUSSION);
        assertThat(state.getDiscussionSkipRequesterId()).isEqualTo("p1");

        engine.apply(state, player("p4"), skipResponse("skip-p4", true), new SeededRandomSource(12));

        assertThat(state.getPhase()).isEqualTo(WheresTheBonePhase.VOTING);
        assertThat(state.getDiscussionSkipRequesterId()).isNull();
        assertThat(state.getDiscussionSkipResponses()).isEmpty();
        assertThat(state.getEvents()).extracting(WheresTheBoneEvent::type)
                .contains("DISCUSSION_SKIP_APPROVED", "VOTING_STARTED");
    }

    @Test
    void discussionTimeoutCancelsPendingSkipAndStartsOfficialVoting() {
        WheresTheBoneGameState state = discussionState(8);
        engine.apply(state, player("p1"), skipRequest("timeout-request-p1"), new SeededRandomSource(14));
        state.setDeadline(Instant.now().minusMillis(1));

        WheresTheBoneAction timeout = action(WheresTheBoneActionType.TIMEOUT, null);
        assertThat(engine.validate(state, player("p1"), timeout).valid()).isTrue();
        engine.apply(state, player("p1"), timeout, new SeededRandomSource(14));

        assertThat(state.getPhase()).isEqualTo(WheresTheBonePhase.VOTING);
        assertThat(state.getDiscussionSkipRequesterId()).isNull();
        assertThat(state.getDiscussionSkipResponses()).isEmpty();
        assertThat(projector.project(state, player("p1")).legalActions())
                .containsExactly(WheresTheBoneActionType.VOTE.name());
        assertThat(state.getEvents()).extracting(WheresTheBoneEvent::type)
                .containsSubsequence("DISCUSSION_SKIP_CANCELLED", "VOTING_STARTED");
    }

    @Test
    void rejectedDiscussionSkipLetsAnotherPlayerRequestButNotTheSamePlayer() {
        WheresTheBoneGameState state = discussionState(4);
        engine.apply(state, player("p1"), skipRequest("rejected-request-p1"), new SeededRandomSource(13));
        engine.apply(state, player("p2"), skipResponse("reject-p2", false), new SeededRandomSource(13));
        engine.apply(state, player("p3"), skipResponse("reject-p3", false), new SeededRandomSource(13));

        assertThat(state.getPhase()).isEqualTo(WheresTheBonePhase.DISCUSSION);
        assertThat(state.getDiscussionSkipRequesterId()).isNull();
        assertThat(engine.validate(state, player("p1"), skipRequest("retry-request-p1")).valid())
                .isFalse();
        assertThat(engine.validate(state, player("p4"), skipRequest("skip-request-p4")).valid())
                .isTrue();
        assertThat(projector.project(state, player("p1")).legalActions()).isEmpty();
        assertThat(projector.project(state, player("p4")).legalActions())
                .containsExactly(WheresTheBoneActionType.REQUEST_SKIP_DISCUSSION.name());
    }

    private WheresTheBoneGameState createGame(int count, Map<String, Object> settings) {
        List<String> ids = ids(count);
        Map<String, String> names = names(ids);
        return engine.createGame(
                new GameConfig(WheresTheBoneGameManifest.ID, "ROOM", ids, names, 7L, settings),
                new SeededRandomSource(7L)
        );
    }

    private static WheresTheBoneGameState state(int count) {
        List<String> ids = ids(count);
        return new WheresTheBoneGameState("ROOM", "p1", ids, names(ids), WheresTheBoneSettings.defaults());
    }

    private static WheresTheBoneGameState votingState() {
        WheresTheBoneGameState state = state(4);
        assignRoles(state, true);
        state.setPhase(WheresTheBonePhase.VOTING);
        state.setDeadline(Instant.now().plusSeconds(60));
        return state;
    }

    private static WheresTheBoneGameState discussionState(int count) {
        WheresTheBoneGameState state = state(count);
        assignRoles(state, false);
        state.setPhase(WheresTheBonePhase.DISCUSSION);
        state.setDeadline(Instant.now().plusSeconds(180));
        return state;
    }

    private static void assignRoles(WheresTheBoneGameState state, boolean whiteDog) {
        state.getRoles().put("p1", WheresTheBoneRole.BONE_THIEF);
        for (String id : state.getPlayerIds()) {
            if (!id.equals("p1")) state.getRoles().put(id, WheresTheBoneRole.YARD_DOG);
        }
        if (whiteDog) state.getRoles().put("p2", WheresTheBoneRole.WHITE_DOG);
    }

    private static WheresTheBoneAction action(WheresTheBoneActionType type, String target) {
        return new WheresTheBoneAction(type, "cmd-" + type + "-" + target, null, null, target, List.of(), null);
    }

    private static WheresTheBoneAction skipResponse(String commandId, boolean agree) {
        return new WheresTheBoneAction(
                WheresTheBoneActionType.RESPOND_SKIP_DISCUSSION,
                commandId,
                null,
                null,
                null,
                List.of(),
                agree
        );
    }

    private static WheresTheBoneAction skipRequest(String commandId) {
        return new WheresTheBoneAction(
                WheresTheBoneActionType.REQUEST_SKIP_DISCUSSION,
                commandId,
                null,
                null,
                null,
                List.of(),
                null
        );
    }

    private int requiredPackmatesAfterNight(int count) {
        WheresTheBoneGameState state = state(count);
        assignRoles(state, false);
        state.setPhase(WheresTheBonePhase.NIGHT_HOUR);
        state.setCurrentHour(WheresTheBoneGameState.MAX_HOUR);
        state.setBoneTaken(true);
        state.setBoneTakenBy("p1");
        state.setBoneTakenHour(3);
        state.setDeadline(Instant.now().minusMillis(1));

        engine.apply(state, player("p1"), action(WheresTheBoneActionType.TIMEOUT, null), new SeededRandomSource(count));

        assertThat(state.getPhase()).isEqualTo(WheresTheBonePhase.PACK_SELECTION);
        return state.getPendingPackCount();
    }

    private static String rolePlayer(WheresTheBoneGameState state, WheresTheBoneRole role) {
        return state.getRoles().entrySet().stream()
                .filter(entry -> entry.getValue() == role)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
    }

    private static PlayerContext player(String id) {
        return PlayerContext.player(id, id.toUpperCase());
    }

    private static List<String> ids(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count).mapToObj(index -> "p" + index).toList();
    }

    private static Map<String, String> names(List<String> ids) {
        Map<String, String> names = new LinkedHashMap<>();
        ids.forEach(id -> names.put(id, id.toUpperCase()));
        return names;
    }
}
