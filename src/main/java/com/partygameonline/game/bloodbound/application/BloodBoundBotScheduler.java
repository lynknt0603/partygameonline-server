package com.partygameonline.game.bloodbound.application;

import com.partygameonline.game.bloodbound.BloodBoundGameManifest;
import com.partygameonline.game.bloodbound.domain.BloodBoundActionType;
import com.partygameonline.game.bloodbound.domain.BloodBoundGameState;
import com.partygameonline.game.bloodbound.domain.BloodBoundPhase;
import com.partygameonline.game.bloodbound.domain.BloodBoundPlayerState;
import com.partygameonline.game.bloodbound.domain.BloodClan;
import com.partygameonline.game.bloodbound.domain.ClueTokenType;
import com.partygameonline.game.bloodbound.domain.RevealedToken;
import com.partygameonline.game.runtime.GameActionDispatcher;
import com.partygameonline.game.runtime.GameSession;
import com.partygameonline.game.runtime.GameSessionRepository;
import com.partygameonline.session.domain.PlayerPrincipal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler tự động thực hiện lượt chơi cho Bot bằng thuật toán suy luận thông minh (Rule-based Heuristic)
 * mà không cần mô hình AI nặng, đảm bảo tốc độ phản hồi nhanh, chính xác và bám sát luật chơi Huyết Thệ.
 */
@Component
@ConditionalOnProperty(name = "games.blood-bound.bot-scheduler-enabled", matchIfMissing = true)
public class BloodBoundBotScheduler {

    private static final Logger log = LoggerFactory.getLogger(BloodBoundBotScheduler.class);

    private final GameSessionRepository sessionRepository;
    private final GameActionDispatcher dispatcher;

    public BloodBoundBotScheduler(
            GameSessionRepository sessionRepository,
            GameActionDispatcher dispatcher
    ) {
        this.sessionRepository = sessionRepository;
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelay = 700)
    public void tick() {
        Collection<GameSession> sessions;
        try {
            sessions = sessionRepository.findAll();
        } catch (RuntimeException ex) {
            log.error("BloodBound bot scan failed", ex);
            return;
        }

        for (GameSession session : sessions) {
            try {
                if (session.isFinished() || !BloodBoundGameManifest.ID.equals(session.getGameId())) {
                    continue;
                }
                if (!(session.getState() instanceof BloodBoundGameState state)) {
                    continue;
                }

                handleBotStep(session, state);
            } catch (RuntimeException ex) {
                log.warn("BloodBound bot turn error in roomId={}: {}", session.getRoomId(), ex.getMessage());
            }
        }
    }

    private void handleBotStep(GameSession session, BloodBoundGameState state) {
        BloodBoundPhase phase = state.getPhase();

        // 1. Phase LOOK_LEFT: Tự động xác nhận xem manh mối bên trái
        if (phase == BloodBoundPhase.LOOK_LEFT) {
            for (BloodBoundPlayerState p : state.getPlayers()) {
                if (isBot(p.getPlayerId()) && !state.getAcknowledgedLookLeftPlayerIds().contains(p.getPlayerId())) {
                    dispatchAction(session, p, Map.of(
                            "type", BloodBoundActionType.LOOK_LEFT_ACK.name()
                    ));
                    return; // Xử lý từng lượt cách quãng
                }
            }
            return;
        }

        // 2. Phase ATTACK_CHOICE: Bot cầm dao găm tấn công thông minh
        if (phase == BloodBoundPhase.ATTACK_CHOICE) {
            String daggerHolderId = state.getDaggerPlayerId();
            if (daggerHolderId != null && isBot(daggerHolderId)) {
                BloodBoundPlayerState attacker = state.player(daggerHolderId);
                if (attacker == null || attacker.getWounds() >= 4) {
                    return;
                }
                String targetId = pickSmartAttackTarget(state, attacker);
                if (targetId != null) {
                    dispatchAction(session, attacker, Map.of(
                            "type", BloodBoundActionType.ATTACK.name(),
                            "targetPlayerId", targetId
                    ));
                }
            }
            return;
        }

        // 3. Phase INTERVENTION_WINDOW: Bot quyết định can thiệp đỡ đòn hoặc bỏ qua
        if (phase == BloodBoundPhase.INTERVENTION_WINDOW) {
            for (BloodBoundPlayerState p : state.getPlayers()) {
                if (!isBot(p.getPlayerId())) {
                    continue;
                }
                if (p.getPlayerId().equals(state.getDaggerPlayerId())
                        || p.getPlayerId().equals(state.getTargetPlayerId())
                        || p.isHasRevealedRank()
                        || p.getWounds() >= 4
                        || state.getPassedPlayerIds().contains(p.getPlayerId())) {
                    continue;
                }

                boolean shouldIntervene = shouldSmartIntervene(state, p);

                if (shouldIntervene) {
                    dispatchAction(session, p, Map.of(
                            "type", BloodBoundActionType.INTERVENE.name()
                    ));
                } else {
                    dispatchAction(session, p, Map.of(
                            "type", BloodBoundActionType.PASS_INTERVENTION.name()
                    ));
                }
                return;
            }
            return;
        }

        // 4. Phase WOUND_ASSIGNMENT: Nạn nhân là Bot chọn lộ token manh mối khôn ngoan
        if (phase == BloodBoundPhase.WOUND_ASSIGNMENT) {
            String victimId = state.getIntervenerPlayerId() != null
                    ? state.getIntervenerPlayerId()
                    : state.getTargetPlayerId();
            if (victimId != null && isBot(victimId)) {
                BloodBoundPlayerState victim = state.player(victimId);
                if (victim != null) {
                    ClueTokenType token = pickSmartWoundToken(victim);
                    dispatchAction(session, victim, Map.of(
                            "type", BloodBoundActionType.REVEAL_WOUND_TOKEN.name(),
                            "tokenType", token.name()
                    ));
                }
            }
        }
    }

