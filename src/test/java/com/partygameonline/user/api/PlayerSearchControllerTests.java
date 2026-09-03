package com.partygameonline.user.api;

import static com.partygameonline.testing.BearerTestSupport.bearer;
import static com.partygameonline.testing.BearerTestSupport.guest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.partygameonline.game.nob.NobGameManifest;
import com.partygameonline.history.infrastructure.MatchEntity;
import com.partygameonline.history.infrastructure.MatchJpaRepository;
import com.partygameonline.history.infrastructure.MatchPlayerEntity;
import com.partygameonline.history.infrastructure.MatchPlayerJpaRepository;
import com.partygameonline.user.infrastructure.UserEntity;
import com.partygameonline.user.infrastructure.UserJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlayerSearchControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserJpaRepository userRepository;

    @Autowired
    private MatchJpaRepository matchRepository;

    @Autowired
    private MatchPlayerJpaRepository matchPlayerRepository;

    @Test
    void searchesMemberByUsernameAndPlayerId() throws Exception {
        String username = "searchuser" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UserEntity user = userRepository.saveAndFlush(UserEntity.newMember(username, "encrypted-password"));
        String token = guest(mockMvc, "Searcher").token();

        mockMvc.perform(get("/api/v1/players/search")
                        .param("query", username)
                        .with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].playerId").value(user.getUserKey()))
                .andExpect(jsonPath("$[0].username").value(username))
                .andExpect(jsonPath("$[0].displayName").value(username));

        mockMvc.perform(get("/api/v1/players/search")
                        .param("query", user.getUserKey())
                        .with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].playerId").value(user.getUserKey()))
                .andExpect(jsonPath("$[0].username").value(username));
    }

    @Test
    void searchesKnownGuestByPlayerId() throws Exception {
        String playerId = "guest-search-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Instant startedAt = Instant.now().minusSeconds(60);
        MatchEntity match = matchRepository.saveAndFlush(MatchEntity.completed(
                NobGameManifest.ID,
                "SRCH01",
                playerId,
                "COMPLETED",
                startedAt,
                Instant.now()
        ));
        matchPlayerRepository.saveAndFlush(MatchPlayerEntity.newPlayer(
                match.getId(),
                null,
                playerId,
                "Guest Search",
                0,
                "WIN"
        ));

        mockMvc.perform(get("/api/v1/players/search")
                        .param("query", playerId.substring(0, 16))
                        .with(bearer(guest(mockMvc, "Searcher").token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].playerId").value(playerId))
                .andExpect(jsonPath("$[0].username").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].displayName").value("Guest Search"));
    }

}
