package com.partygameonline.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.partygameonline.game.core.GameRegistry;
import com.partygameonline.game.runtime.GameActionDispatcher;
import com.partygameonline.game.runtime.GameRuntimeService;
import com.partygameonline.game.runtime.InMemoryGameSessionRepository;
import com.partygameonline.room.application.RoomService;
import com.partygameonline.room.domain.GameRoom;
import com.partygameonline.room.domain.RoomId;
import com.partygameonline.room.domain.RoomName;
import com.partygameonline.room.domain.RoomVisibility;
import com.partygameonline.room.infrastructure.InMemoryRoomRepository;
import com.partygameonline.room.infrastructure.RoomLocks;
import com.partygameonline.session.domain.PlayerPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.TaskScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RoomWebSocketHandlerTests {

    @Mock
    private RoomService roomService;

    @Mock
    private WebSocketSession session;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final WebSocketConnectionHub hub = new WebSocketConnectionHub();
    private final RequestIdDeduper deduper = new RequestIdDeduper();
    private RoomWebSocketHandler handler;
    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        WebSocketRoomRealtimePublisher publisher = new WebSocketRoomRealtimePublisher(hub, jsonMapper);
        GameRegistry registry = new GameRegistry(List.of());
        GameRuntimeService runtime = new GameRuntimeService(registry, new InMemoryGameSessionRepository());
        GameActionDispatcher dispatcher = new GameActionDispatcher(
                new RoomLocks(),
                new InMemoryRoomRepository(),
                runtime,
                publisher,
                org.mockito.Mockito.mock(com.partygameonline.history.application.MatchHistoryService.class),
                roomService
        );
        lenient().when(roomService.socketReconnected(any())).thenReturn(Optional.empty());
        DisconnectGraceService grace = new DisconnectGraceService(
                org.mockito.Mockito.mock(TaskScheduler.class),
                new RealtimeProperties(Duration.ofSeconds(30)),
                roomService
        );
        handler = new RoomWebSocketHandler(
                hub, publisher, roomService, dispatcher, runtime, grace, deduper, new RoomChatService(), jsonMapper);
        attributes = new ConcurrentHashMap<>();
        attributes.put(WebSocketConnectionHub.PLAYER_ATTRIBUTE, PlayerPrincipal.guest("p1", "Linh"));
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        hub.register("p1", session);
    }

    @Test
    void connectSendsAuthenticatedPlayerNotClientChosenId() throws Exception {
        handler.afterConnectionEstablished(session);

        String payload = lastPayload();
        assertThat(payload).contains("\"type\":\"CONNECTED\"");
        assertThat(payload).contains("\"playerId\":\"p1\"");
        assertThat(payload).doesNotContain("spoof");
    }

    @Test
    void missingRequestIdIsRejected() throws Exception {
        handler.handleTextMessage(session, new TextMessage("""
                {"version":1,"type":"ROOM_SNAPSHOT","roomId":"ABCD"}
                """));

        assertThat(lastPayload()).contains("MISSING_REQUEST_ID");
    }

    @Test
    void roomSnapshotUsesSessionIdentity() throws Exception {
        GameRoom room = new GameRoom(
                RoomId.parse("ABCD"),
                new RoomName("Lobby"),
                "night-of-bloodlines",
                "p1",
                "Linh",
                2,
                RoomVisibility.PUBLIC,
                Instant.parse("2026-08-19T00:00:00Z")
        );
        when(roomService.get("ABCD")).thenReturn(room);

        handler.handleTextMessage(session, new TextMessage("""
                {"version":1,"type":"ROOM_SNAPSHOT","requestId":"req-1","roomId":"ABCD","payload":{"playerId":"spoof"}}
                """));

        String payload = lastPayload();
        assertThat(payload).contains("\"type\":\"ROOM_SNAPSHOT\"");
        assertThat(payload).contains("\"requestId\":\"req-1\"");
        assertThat(payload).contains("\"roomId\":\"ABCD\"");
        assertThat(payload).contains("\"playerId\":\"p1\"");
        assertThat(payload).doesNotContain("spoof");
    }

    @Test
    void staleSequenceAsksForResyncThenSendsSnapshot() throws Exception {
        GameRoom room = new GameRoom(
                RoomId.parse("ABCD"),
                new RoomName("Lobby"),
                "night-of-bloodlines",
                "p1",
                "Linh",
                2,
                RoomVisibility.PUBLIC,
                Instant.parse("2026-08-19T00:00:00Z")
        );
        room.nextSequence();
        room.nextSequence();
        when(roomService.get("ABCD")).thenReturn(room);

        handler.handleTextMessage(session, new TextMessage("""
                {"version":1,"type":"ROOM_SNAPSHOT","requestId":"req-2","roomId":"ABCD","lastServerSequence":0}
                """));

        ArgumentCaptor<org.springframework.web.socket.TextMessage> captor =
                ArgumentCaptor.forClass(org.springframework.web.socket.TextMessage.class);
        verify(session, org.mockito.Mockito.atLeastOnce()).sendMessage(captor.capture());
        String all = captor.getAllValues().stream()
                .map(org.springframework.web.socket.TextMessage::getPayload)
                .reduce("", (left, right) -> left + right);
        assertThat(all).contains("RESYNC_REQUIRED");
        assertThat(all).contains("ROOM_SNAPSHOT");
    }

    @Test
    void roomChatBroadcastsToMembers() throws Exception {
        GameRoom room = new GameRoom(
                RoomId.parse("ABCD"),
                new RoomName("Lobby"),
                "night-of-bloodlines",
                "p1",
                "Linh",
                2,
                RoomVisibility.PUBLIC,
                Instant.parse("2026-08-19T00:00:00Z")
        );
        when(roomService.get("ABCD")).thenReturn(room);

        handler.handleTextMessage(session, new TextMessage("""
                {"version":1,"type":"ROOM_CHAT","requestId":"chat-1","roomId":"ABCD","payload":{"text":"hello table"}}
                """));

        String payload = lastPayload();
        assertThat(payload).contains("\"type\":\"ROOM_CHAT\"");
        assertThat(payload).contains("hello table");
        assertThat(payload).contains("\"playerId\":\"p1\"");
        assertThat(payload).doesNotContain("spoof");
    }

    @Test
    void gameActionIsRejectedUntilEngineExists() throws Exception {
        handler.handleTextMessage(session, new TextMessage("""
                {"version":1,"type":"GAME_ACTION","requestId":"act-1","roomId":"ABCD","payload":{}}
                """));

        String first = lastPayload();
        assertThat(first).contains("ACTION_REJECTED");
        assertThat(first).contains("ROOM_NOT_FOUND");
        assertThat(first).contains("\"requestId\":\"act-1\"");

        handler.handleTextMessage(session, new TextMessage("""
                {"version":1,"type":"GAME_ACTION","requestId":"act-1","roomId":"ABCD","payload":{}}
                """));
        assertThat(lastPayload()).contains("DUPLICATE_REQUEST");
    }

    private String lastPayload() throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, org.mockito.Mockito.atLeastOnce()).sendMessage(captor.capture());
        return captor.getValue().getPayload();
    }
}
