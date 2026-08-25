package com.partygameonline.room.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class GameRoomTests {

    @Test
    void rejectsSecondJoinOfSamePlayer() {
        GameRoom room = waitingRoom();
        assertThatThrownBy(() -> room.join("host", "Linh")).isInstanceOf(RoomException.class)
                .extracting(ex -> ((RoomException) ex).getErrorCode())
                .isEqualTo("ROOM_ALREADY_JOINED");
    }

    @Test
    void rejectsJoinWhenFull() {
        GameRoom room = waitingRoom();
        room.join("p2", "Guest");
        assertThatThrownBy(() -> room.join("p3", "Other"))
                .extracting(ex -> ((RoomException) ex).getErrorCode())
                .isEqualTo("ROOM_FULL");
    }

    @Test
    void startRequiresHostAllReadyAndMinPlayers() {
        GameRoom room = waitingRoom();
        room.join("p2", "Guest");

        assertThatThrownBy(() -> room.start("p2", 2))
                .extracting(ex -> ((RoomException) ex).getErrorCode())
                .isEqualTo("NOT_ROOM_HOST");

        assertThatThrownBy(() -> room.start("host", 2))
                .extracting(ex -> ((RoomException) ex).getErrorCode())
                .isEqualTo("PLAYERS_NOT_READY");

        room.setReady("p2", true);
        room.start("host", 2);

        assertThat(room.getStatus()).isEqualTo(RoomStatus.STARTING);
        room.markInGame();
        assertThat(room.getStatus()).isEqualTo(RoomStatus.IN_GAME);
        room.markFinished();
        assertThat(room.getStatus()).isEqualTo(RoomStatus.FINISHED);
        room.returnToWaiting();
        assertThat(room.getStatus()).isEqualTo(RoomStatus.WAITING);
        assertThat(room.getPlayers()).allMatch(player -> !player.isReady());
        room.setReady("p2", true);
        room.start("host", 2);
        room.markInGame();
        room.markFinished();
        assertThatThrownBy(() -> room.join("p3", "Late"))
                .extracting(ex -> ((RoomException) ex).getErrorCode())
                .isEqualTo("ROOM_ALREADY_STARTED");
    }

    @Test
    void duplicateDisplayNamesGetNumericSuffixes() {
        GameRoom room = new GameRoom(
                RoomId.parse("ABCD"),
                new RoomName("Linh's Room"),
                "night-of-bloodlines",
                "host",
                "Linh",
                5,
                RoomVisibility.PUBLIC,
                Instant.parse("2026-08-19T00:00:00Z")
        );
        room.join("p2", "Linh");
        assertThat(room.getPlayers()).extracting(RoomPlayer::getDisplayName)
                .containsExactly("Linh 1", "Linh 2");
        room.join("p3", "Linh");
        assertThat(room.getPlayers()).extracting(RoomPlayer::getDisplayName)
                .containsExactly("Linh 1", "Linh 2", "Linh 3");
        room.join("p4", "Minh");
        assertThat(room.getPlayers()).extracting(RoomPlayer::getDisplayName)
                .containsExactly("Linh 1", "Linh 2", "Linh 3", "Minh");
    }

    @Test
    void sequenceIncreasesMonotonically() {
        GameRoom room = waitingRoom();
        assertThat(room.getServerSequence()).isZero();
        assertThat(room.nextSequence()).isEqualTo(1);
        assertThat(room.nextSequence()).isEqualTo(2);
        assertThat(room.getServerSequence()).isEqualTo(2);
    }

    @Test
    void hostLeaveTransfersHost() {
        GameRoom room = waitingRoom();
        room.join("p2", "Guest");
        room.leave("host");

        assertThat(room.getHostPlayerId()).isEqualTo("p2");
        assertThat(room.getPlayers()).hasSize(1);
    }

    @Test
    void leaveAfterGameOverRemovesPlayer() {
        GameRoom room = waitingRoom();
        room.join("p2", "Guest");
        room.setReady("p2", true);
        room.start("host", 2);
        room.markInGame();
        room.markFinished();
        room.leave("p2");

        assertThat(room.findPlayer("p2")).isEmpty();
        assertThat(room.getPlayers()).hasSize(1);
        assertThat(room.getHostPlayerId()).isEqualTo("host");
    }

    private static GameRoom waitingRoom() {
        return new GameRoom(
                RoomId.parse("ABCD"),
                new RoomName("Linh's Room"),
                "night-of-bloodlines",
                "host",
                "Linh",
                2,
                RoomVisibility.PUBLIC,
                Instant.parse("2026-08-19T00:00:00Z")
        );
    }
}
