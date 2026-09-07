package com.partygameonline.ranking.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LiarsNumberGameEloPolicyTests {

    private final LiarsNumberGameEloPolicy policy = new LiarsNumberGameEloPolicy();

    @Test
    void rewardsEveryWinnerByTenAndChargesTheSingleLoserByRoomSizeTimesTen() {
        EloRatingService.EloMatchResult result = policy.calculateMatch(
                List.of(
                        new EloRatingService.PlayerOutcome("winner-1", true),
                        new EloRatingService.PlayerOutcome("winner-2", true),
                        new EloRatingService.PlayerOutcome("winner-3", true),
                        new EloRatingService.PlayerOutcome("loser", false)
                ),
                Map.of("winner-1", 5000, "winner-2", 5000, "winner-3", 5000, "loser", 15),
                null
        );

        assertThat(result.changes().get("winner-1").eloDelta()).isEqualTo(10);
        assertThat(result.changes().get("winner-2").eloDelta()).isEqualTo(10);
        assertThat(result.changes().get("winner-3").eloDelta()).isEqualTo(10);
        assertThat(result.changes().get("loser").eloDelta()).isEqualTo(-40);
        assertThat(result.changes().get("loser").newElo()).isZero();
    }
}
