package com.partygameonline.game.nob.domain;

public record NobBloodline(NobBloodlineType type, Integer rank) {

    public static NobBloodline vampire(int rank) {
        return new NobBloodline(NobBloodlineType.VAMPIRE, rank);
    }

    public static NobBloodline werewolf(int rank) {
        return new NobBloodline(NobBloodlineType.WEREWOLF, rank);
    }

    public static NobBloodline halfblood() {
        return new NobBloodline(NobBloodlineType.HALFBLOOD, null);
    }

    public boolean isMainFaction() {
        return type == NobBloodlineType.VAMPIRE || type == NobBloodlineType.WEREWOLF;
    }
}
