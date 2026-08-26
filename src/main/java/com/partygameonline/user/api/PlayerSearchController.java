package com.partygameonline.user.api;

import com.partygameonline.user.api.dto.PlayerSearchResponse;
import com.partygameonline.user.application.PlayerSearchService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/players")
public class PlayerSearchController {

    private final PlayerSearchService playerSearchService;

    public PlayerSearchController(PlayerSearchService playerSearchService) {
        this.playerSearchService = playerSearchService;
    }

    @GetMapping("/search")
    public List<PlayerSearchResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer limit
    ) {
        return playerSearchService.search(query, limit);
    }
}
