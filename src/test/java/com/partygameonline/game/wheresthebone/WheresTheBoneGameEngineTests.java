package com.partygameonline.game.wheresthebone;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.game.core.GameConfig;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.SeededRandomSource;
import com.partygameonline.game.wheresthebone.api.dto.WheresTheBoneView;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneAction;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneActionType;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneEvent;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneGameState;
import com.partygameonline.game.wheresthebone.domain.WheresTheBonePackSelectionMode;
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

        assertThat(state.getPhase()).isEqualTo(WheresTheBonePhase.WAKE_SELECTION);
        assertThat(state.getDeadline()).isNull();
        assertThat(state.diceFor(thief)).hasSize(2);
        assertThat(state.wakeFor(thief)).containsExactlyElementsOf(
                state.diceFor(thief).stream().distinct().sorted().toList()
        );
        assertThat(state.getRoles().entrySet())
                .filteredOn(entry -> entry.getValue() != WheresTheBoneRole.BONE_THIEF)
                .allSatisfy(entry -> assertThat(state.wakeFor(entry.getKey())).isEmpty());
    }

    @Test
    void firstNightHourIncludesTheStandaloneGraceWindow() {
        WheresTheBoneGameState state = createGame(5, Map.of(
                "wheresTheBone", Map.of("nightSeconds", 10)
        ));

        assertThat(state.getPhase()).isEqualTo(WheresTheBonePhase.NIGHT_HOUR);
        assertThat(state.getCurrentHour()).isEqualTo(1);
        assertThat(Duration.between(state.getPhaseStartedAt(), state.getDeadline()).toMillis())
                .isBetween(19_000L, 20_000L);
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

    private static void assignRoles(WheresTheBoneGameState state, boolean whiteDog) {
        state.getRoles().put("p1", WheresTheBoneRole.BONE_THIEF);
        for (String id : state.getPlayerIds()) {
            if (!id.equals("p1")) state.getRoles().put(id, WheresTheBoneRole.YARD_DOG);
        }
        if (whiteDog) state.getRoles().put("p2", WheresTheBoneRole.WHITE_DOG);
    }

    private static WheresTheBoneAction action(WheresTheBoneActionType type, String target) {
        return new WheresTheBoneAction(type, "cmd-" + type + "-" + target, null, null, target, List.of());
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
