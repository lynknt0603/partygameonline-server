package com.partygameonline.common.avatar;

import java.util.Set;

public final class AvatarCatalog {

    public static final String DEFAULT_KEY = "default.png";
    public static final String DEFAULT_URL = "/assets/avatars/default.png";

    private static final String BASE_URL = "/assets/avatars/";
    private static final Set<String> SELECTABLE_KEYS = Set.of(
            DEFAULT_KEY,
            "09_happy_dog.png",
            "10_black_cat.png",
            "11_calm_panda.png",
            "15_rabbit.png",
            "16_frog.png"
    );
    private static final Set<String> REWARD_KEYS = Set.of(
            "23_pot.png", "20_tofu.png", "21_meat.png", "17_broccoli.png",
            "01_chef_girl.png", "03_smirking_guy.png", "halfblood_2.png",
            "vampire_2.png", "werewolf_2.png", "halfblood.png", "vampire.png",
            "werewolf.png", "top1.png", "master.png", "master_girl.png"
    );

    private AvatarCatalog() {
    }

    public static boolean isSelectable(String avatarKey) {
        return avatarKey != null && SELECTABLE_KEYS.contains(avatarKey.trim());
    }

    public static boolean isKnown(String avatarKey) {
        return avatarKey != null
                && (SELECTABLE_KEYS.contains(avatarKey.trim()) || REWARD_KEYS.contains(avatarKey.trim()));
    }

    public static Set<String> freeKeys() {
        return SELECTABLE_KEYS;
    }

    public static Set<String> allKeys() {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>(SELECTABLE_KEYS);
        keys.addAll(REWARD_KEYS);
        return Set.copyOf(keys);
    }

    public static String normalizeKey(String avatarKey) {
        return isKnown(avatarKey) ? avatarKey.trim() : DEFAULT_KEY;
    }

    public static String urlForKey(String avatarKey) {
        return BASE_URL + normalizeKey(avatarKey);
    }

    public static String url(String avatarKey) {
        return BASE_URL + (isKnown(avatarKey) ? avatarKey.trim() : DEFAULT_KEY);
    }
}
