package com.partygameonline.profile.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.partygameonline.user.infrastructure.UserEntity;
import com.partygameonline.user.infrastructure.UserJpaRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileStatsControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserJpaRepository userRepository;

    @Test
    void guestWithoutMatchesReceivesZeroNobStats() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/v1/session/guest")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Stats Guest\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/profile/me/stats").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player.playerId").isNotEmpty())
                .andExpect(jsonPath("$.player.displayName").value("Stats Guest"))
                .andExpect(jsonPath("$.player.avatarUrl").value("/assets/avatar-default.png"))
                .andExpect(jsonPath("$.player.joinedAt").isNotEmpty())
                .andExpect(jsonPath("$.player.role").value("Guest"))
                .andExpect(jsonPath("$.player.platform").value("Web"))
                .andExpect(jsonPath("$.nobStats.totalMatches").value(0))
                .andExpect(jsonPath("$.nobStats.matchesWon").value(0))
                .andExpect(jsonPath("$.nobStats.winRate").value(0.0))
                .andExpect(jsonPath("$.nobStats.elo").value(5000))
                .andExpect(jsonPath("$.nobStats.highestElo").value(5000))
                .andExpect(jsonPath("$.nobStats.vampire.matchesPlayed").value(0))
                .andExpect(jsonPath("$.nobStats.vampire.matchesWon").value(0))
                .andExpect(jsonPath("$.nobStats.vampire.winRate").value(0.0))
                .andExpect(jsonPath("$.nobStats.werewolf.matchesPlayed").value(0))
                .andExpect(jsonPath("$.nobStats.werewolf.matchesWon").value(0))
                .andExpect(jsonPath("$.nobStats.werewolf.winRate").value(0.0))
                .andExpect(jsonPath("$.nobStats.halfblood.matchesPlayed").value(0))
                .andExpect(jsonPath("$.nobStats.halfblood.matchesWon").value(0))
                .andExpect(jsonPath("$.nobStats.halfblood.winRate").value(0.0));

        mockMvc.perform(get("/api/v1/matches/history?page=0&size=10").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void memberProfileCanBeViewedByUsername() throws Exception {
        String username = "profileviewer" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/v1/auth/register")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/profile/" + username).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player.username").value(username))
                .andExpect(jsonPath("$.player.displayName").value(username))
                .andExpect(jsonPath("$.player.role").value("Member"))
                .andExpect(jsonPath("$.nobStats.totalMatches").value(0))
                .andExpect(jsonPath("$.nobStats.winRate").value(0.0));
    }

    @Test
    void memberProfileCanBeViewedByPlayerId() throws Exception {
        String username = "profileid" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UserEntity user = userRepository.saveAndFlush(UserEntity.newMember(username, "encrypted-password"));
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/v1/session/guest")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Profile Viewer\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/profile/" + user.getUserKey()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player.playerId").value(user.getUserKey()))
                .andExpect(jsonPath("$.player.username").value(username))
                .andExpect(jsonPath("$.player.displayName").value(username))
                .andExpect(jsonPath("$.player.role").value("Member"));
    }

    @Test
    void memberCanUpdateDisplayNameWithoutLosingMemberSession() throws Exception {
        String username = "rename" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/v1/auth/register")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("MEMBER"));

        mockMvc.perform(patch("/api/v1/profile/me")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Room Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Room Name"))
                .andExpect(jsonPath("$.kind").value("MEMBER"));

        mockMvc.perform(get("/api/v1/session/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Room Name"))
                .andExpect(jsonPath("$.kind").value("MEMBER"));

        mockMvc.perform(get("/api/v1/profile/" + username).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player.username").value(username))
                .andExpect(jsonPath("$.player.displayName").value("Room Name"));
    }

    @Test
    void guestCanUpdateDisplayNameWithoutChangingPlayerId() throws Exception {
        MockHttpSession session = new MockHttpSession();

        String playerId = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(post("/api/v1/session/guest")
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"displayName\":\"Guest Before\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.playerId"
        );

        mockMvc.perform(patch("/api/v1/profile/me")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Guest In Room\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(playerId))
                .andExpect(jsonPath("$.displayName").value("Guest In Room"))
                .andExpect(jsonPath("$.kind").value("GUEST"));
    }
}
