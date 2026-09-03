package com.partygameonline.testing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class BearerTestSupport {

    private BearerTestSupport() {
    }

    public static Identity guest(MockMvc mockMvc, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/session/guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"" + displayName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return identity(result);
    }

    public static Identity member(MockMvc mockMvc, String username, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"Secret123!\",\"displayName\":\""
                                + displayName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return identity(result);
    }

    public static Identity identity(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        return new Identity(JsonPath.read(json, "$.playerId"), JsonPath.read(json, "$.accessToken"));
    }

    public static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }

    public record Identity(String playerId, String token) {
    }
}
