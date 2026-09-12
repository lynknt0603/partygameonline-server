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
import com.partygameonline.game.bloodbound.domain.RevealedToken;
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
        for (String pid : List.of("p1", "p2", "p3", "p4", "p5", "p6")) {
            PlayerContext actor = PlayerContext.player(pid, pid);
            BloodBoundAction action = BloodBoundAction.of(BloodBoundActionType.LOOK_LEFT_ACK);
            ValidationResult val = engine.validate(state, actor, action);
            assertThat(val.valid()).isTrue();
            engine.apply(state, actor, action, new SeededRandomSource(1L));
        }

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
    void interventionWindowRequiresAllEligiblePlayersToPass() {
        BloodBoundGameState state = createTestGame(6);
        state.setPhase(BloodBoundPhase.INTERVENTION_WINDOW);
        state.setDaggerPlayerId("p1");
        state.setTargetPlayerId("p2");

        // Target cannot pass
        PlayerContext p2 = PlayerContext.player("p2", "Bob");
        BloodBoundAction pass = BloodBoundAction.of(BloodBoundActionType.PASS_INTERVENTION);
        ValidationResult targetValidation = engine.validate(state, p2, pass);
        assertThat(targetValidation.valid()).isFalse();
        assertThat(targetValidation.errorCode()).isEqualTo("CANNOT_INTERVENE");

        // Attacker cannot pass
        PlayerContext p1 = PlayerContext.player("p1", "Alice");
        ValidationResult attackerValidation = engine.validate(state, p1, pass);
        assertThat(attackerValidation.valid()).isFalse();
        assertThat(attackerValidation.errorCode()).isEqualTo("CANNOT_INTERVENE");

        // Player who revealed rank cannot pass
        state.player("p3").setHasRevealedRank(true);
        PlayerContext p3_special = PlayerContext.player("p3", "p3");
        ValidationResult rankRevealedValidation = engine.validate(state, p3_special, pass);
        assertThat(rankRevealedValidation.valid()).isFalse();
        assertThat(rankRevealedValidation.errorCode()).isEqualTo("CANNOT_INTERVENE");
        state.player("p3").setHasRevealedRank(false);

        // Eligible players p3, p4, p5 pass one by one
        for (String pid : List.of("p3", "p4", "p5")) {
            PlayerContext p = PlayerContext.player(pid, pid);
            assertThat(engine.validate(state, p, pass).valid()).isTrue();
            engine.apply(state, p, pass, new SeededRandomSource(1L));
            assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.INTERVENTION_WINDOW);
        }

        // Last eligible player p6 passes -> window closes
        PlayerContext p6 = PlayerContext.player("p6", "p6");
        engine.apply(state, p6, pass, new SeededRandomSource(1L));
        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.WOUND_ASSIGNMENT);
        assertThat(state.getIntervenerPlayerId()).isNull();
    }

    @Test
    void timeoutActionTransitionsInterventionWindowToWoundAssignment() {
        BloodBoundGameState state = createTestGame(6);
        state.setPhase(BloodBoundPhase.INTERVENTION_WINDOW);
        state.setDaggerPlayerId("p1");
        state.setTargetPlayerId("p2");
        state.setPhaseDeadline(java.time.Instant.now().minusSeconds(1));

        PlayerContext systemActor = PlayerContext.player("p2", "Bob");
        BloodBoundAction timeoutAction = BloodBoundAction.of(BloodBoundActionType.TIMEOUT);
        assertThat(engine.validate(state, systemActor, timeoutAction).valid()).isTrue();

        engine.apply(state, systemActor, timeoutAction, new SeededRandomSource(1L));
        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.WOUND_ASSIGNMENT);
        assertThat(state.getPhaseDeadline()).isNull();
    }

    @Test
    void questionTokenDoesNotBypassClueDuplicatePrevention() {
        BloodBoundGameState state = createTestGame(6);
        state.setPhase(BloodBoundPhase.WOUND_ASSIGNMENT);
        state.setTargetPlayerId("p2");
        BloodBoundPlayerState p2State = state.player("p2");
        assertThat(p2State).isNotNull();

        // Target received QUESTION token from Harlequin, plus COLOR and CREST tokens from previous wounds
        p2State.addRevealedToken(new RevealedToken(ClueTokenType.QUESTION, "?"));
        p2State.addRevealedToken(new RevealedToken(ClueTokenType.COLOR, "RED"));
        p2State.addRevealedToken(new RevealedToken(ClueTokenType.CREST, "ROSE-CREST"));

        PlayerContext p2 = PlayerContext.player("p2", "Bob");

        // Attempting to reveal COLOR again must be REJECTED
        BloodBoundAction revealColorAgain = new BloodBoundAction(
                "cmd1", BloodBoundActionType.REVEAL_WOUND_TOKEN, null, ClueTokenType.COLOR, null, null
        );
        ValidationResult colorResult = engine.validate(state, p2, revealColorAgain);
        assertThat(colorResult.valid()).isFalse();
        assertThat(colorResult.errorCode()).isEqualTo("TOKEN_ALREADY_REVEALED");

        // Attempting to reveal CREST again must be REJECTED
        BloodBoundAction revealCrestAgain = new BloodBoundAction(
                "cmd2", BloodBoundActionType.REVEAL_WOUND_TOKEN, null, ClueTokenType.CREST, null, null
        );
        ValidationResult crestResult = engine.validate(state, p2, revealCrestAgain);
        assertThat(crestResult.valid()).isFalse();
        assertThat(crestResult.errorCode()).isEqualTo("TOKEN_ALREADY_REVEALED");

        // Revealing RANK must be ACCEPTED
        BloodBoundAction revealRank = new BloodBoundAction(
                "cmd3", BloodBoundActionType.REVEAL_WOUND_TOKEN, null, ClueTokenType.RANK, null, null
        );
        ValidationResult rankResult = engine.validate(state, p2, revealRank);
        assertThat(rankResult.valid()).isTrue();
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
    void captureInquisitorAwardsSoloVictoryToInquisitor() {
        BloodBoundGameState state = createTestGame(6);
        BloodBoundPlayerState p2 = state.player("p2");
        p2.setClan(BloodClan.INQUISITOR);
        p2.setRank(8);
        p2.setWounds(3);

        state.setPhase(BloodBoundPhase.WOUND_ASSIGNMENT);
        state.setDaggerPlayerId("p1"); // Rose attacker
        state.setTargetPlayerId("p2"); // Inquisitor victim

        PlayerContext p2Context = PlayerContext.player("p2", "Bob");
        BloodBoundAction reveal = new BloodBoundAction(
                null,
                BloodBoundActionType.REVEAL_WOUND_TOKEN,
                null,
                ClueTokenType.RANK,
                null,
                null
        );
        engine.apply(state, p2Context, reveal, new SeededRandomSource(1L));

        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.GAME_OVER);
        assertThat(state.getCapturedPlayerId()).isEqualTo("p2");
        assertThat(state.getWinnerClan()).isEqualTo(BloodClan.INQUISITOR);
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
    void abandonedVictimDuringWoundAssignmentAutoResolvesAndHandsOffDagger() {
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
        assertThat(p2.isConnected()).isFalse();

        // Dagger MUST NOT remain with the disconnected player
        assertThat(state.getDaggerPlayerId()).isNotEqualTo("p2");
        assertThat(state.player(state.getDaggerPlayerId()).isConnected()).isTrue();
        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.ATTACK_CHOICE);
    }

    @Test
    void rejectsQuestionTokenOnWoundReveal() {
        BloodBoundGameState state = createTestGame(6);
        state.setPhase(BloodBoundPhase.WOUND_ASSIGNMENT);
        state.setDaggerPlayerId("p1");
        state.setTargetPlayerId("p2");

        PlayerContext victim = PlayerContext.player("p2", "Bob");
        BloodBoundAction cheatAction = new BloodBoundAction(null, BloodBoundActionType.REVEAL_WOUND_TOKEN, null, ClueTokenType.QUESTION, null, null);
        ValidationResult result = engine.validate(state, victim, cheatAction);

        assertThat(result.valid()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_TOKEN_TYPE");
    }

    @Test
    void gameOutcomeStateReturnsAllWinningFactionPlayers() {
        BloodBoundGameState state = createTestGame(6);
        state.setPhase(BloodBoundPhase.WOUND_ASSIGNMENT);
        state.setDaggerPlayerId("p1");
        state.setTargetPlayerId("p2");

        BloodBoundPlayerState p1 = state.player("p1");
        p1.setClan(BloodClan.FAN);

        BloodBoundPlayerState p2 = state.player("p2");
        p2.setClan(BloodClan.ROSE);
        p2.setRank(1);
        p2.setWounds(3);

        PlayerContext p2Actor = PlayerContext.player("p2", "Bob");
        BloodBoundAction action = new BloodBoundAction(null, BloodBoundActionType.REVEAL_WOUND_TOKEN, null, ClueTokenType.RANK, null, null);
        engine.apply(state, p2Actor, action, new SeededRandomSource(1L));

        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.GAME_OVER);
        assertThat(state.getWinnerClan()).isEqualTo(BloodClan.FAN);

        com.partygameonline.game.core.GameOutcomeState outcome = state;
        java.util.Set<String> winners = outcome.winnerPlayerIds();
        assertThat(winners).isNotEmpty();

        for (BloodBoundPlayerState player : state.getPlayers()) {
            if (player.getClan() == BloodClan.FAN) {
                assertThat(winners).contains(player.getPlayerId());
                com.partygameonline.game.core.GamePlayerOutcome po = outcome.playerOutcome(player.getPlayerId());
                assertThat(po).isNotNull();
                assertThat(po.bloodline()).isEqualTo("FAN");
            } else {
                assertThat(winners).doesNotContain(player.getPlayerId());
            }
        }
    }

    @Test
    void useAbilityRank8CourtesanForcesAttackTarget() {
        BloodBoundGameState state = createTestGame(6);
        state.setPhase(BloodBoundPhase.ATTACK_CHOICE);
        state.setDaggerPlayerId("p1");

        BloodBoundPlayerState p1 = state.player("p1");
        p1.setRank(8);
        p1.setHasRevealedRank(true);

        PlayerContext p1Actor = PlayerContext.player("p1", "Alice");
        BloodBoundAction ability = new BloodBoundAction(null, BloodBoundActionType.USE_ABILITY, null, null, null, "p3");
        engine.apply(state, p1Actor, ability, new SeededRandomSource(1L));

        assertThat(p1.isHasUsedAbility()).isTrue();
        assertThat(state.getForcedAttackTargetId()).isEqualTo("p3");

        // Attacking wrong target is rejected
        BloodBoundAction wrongTarget = BloodBoundAction.attack("p2");
        ValidationResult invalidResult = engine.validate(state, p1Actor, wrongTarget);
        assertThat(invalidResult.valid()).isFalse();
        assertThat(invalidResult.errorCode()).isEqualTo("FORCED_TARGET");

        // Attacking forced target is valid
        BloodBoundAction correctTarget = BloodBoundAction.attack("p3");
        ValidationResult validResult = engine.validate(state, p1Actor, correctTarget);
        assertThat(validResult.valid()).isTrue();

        // After attack, forced target is cleared
        engine.apply(state, p1Actor, correctTarget, new SeededRandomSource(1L));
        assertThat(state.getForcedAttackTargetId()).isNull();
    }

    @Test
    void lookLeftRequiresAllConnectedPlayersToAcknowledge() {
        BloodBoundGameState state = createTestGame(6);
        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.LOOK_LEFT);

        // Player 1 acknowledges
        engine.apply(state, PlayerContext.player("p1", "Alice"), BloodBoundAction.of(BloodBoundActionType.LOOK_LEFT_ACK), new SeededRandomSource(1L));
        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.LOOK_LEFT);
        assertThat(state.getAcknowledgedLookLeftPlayerIds()).containsExactly("p1");

        // Next 4 players acknowledge
        List.of("p2", "p3", "p4", "p5").forEach(pid ->
                engine.apply(state, PlayerContext.player(pid, pid), BloodBoundAction.of(BloodBoundActionType.LOOK_LEFT_ACK), new SeededRandomSource(1L))
        );
        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.LOOK_LEFT);

        // Last player acknowledges -> transitions to ATTACK_CHOICE
        engine.apply(state, PlayerContext.player("p6", "Frank"), BloodBoundAction.of(BloodBoundActionType.LOOK_LEFT_ACK), new SeededRandomSource(1L));
        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.ATTACK_CHOICE);
    }

    @Test
    void interventionWindowAutoPassesOnDeadlineExpiry() {
        BloodBoundGameState state = createTestGame(6);
        state.setPhase(BloodBoundPhase.INTERVENTION_WINDOW);
        state.setDaggerPlayerId("p1");
        state.setTargetPlayerId("p2");
        state.setPhaseDeadline(java.time.Instant.now().minusSeconds(1));

        engine.checkPhaseTimeout(state, java.time.Instant.now());

        assertThat(state.getPhase()).isEqualTo(BloodBoundPhase.WOUND_ASSIGNMENT);
        assertThat(state.getPhaseDeadline()).isNull();
    }
}
