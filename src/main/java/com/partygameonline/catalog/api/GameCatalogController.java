package com.partygameonline.catalog.api;

import com.partygameonline.catalog.api.dto.GameResponse;
import com.partygameonline.common.error.ResourceNotFoundException;
import com.partygameonline.game.core.GameRegistry;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/games")
public class GameCatalogController {

    private final GameRegistry gameRegistry;

    public GameCatalogController(GameRegistry gameRegistry) {
        this.gameRegistry = gameRegistry;
    }

    @GetMapping
    public List<GameResponse> list() {
        return gameRegistry.all().stream().map(GameResponse::from).toList();
    }

    @GetMapping("/{gameId}")
    public GameResponse get(@PathVariable String gameId) {
        return gameRegistry.findById(gameId)
                .map(GameResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("GAME_NOT_FOUND", "The game was not found"));
    }
}
