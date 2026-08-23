package com.partygameonline.room.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.partygameonline.room.infrastructure.RoomRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
class RoomControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @BeforeEach
    void clearRooms() {
        roomRepository.deleteAll();
    }

    @Test
    void createListJoinReadyStartAndLeave() throws Exception {
        Guest host = guest("Linh");
        Guest joiner = guest("Minh");

        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                        .session(host.session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId":"demo-card-game","name":"Linh's Room","maxPlayers":2,"visibility":"PUBLIC","playerId":"spoof"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameId").value("demo-card-game"))
                .andExpect(jsonPath("$.hostPlayerId").value(host.playerId))
                .andExpect(jsonPath("$.hostPlayerId").value(org.hamcrest.Matchers.not("spoof")))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.players.length()").value(1))
                .andReturn();

        String roomId = read(created, "$.id");

        mockMvc.perform(get("/api/v1/rooms").session(host.session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.hasItem(roomId)));

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/join")
                        .session(joiner.session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(2));

        mockMvc.perform(put("/api/v1/rooms/" + roomId + "/ready")
                        .session(host.session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ready\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[?(@.playerId=='" + host.playerId + "')].state")
                        .value(org.hamcrest.Matchers.contains("READY")));

        mockMvc.perform(put("/api/v1/rooms/" + roomId + "/ready")
                        .session(joiner.session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ready\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/start")
                        .session(host.session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_GAME"));

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/leave")
                        .session(joiner.session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/rooms/" + roomId).session(host.session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(1))
                .andExpect(jsonPath("$.players[0].playerId").value(host.playerId));

        mockMvc.perform(post("/api/v1/rooms")
                        .session(joiner.session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId":"demo-card-game","name":"Next table","visibility":"PUBLIC"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void privateRoomIsHiddenFromPublicList() throws Exception {
        Guest host = guest("Linh");
        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                        .session(host.session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId":"demo-card-game","name":"Secret","visibility":"PRIVATE"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String roomId = read(created, "$.id");

        mockMvc.perform(get("/api/v1/rooms").session(host.session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(roomId)
                )));
    }

    @Test
    void startAndJoinRules() throws Exception {
        Guest host = guest("Linh");
        Guest joiner = guest("Minh");
        String roomId = createPublicRoom(host);

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/start")
                        .session(host.session)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NOT_ENOUGH_PLAYERS"));

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/join")
                        .session(joiner.session)
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/start")
                        .session(joiner.session)
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_ROOM_HOST"));

        mockMvc.perform(put("/api/v1/rooms/" + roomId + "/ready")
                        .session(host.session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ready\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/start")
                        .session(host.session)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PLAYERS_NOT_READY"));
    }

    @Test
    void cannotCreateDisabledGameOrSecondRoom() throws Exception {
        Guest host = guest("Linh");
        mockMvc.perform(post("/api/v1/rooms")
                        .session(host.session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId":"disabled-test-game","name":"Vampires"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GAME_DISABLED"));

        createPublicRoom(host);
        mockMvc.perform(post("/api/v1/rooms")
                        .session(host.session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId":"demo-card-game","name":"Another"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_IN_ROOM"));
    }

    @Test
    void hostCanUpdateNobTimersBeforeStart() throws Exception {
        Guest host = guest("Linh");
        Guest joiner = guest("Minh");
        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                        .session(host.session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId":"night-of-bloodlines","name":"NOB","maxPlayers":4}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.settings.nob.draftPickSeconds").value(30))
                .andReturn();
        String roomId = read(created, "$.id");

        mockMvc.perform(put("/api/v1/rooms/" + roomId + "/settings")
                        .session(host.session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nob":{"draftPickSeconds":45,"reactionDecisionSeconds":8}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.nob.draftPickSeconds").value(45))
                .andExpect(jsonPath("$.settings.nob.reactionDecisionSeconds").value(8));

        mockMvc.perform(put("/api/v1/rooms/" + roomId + "/settings")
                        .session(joiner.session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nob":{"draftPickSeconds":15}}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_ROOM_HOST"));
    }

    @Test
    void roomsRequireSession() throws Exception {
        mockMvc.perform(get("/api/v1/rooms"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void guestCannotCreateOrEnterRoom() throws Exception {
        MockHttpSession guestSession = new MockHttpSession();
        mockMvc.perform(post("/api/v1/session/guest")
                        .session(guestSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Guest\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/rooms")
                        .session(guestSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":\"demo-card-game\",\"name\":\"Guest room\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("MEMBER_LOGIN_REQUIRED"));

        String roomId = createPublicRoom(guest("Host"));
        mockMvc.perform(get("/api/v1/rooms/" + roomId).session(guestSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("MEMBER_LOGIN_REQUIRED"));
    }

    @Test
    void concurrentJoinsDoNotExceedMaxPlayers() throws Exception {
        Guest host = guest("Linh");
        Guest a = guest("A");
        Guest b = guest("B");
        String roomId = createPublicRoom(host);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        try {
            Future<Integer> first = executor.submit(() -> joinStatus(a, roomId, ready));
            Future<Integer> second = executor.submit(() -> joinStatus(b, roomId, ready));
            int statusA = first.get(5, TimeUnit.SECONDS);
            int statusB = second.get(5, TimeUnit.SECONDS);
            assertThat(statusA == 200 || statusB == 200).isTrue();
            assertThat(statusA == 409 || statusB == 409).isTrue();

            mockMvc.perform(get("/api/v1/rooms/" + roomId).session(host.session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.players.length()").value(2));
        } finally {
            executor.shutdownNow();
        }
    }

    private int joinStatus(Guest guest, String roomId, CountDownLatch ready) throws Exception {
        ready.countDown();
        ready.await(2, TimeUnit.SECONDS);
        return mockMvc.perform(post("/api/v1/rooms/" + roomId + "/join")
                        .session(guest.session)
                        .with(csrf()))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private String createPublicRoom(Guest host) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                        .session(host.session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId":"demo-card-game","name":"Linh's Room","visibility":"PUBLIC"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return read(created, "$.id");
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
