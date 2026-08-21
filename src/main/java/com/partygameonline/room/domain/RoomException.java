package com.partygameonline.room.domain;

import com.partygameonline.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class RoomException extends ApiException {

    public RoomException(String errorCode, HttpStatus status, String clientMessage) {
        super(errorCode, status, clientMessage);
    }

    public static RoomException notFound() {
        return new RoomException("ROOM_NOT_FOUND", HttpStatus.NOT_FOUND, "The room was not found");
    }

    public static RoomException full() {
        return new RoomException("ROOM_FULL", HttpStatus.CONFLICT, "The room is full");
    }

    public static RoomException alreadyJoined() {
        return new RoomException("ROOM_ALREADY_JOINED", HttpStatus.CONFLICT, "You are already in this room");
    }

    public static RoomException alreadyInARoom() {
        return new RoomException("ALREADY_IN_ROOM", HttpStatus.CONFLICT, "You are already in a room");
    }

    public static RoomException notMember() {
        return new RoomException("NOT_ROOM_MEMBER", HttpStatus.FORBIDDEN, "You are not a member of this room");
    }

    public static RoomException notHost() {
        return new RoomException("NOT_ROOM_HOST", HttpStatus.FORBIDDEN, "Only the host can start the game");
    }

    public static RoomException alreadyStarted() {
        return new RoomException("ROOM_ALREADY_STARTED", HttpStatus.CONFLICT, "The room has already started");
    }

    public static RoomException notEnoughPlayers() {
        return new RoomException("NOT_ENOUGH_PLAYERS", HttpStatus.CONFLICT, "Not enough players to start");
    }

    public static RoomException playersNotReady() {
        return new RoomException("PLAYERS_NOT_READY", HttpStatus.CONFLICT, "All players must be ready to start");
    }

    public static RoomException gameDisabled() {
        return new RoomException("GAME_DISABLED", HttpStatus.BAD_REQUEST, "This game is not available");
    }

    public static RoomException invalidMaxPlayers() {
        return new RoomException("INVALID_MAX_PLAYERS", HttpStatus.BAD_REQUEST, "maxPlayers is outside the game limits");
    }

    public static RoomException invalidSettings() {
        return new RoomException("INVALID_SETTINGS", HttpStatus.BAD_REQUEST, "These settings are not valid for this game");
    }
}
