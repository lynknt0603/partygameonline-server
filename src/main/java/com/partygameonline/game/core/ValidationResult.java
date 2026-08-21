package com.partygameonline.game.core;

public record ValidationResult(boolean valid, String errorCode, String message) {

    public static ValidationResult ok() {
        return new ValidationResult(true, null, null);
    }

    public static ValidationResult reject(String errorCode, String message) {
        return new ValidationResult(false, errorCode, message);
    }
}
