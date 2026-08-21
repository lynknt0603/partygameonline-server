package com.partygameonline.room.domain;

public record RoomName(String value) {

    public static final int MAX_LENGTH = 40;

    public RoomName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Room name is required");
        }
        value = value.trim();
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Room name is too long");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
