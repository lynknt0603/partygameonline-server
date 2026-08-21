package com.partygameonline.game.runtime;

import com.partygameonline.game.core.GameResult;
import com.partygameonline.game.core.ValidationResult;

public record AppliedAction(
        boolean accepted,
        ValidationResult rejection,
        GameResult<Object, Object> result
) {

    public static AppliedAction rejected(ValidationResult rejection) {
        return new AppliedAction(false, rejection, null);
    }

    public static AppliedAction accepted(GameResult<Object, Object> result) {
        return new AppliedAction(true, ValidationResult.ok(), result);
    }
}
