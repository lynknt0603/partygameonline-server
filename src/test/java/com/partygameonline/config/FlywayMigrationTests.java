package com.partygameonline.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayAppliedUserAndMatchSkeleton() {
        Integer versionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true AND version IN ('1', '2', '3', '4', '5')",
                Integer.class
        );
        assertThat(versionCount).isEqualTo(5);

        assertThat(tableExists("users")).isTrue();
        assertThat(tableExists("matches")).isTrue();
        assertThat(tableExists("match_players")).isTrue();
        assertThat(tableExists("nob_game_rounds")).isTrue();
        assertThat(tableExists("user_game_statistic")).isTrue();
        assertThat(tableExists("nob_game_session")).isTrue();
        assertThat(tableExists("nob_game_event")).isTrue();
        assertThat(tableExists("user_achievement")).isTrue();
        assertThat(tableExists("user_avatar_unlock")).isTrue();
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name = ?
                        """,
                Integer.class,
                tableName
        );
        return count != null && count == 1;
    }
}
