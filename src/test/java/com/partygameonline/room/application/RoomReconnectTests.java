package com.partygameonline.room.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.game.runtime.GameSessionRepository;
import com.partygameonline.room.domain.GameRoom;
import com.partygameonline.room.domain.PlayerLobbyState;
import com.partygameonline.room.domain.RoomStatus;
import com.partygameonline.room.domain.RoomVisibility;
import com.partygameonline.room.infrastructure.RoomRepository;
import com.partygameonline.session.domain.PlayerPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RoomReconnectTests {

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @BeforeEach
    void clear() {
        roomRepository.deleteAll();
    }

    @Test
    void socketDisconnectKeepsSeatAndReconnectRestores() {
        PlayerPrincipal host = PlayerPrincipal.guest("host", "Linh");
        PlayerPrincipal guest = PlayerPrincipal.guest("p2", "Minh");
        GameRoom room = roomService.create(host, "demo-card-game", "Lobby", 2, RoomVisibility.PUBLIC);
        roomService.join(guest, room.getId().value());

        roomService.socketDisconnected(guest);
        GameRoom disconnected = roomService.get(room.getId().value());
        assertThat(disconnected.findPlayer("p2").orElseThrow().getState()).isEqualTo(PlayerLobbyState.DISCONNECTED);
        assertThat(disconnected.getPlayers()).hasSize(2);

        GameRoom reconnected = roomService.socketReconnected(guest).orElseThrow();
        assertThat(reconnected.findPlayer("p2").orElseThrow().getState()).isEqualTo(PlayerLobbyState.CONNECTED);
    }

    @Test
    void waitingGraceExpiryRemovesPlayer() {
        PlayerPrincipal host = PlayerPrincipal.guest("host", "Linh");
        PlayerPrincipal guest = PlayerPrincipal.guest("p2", "Minh");
        GameRoom room = roomService.create(host, "demo-card-game", "Lobby", 2, RoomVisibility.PUBLIC);
        roomService.join(guest, room.getId().value());
        roomService.socketDisconnected(guest);
        roomService.expireDisconnect("p2");

        GameRoom after = roomService.get(room.getId().value());
        assertThat(after.findPlayer("p2")).isEmpty();
        assertThat(after.getPlayers()).hasSize(1);
    }

    @Test
    void inGameGraceExpiryForfeitsToOpponent() {
        PlayerPrincipal host = PlayerPrincipal.guest("host", "Linh");
        PlayerPrincipal guest = PlayerPrincipal.guest("p2", "Minh");
        GameRoom room = roomService.create(host, "demo-card-game", "Lobby", 2, RoomVisibility.PUBLIC);
        roomService.join(guest, room.getId().value());
        roomService.ready(host, room.getId().value(), true);
        roomService.ready(guest, room.getId().value(), true);
        roomService.start(host, room.getId().value());

        roomService.socketDisconnected(guest);
        roomService.expireDisconnect("p2");

        GameRoom after = roomService.get(room.getId().value());
        assertThat(after.getStatus()).isEqualTo(RoomStatus.WAITING);
        assertThat(after.findPlayer("p2")).isPresent();
    }
}
