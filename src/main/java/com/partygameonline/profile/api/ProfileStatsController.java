package com.partygameonline.profile.api;

import com.partygameonline.profile.api.dto.ProfileStatsResponse;
import com.partygameonline.profile.application.ProfileStatsService;
import com.partygameonline.session.domain.PlayerPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile/me")
public class ProfileStatsController {

    private final ProfileStatsService profileStatsService;

    public ProfileStatsController(ProfileStatsService profileStatsService) {
        this.profileStatsService = profileStatsService;
    }

    @GetMapping("/stats")
    public ProfileStatsResponse stats(@AuthenticationPrincipal PlayerPrincipal principal) {
        return profileStatsService.getStats(principal);
    }
}
