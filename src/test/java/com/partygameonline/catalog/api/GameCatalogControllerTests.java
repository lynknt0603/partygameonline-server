package com.partygameonline.catalog.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GameCatalogControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsRegisteredGamesWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.hasItems(
                        "night-of-bloodlines"
                )))
                .andExpect(jsonPath("$[?(@.id=='night-of-bloodlines')].enabled").value(
                        org.hamcrest.Matchers.contains(true)
                ))
                .andExpect(jsonPath("$[?(@.id=='night-of-bloodlines')].minPlayers").value(
                        org.hamcrest.Matchers.contains(4)
                ))
                .andExpect(jsonPath("$[?(@.id=='night-of-bloodlines')].maxPlayers").value(
                        org.hamcrest.Matchers.contains(11)
                ));
    }

    @Test
    void returnsGameById() throws Exception {
        mockMvc.perform(get("/api/v1/games/night-of-bloodlines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("night-of-bloodlines"))
                .andExpect(jsonPath("$.name").value("Night of Bloodlines"))
                .andExpect(jsonPath("$.minPlayers").value(4))
                .andExpect(jsonPath("$.maxPlayers").value(11))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void unknownGameIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/games/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GAME_NOT_FOUND"));
    }
}
