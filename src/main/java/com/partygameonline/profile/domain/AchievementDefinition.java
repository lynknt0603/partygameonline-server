package com.partygameonline.profile.domain;

import java.util.List;
import java.util.Locale;

public enum AchievementDefinition {
    NIMP_POT_REVEALED(10, List.of("23_pot.png")),
    NIMP_TOFU_PLAYED(20, List.of("20_tofu.png")),
    NIMP_MEAT_PLAYED(20, List.of("21_meat.png")),
    NIMP_VEGETABLE_PLAYED(20, List.of("17_broccoli.png")),
    NIMP_VEGETARIAN_WINS(10, List.of("01_chef_girl.png")),
    NIMP_MEAT_EATER_WINS(10, List.of("03_smirking_guy.png")),
    NOB_HALFBLOOD_PLAYED(20, List.of("halfblood_2.png")),
    NOB_VAMPIRE_PLAYED(20, List.of("vampire_2.png")),
    NOB_WEREWOLF_PLAYED(20, List.of("werewolf_2.png")),
    NOB_HALFBLOOD_WINS(20, List.of("halfblood.png")),
    NOB_VAMPIRE_WINS(20, List.of("vampire.png")),
    NOB_WEREWOLF_WINS(20, List.of("werewolf.png")),
    RANKING_TOP_ONE(1, List.of("top1.png")),
    ACHIEVEMENT_MASTER(13, List.of("master.png", "master_girl.png"));

    private final int target;
    private final List<String> avatarKeys;

    AchievementDefinition(int target, List<String> avatarKeys) {
        this.target = target;
        this.avatarKeys = List.copyOf(avatarKeys);
    }

    public int target() {
        return target;
    }

    public List<String> avatarKeys() {
        return avatarKeys;
    }

    public boolean isMaster() {
        return this == ACHIEVEMENT_MASTER;
    }

    public static AchievementDefinition fromCode(String code) {
        return valueOf(code.trim().toUpperCase(Locale.ROOT));
    }
}
