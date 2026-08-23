package com.partygameonline.game.games.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.partygameonline.game.runtime.GameActionDispatcher;
import com.partygameonline.game.runtime.GameSession;
import com.partygameonline.game.runtime.GameSessionRepository;
import com.partygameonline.room.domain.RoomStatus;
import com.partygameonline.room.infrastructure.RoomRepository;
import com.partygameonline.session.domain.PlayerPrincipal;
import java.util.List;
import java.util.Map;
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
class DemoGameFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private GameActionDispatcher dispatcher;

    @BeforeEach
    void clearRooms() {
        roomRepository.deleteAll();
    }

    @Test
    void hostCanEmptyHandAndHistoryRecordsWinner() throws Exception {
        Guest host = guest("Linh");
        Guest guest = guest("Minh");

        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                        .session(host.session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId":"demo-card-game","name":"Flow","visibility":"PUBLIC"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String roomId = read(created, "$.id");

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/join").session(guest.session).with(csrf()))
                .andExpect(status().isOk());
        ready(host, roomId);
        ready(guest, roomId);
        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/start").session(host.session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_GAME"));

        GameSession session = gameSessionRepository.findByRoomId(roomId).orElseThrow();
        DemoGameState state = (DemoGameState) session.getState();
        List<String> openingHand = List.copyOf(state.handOf(host.playerId));
        int request = 1;
        for (int i = 0; i < openingHand.size(); i++) {
            dispatcher.dispatch(
                    PlayerPrincipal.guest(host.playerId, "Linh"),
                    roomId,
                    "play-" + request++,
                    Map.of("type", "PLAY_CARD", "cardId", openingHand.get(i))
            );
            GameSession latest = gameSessionRepository.findByRoomId(roomId).orElse(null);
            if (latest == null || latest.isFinished()) {
                break;
            }
            dispatcher.dispatch(
                    PlayerPrincipal.guest(host.playerId, "Linh"),
                    roomId,
                    "end-" + request++,
                    Map.of("type", "END_TURN")
            );
            dispatcher.dispatch(
                    PlayerPrincipal.guest(guest.playerId, "Minh"),
                    roomId,
                    "end-" + request++,
                    Map.of("type", "END_TURN")
            );
        }

        assertThat(gameSessionRepository.findByRoomId(roomId)).isEmpty();
        assertThat(roomRepository.findById(com.partygameonline.room.domain.RoomId.parse(roomId)).orElseThrow().getStatus())
                .isEqualTo(RoomStatus.WAITING);

        mockMvc.perform(get("/api/v1/matches").session(host.session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].winnerPlayerId").value(host.playerId))
                .andExpect(jsonPath("$.content[0].result").value("COMPLETED"))
                .andExpect(jsonPath("$.content[0].hand").doesNotExist());
    }

    private void ready(Guest player, String roomId) throws Exception {
        mockMvc.perform(put("/api/v1/rooms/" + roomId + "/ready")
                        .session(player.session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ready\":true}"))
                .andExpect(status().isOk());
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
        return new Guest(session, read(created, "$.playerId"));
    }

    private static String read(MvcResult result, String path) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private record Guest(MockHttpSession session, String playerId) {
    }
}
