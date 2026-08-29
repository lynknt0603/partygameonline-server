package com.partygameonline.game.notinmypot.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record NotInMyPotPendingAction(
        NotInMyPotPendingType type,
        String actorPlayerId,
        NotInMyPotCard sourceCard,
        List<NotInMyPotCard> inspectedCards,
        List<String> allowedTargetPlayerIds,
        Instant startedAt,
        Instant expiresAt
) {

    public NotInMyPotPendingAction {
        inspectedCards = inspectedCards == null ? List.of() : List.copyOf(inspectedCards);
        allowedTargetPlayerIds = allowedTargetPlayerIds == null
                ? List.of()
                : List.copyOf(allowedTargetPlayerIds);
    }

    public List<String> inspectedCardIds() {
        return inspectedCards.stream().map(NotInMyPotCard::cardId).toList();
    }

    public List<String> allowedCardIds() {
        return new ArrayList<>(inspectedCardIds());
    }

    public int requiredCardCount() {
        return switch (type) {
            case SELECT_TARGET -> 1;
            case INSPECT_SHUFFLED_POT -> inspectedCards.size();
            case RETURN_SHOPPING_CARDS -> 2;
        };
    }
}
