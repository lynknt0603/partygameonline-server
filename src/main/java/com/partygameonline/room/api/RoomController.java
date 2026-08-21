package com.partygameonline.room.api;

import com.partygameonline.room.api.dto.CreateRoomRequest;
import com.partygameonline.room.api.dto.ReadyRequest;
import com.partygameonline.room.api.dto.RoomResponse;
import com.partygameonline.room.api.dto.RoomSettingsRequest;
import com.partygameonline.room.application.RoomService;
import com.partygameonline.session.domain.PlayerPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public List<RoomResponse> list() {
        return roomService.listPublicWaiting().stream().map(RoomResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<RoomResponse> create(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @Valid @RequestBody CreateRoomRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(RoomResponse.from(roomService.create(
                principal,
                request.gameId(),
                request.name(),
                request.maxPlayers(),
                request.visibility()
        )));
    }

    @GetMapping("/{roomId}")
    public RoomResponse get(@PathVariable String roomId) {
        return RoomResponse.from(roomService.get(roomId));
    }

    @PostMapping("/{roomId}/join")
    public RoomResponse join(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable String roomId
    ) {
        return RoomResponse.from(roomService.join(principal, roomId));
    }

    @PostMapping("/{roomId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable String roomId
    ) {
        roomService.leave(principal, roomId);
    }

    @PutMapping("/{roomId}/ready")
    public RoomResponse ready(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable String roomId,
            @Valid @RequestBody ReadyRequest request
    ) {
        return RoomResponse.from(roomService.ready(principal, roomId, request.ready()));
    }

    @PutMapping("/{roomId}/settings")
    public RoomResponse settings(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable String roomId,
            @RequestBody RoomSettingsRequest request
    ) {
        return RoomResponse.from(roomService.updateSettings(
                principal,
                roomId,
                request == null ? java.util.Map.of() : request.nob()
        ));
    }

    @PostMapping("/{roomId}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable String roomId
    ) {
        roomService.close(principal, roomId);
    }

    @PostMapping("/{roomId}/start")
    public RoomResponse start(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable String roomId
    ) {
        return RoomResponse.from(roomService.start(principal, roomId));
    }
}