    /**
     * Thuật toán suy luận mục tiêu tấn công thông minh:
     * - Ưu tiên cao nhất: Dứt điểm Thủ Lĩnh địch (Rank 1 với 3 vết thương) để CHIẾN THẮNG.
     * - Ưu tiên cao: Tấn công kẻ địch đã lộ diện để tiếp tục đào sâu manh mối.
     * - Cấm kỵ: Tuyệt đối không tấn công đồng đội cùng phe đã xác nhận.
     * - Thận trọng: Tránh tấn công người chơi có 3 vết thương mà chưa chắc chắn là Thủ Lĩnh (tránh án phạt bắt nhầm).
     */
    private String pickSmartAttackTarget(BloodBoundGameState state, BloodBoundPlayerState attacker) {
        String forced = state.getForcedAttackTargetId();
        if (forced != null) {
            BloodBoundPlayerState forcedTarget = state.player(forced);
            if (forcedTarget != null && forcedTarget.isConnected() && forcedTarget.getWounds() < 4
                    && !forced.equals(attacker.getPlayerId())) {
                return forced;
            }
        }

        List<BloodBoundPlayerState> candidates = state.getPlayers().stream()
                .filter(p -> !p.getPlayerId().equals(attacker.getPlayerId()) && p.isConnected() && p.getWounds() < 4)
                .toList();

        if (candidates.isEmpty()) {
            return null;
        }

        BloodBoundPlayerState bestTarget = null;
        int highestScore = Integer.MIN_VALUE;

        for (BloodBoundPlayerState c : candidates) {
            int score = 50;
            boolean isAlly = c.getClan() == attacker.getClan();
            boolean isEnemy = !isAlly;
            boolean isConfirmedLeader = false;

            for (RevealedToken t : c.getRevealedTokens()) {
                if (t.type() == ClueTokenType.RANK && "1".equals(t.value())) {
                    isConfirmedLeader = true;
                }
            }

            if (isEnemy && isConfirmedLeader) {
                if (c.getWounds() == 3) {
                    score += 1000; // Đòn dứt điểm đem lại chiến thắng ngay lập tức
                } else {
                    score += 300;
                }
            } else if (isAlly) {
                if (c.getWounds() == 3) {
                    score -= 10000; // Cực kỳ cấm kỵ: giết đồng đội tự thua
                } else {
                    score -= 500; // Tránh bắn đồng minh
                }
            } else if (c.getWounds() == 3 && !isConfirmedLeader) {
                score -= 150; // Thận trọng: không bắn người 3 vết thương chưa rõ danh tính
            } else {
                score += c.getWounds() * 15;
                if (isEnemy) {
                    score += 80;
                }
            }

            // Chiến thuật Nâng Cao dành riêng cho Bot AI:
            if (isAiBot(attacker.getPlayerId())) {
                // Nhận diện Hộ Vệ / VIP: Nếu mục tiêu từng có người can thiệp bảo vệ
                boolean wasProtected = state.getIntervenerPlayerId() != null && c.getPlayerId().equals(state.getTargetPlayerId());
                if (wasProtected && isEnemy) {
                    score += 180; // Mục tiêu này được bảo vệ chứng tỏ là nhân vật quan trọng (Thủ Lĩnh)
                }

                // Nhận diện suy luận màu cờ (nếu mục tiêu đã lộ token màu)
                boolean hasRevealedColor = c.getRevealedTokens().stream().anyMatch(t -> t.type() == ClueTokenType.COLOR);
                if (hasRevealedColor && isEnemy) {
                    score += 50; // Tập trung hỏa lực vào kẻ địch đã xác nhận phe
                }
            }

            if (score > highestScore) {
                highestScore = score;
                bestTarget = c;
            }
        }

        return bestTarget != null ? bestTarget.getPlayerId() : candidates.getFirst().getPlayerId();
    }

