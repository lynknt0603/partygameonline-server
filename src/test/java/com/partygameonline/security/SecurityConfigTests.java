package com.partygameonline.security;

import static com.partygameonline.testing.BearerTestSupport.bearer;
import static com.partygameonline.testing.BearerTestSupport.guest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicGuestCreationDoesNotRequireCookieOrCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/session/guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Linh\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void csrfEndpointWasRemoved() throws Exception {
        mockMvc.perform(get("/api/v1/csrf"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowedOriginCanPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/session/me")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "If-None-Match"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Headers", "If-None-Match"))
                .andExpect(header().string("Access-Control-Expose-Headers", "X-Request-Id, ETag"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void unknownOriginIsNotReflected() throws Exception {
        mockMvc.perform(options("/api/v1/session/me")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void authenticatedUnknownPathReturnsNotFound() throws Exception {
        String token = guest(mockMvc, "Linh").token();

        mockMvc.perform(get("/api/v1/does-not-exist").with(bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }
}
