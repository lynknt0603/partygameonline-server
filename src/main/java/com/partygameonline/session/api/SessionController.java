package com.partygameonline.session.api;

import com.partygameonline.room.application.RoomService;
import com.partygameonline.room.infrastructure.RoomRepository;
import com.partygameonline.security.AuthTokenService;
import com.partygameonline.session.api.dto.CreateGuestSessionRequest;
import com.partygameonline.session.api.dto.SessionResponse;
import com.partygameonline.session.application.SessionService;
import com.partygameonline.session.domain.PlayerPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/session")
public class SessionController {

    private final SessionService sessionService;
    private final RoomRepository roomRepository;
    private final RoomService roomService;
    private final AuthTokenService tokens;

    public SessionController(
            SessionService sessionService,
            RoomRepository roomRepository,
            RoomService roomService,
            AuthTokenService tokens
    ) {
        this.sessionService = sessionService;
        this.roomRepository = roomRepository;
        this.roomService = roomService;
        this.tokens = tokens;
    }

    @PostMapping("/guest")
    public ResponseEntity<SessionResponse> createGuest(
            @Valid @RequestBody CreateGuestSessionRequest request,
            @AuthenticationPrincipal(errorOnInvalidType = false) PlayerPrincipal current
    ) {
        PlayerPrincipal principal = sessionService.createOrRefreshGuest(
                request.displayName(),
                current
        );
        roomService.syncPlayerDisplayName(principal.playerId(), principal.displayName());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(principal));
    }

    @GetMapping("/me")
    public SessionResponse me(@AuthenticationPrincipal PlayerPrincipal principal) {
        return toResponse(principal);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void terminate() {
        // Stateless bearer tokens are terminated by deleting them on the client.
    }

    private SessionResponse toResponse(PlayerPrincipal principal) {
        String roomId = roomRepository.findByPlayerId(principal.playerId())
                .map(room -> room.getId().value())
                .orElse(null);
        return SessionResponse.from(principal, roomId, tokens.issue(principal));
    }
}
