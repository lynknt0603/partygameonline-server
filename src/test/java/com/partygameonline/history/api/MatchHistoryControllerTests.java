package com.partygameonline.history.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.partygameonline.history.infrastructure.MatchEntity;
import com.partygameonline.history.infrastructure.MatchJpaRepository;
import com.partygameonline.history.infrastructure.MatchPlayerEntity;
import com.partygameonline.history.infrastructure.MatchPlayerJpaRepository;
import com.partygameonline.room.infrastructure.RoomRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MatchHistoryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private MatchJpaRepository matchJpaRepository;

    @Autowired
    private MatchPlayerJpaRepository matchPlayerJpaRepository;

    @BeforeEach
    void clearRooms() {
        roomRepository.deleteAll();
        matchPlayerJpaRepository.deleteAll();
        matchJpaRepository.deleteAll();
    }

    @Test
    void finishedMatchAppearsInHistoryWithoutHiddenCards() throws Exception {
        Guest host = guest("Linh");
        Guest joiner = guest("Minh");
        Instant startedAt = Instant.now().minusSeconds(30);
        MatchEntity match = matchJpaRepository.saveAndFlush(MatchEntity.completed(
                "night-of-bloodlines",
                "ABCD",
                host.playerId,
                "FORFEIT",
                startedAt,
                Instant.now()
        ));
        matchPlayerJpaRepository.saveAndFlush(
                MatchPlayerEntity.newPlayer(match.getId(), null, host.playerId, host.displayName, 0, "WIN")
        );
        matchPlayerJpaRepository.saveAndFlush(
                MatchPlayerEntity.newPlayer(match.getId(), null, joiner.playerId, joiner.displayName, 1, "LOSS")
        );

        MvcResult listed = mockMvc.perform(get("/api/v1/matches?page=0&size=20").session(host.session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].gameId").value("night-of-bloodlines"))
                .andExpect(jsonPath("$.content[0].winnerPlayerId").value(host.playerId))
                .andExpect(jsonPath("$.content[0].result").value("FORFEIT"))
                .andExpect(jsonPath("$.content[0].players.length()").value(2))
                .andExpect(jsonPath("$.content[0].hand").doesNotExist())
                .andExpect(jsonPath("$.content[0].deck").doesNotExist())
                .andReturn();

        String matchId = com.jayway.jsonpath.JsonPath.read(listed.getResponse().getContentAsString(), "$.content[0].id");

        mockMvc.perform(get("/api/v1/matches/" + matchId).session(host.session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(matchId))
                .andExpect(jsonPath("$.winnerPlayerId").value(host.playerId));

        Guest stranger = guest("Other");
        mockMvc.perform(get("/api/v1/matches/" + matchId).session(stranger.session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MATCH_NOT_FOUND"));
    }

    @Test
    void matchesRequireSession() throws Exception {
        mockMvc.perform(get("/api/v1/matches"))
                .andExpect(status().isUnauthorized());
    }

    private Guest guest(String displayName) throws Exception {
        MockHttpSession session = new MockHttpSession();
        String username = displayName.toLowerCase() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        MvcResult created = mockMvc.perform(post("/api/v1/auth/register")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new Guest(session, read(created, "$.playerId"), displayName);
    }

    private static String read(MvcResult result, String path) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private record Guest(MockHttpSession session, String playerId, String displayName) {
    }
}
