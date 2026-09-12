package com.partygameonline.game.bloodbound.application;

import com.partygameonline.game.core.GameResult;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.RandomSource;
import com.partygameonline.game.core.ValidationResult;
import com.partygameonline.game.bloodbound.domain.BloodBoundAction;
import com.partygameonline.game.bloodbound.domain.BloodBoundEvent;
import com.partygameonline.game.bloodbound.domain.BloodBoundGameState;
import com.partygameonline.game.bloodbound.domain.BloodBoundPhase;
import com.partygameonline.game.bloodbound.domain.BloodBoundPlayerState;
import com.partygameonline.game.bloodbound.domain.BloodClan;
import com.partygameonline.game.bloodbound.domain.ClueTokenType;
import com.partygameonline.game.bloodbound.domain.RevealedToken;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BloodBoundRulesEngine {

    public ValidationResult validate(BloodBoundGameState state, PlayerContext actor, BloodBoundAction action) {
        if (state == null || action == null || action.type() == null) {
            return ValidationResult.reject("INVALID_ACTION", "Action cannot be null");
        }
        if (state.getPhase() == BloodBoundPhase.GAME_OVER) {
            return ValidationResult.reject("GAME_OVER", "Game is already finished");
        }
        String actorId = actor.playerId();
        BloodBoundPlayerState actorPlayer = state.player(actorId);
        if (actorPlayer == null) {
            return ValidationResult.reject("PLAYER_NOT_FOUND", "Player not in room");
        }

        switch (action.type()) {
            case LOOK_LEFT_ACK:
                if (state.getPhase() != BloodBoundPhase.LOOK_LEFT) {
                    return ValidationResult.reject("INVALID_PHASE", "Not in look left phase");
                }
                return ValidationResult.ok();

            case ATTACK:
                if (state.getPhase() != BloodBoundPhase.ATTACK_CHOICE) {
                    return ValidationResult.reject("INVALID_PHASE", "Not in attack phase");
                }
                if (!actorId.equals(state.getDaggerPlayerId())) {
                    return ValidationResult.reject("NOT_YOUR_TURN", "You do not hold the dagger");
                }
                if (action.targetPlayerId() == null || action.targetPlayerId().equals(actorId)) {
                    return ValidationResult.reject("INVALID_TARGET", "Cannot attack yourself or null target");
                }
                BloodBoundPlayerState target = state.player(action.targetPlayerId());
                if (target == null || target.getWounds() >= 4) {
                    return ValidationResult.reject("INVALID_TARGET", "Target player invalid or already captured");
                }
                if (state.getForcedAttackTargetId() != null) {
                    BloodBoundPlayerState forced = state.player(state.getForcedAttackTargetId());
                    if (forced != null && forced.isConnected() && forced.getWounds() < 4 && !forced.getPlayerId().equals(actorId)) {
                        if (!action.targetPlayerId().equals(state.getForcedAttackTargetId())) {
                            return ValidationResult.reject("FORCED_TARGET", "Rank 8 intrigue forces attack on " + forced.getDisplayName());
                        }
                    }
                }
                return ValidationResult.ok();

            case INTERVENE:
                if (state.getPhase() != BloodBoundPhase.INTERVENTION_WINDOW) {
                    return ValidationResult.reject("INVALID_PHASE", "Intervention window is not open");
                }
                if (actorId.equals(state.getDaggerPlayerId())) {
                    return ValidationResult.reject("CANNOT_INTERVENE", "Attacker cannot intervene");
                }
                if (actorId.equals(state.getTargetPlayerId())) {
                    return ValidationResult.reject("CANNOT_INTERVENE", "Target cannot intervene on self");
                }
                if (actorPlayer.isHasRevealedRank() || actorPlayer.getWounds() >= 4) {
                    return ValidationResult.reject("CANNOT_INTERVENE", "Player already revealed rank or captured");
                }
                return ValidationResult.ok();

            case PASS_INTERVENTION:
                if (state.getPhase() != BloodBoundPhase.INTERVENTION_WINDOW) {
                    return ValidationResult.reject("INVALID_PHASE", "Not in intervention window");
                }
                if (actorId.equals(state.getDaggerPlayerId())) {
                    return ValidationResult.reject("CANNOT_INTERVENE", "Attacker cannot pass intervention");
                }
                if (actorId.equals(state.getTargetPlayerId())) {
                    return ValidationResult.reject("CANNOT_INTERVENE", "Target cannot pass intervention");
                }
                if (actorPlayer.isHasRevealedRank() || actorPlayer.getWounds() >= 4) {
                    return ValidationResult.reject("CANNOT_INTERVENE", "Player already revealed rank or captured");
                }
                return ValidationResult.ok();

            case TIMEOUT:
                if (!state.timeoutIsDue(java.time.Instant.now())) {
                    return ValidationResult.reject("TIMEOUT_NOT_DUE", "The current phase timeout is not due");
                }
                return ValidationResult.ok();

            case REVEAL_WOUND_TOKEN:
                if (state.getPhase() != BloodBoundPhase.WOUND_ASSIGNMENT) {
                    return ValidationResult.reject("INVALID_PHASE", "Not in wound assignment phase");
                }
                String victimId = state.getIntervenerPlayerId() != null
                        ? state.getIntervenerPlayerId()
                        : state.getTargetPlayerId();
                if (!actorId.equals(victimId)) {
                    return ValidationResult.reject("NOT_VICTIM", "Only the victim can choose reveal token");
                }
                if (action.tokenType() == null && state.getIntervenerPlayerId() == null) {
                    return ValidationResult.reject("MISSING_TOKEN_TYPE", "Token type must be chosen");
                }
                if (action.tokenType() == ClueTokenType.QUESTION) {
                    return ValidationResult.reject("INVALID_TOKEN_TYPE", "QUESTION token can only be placed by Harlequin ability");
                }
                if (action.tokenType() != null && action.tokenType() != ClueTokenType.COLOR
                        && action.tokenType() != ClueTokenType.CREST
                        && action.tokenType() != ClueTokenType.RANK) {
                    return ValidationResult.reject("INVALID_TOKEN_TYPE", "Must choose COLOR, CREST, or RANK");
                }
                BloodBoundPlayerState vicPlayer = state.player(victimId);
                if (vicPlayer != null && action.tokenType() != null) {
                    boolean alreadyRevealed = vicPlayer.getRevealedTokens().stream()
                            .anyMatch(t -> t.type() == action.tokenType());
                    long distinctCluesRevealed = vicPlayer.getRevealedTokens().stream()
                            .map(RevealedToken::type)
                            .filter(t -> t == ClueTokenType.COLOR || t == ClueTokenType.CREST || t == ClueTokenType.RANK)
                            .distinct()
                            .count();
                    if (alreadyRevealed && distinctCluesRevealed < 3) {
                        return ValidationResult.reject("TOKEN_ALREADY_REVEALED", "This token type has already been revealed");
                    }
                }
                return ValidationResult.ok();

            case USE_ABILITY:
                if (state.getPhase() == BloodBoundPhase.LOOK_LEFT || state.getPhase() == BloodBoundPhase.GAME_OVER) {
                    return ValidationResult.reject("INVALID_PHASE", "Cannot use ability in current phase");
                }
                if (actorPlayer.getRank() == 1) {
                    return ValidationResult.reject("PASSIVE_ABILITY", "Leader ability is passive");
                }
                if (!actorPlayer.isHasRevealedRank()) {
                    return ValidationResult.reject("ABILITY_LOCKED", "Must have revealed rank to use ability");
                }
                if (actorPlayer.isHasUsedAbility()) {
                    return ValidationResult.reject("ABILITY_USED", "Ability already used");
                }
                String targetId = action.abilityTargetPlayerId() != null
                        ? action.abilityTargetPlayerId()
                        : action.targetPlayerId();
                if (targetId == null) {
                    return ValidationResult.reject("MISSING_TARGET", "This ability requires a target");
                }
                BloodBoundPlayerState targetPlayer = state.player(targetId);
                if (targetPlayer == null) {
                    return ValidationResult.reject("INVALID_TARGET", "Target player was not found");
                }
                if (targetPlayer.getWounds() >= 4) {
                    return ValidationResult.reject("TARGET_ALREADY_CAPTURED", "Target is already captured");
                }
                if ((actorPlayer.getRank() == 2 || actorPlayer.getRank() == 7) && actorId.equals(targetId)) {
                    return ValidationResult.reject("CANNOT_TARGET_SELF", "Assassin and Berserker cannot target self");
                }
                if (actorPlayer.getRank() == 5 && !hasMissingCoreClue(targetPlayer)) {
                    return ValidationResult.reject("NO_CLUE_AVAILABLE", "Target has already revealed all core clues");
                }
                if (actorPlayer.getRank() == 7) {
                    if (!actorId.equals(state.getDaggerPlayerId())) {
                        return ValidationResult.reject("NOT_YOUR_TURN", "Berserker may only react while holding the dagger");
                    }
                    if (state.getLastAttackerPlayerId() == null
                            || !state.getLastAttackerPlayerId().equals(targetId)) {
                        return ValidationResult.reject("INVALID_TARGET", "Berserker must strike the player who attacked them");
                    }
                }
                return ValidationResult.ok();

            case PASS_DAGGER:
            case CAPTURE:
            default:
                return ValidationResult.ok();
        }
    }

    public GameResult<BloodBoundGameState, BloodBoundEvent> apply(
            BloodBoundGameState state,
            PlayerContext actor,
            BloodBoundAction action,
            RandomSource random
    ) {
        List<BloodBoundEvent> events = new ArrayList<>();
        String actorId = actor.playerId();
        BloodBoundPlayerState actorPlayer = state.player(actorId);

        switch (action.type()) {
            case LOOK_LEFT_ACK:
                state.getAcknowledgedLookLeftPlayerIds().add(actorId);
                long connectedPlayers = state.getPlayers().stream().filter(BloodBoundPlayerState::isConnected).count();
                if (state.getAcknowledgedLookLeftPlayerIds().size() >= connectedPlayers) {
                    state.setPhase(BloodBoundPhase.ATTACK_CHOICE);
                    state.setPhaseDeadline(java.time.Instant.now().plusSeconds(state.getSettings().turnSeconds()));
                    BloodBoundEvent ackEvt = BloodBoundEvent.log(
                            "All players have acknowledged clues. The dagger is ready! Choose an opponent to attack.",
                            "Tất cả người chơi đã xem xong manh mối. Thanh đoản kiếm đã sẵn sàng! Hãy chọn mục tiêu để tấn công."
                    );
                    events.add(ackEvt);
                    state.addLog(ackEvt);
                } else {
                    BloodBoundEvent ackEvt = BloodBoundEvent.log(
                            (actorPlayer != null ? actorPlayer.getDisplayName() : "A player") + " acknowledged clue ("
                                    + state.getAcknowledgedLookLeftPlayerIds().size() + "/" + connectedPlayers + ").",
                            (actorPlayer != null ? actorPlayer.getDisplayName() : "Một người chơi") + " đã xem xong manh mối ("
                                    + state.getAcknowledgedLookLeftPlayerIds().size() + "/" + connectedPlayers + ")."
                    );
                    events.add(ackEvt);
                    state.addLog(ackEvt);
                }
                break;

            case ATTACK:
                state.setLastAttackerPlayerId(actorId);
                state.setTargetPlayerId(action.targetPlayerId());
                state.setIntervenerPlayerId(null);
                state.setPhase(BloodBoundPhase.INTERVENTION_WINDOW);
                state.setPhaseDeadline(java.time.Instant.now().plusSeconds(state.getSettings().interventionSeconds()));
                state.clearPassedPlayerIds();
                state.setForcedAttackTargetId(null);
                BloodBoundPlayerState attackTarget = state.player(action.targetPlayerId());
                BloodBoundEvent attackEvt = BloodBoundEvent.log(
                        (actorPlayer != null ? actorPlayer.getDisplayName() : "Attacker")
                                + " points the dagger at "
                                + (attackTarget != null ? attackTarget.getDisplayName() : "target")
                                + ". Any intervention?",
                        (actorPlayer != null ? actorPlayer.getDisplayName() : "Người tấn công")
                                + " giương kiếm về phía "
                                + (attackTarget != null ? attackTarget.getDisplayName() : "mục tiêu")
                                + ". Có ai can thiệp đỡ đòn không?"
                );
                events.add(attackEvt);
                state.addLog(attackEvt);
                break;

            case INTERVENE:
                state.setIntervenerPlayerId(actorId);
                state.setPhase(BloodBoundPhase.WOUND_ASSIGNMENT);
                state.setPhaseDeadline(null);
                state.clearPassedPlayerIds();
                BloodBoundEvent intEvt = BloodBoundEvent.log(
                        (actorPlayer != null ? actorPlayer.getDisplayName() : "A player")
                                + " boldly intervenes to take the strike!",
                        (actorPlayer != null ? actorPlayer.getDisplayName() : "Một người chơi")
                                + " dũng cảm nhảy ra đỡ đòn!"
                );
                events.add(intEvt);
                state.addLog(intEvt);
                // Người can thiệp tự động nhận đòn & lộ token số ngay lập tức
                applyWoundAndReveal(state, actorPlayer, ClueTokenType.RANK, events);
                break;

            case PASS_INTERVENTION:
                state.getPassedPlayerIds().add(actorId);
                long eligibleInterveners = state.getPlayers().stream()
                        .filter(p -> !p.getPlayerId().equals(state.getDaggerPlayerId())
                                && !p.getPlayerId().equals(state.getTargetPlayerId())
                                && p.isConnected()
                                && !p.isHasRevealedRank()
                                && p.getWounds() < 4)
                        .count();
                long passedCount = state.getPassedPlayerIds().stream()
                        .filter(pid -> !pid.equals(state.getDaggerPlayerId()) && !pid.equals(state.getTargetPlayerId()))
                        .count();

                boolean allPassed = eligibleInterveners == 0 || passedCount >= eligibleInterveners;

                if (allPassed) {
                    state.setIntervenerPlayerId(null);
                    state.setPhase(BloodBoundPhase.WOUND_ASSIGNMENT);
                    state.setPhaseDeadline(null);
                    state.clearPassedPlayerIds();
                    BloodBoundEvent passEvt = BloodBoundEvent.log(
                            "No intervention. Direct hit!",
                            "Không có ai can thiệp. Đòn đánh trúng đích!"
                    );
                    events.add(passEvt);
                    state.addLog(passEvt);
                } else {
                    BloodBoundEvent passEvt = BloodBoundEvent.log(
                            actorPlayer.getDisplayName() + " passed on intervening.",
                            actorPlayer.getDisplayName() + " bỏ qua lượt can thiệp."
                    );
                    events.add(passEvt);
                    state.addLog(passEvt);
                }
                break;

            case TIMEOUT:
                return checkPhaseTimeout(state, java.time.Instant.now());

            case REVEAL_WOUND_TOKEN:
                String vicId = state.getIntervenerPlayerId() != null
                        ? state.getIntervenerPlayerId()
                        : state.getTargetPlayerId();
                BloodBoundPlayerState victim = state.player(vicId);
                if (victim != null) {
                    ClueTokenType chosenType = state.getIntervenerPlayerId() != null
                            ? ClueTokenType.RANK
                            : (action.tokenType() != null ? action.tokenType() : ClueTokenType.RANK);
                    applyWoundAndReveal(state, victim, chosenType, events);
                }
                break;

            case USE_ABILITY:
                applyAbility(state, actorPlayer, action, events);
                break;

            default:
                break;
        }

        state.incrementVersion();
        if (state.getPhase() == BloodBoundPhase.GAME_OVER) {
            String winnerPlayerId = state.getCapturedPlayerId();
            for (BloodBoundPlayerState p : state.getPlayers()) {
                if (p.getClan() == state.getWinnerClan()) {
                    winnerPlayerId = p.getPlayerId();
                    break;
                }
            }
            return GameResult.finished(state, events, winnerPlayerId);
        }
        return GameResult.of(state, events);
    }

    private void applyWoundAndReveal(
            BloodBoundGameState state,
            BloodBoundPlayerState victim,
            ClueTokenType tokenType,
            List<BloodBoundEvent> events
    ) {
        // Kiểm tra Khiên Hộ Vệ
        if (victim.isShielded()) {
            victim.setShielded(false);
            state.setPhase(BloodBoundPhase.ATTACK_CHOICE);
            state.setPhaseDeadline(java.time.Instant.now().plusSeconds(state.getSettings().turnSeconds()));
            state.setTargetPlayerId(null);
            state.setIntervenerPlayerId(null);
            state.setLastAttackerPlayerId(null);
            BloodBoundEvent shieldEvt = BloodBoundEvent.log(
                    victim.getDisplayName() + "'s Guardian Shield absorbed the damage!",
                    "Khiên Hộ Vệ của " + victim.getDisplayName() + " đã hấp thụ hoàn toàn sát thương!"
            );
            events.add(shieldEvt);
            state.addLog(shieldEvt);
            return;
        }

        // Xác định giá trị token
        String tokenVal;
        ClueTokenType actualType = tokenType;
        if (actualType == ClueTokenType.RANK) {
            tokenVal = String.valueOf(victim.getRank());
            victim.setHasRevealedRank(true);
        } else if (actualType == ClueTokenType.COLOR) {
            tokenVal = victim.getClan() == BloodClan.ROSE ? "RED"
                    : victim.getClan() == BloodClan.FAN ? "GREEN" : "YELLOW";
        } else if (actualType == ClueTokenType.CREST) {
            tokenVal = victim.getClan().name() + "-CREST";
        } else {
            actualType = ClueTokenType.COLOR;
            tokenVal = victim.getClan() == BloodClan.ROSE ? "RED"
                    : victim.getClan() == BloodClan.FAN ? "GREEN" : "YELLOW";
        }

        victim.addRevealedToken(new RevealedToken(actualType, tokenVal));
        victim.addWound();
        int wounds = victim.getWounds();

        if (wounds >= 4) {
            // Bị bắt giữ -> Game Over
            state.setPhase(BloodBoundPhase.GAME_OVER);
            state.setCapturedPlayerId(victim.getPlayerId());
            state.setPhaseDeadline(null);

            BloodBoundPlayerState attacker = state.player(state.getDaggerPlayerId());
            BloodClan attackerClan = attacker != null ? attacker.getClan() : BloodClan.ROSE;
            boolean isLeader = victim.getRank() == 1;

            BloodClan winningClan;
            if (victim.getClan() == BloodClan.INQUISITOR) {
                winningClan = BloodClan.INQUISITOR;
            } else if (attackerClan != victim.getClan() && isLeader) {
                winningClan = attackerClan;
            } else {
                winningClan = (attackerClan == BloodClan.ROSE ? BloodClan.FAN : BloodClan.ROSE);
            }
            state.setWinnerClan(winningClan);

            BloodBoundEvent capEvt = BloodBoundEvent.log(
                    victim.getDisplayName() + " is CAPTURED! Secret identity: " + victim.getClan()
                            + " (Rank " + victim.getRank() + "). "
                            + (isLeader ? "Leader was captured! " + winningClan + " wins!"
                            : "Wrongful capture! " + winningClan + " wins!"),
                    victim.getDisplayName() + " ĐÃ BỊ BẮT! Danh tính: " + victim.getClan()
                            + " (Cấp " + victim.getRank() + "). "
                            + (isLeader ? "Bắt đúng Thủ Lĩnh! " + winningClan + " CHIẾN THẮNG!"
                            : "Bắt nhầm người vô tội! " + winningClan + " CHIẾN THẮNG!")
            );
            events.add(capEvt);
            state.addLog(capEvt);
        } else {
            // Chưa bị bắt -> Chuyển dao găm cho nạn nhân
            state.setDaggerPlayerId(victim.getPlayerId());
            state.setTargetPlayerId(null);
            state.setIntervenerPlayerId(null);
            state.setPhase(BloodBoundPhase.ATTACK_CHOICE);
            state.setPhaseDeadline(java.time.Instant.now().plusSeconds(state.getSettings().turnSeconds()));
            state.setRoundNumber(state.getRoundNumber() + 1);

            BloodBoundEvent woundEvt = BloodBoundEvent.log(
                    victim.getDisplayName() + " took 1 wound (" + wounds + "/4) and revealed ["
                            + actualType + ": " + tokenVal + "]. Now holds the dagger.",
                    victim.getDisplayName() + " nhận 1 vết thương (" + wounds + "/4) và để lộ ["
                            + actualType + ": " + tokenVal + "]. Giờ nắm giữ Đoản Kiếm."
            );
            events.add(woundEvt);
            state.addLog(woundEvt);
        }
    }

    private void applyAbility(
            BloodBoundGameState state,
            BloodBoundPlayerState actor,
            BloodBoundAction action,
            List<BloodBoundEvent> events
    ) {
        actor.setHasUsedAbility(true);
        int rank = actor.getRank();
        String targetId = action.abilityTargetPlayerId() != null
                ? action.abilityTargetPlayerId()
                : action.targetPlayerId();
        BloodBoundPlayerState target = targetId != null ? state.player(targetId) : null;

        String logEn = "";
        String logVi = "";

        switch (rank) {
            case 1:
                logEn = "Leader " + actor.getDisplayName() + " rallies the clan with resolve!";
                logVi = "Thủ Lĩnh " + actor.getDisplayName() + " kiên cường cổ vũ toàn gia tộc!";
                break;
            case 2: // Assassin: gây 1 vết thương
                if (target != null) {
                    target.addWound();
                    logEn = "Assassin " + actor.getDisplayName() + " deals 1 direct wound to " + target.getDisplayName() + "!";
                    logVi = "Sát thủ " + actor.getDisplayName() + " gây 1 vết thương lên " + target.getDisplayName() + "!";
                    checkCaptureGameOver(state, actor, target, events);
                }
                break;
            case 3: // Harlequin: gắn token ?
                if (target != null) {
                    if (target.getRevealedTokens().stream().noneMatch(t -> t.type() == ClueTokenType.QUESTION)) {
                        target.addRevealedToken(new RevealedToken(ClueTokenType.QUESTION, "?"));
                    }
                    logEn = "Harlequin " + actor.getDisplayName() + " adds a Mystery (?) token to " + target.getDisplayName() + ".";
                    logVi = "Tắc Kè Hoa " + actor.getDisplayName() + " gắn thêm token Dấu Hỏi (?) cho " + target.getDisplayName() + ".";
                }
                break;
            case 4: // Alchemist: hồi 1 vết thương
                if (target != null) {
                    target.healWound();
                    logEn = "Alchemist " + actor.getDisplayName() + " heals " + target.getDisplayName() + " (-1 wound).";
                    logVi = "Nhà giả kim " + actor.getDisplayName() + " hồi phục 1 vết thương cho " + target.getDisplayName() + ".";
                }
                break;
            case 5: // Mentalist: ép lộ clue còn thiếu
                if (target != null) {
                    ClueTokenType clueType = firstMissingCoreClue(target);
                    if (clueType != null) {
                        String clueValue = clueValue(target, clueType);
                        target.addRevealedToken(new RevealedToken(clueType, clueValue));
                        if (clueType == ClueTokenType.RANK) {
                            target.setHasRevealedRank(true);
                        }
                        logEn = "Mentalist " + actor.getDisplayName() + " forces " + target.getDisplayName()
                                + " to reveal " + clueType + "!";
                        logVi = "Thần Trí " + actor.getDisplayName() + " ép " + target.getDisplayName()
                                + " lộ manh mối " + clueType + "!";
                    }
                }
                break;
            case 6: // Guardian: ban khiên
                if (target != null) {
                    target.setShielded(true);
                    logEn = "Guardian " + actor.getDisplayName() + " grants Aegis Shield to " + target.getDisplayName() + ".";
                    logVi = "Hộ Vệ " + actor.getDisplayName() + " ban khiên bảo vệ cho " + target.getDisplayName() + ".";
                }
                break;
            case 7: // Berserker: gây 1 vết thương
                if (target != null) {
                    target.addWound();
                    logEn = "Berserker " + actor.getDisplayName() + " strikes back, dealing 1 wound to " + target.getDisplayName() + "!";
                    logVi = "Cuồng Nộ " + actor.getDisplayName() + " phản đòn, gây 1 vết thương lên " + target.getDisplayName() + "!";
                    checkCaptureGameOver(state, actor, target, events);
                    state.setLastAttackerPlayerId(null);
                }
                break;
            case 8: // Courtesan / Inquisitor: ép người cầm dao tiếp theo tấn công mục tiêu chỉ định
                if (target != null) {
                    state.setForcedAttackTargetId(target.getPlayerId());
                    logEn = "Courtesan " + actor.getDisplayName() + " schemes, forcing the next attack to target " + target.getDisplayName() + "!";
                    logVi = "Mê Hoặc " + actor.getDisplayName() + " dùng mưu lược, ép đòn tấn công tiếp theo phải nhắm vào " + target.getDisplayName() + "!";
                }
                break;
            default:
                logEn = actor.getDisplayName() + " activates Rank " + rank + " special ability!";
                logVi = actor.getDisplayName() + " kích hoạt kỹ năng Cấp " + rank + "!";
                break;
        }

        if (!logEn.isEmpty()) {
            BloodBoundEvent abilityEvt = BloodBoundEvent.log(logEn, logVi);
            events.add(abilityEvt);
            state.addLog(abilityEvt);
        }
    }

    private boolean hasMissingCoreClue(BloodBoundPlayerState player) {
        return firstMissingCoreClue(player) != null;
    }

    private ClueTokenType firstMissingCoreClue(BloodBoundPlayerState player) {
        if (player.getRevealedTokens().stream().noneMatch(t -> t.type() == ClueTokenType.COLOR)) {
            return ClueTokenType.COLOR;
        }
        if (player.getRevealedTokens().stream().noneMatch(t -> t.type() == ClueTokenType.CREST)) {
            return ClueTokenType.CREST;
        }
        if (player.getRevealedTokens().stream().noneMatch(t -> t.type() == ClueTokenType.RANK)) {
            return ClueTokenType.RANK;
        }
        return null;
    }

    private String clueValue(BloodBoundPlayerState player, ClueTokenType type) {
        return switch (type) {
            case COLOR -> player.getClan() == BloodClan.ROSE ? "RED"
                    : player.getClan() == BloodClan.FAN ? "GREEN" : "YELLOW";
            case CREST -> player.getClan().name() + "-CREST";
            case RANK -> String.valueOf(player.getRank());
            default -> "?";
        };
    }

    private void checkCaptureGameOver(
            BloodBoundGameState state,
            BloodBoundPlayerState attacker,
            BloodBoundPlayerState victim,
            List<BloodBoundEvent> events
    ) {
        if (victim.getWounds() >= 4) {
            state.setPhase(BloodBoundPhase.GAME_OVER);
            state.setCapturedPlayerId(victim.getPlayerId());
            state.setPhaseDeadline(null);

            boolean isLeader = victim.getRank() == 1;
            BloodClan attackerClan = attacker.getClan();
            BloodClan winningClan;
            if (victim.getClan() == BloodClan.INQUISITOR) {
                winningClan = BloodClan.INQUISITOR;
            } else if (attackerClan != victim.getClan() && isLeader) {
                winningClan = attackerClan;
            } else {
                winningClan = (attackerClan == BloodClan.ROSE ? BloodClan.FAN : BloodClan.ROSE);
            }
            state.setWinnerClan(winningClan);

            BloodBoundEvent capEvt = BloodBoundEvent.log(
                    victim.getDisplayName() + " is CAPTURED! Secret identity: " + victim.getClan()
                            + " (Rank " + victim.getRank() + "). "
                            + (isLeader ? "Leader was captured! " + winningClan + " wins!"
                            : "Wrongful capture! " + winningClan + " wins!"),
                    victim.getDisplayName() + " ĐÃ BỊ BẮT! Danh tính: " + victim.getClan()
                            + " (Cấp " + victim.getRank() + "). "
                            + (isLeader ? "Bắt đúng Thủ Lĩnh! " + winningClan + " CHIẾN THẮNG!"
                            : "Bắt nhầm người vô tội! " + winningClan + " CHIẾN THẮNG!")
            );
            events.add(capEvt);
            state.addLog(capEvt);
        }
    }

    public GameResult<BloodBoundGameState, BloodBoundEvent> checkPhaseTimeout(BloodBoundGameState state, java.time.Instant now) {
        List<BloodBoundEvent> events = new ArrayList<>();
        java.time.Instant deadline = state.getPhaseDeadline();
        if (deadline == null || now.isBefore(deadline)) {
            return GameResult.of(state, events);
        }

        if (state.getPhase() == BloodBoundPhase.ATTACK_CHOICE) {
            BloodBoundPlayerState attacker = state.player(state.getDaggerPlayerId());
            BloodBoundPlayerState target = state.player(state.getForcedAttackTargetId());
            if (target == null || !target.isConnected() || target.getWounds() >= 4
                    || (attacker != null && target.getPlayerId().equals(attacker.getPlayerId()))) {
                target = state.getPlayers().stream()
                        .filter(p -> p.isConnected() && p.getWounds() < 4
                                && (attacker == null || !p.getPlayerId().equals(attacker.getPlayerId())))
                        .findFirst()
                        .orElse(null);
            }
            if (attacker != null && target != null) {
                state.setLastAttackerPlayerId(attacker.getPlayerId());
                state.setTargetPlayerId(target.getPlayerId());
                state.setIntervenerPlayerId(null);
                state.setPhase(BloodBoundPhase.INTERVENTION_WINDOW);
                state.setPhaseDeadline(now.plusSeconds(state.getSettings().interventionSeconds()));
                state.clearPassedPlayerIds();
                state.setForcedAttackTargetId(null);
                BloodBoundEvent timeoutEvt = BloodBoundEvent.log(
                        "Attack turn timed out. The dagger automatically targets " + target.getDisplayName() + ".",
                        "Hết thời gian tấn công. Đoản Kiếm tự động nhắm vào " + target.getDisplayName() + "."
                );
                events.add(timeoutEvt);
                state.addLog(timeoutEvt);
                state.incrementVersion();
            } else {
                state.setPhaseDeadline(null);
            }
        } else if (state.getPhase() == BloodBoundPhase.INTERVENTION_WINDOW) {
            state.setIntervenerPlayerId(null);
            state.setPhase(BloodBoundPhase.WOUND_ASSIGNMENT);
            state.setPhaseDeadline(null);
            state.clearPassedPlayerIds();
            BloodBoundEvent timeoutEvt = BloodBoundEvent.log(
                    "Intervention window timed out. Direct hit!",
                    "Hết thời gian can thiệp. Đòn đánh trúng đích!"
            );
            events.add(timeoutEvt);
            state.addLog(timeoutEvt);
            state.incrementVersion();
        }
        return GameResult.of(state, events);
    }
}
