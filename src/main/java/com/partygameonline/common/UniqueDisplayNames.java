package com.partygameonline.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UniqueDisplayNames {

    private UniqueDisplayNames() {
    }

    public static String normalize(String requested) {
        if (requested == null || requested.trim().isEmpty()) {
            return "Player";
        }
        return requested.trim();
    }

    public static boolean belongsToFamily(String name, String base) {
        if (name.equals(base)) {
            return true;
        }
        String prefix = base + " ";
        if (!name.startsWith(prefix)) {
            return false;
        }
        String suffix = name.substring(prefix.length());
        return !suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit);
    }

    public static boolean familyOccupied(String base, Collection<String> names) {
        return names.stream().anyMatch(name -> belongsToFamily(name, base));
    }

    public static String nextNumbered(String base, Collection<String> taken) {
        int n = 1;
        String candidate;
        do {
            candidate = base + " " + n;
            n++;
        } while (taken.contains(candidate));
        return candidate;
    }

    public static Map<String, String> uniquifyAll(List<String> playerIds, Map<String, String> displayNames) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String playerId : playerIds) {
            String raw = normalize(displayNames.getOrDefault(playerId, playerId));
            counts.merge(raw, 1, Integer::sum);
        }
        Map<String, Integer> index = new HashMap<>();
        Map<String, String> result = new LinkedHashMap<>();
        List<String> taken = new ArrayList<>();
        for (String playerId : playerIds) {
            String raw = normalize(displayNames.getOrDefault(playerId, playerId));
            String unique;
            if (counts.getOrDefault(raw, 1) <= 1) {
                unique = familyOccupied(raw, taken) || taken.contains(raw)
                        ? nextNumbered(raw, taken)
                        : raw;
            } else {
                int n = index.merge(raw, 1, Integer::sum);
                unique = raw + " " + n;
                while (taken.contains(unique)) {
                    n = index.merge(raw, 1, Integer::sum);
                    unique = raw + " " + n;
                }
            }
            taken.add(unique);
            result.put(playerId, unique);
        }
        return result;
    }
}
