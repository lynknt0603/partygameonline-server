package com.partygameonline.room.domain;

import java.security.SecureRandom;
import java.util.Locale;

public record RoomId(String value) {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    public RoomId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Room id is required");
        }
        value = value.trim().toUpperCase(Locale.ROOT);
    }

    public static RoomId parse(String raw) {
        return new RoomId(raw);
    }

    public static RoomId random() {
        char[] chars = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            chars[i] = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
        }
        return new RoomId(new String(chars));
    }

    @Override
    public String toString() {
        return value;
    }
}
