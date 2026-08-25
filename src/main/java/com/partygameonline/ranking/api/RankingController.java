package com.partygameonline.ranking.api;

import com.partygameonline.ranking.api.dto.RankingResponse;
import com.partygameonline.ranking.application.RankingService;
import com.partygameonline.session.domain.PlayerPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rankings")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping
    public RankingResponse get(
            @AuthenticationPrincipal(errorOnInvalidType = false) PlayerPrincipal principal,
            @RequestParam(required = false) String gameId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String bloodline,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return rankingService.getRanking(gameId, sort, bloodline, page, size, principal);
    }
}
