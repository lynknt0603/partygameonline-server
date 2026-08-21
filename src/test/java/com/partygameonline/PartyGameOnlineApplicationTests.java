package com.partygameonline;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.game.core.GameRegistry;
import com.partygameonline.game.games.demo.DemoCardGameManifest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PartyGameOnlineApplicationTests {

    @Autowired
    private GameRegistry gameRegistry;

    @Test
    void contextLoads() {
    }

    @Test
    void demoCardGameEngineIsRegisteredWithoutSwitch() {
        assertThat(gameRegistry.hasEngine(DemoCardGameManifest.ID)).isTrue();
        assertThat(gameRegistry.hasEngine("night-of-bloodlines")).isTrue();
    }
}
