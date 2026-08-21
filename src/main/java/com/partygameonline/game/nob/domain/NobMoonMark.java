package com.partygameonline.game.nob.domain;

import java.util.UUID;

public record NobMoonMark(String tokenId, int value) {

    public static NobMoonMark of(int value) {
        return new NobMoonMark(UUID.randomUUID().toString(), value);
    }
}
