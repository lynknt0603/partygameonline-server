package com.partygameonline.history.api;

import com.partygameonline.history.api.dto.MatchResponse;
import com.partygameonline.history.api.dto.PageResponse;
import com.partygameonline.history.application.MatchHistoryService;
import com.partygameonline.session.domain.PlayerPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/matches")
public class MatchHistoryController {

    private final MatchHistoryService matchHistoryService;

    public MatchHistoryController(MatchHistoryService matchHistoryService) {
        this.matchHistoryService = matchHistoryService;
    }

    @GetMapping
    public PageResponse<MatchResponse> list(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return matchHistoryService.listForPlayer(principal.playerId(), page, size);
    }

    @GetMapping("/{matchId}")
    public MatchResponse get(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable UUID matchId
    ) {
        return matchHistoryService.getForPlayer(principal.playerId(), matchId);
    }
}
