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

    private AvatarCatalog() {
    }

    public static boolean isSelectable(String avatarKey) {
        return avatarKey != null && SELECTABLE_KEYS.contains(avatarKey.trim());
    }

    public static String normalizeKey(String avatarKey) {
        return isSelectable(avatarKey) ? avatarKey.trim() : DEFAULT_KEY;
    }

    public static String urlForKey(String avatarKey) {
        return BASE_URL + normalizeKey(avatarKey);
    }
}
