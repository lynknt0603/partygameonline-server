package com.partygameonline.game.bloodbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.game.core.GameConfig;
import com.partygameonline.game.core.GameResult;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.SeededRandomSource;
import com.partygameonline.game.core.ValidationResult;
import com.partygameonline.game.bloodbound.api.dto.BloodBoundView;
import com.partygameonline.game.bloodbound.application.BloodBoundRulesEngine;
import com.partygameonline.game.bloodbound.domain.BloodBoundAction;
import com.partygameonline.game.bloodbound.domain.BloodBoundActionType;
import com.partygameonline.game.bloodbound.domain.BloodBoundGameState;
import com.partygameonline.game.bloodbound.domain.BloodBoundPhase;
import com.partygameonline.game.bloodbound.domain.BloodBoundPlayerState;
import com.partygameonline.game.bloodbound.domain.BloodClan;
import com.partygameonline.game.bloodbound.domain.ClueTokenType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BloodBoundGameEngineTests {

    private BloodBoundRulesEngine rulesEngine;
    private BloodBoundGameEngine engine;
    private BloodBoundGameStateProjector projector;

    @BeforeEach
    void setUp() {
        rulesEngine = new BloodBoundRulesEngine();
        engine = new BloodBoundGameEngine(rulesEngine);
        projector = new BloodBoundGameStateProjector();
    }

    private BloodBoundGameState createTestGame(int count) {
        List<String> playerIds = List.of("p1", "p2", "p3", "p4", "p5", "p6").subList(0, count);
        Map<String, String> displayNames = Map.of(
                "p1", "Alice",
                "p2", "Bob",
                "p3", "Charlie",
                "p4", "David",
                "p5", "Eve",
                "p6", "Frank"
        );
        GameConfig config = new GameConfig(BloodBoundGameManifest.ID, "ROOM1", playerIds, displayNames, 42L);
        return engine.createGame(config, new SeededRandomSource(42L));
    }

    @Test
    void createsGameWithValidSeatingAndRoles() {
        BloodBoundGameState state = createTestGame(6);

        assertThat(state.getPlayers()).hasSize(6);
        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.LOOK_LEFT);
        assertThat(state.getDaggerPlayerId()).isEqualTo("p1");

        long roseCount = state.getPlayers().stream().filter(p -> p.getClan() == BloodClan.ROSE).count();
        long fanCount = state.getPlayers().stream().filter(p -> p.getClan() == BloodClan.FAN).count();

        assertThat(roseCount).isEqualTo(3);
        assertThat(fanCount).isEqualTo(3);
    }

    @Test
    void lookLeftAcknowledgeTransitionsToAttack() {
        BloodBoundGameState state = createTestGame(6);
        PlayerContext actor = PlayerContext.player("p1", "Alice");

        BloodBoundAction action = BloodBoundAction.of(BloodBoundActionType.LOOK_LEFT_ACK);
        ValidationResult val = engine.validate(state, actor, action);
        assertThat(val.valid()).isTrue();

        engine.apply(state, actor, action, new SeededRandomSource(1L));
        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.ATTACK_CHOICE);
    }

    @Test
    void validatesAttackRules() {
        BloodBoundGameState state = createTestGame(6);
        state.setPhase(BloodBoundPhase.ATTACK_CHOICE);

        // Player 2 does not hold the dagger
        PlayerContext wrongActor = PlayerContext.player("p2", "Bob");
        BloodBoundAction action = new BloodBoundAction(null, BloodBoundActionType.ATTACK, "p3", null, null, null);
        ValidationResult val1 = engine.validate(state, wrongActor, action);
        assertThat(val1.valid()).isFalse();

        // Player 1 attacks self
        PlayerContext p1Actor = PlayerContext.player("p1", "Alice");
        BloodBoundAction selfAttack = new BloodBoundAction(null, BloodBoundActionType.ATTACK, "p1", null, null, null);
        ValidationResult val2 = engine.validate(state, p1Actor, selfAttack);
        assertThat(val2.valid()).isFalse();

        // Valid attack on Player 2
        BloodBoundAction validAttack = new BloodBoundAction(null, BloodBoundActionType.ATTACK, "p2", null, null, null);
        ValidationResult val3 = engine.validate(state, p1Actor, validAttack);
        assertThat(val3.valid()).isTrue();
    }

    @Test
    void attackOpensInterventionWindow() {
        BloodBoundGameState state = createTestGame(6);
        state.setPhase(BloodBoundPhase.ATTACK_CHOICE);
        PlayerContext p1 = PlayerContext.player("p1", "Alice");

        BloodBoundAction attack = new BloodBoundAction(null, BloodBoundActionType.ATTACK, "p2", null, null, null);
        engine.apply(state, p1, attack, new SeededRandomSource(1L));

        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.INTERVENTION_WINDOW);
        assertThat(state.getTargetPlayerId()).isEqualTo("p2");
    }

    @Test
    void interventionWindowAllowsPass() {
        BloodBoundGameState state = createTestGame(6);
        state.setPhase(BloodBoundPhase.INTERVENTION_WINDOW);
        state.setTargetPlayerId("p2");
        PlayerContext p2 = PlayerContext.player("p2", "Bob");

        BloodBoundAction pass = BloodBoundAction.of(BloodBoundActionType.PASS_INTERVENTION);
        engine.apply(state, p2, pass, new SeededRandomSource(1L));

        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.WOUND_ASSIGNMENT);
        assertThat(state.getIntervenerPlayerId()).isNull();
    }

    @Test
    void interveneForcesRankTokenAndImmediateWound() {
        BloodBoundGameState state = createTestGame(6);
        state.setPhase(BloodBoundPhase.INTERVENTION_WINDOW);
        state.setTargetPlayerId("p2");

        // Player 3 intervenes
        PlayerContext p3 = PlayerContext.player("p3", "Charlie");
        BloodBoundAction intervene = BloodBoundAction.of(BloodBoundActionType.INTERVENE);

        engine.apply(state, p3, intervene, new SeededRandomSource(1L));

        BloodBoundPlayerState p3State = state.player("p3");
        assertThat(p3State.getWounds()).isEqualTo(1);
        assertThat(p3State.isHasRevealedRank()).isTrue();
        assertThat(state.getDaggerPlayerId()).isEqualTo("p3"); // Charlie takes dagger
        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.ATTACK_CHOICE);
    }

    @Test
    void woundRevealAddsTokenAndPassesDagger() {
        BloodBoundGameState state = createTestGame(6);
        state.setPhase(BloodBoundPhase.WOUND_ASSIGNMENT);
        state.setTargetPlayerId("p2");

        PlayerContext p2 = PlayerContext.player("p2", "Bob");
        BloodBoundAction revealColor = new BloodBoundAction(
                null,
                BloodBoundActionType.REVEAL_WOUND_TOKEN,
                null,
                ClueTokenType.COLOR,
                null,
                null
        );

        engine.apply(state, p2, revealColor, new SeededRandomSource(1L));

        BloodBoundPlayerState p2State = state.player("p2");
        assertThat(p2State.getWounds()).isEqualTo(1);
        assertThat(p2State.getRevealedTokens()).hasSize(1);
        assertThat(p2State.getRevealedTokens().getFirst().type()).isEqualTo(ClueTokenType.COLOR);
        assertThat(state.getDaggerPlayerId()).isEqualTo("p2"); // Dagger transferred to Bob
        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.ATTACK_CHOICE);
    }

    @Test
    void guardianShieldAbsorbsDamage() {
        BloodBoundGameState state = createTestGame(6);
        state.setPhase(BloodBoundPhase.WOUND_ASSIGNMENT);
        state.setTargetPlayerId("p2");

        BloodBoundPlayerState p2State = state.player("p2");
        p2State.setShielded(true);

        PlayerContext p2 = PlayerContext.player("p2", "Bob");
        BloodBoundAction reveal = new BloodBoundAction(
                null,
                BloodBoundActionType.REVEAL_WOUND_TOKEN,
                null,
                ClueTokenType.RANK,
                null,
                null
        );

        engine.apply(state, p2, reveal, new SeededRandomSource(1L));

        assertThat(p2State.isShielded()).isFalse();
        assertThat(p2State.getWounds()).isEqualTo(0); // Shield absorbed
        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.ATTACK_CHOICE);
    }

    @Test
    void captureVictoryConditions() {
        // Test 1: Leader capture -> Attacker wins
        BloodBoundGameState state1 = createTestGame(6);
        state1.setPhase(BloodBoundPhase.WOUND_ASSIGNMENT);
        state1.setDaggerPlayerId("p1"); // Attacker: Rose
        state1.player("p1").setClan(BloodClan.ROSE);

        state1.setTargetPlayerId("p2"); // Defender: Fan Leader
        BloodBoundPlayerState p2State = state1.player("p2");
        p2State.setClan(BloodClan.FAN);
        p2State.setRank(1); // Leader
        p2State.setWounds(3); // About to hit 4

        PlayerContext p2 = PlayerContext.player("p2", "Bob");
        BloodBoundAction reveal = new BloodBoundAction(
                null,
                BloodBoundActionType.REVEAL_WOUND_TOKEN,
                null,
                ClueTokenType.RANK,
                null,
                null
        );
        engine.apply(state1, p2, reveal, new SeededRandomSource(1L));

        assertThat(state1.getPhase()).isEqualTo(BloodBoundPhase.GAME_OVER);
        assertThat(state1.getWinnerClan()).isEqualTo(BloodClan.ROSE);
        assertThat(state1.getCapturedPlayerId()).isEqualTo("p2");

        // Test 2: Non-Leader capture -> Defender wins due to wrongful capture
        BloodBoundGameState state2 = createTestGame(6);
        state2.setPhase(BloodBoundPhase.WOUND_ASSIGNMENT);
        state2.setDaggerPlayerId("p1"); // Attacker: Rose
        state2.player("p1").setClan(BloodClan.ROSE);

        state2.setTargetPlayerId("p3"); // Defender: Fan Assassin (Rank 2)
        BloodBoundPlayerState p3State = state2.player("p3");
        p3State.setClan(BloodClan.FAN);
        p3State.setRank(2); // Not Leader!
        p3State.setWounds(3);

        PlayerContext p3 = PlayerContext.player("p3", "Charlie");
        engine.apply(state2, p3, reveal, new SeededRandomSource(1L));

        assertThat(state2.getPhase()).isEqualTo(BloodBoundPhase.GAME_OVER);
        assertThat(state2.getWinnerClan()).isEqualTo(BloodClan.FAN); // Wrongful capture penalty!
    }

    @Test
    void projectorHidesOpponentSecretCards() {
        BloodBoundGameState state = createTestGame(6);
        PlayerContext viewerAlice = PlayerContext.player("p1", "Alice");

        BloodBoundView view = projector.project(state, viewerAlice);

        // Alice sees her own card
        assertThat(view.mySecretCard()).isNotNull();
        assertThat(view.mySecretCard().clan()).isEqualTo(state.player("p1").getClan());

        // In LOOK_LEFT, Alice sees left neighbor clue
        assertThat(view.leftNeighborClue()).isNotNull();
        assertThat(view.leftNeighborClue().clan()).isEqualTo(state.player("p2").getClan());

        // Alice cannot see Bob or Charlie's secret card inside public player list
        for (var publicPlayer : view.players()) {
            // Public players only have public tokens and wounds, no secret rank/clan field
            assertThat(publicPlayer.wounds()).isEqualTo(0);
        }
    }

    @Test
    void decodeActionAcceptsTargetPlayerIdForUseAbility() {
        Map<String, Object> payload = Map.of(
                "type", "USE_ABILITY",
                "targetPlayerId", "p2"
        );
        BloodBoundAction action = engine.decodeAction(payload);
        assertThat(action.type()).isEqualTo(BloodBoundActionType.USE_ABILITY);
        assertThat(action.abilityTargetPlayerId()).isEqualTo("p2");
    }

    @Test
    void useAbilityAssassinDealsWound() {
        BloodBoundGameState state = createTestGame(6);
        BloodBoundPlayerState p1 = state.player("p1");
        p1.setRank(2); // Assassin
        p1.setHasRevealedRank(true);

        BloodBoundPlayerState p2 = state.player("p2");
        p2.setWounds(0);

        PlayerContext actor = PlayerContext.player("p1", "Alice");
        BloodBoundAction ability = new BloodBoundAction(null, BloodBoundActionType.USE_ABILITY, null, null, null, "p2");
        engine.apply(state, actor, ability, new SeededRandomSource(1L));

        assertThat(p2.getWounds()).isEqualTo(1);
        assertThat(p1.isHasUsedAbility()).isTrue();
    }

    @Test
    void useAbilityAssassinLethalWoundOnLeaderTriggersGameOver() {
        BloodBoundGameState state = createTestGame(6);
        BloodBoundPlayerState p1 = state.player("p1");
        p1.setClan(BloodClan.ROSE);
        p1.setRank(2); // Assassin
        p1.setHasRevealedRank(true);

        BloodBoundPlayerState p2 = state.player("p2");
        p2.setClan(BloodClan.FAN);
        p2.setRank(1); // Leader
        p2.setWounds(3); // 1 wound away from capture

        PlayerContext actor = PlayerContext.player("p1", "Alice");
        BloodBoundAction ability = new BloodBoundAction(null, BloodBoundActionType.USE_ABILITY, null, null, null, "p2");
        GameResult<BloodBoundGameState, com.partygameonline.game.bloodbound.domain.BloodBoundEvent> result =
                engine.apply(state, actor, ability, new SeededRandomSource(1L));

        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.GAME_OVER);
        assertThat(state.getCapturedPlayerId()).isEqualTo("p2");
        assertThat(state.getWinnerClan()).isEqualTo(BloodClan.ROSE);
        assertThat(result.finished()).isTrue();
    }

    @Test
    void useAbilityAssassinLethalWoundOnNonLeaderTriggersWrongfulCapture() {
        BloodBoundGameState state = createTestGame(6);
        BloodBoundPlayerState p1 = state.player("p1");
        p1.setClan(BloodClan.ROSE);
        p1.setRank(2); // Assassin
        p1.setHasRevealedRank(true);

        BloodBoundPlayerState p3 = state.player("p3");
        p3.setClan(BloodClan.FAN);
        p3.setRank(3); // Non-Leader (Harlequin)
        p3.setWounds(3);

        PlayerContext actor = PlayerContext.player("p1", "Alice");
        BloodBoundAction ability = new BloodBoundAction(null, BloodBoundActionType.USE_ABILITY, null, null, null, "p3");
        GameResult<BloodBoundGameState, com.partygameonline.game.bloodbound.domain.BloodBoundEvent> result =
                engine.apply(state, actor, ability, new SeededRandomSource(1L));

        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.GAME_OVER);
        assertThat(state.getCapturedPlayerId()).isEqualTo("p3");
        assertThat(state.getWinnerClan()).isEqualTo(BloodClan.FAN); // Wrongful capture -> Defending clan wins
        assertThat(result.finished()).isTrue();
    }

    @Test
    void gameResultFinishedIsTrueWhenGameOverFromAttack() {
        BloodBoundGameState state = createTestGame(6);
        state.setPhase(BloodBoundPhase.WOUND_ASSIGNMENT);
        state.setDaggerPlayerId("p1");
        state.player("p1").setClan(BloodClan.ROSE);

        state.setTargetPlayerId("p2");
        BloodBoundPlayerState p2State = state.player("p2");
        p2State.setClan(BloodClan.FAN);
        p2State.setRank(1); // Leader
        p2State.setWounds(3);

        PlayerContext p2 = PlayerContext.player("p2", "Bob");
        BloodBoundAction reveal = new BloodBoundAction(
                null,
                BloodBoundActionType.REVEAL_WOUND_TOKEN,
                null,
                ClueTokenType.RANK,
                null,
                null
        );
        GameResult<BloodBoundGameState, com.partygameonline.game.bloodbound.domain.BloodBoundEvent> result =
                engine.apply(state, p2, reveal, new SeededRandomSource(1L));

        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.GAME_OVER);
        assertThat(result.finished()).isTrue();
    }

    @Test
    void useAbilityAlchemistHealsWound() {
        BloodBoundGameState state = createTestGame(6);
        BloodBoundPlayerState p1 = state.player("p1");
        p1.setRank(4); // Alchemist
        p1.setHasRevealedRank(true);

        BloodBoundPlayerState p2 = state.player("p2");
        p2.setWounds(2);

        PlayerContext actor = PlayerContext.player("p1", "Alice");
        BloodBoundAction heal = new BloodBoundAction(null, BloodBoundActionType.USE_ABILITY, null, null, null, "p2");
        engine.apply(state, actor, heal, new SeededRandomSource(1L));

        assertThat(p2.getWounds()).isEqualTo(1);
        assertThat(p1.isHasUsedAbility()).isTrue();
    }

    @Test
    void useAbilityGuardianGrantsShield() {
        BloodBoundGameState state = createTestGame(6);
        BloodBoundPlayerState p1 = state.player("p1");
        p1.setRank(6); // Guardian
        p1.setHasRevealedRank(true);

        BloodBoundPlayerState p2 = state.player("p2");
        p2.setShielded(false);

        PlayerContext actor = PlayerContext.player("p1", "Alice");
        BloodBoundAction shield = new BloodBoundAction(null, BloodBoundActionType.USE_ABILITY, null, null, null, "p2");
        engine.apply(state, actor, shield, new SeededRandomSource(1L));

        assertThat(p2.isShielded()).isTrue();
        assertThat(p1.isHasUsedAbility()).isTrue();
    }

    @Test
    void useAbilityMentalistForcesCrestToken() {
        BloodBoundGameState state = createTestGame(6);
        BloodBoundPlayerState p1 = state.player("p1");
        p1.setRank(5); // Mentalist
        p1.setHasRevealedRank(true);

        BloodBoundPlayerState p2 = state.player("p2");
        p2.setClan(BloodClan.FAN);

        PlayerContext actor = PlayerContext.player("p1", "Alice");
        BloodBoundAction mentalist = new BloodBoundAction(null, BloodBoundActionType.USE_ABILITY, null, null, null, "p2");
        engine.apply(state, actor, mentalist, new SeededRandomSource(1L));

        assertThat(p2.getRevealedTokens()).anyMatch(t -> t.type() == ClueTokenType.CREST);
        assertThat(p1.isHasUsedAbility()).isTrue();
    }

    @Test
    void useAbilityHarlequinAddsQuestionToken() {
        BloodBoundGameState state = createTestGame(6);
        BloodBoundPlayerState p1 = state.player("p1");
        p1.setRank(3); // Harlequin
        p1.setHasRevealedRank(true);

        BloodBoundPlayerState p2 = state.player("p2");

        PlayerContext actor = PlayerContext.player("p1", "Alice");
        BloodBoundAction harlequin = new BloodBoundAction(null, BloodBoundActionType.USE_ABILITY, null, null, null, "p2");
        engine.apply(state, actor, harlequin, new SeededRandomSource(1L));

        assertThat(p2.getRevealedTokens()).anyMatch(t -> t.type() == ClueTokenType.QUESTION);
        assertThat(p1.isHasUsedAbility()).isTrue();
    }

    @Test
    void friendlyFireLeaderCaptureAwardsVictoryToOpponent() {
        BloodBoundGameState state = createTestGame(6);
        state.setPhase(BloodBoundPhase.WOUND_ASSIGNMENT);
        state.setDaggerPlayerId("p1"); // Alice (ROSE)
        state.setTargetPlayerId("p3"); // Charlie (ROSE, Leader rank 1)

        BloodBoundPlayerState p1 = state.player("p1");
        p1.setClan(BloodClan.ROSE);
        BloodBoundPlayerState p3 = state.player("p3");
        p3.setClan(BloodClan.ROSE);
        p3.setRank(1);
        p3.setWounds(3); // 1 more wound to capture

        PlayerContext p3Actor = PlayerContext.player("p3", "Charlie");
        BloodBoundAction action = new BloodBoundAction(null, BloodBoundActionType.REVEAL_WOUND_TOKEN, null, ClueTokenType.RANK, null, null);
        engine.apply(state, p3Actor, action, new SeededRandomSource(1L));

        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.GAME_OVER);
        assertThat(state.getCapturedPlayerId()).isEqualTo("p3");
        // Rose shot their own Leader -> Opposing clan FAN wins!
        assertThat(state.getWinnerClan()).isEqualTo(BloodClan.FAN);
    }

    @Test
    void friendlyFireTeammateCaptureAwardsVictoryToOpponent() {
        BloodBoundGameState state = createTestGame(6);
        state.setPhase(BloodBoundPhase.WOUND_ASSIGNMENT);
        state.setDaggerPlayerId("p1"); // Alice (ROSE)
        state.setTargetPlayerId("p3"); // Charlie (ROSE, Rank 3 Chameleon)

        BloodBoundPlayerState p1 = state.player("p1");
        p1.setClan(BloodClan.ROSE);
        BloodBoundPlayerState p3 = state.player("p3");
        p3.setClan(BloodClan.ROSE);
        p3.setRank(3);
        p3.setWounds(3); // 1 more wound to capture

        PlayerContext p3Actor = PlayerContext.player("p3", "Charlie");
        BloodBoundAction action = new BloodBoundAction(null, BloodBoundActionType.REVEAL_WOUND_TOKEN, null, ClueTokenType.RANK, null, null);
        engine.apply(state, p3Actor, action, new SeededRandomSource(1L));

        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.GAME_OVER);
        assertThat(state.getCapturedPlayerId()).isEqualTo("p3");
        // Rose killed their own teammate -> Opposing clan FAN wins!
        assertThat(state.getWinnerClan()).isEqualTo(BloodClan.FAN);
    }

    @Test
    void berserkerCannotTargetSelf() {
        BloodBoundGameState state = createTestGame(6);
        state.setPhase(BloodBoundPhase.ATTACK_CHOICE);
        BloodBoundPlayerState p1 = state.player("p1");
        p1.setRank(7); // Berserker
        p1.setHasRevealedRank(true);

        PlayerContext actor = PlayerContext.player("p1", "Alice");
        BloodBoundAction selfTarget = new BloodBoundAction(null, BloodBoundActionType.USE_ABILITY, null, null, null, "p1");
        ValidationResult val = engine.validate(state, actor, selfTarget);

        assertThat(val.valid()).isFalse();
        assertThat(val.errorCode()).isEqualTo("CANNOT_TARGET_SELF");
    }

    @Test
    void abandonedVictimDuringWoundAssignmentAutoResolves() {
        BloodBoundGameState state = createTestGame(6);
        state.setPhase(BloodBoundPhase.WOUND_ASSIGNMENT);
        state.setDaggerPlayerId("p1");
        state.setTargetPlayerId("p2");

        BloodBoundPlayerState p2 = state.player("p2");
        p2.setWounds(1);

        PlayerContext abandonedPlayer = PlayerContext.player("p2", "Bob");
        engine.onPlayerAbandoned(state, abandonedPlayer, new SeededRandomSource(1L));

        // Abandoned victim should have taken their wound and revealed token
        assertThat(p2.getWounds()).isEqualTo(2);
        assertThat(p2.getRevealedTokens()).isNotEmpty();
    }
}

