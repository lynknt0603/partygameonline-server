package com.partygameonline.ranking.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.partygameonline.ranking.infrastructure.UserGameStatisticJpaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RankingControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserGameStatisticJpaRepository statisticRepository;

    @BeforeEach
    void setUp() {
        statisticRepository.deleteAll();
    }

    @Test
    void emptyRankingIsSafeForAPlayerWithoutCompletedMatches() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/v1/session/guest")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"RankGuest\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/rankings?sort=highestElo").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value("night-of-bloodlines"))
                .andExpect(jsonPath("$.sort").value("highestElo"))
                .andExpect(jsonPath("$.podium").isArray())
                .andExpect(jsonPath("$.podium.length()").value(0))
                .andExpect(jsonPath("$.entries").isArray())
                .andExpect(jsonPath("$.entries.length()").value(0))
                .andExpect(jsonPath("$.totalPlayers").value(0));
    }

    @Test
    void notInMyPotHasASeparateRankingResponse() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/v1/session/guest")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"PotRank\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/rankings")
                        .session(session)
                        .param("gameId", "not-in-my-pot")
                        .param("sort", "highestElo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value("not-in-my-pot"))
                .andExpect(jsonPath("$.sort").value("highestElo"))
                .andExpect(jsonPath("$.bloodline").doesNotExist())
                .andExpect(jsonPath("$.podium").isArray())
                .andExpect(jsonPath("$.entries").isArray());
    }
}
