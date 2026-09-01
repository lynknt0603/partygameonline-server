package com.partygameonline.session.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class SessionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registrationUsesDisplayNameAndLoginKeepsIt() throws Exception {
        String username = "register" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        MockHttpSession registrationSession = new MockHttpSession();

        mockMvc.perform(post("/api/v1/auth/register")
                        .session(registrationSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"Secret123!\",\"displayName\":\"Linh Test1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("Linh Test1"))
                .andExpect(jsonPath("$.kind").value("MEMBER"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .session(new MockHttpSession())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Linh Test1"))
                .andExpect(jsonPath("$.kind").value("MEMBER"));
    }

    @Test
    void registrationRequiresDisplayNameWithAtMostTenCharacters() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"missing" + suffix + "\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("displayName"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"toolong" + suffix
                                + "\",\"password\":\"Secret123!\",\"displayName\":\"12345678901\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("displayName"));
    }

    @Test
    void guestSessionRoundTripKeepsServerGeneratedPlayerId() throws Exception {
        MockHttpSession session = new MockHttpSession();

        MvcResult created = mockMvc.perform(post("/api/v1/session/guest")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Linh\",\"playerId\":\"spoofed-id\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("Linh"))
                .andExpect(jsonPath("$.kind").value("GUEST"))
                .andExpect(jsonPath("$.avatarUrl").value("/assets/avatars/default.png"))
                .andExpect(jsonPath("$.playerId").exists())
                .andExpect(jsonPath("$.playerId").value(org.hamcrest.Matchers.not("spoofed-id")))
                .andReturn();

        String playerId = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(),
                "$.playerId"
        );

        mockMvc.perform(get("/api/v1/session/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(playerId))
                .andExpect(jsonPath("$.displayName").value("Linh"))
                .andExpect(jsonPath("$.currentRoomId").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(post("/api/v1/session/guest")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"LinhNguyen\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.playerId").value(playerId))
                .andExpect(jsonPath("$.displayName").value("LinhNguyen"));
    }

    @Test
    void guestEndpointDoesNotDowngradeMemberSession() throws Exception {
        String username = "member" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/v1/auth/register")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Secret123!\",\"displayName\":\"Member\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("MEMBER"))
                .andExpect(jsonPath("$.displayName").value("Member"));

        mockMvc.perform(post("/api/v1/session/guest")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"NoChange\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("MEMBER"))
                .andExpect(jsonPath("$.displayName").value("Member"));

        mockMvc.perform(get("/api/v1/session/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("MEMBER"))
                .andExpect(jsonPath("$.displayName").value("Member"));
    }

    @Test
    void meRequiresSession() throws Exception {
        mockMvc.perform(get("/api/v1/session/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    void deleteTerminatesSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/v1/session/guest")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Linh\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/session").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/session/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void guestRequiresDisplayName() throws Exception {
        mockMvc.perform(post("/api/v1/session/guest")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }
}
