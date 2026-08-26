package com.partygameonline.profile.api;

import com.partygameonline.profile.api.dto.ProfileStatsResponse;
import com.partygameonline.profile.api.dto.UpdateDisplayNameRequest;
import com.partygameonline.profile.application.ProfileService;
import com.partygameonline.profile.application.ProfileStatsService;
import com.partygameonline.room.infrastructure.RoomRepository;
import com.partygameonline.session.api.dto.SessionResponse;
import com.partygameonline.session.domain.PlayerPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile/me")
public class ProfileStatsController {

    private final ProfileStatsService profileStatsService;
    private final ProfileService profileService;
    private final RoomRepository roomRepository;

    public ProfileStatsController(
            ProfileStatsService profileStatsService,
            ProfileService profileService,
            RoomRepository roomRepository
    ) {
        this.profileStatsService = profileStatsService;
        this.profileService = profileService;
        this.roomRepository = roomRepository;
    }

    @PatchMapping
    public SessionResponse update(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @Valid @RequestBody UpdateDisplayNameRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        PlayerPrincipal updated = profileService.updateDisplayName(
                principal,
                request.displayName(),
                httpRequest,
                httpResponse
        );
        String roomId = roomRepository.findByPlayerId(updated.playerId())
                .map(room -> room.getId().value())
                .orElse(null);
        return SessionResponse.from(updated, roomId);
    }

    @GetMapping("/stats")
    public ProfileStatsResponse stats(@AuthenticationPrincipal PlayerPrincipal principal) {
        return profileStatsService.getStats(principal);
    }
}
