package com.partygameonline.game.nob.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NobSnapshotCachingTests {

    @Test
    void etagChangesWithStateVersionAndViewer() {
        String initial = NobGameController.snapshotEtag(4, "player-1");

        assertThat(NobGameController.snapshotEtag(5, "player-1")).isNotEqualTo(initial);
        assertThat(NobGameController.snapshotEtag(4, "player-2")).isNotEqualTo(initial);
    }

    @Test
    void acceptsStrongWeakAndListConditionalEtags() {
        String etag = NobGameController.snapshotEtag(7, "player-1");

        assertThat(NobGameController.matchesEtag(etag, etag)).isTrue();
        assertThat(NobGameController.matchesEtag("W/" + etag, etag)).isTrue();
        assertThat(NobGameController.matchesEtag("\"other\", " + etag, etag)).isTrue();
        assertThat(NobGameController.matchesEtag("\"other\"", etag)).isFalse();
        assertThat(NobGameController.matchesEtag(null, etag)).isFalse();
    }
}