    /**
     * Thuật toán can thiệp (Intervention):
     * - Bot chỉ nhảy ra đỡ đòn khi mục tiêu bị tấn công là ĐỒNG ĐỘI (cùng phe).
     * - Ưu tiên đỡ đòn nếu mục tiêu là Thủ Lĩnh (Rank 1) hoặc đang trong tình trạng nguy kịch (>= 2 vết thương).
     * - Bot không can thiệp nếu bản thân đã bị thương nặng (>= 3 vết thương) để tránh bị hạ gục.
     */
    private boolean shouldSmartIntervene(BloodBoundGameState state, BloodBoundPlayerState bot) {
        String targetId = state.getTargetPlayerId();
        if (targetId == null) {
            return false;
        }
        BloodBoundPlayerState target = state.player(targetId);
        if (target == null || target.getClan() != bot.getClan()) {
            return false;
        }

        boolean targetIsLeader = target.getRank() == 1;
        boolean targetCritical = target.getWounds() >= 2;

        return (targetIsLeader || targetCritical) && bot.getWounds() <= 2;
    }

    /**
     * Thuật toán chọn token vết thương:
     * - Thủ Lĩnh (Rank 1): Luôn giấu Rank 1 bằng mọi giá, ưu tiên lộ Màu (COLOR) hoặc Phù hiệu (CREST).
     * - Thành viên khác: Ưu tiên lộ Màu -> Phù hiệu -> Số Rank.
     * - Bot AI: Nếu là Phụ tá / Hộ vệ, có thể chủ động lộ Phù hiệu để đánh lạc hướng đối phương (Bluff).
     */
    private ClueTokenType pickSmartWoundToken(BloodBoundPlayerState victim) {
        Set<ClueTokenType> revealedTypes = victim.getRevealedTokens().stream()
                .map(RevealedToken::type)
                .collect(Collectors.toSet());

        if (victim.getRank() == 1) {
            if (!revealedTypes.contains(ClueTokenType.COLOR)) {
                return ClueTokenType.COLOR;
            }
            if (!revealedTypes.contains(ClueTokenType.CREST)) {
                return ClueTokenType.CREST;
            }
            return ClueTokenType.RANK;
        }

        if (isAiBot(victim.getPlayerId()) && victim.getRank() > 1 && !revealedTypes.contains(ClueTokenType.CREST)) {
            // Bot AI làm mồi nhử (Decoy/Bluff): lộ huy hiệu uy phong
            return ClueTokenType.CREST;
        }

        if (!revealedTypes.contains(ClueTokenType.COLOR)) {
            return ClueTokenType.COLOR;
        }
        if (!revealedTypes.contains(ClueTokenType.CREST)) {
            return ClueTokenType.CREST;
        }
        return ClueTokenType.RANK;
    }

    private boolean isBot(String playerId) {
        return playerId != null && playerId.startsWith("bot-");
    }

    private boolean isAiBot(String playerId) {
        return playerId != null && playerId.startsWith("bot-ai-");
    }

    private void dispatchAction(GameSession session, BloodBoundPlayerState player, Map<String, Object> payload) {
        String commandId = "bloodbound-bot-" + UUID.randomUUID();
        Map<String, Object> fullPayload = new java.util.HashMap<>(payload);
        fullPayload.put("commandId", commandId);

        dispatcher.dispatch(
                PlayerPrincipal.guest(player.getPlayerId(), player.getDisplayName()),
                session.getRoomId(),
                commandId,
                fullPayload
        );
    }
}
