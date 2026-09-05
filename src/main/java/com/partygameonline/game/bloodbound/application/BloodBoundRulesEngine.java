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
                return ValidationResult.ok();

            case USE_ABILITY:
                if (!actorPlayer.isHasRevealedRank()) {
                    return ValidationResult.reject("ABILITY_LOCKED", "Must have revealed rank to use ability");
                }
                if (actorPlayer.isHasUsedAbility()) {
                    return ValidationResult.reject("ABILITY_USED", "Ability already used");
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
                state.setPhase(BloodBoundPhase.ATTACK_CHOICE);
                BloodBoundEvent ackEvt = BloodBoundEvent.log(
                        "The dagger is ready. Choose an opponent to attack.",
                        "Thanh đoản kiếm đã sẵn sàng. Hãy chọn mục tiêu để tấn công."
                );
                events.add(ackEvt);
                state.addLog(ackEvt);
                break;

            case ATTACK:
                state.setTargetPlayerId(action.targetPlayerId());
                state.setIntervenerPlayerId(null);
                state.setPhase(BloodBoundPhase.INTERVENTION_WINDOW);
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
                state.setIntervenerPlayerId(null);
                state.setPhase(BloodBoundPhase.WOUND_ASSIGNMENT);
                BloodBoundEvent passEvt = BloodBoundEvent.log(
                        "No intervention. Direct hit!",
                        "Không có ai can thiệp. Đòn đánh trúng đích!"
                );
                events.add(passEvt);
                state.addLog(passEvt);
                break;

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
            state.setTargetPlayerId(null);
            state.setIntervenerPlayerId(null);
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
            tokenVal = "?";
        }

        victim.addRevealedToken(new RevealedToken(actualType, tokenVal));
        victim.addWound();
        int wounds = victim.getWounds();

        if (wounds >= 4) {
            // Bị bắt giữ -> Game Over
            state.setPhase(BloodBoundPhase.GAME_OVER);
            state.setCapturedPlayerId(victim.getPlayerId());

            BloodBoundPlayerState attacker = state.player(state.getDaggerPlayerId());
            BloodClan attackerClan = attacker != null ? attacker.getClan() : BloodClan.ROSE;
            boolean isLeader = victim.getRank() == 1;

            BloodClan winningClan = isLeader ? attackerClan : victim.getClan();
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
        String targetId = action.abilityTargetPlayerId();
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
                }
                break;
            case 3: // Harlequin: gắn token ?
                if (target != null) {
                    target.addRevealedToken(new RevealedToken(ClueTokenType.QUESTION, "?"));
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
            case 5: // Mentalist: ép lộ crest
                if (target != null) {
                    target.addRevealedToken(new RevealedToken(ClueTokenType.CREST, target.getClan().name() + "-CREST"));
                    logEn = "Mentalist " + actor.getDisplayName() + " forces " + target.getDisplayName() + " to reveal Crest!";
                    logVi = "Thần Trí " + actor.getDisplayName() + " ép " + target.getDisplayName() + " lộ Phù Hiệu Gia Tộc!";
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
}
