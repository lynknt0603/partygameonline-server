package com.partygameonline.profile.api;

import com.partygameonline.profile.api.dto.ProfileStatsResponse;
import com.partygameonline.profile.application.ProfileStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
public class PublicProfileController {

    private final ProfileStatsService profileStatsService;

    public PublicProfileController(ProfileStatsService profileStatsService) {
        this.profileStatsService = profileStatsService;
    }

    @GetMapping("/{identifier}")
    public ProfileStatsResponse profile(@PathVariable("identifier") String identifier) {
        return profileStatsService.getStatsByUsername(identifier);
    }
}
