package com.partygameonline.game.nob.catalog;

import com.partygameonline.game.nob.domain.NobEffectCode;
import com.partygameonline.game.nob.domain.NobRoleType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class NobCardCatalog {

    private static final List<NobCardDef> ALL = build();
    private static final Map<String, NobCardDef> BY_CODE =
            ALL.stream().collect(Collectors.toUnmodifiableMap(NobCardDef::cardCode, Function.identity()));

    private NobCardCatalog() {
    }

    public static List<NobCardDef> all() {
        return ALL;
    }

    public static Optional<NobCardDef> find(String cardCode) {
        return Optional.ofNullable(BY_CODE.get(cardCode));
    }

    public static NobCardDef require(String cardCode) {
        return find(cardCode).orElseThrow(() -> new IllegalArgumentException("Unknown card: " + cardCode));
    }

    private static List<NobCardDef> build() {
        List<NobCardDef> cards = new ArrayList<>(33);
        numbered(cards, "NOB-SS", NobRoleType.SHADOW_STALKER, NobEffectCode.LOOK_BLOODLINE);
        numbered(cards, "NOB-BS", NobRoleType.BLOOD_SEER, NobEffectCode.LOOK_BLOODLINE_AND_RANDOM_CARD);
        cards.add(new NobCardDef("NOB-SH-01", NobRoleType.SHAPESHIFTER, 1, NobEffectCode.BLOODLINE_EXCHANGE));
        cards.add(new NobCardDef("NOB-SH-02", NobRoleType.SHAPESHIFTER, 2, NobEffectCode.ECHOES_OF_FALLEN));
        cards.add(new NobCardDef("NOB-SH-03", NobRoleType.SHAPESHIFTER, 3, NobEffectCode.UNMASK));
        cards.add(new NobCardDef("NOB-SH-04", NobRoleType.SHAPESHIFTER, 4, NobEffectCode.MOON_BROKER));
        cards.add(new NobCardDef("NOB-SH-05", NobRoleType.SHAPESHIFTER, 5, NobEffectCode.MOON_THIEF));
        cards.add(new NobCardDef("NOB-SH-06", NobRoleType.SHAPESHIFTER, 6, NobEffectCode.FINAL_JUDGEMENT));
        numbered(cards, "NOB-FK", NobRoleType.FERAL_KILLER, NobEffectCode.BLIND_ELIMINATE);
        numbered(cards, "NOB-HU", NobRoleType.HUNTER, NobEffectCode.INSPECT_THEN_DECIDE);
        cards.add(new NobCardDef("NOB-SP-VEIL-REVERSAL", NobRoleType.SPECIAL, null, NobEffectCode.VEIL_REVERSAL));
        cards.add(new NobCardDef("NOB-SP-LAST-OFFERING", NobRoleType.SPECIAL, null, NobEffectCode.LAST_OFFERING));
        cards.add(new NobCardDef("NOB-SP-LAST-HOPE", NobRoleType.SPECIAL, null, NobEffectCode.LAST_HOPE));
        return List.copyOf(cards);
    }

    private static void numbered(List<NobCardDef> cards, String prefix, NobRoleType role, NobEffectCode effect) {
        for (int number = 1; number <= 6; number++) {
            cards.add(new NobCardDef(prefix + "-" + String.format("%02d", number), role, number, effect));
        }
    }
}
