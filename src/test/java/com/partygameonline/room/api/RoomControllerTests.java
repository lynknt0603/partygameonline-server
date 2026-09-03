package com.partygameonline.room.api;

import static org.assertj.core.api.Assertions.assertThat;
import static com.partygameonline.testing.BearerTestSupport.bearer;
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
        Guest third = guest("Hoa");
        Guest fourth = guest("An");

        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                        .with(bearer(host.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId":"night-of-bloodlines","name":"Linh's Room","maxPlayers":4,"visibility":"PUBLIC","playerId":"spoof"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameId").value("night-of-bloodlines"))
                .andExpect(jsonPath("$.hostPlayerId").value(host.playerId))
                .andExpect(jsonPath("$.hostPlayerId").value(org.hamcrest.Matchers.not("spoof")))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.players.length()").value(1))
                .andReturn();

        String roomId = read(created, "$.id");

        mockMvc.perform(get("/api/v1/rooms").with(bearer(host.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.hasItem(roomId)));

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/join")
                        .with(bearer(joiner.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(2));

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/join")
                        .with(bearer(third.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(3));

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/join")
                        .with(bearer(fourth.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(4));

        mockMvc.perform(put("/api/v1/rooms/" + roomId + "/ready")
                        .with(bearer(host.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ready\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[?(@.playerId=='" + host.playerId + "')].state")
                        .value(org.hamcrest.Matchers.contains("READY")));

        mockMvc.perform(put("/api/v1/rooms/" + roomId + "/ready")
                        .with(bearer(joiner.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ready\":true}"))
                .andExpect(status().isOk());

        for (Guest player : new Guest[] {third, fourth}) {
            mockMvc.perform(put("/api/v1/rooms/" + roomId + "/ready")
                            .with(bearer(player.token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ready\":true}"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/start")
                        .with(bearer(host.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_GAME"));

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/leave")
                        .with(bearer(joiner.token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/rooms/" + roomId).with(bearer(host.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(3))
                .andExpect(jsonPath("$.players[0].playerId").value(host.playerId));

        mockMvc.perform(post("/api/v1/rooms")
                        .with(bearer(joiner.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId":"night-of-bloodlines","name":"Next table","visibility":"PUBLIC"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void privateRoomIsHiddenFromPublicList() throws Exception {
        Guest host = guest("Linh");
        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                        .with(bearer(host.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId":"night-of-bloodlines","name":"Secret","visibility":"PRIVATE"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String roomId = read(created, "$.id");

        mockMvc.perform(get("/api/v1/rooms").with(bearer(host.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(roomId)
                )));
    }

    @Test
    void startAndJoinRules() throws Exception {
        Guest host = guest("Linh");
        Guest joiner = guest("Minh");
        Guest third = guest("Hoa");
        Guest fourth = guest("An");
        String roomId = createPublicRoom(host);

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/start")
                        .with(bearer(host.token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NOT_ENOUGH_PLAYERS"));

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/join")
                        .with(bearer(joiner.token)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/join")
                        .with(bearer(third.token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/join")
                        .with(bearer(fourth.token)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/start")
                        .with(bearer(joiner.token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_ROOM_HOST"));

        mockMvc.perform(put("/api/v1/rooms/" + roomId + "/ready")
                        .with(bearer(host.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ready\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/start")
                        .with(bearer(host.token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PLAYERS_NOT_READY"));
    }

    @Test
    void cannotCreateDisabledGameOrSecondRoom() throws Exception {
        Guest host = guest("Linh");
        mockMvc.perform(post("/api/v1/rooms")
                        .with(bearer(host.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId":"disabled-test-game","name":"Vampires"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GAME_DISABLED"));

        createPublicRoom(host);
        mockMvc.perform(post("/api/v1/rooms")
                        .with(bearer(host.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId":"night-of-bloodlines","name":"Another"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_IN_ROOM"));
    }

    @Test
    void hostCanUpdateNobTimersBeforeStart() throws Exception {
        Guest host = guest("Linh");
        Guest joiner = guest("Minh");
        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                        .with(bearer(host.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId":"night-of-bloodlines","name":"NOB","maxPlayers":4}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.settings.nob.draftPickSeconds").value(30))
                .andReturn();
        String roomId = read(created, "$.id");

        mockMvc.perform(put("/api/v1/rooms/" + roomId + "/settings")
                        .with(bearer(host.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nob":{"draftPickSeconds":45,"reactionDecisionSeconds":8}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.nob.draftPickSeconds").value(45))
                .andExpect(jsonPath("$.settings.nob.reactionDecisionSeconds").value(8));

        mockMvc.perform(put("/api/v1/rooms/" + roomId + "/settings")
                        .with(bearer(joiner.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nob":{"draftPickSeconds":15}}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_ROOM_HOST"));
    }

    @Test
    void hostCanUpdateNotInMyPotTurnTimerAndActionHistoryBeforeStart() throws Exception {
        Guest host = guest("Linh");
        Guest joiner = guest("Minh");
        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                        .with(bearer(host.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId":"not-in-my-pot","name":"Kitchen","maxPlayers":4}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.settings.notInMyPot.turnSeconds").value(30))
                .andExpect(jsonPath("$.settings.notInMyPot.showActionHistory").value(true))
                .andReturn();
        String roomId = read(created, "$.id");

        mockMvc.perform(put("/api/v1/rooms/" + roomId + "/settings")
                        .with(bearer(host.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notInMyPot":{"turnSeconds":45,"showActionHistory":false}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.notInMyPot.turnSeconds").value(45))
                .andExpect(jsonPath("$.settings.notInMyPot.showActionHistory").value(false))
                .andExpect(jsonPath("$.settings.locked").value(false));

        mockMvc.perform(put("/api/v1/rooms/" + roomId + "/settings")
                        .with(bearer(host.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notInMyPot":{"turnSeconds":45,"showActionHistory":false},"locked":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.locked").value(true));

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/join")
                        .with(bearer(joiner.token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROOM_LOCKED"));

        mockMvc.perform(get("/api/v1/rooms").with(bearer(joiner.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(roomId)
                )));
    }

    @Test
    void hostCanChangeCapacityAndKickPlayersWithoutBreakingOccupancyRules() throws Exception {
        Guest host = guest("Linh");
        Guest[] players = new Guest[] {
                guest("P2"), guest("P3"), guest("P4"), guest("P5"),
                guest("P6"), guest("P7"), guest("P8")
        };
        String roomId = read(mockMvc.perform(post("/api/v1/rooms")
                        .with(bearer(host.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId":"wheres-the-bone","name":"Bone table","maxPlayers":8}
                                """))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");

        for (Guest player : players) {
            mockMvc.perform(post("/api/v1/rooms/" + roomId + "/join")
                            .with(bearer(player.token)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(put("/api/v1/rooms/" + roomId + "/settings")
                        .with(bearer(host.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxPlayers\":6}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MAX_PLAYERS_BELOW_CURRENT_COUNT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("8")));

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/kick/" + players[6].playerId)
                        .with(bearer(host.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(7));

        mockMvc.perform(post("/api/v1/rooms")
                        .with(bearer(players[6].token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId":"night-of-bloodlines","name":"Kicked player's new room","maxPlayers":4}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/rooms/" + roomId + "/settings")
                        .with(bearer(host.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxPlayers\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPlayers").value(7));

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/kick/" + players[5].playerId)
                        .with(bearer(host.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(6));

        mockMvc.perform(put("/api/v1/rooms/" + roomId + "/settings")
                        .with(bearer(host.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxPlayers\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPlayers").value(6));

        Guest extra = guest("Extra");
        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/join")
                        .with(bearer(extra.token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROOM_FULL"));

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/kick/" + players[1].playerId)
                        .with(bearer(players[0].token)))
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
        String guestToken = com.partygameonline.testing.BearerTestSupport
                .guest(mockMvc, "Guest")
                .token();

        mockMvc.perform(post("/api/v1/rooms")
                        .with(bearer(guestToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":\"night-of-bloodlines\",\"name\":\"Guest room\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("MEMBER_LOGIN_REQUIRED"));

        String roomId = createPublicRoom(guest("Host"));
        mockMvc.perform(get("/api/v1/rooms/" + roomId).with(bearer(guestToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("MEMBER_LOGIN_REQUIRED"));
    }

    @Test
    void concurrentJoinsDoNotExceedMaxPlayers() throws Exception {
        Guest host = guest("Linh");
        Guest a = guest("A");
        Guest b = guest("B");
        Guest c = guest("C");
        Guest d = guest("D");
        String roomId = createPublicRoom(host);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch ready = new CountDownLatch(4);
        try {
            Future<Integer> first = executor.submit(() -> joinStatus(a, roomId, ready));
            Future<Integer> second = executor.submit(() -> joinStatus(b, roomId, ready));
            Future<Integer> third = executor.submit(() -> joinStatus(c, roomId, ready));
            Future<Integer> fourth = executor.submit(() -> joinStatus(d, roomId, ready));
            int statusA = first.get(5, TimeUnit.SECONDS);
            int statusB = second.get(5, TimeUnit.SECONDS);
            int statusC = third.get(5, TimeUnit.SECONDS);
            int statusD = fourth.get(5, TimeUnit.SECONDS);
            assertThat(new int[] {statusA, statusB, statusC, statusD}).contains(200, 409);

            mockMvc.perform(get("/api/v1/rooms/" + roomId).with(bearer(host.token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.players.length()").value(4));
        } finally {
            executor.shutdownNow();
        }
    }

    private int joinStatus(Guest guest, String roomId, CountDownLatch ready) throws Exception {
        ready.countDown();
        ready.await(2, TimeUnit.SECONDS);
        return mockMvc.perform(post("/api/v1/rooms/" + roomId + "/join")
                        .with(bearer(guest.token)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private String createPublicRoom(Guest host) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/rooms")
                        .with(bearer(host.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameId":"night-of-bloodlines","name":"Linh's Room","maxPlayers":4,"visibility":"PUBLIC"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return read(created, "$.id");
    }

    private Guest guest(String displayName) throws Exception {
        String username = displayName.toLowerCase() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        MvcResult created = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Secret123!\",\"displayName\":\"" + displayName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new Guest(read(created, "$.accessToken"), read(created, "$.playerId"));
    }

    private static String read(MvcResult result, String path) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private record Guest(String token, String playerId) {
    }
}
