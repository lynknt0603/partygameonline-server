package com.partygameonline.game.games.demo;

import com.partygameonline.game.core.GameStateProjector;
import com.partygameonline.game.core.PlayerContext;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DemoCardGameProjector implements GameStateProjector<DemoGameState, DemoView> {

    @Override
    public String gameType() {
        return DemoCardGameManifest.ID;
    }

    @Override
    public DemoView project(DemoGameState state, PlayerContext viewer) {
        String you = viewer.playerId();
        String opponent = state.opponentOf(you);
        int opponentHandSize = opponent == null ? 0 : state.handOf(opponent).size();
        boolean inGame = state.playerIds().contains(you);
        return new DemoView(
                you,
                state.currentPlayerId(),
                state.turnNumber(),
                you.equals(state.currentPlayerId()),
                state.hasDrawn(),
                state.hasPlayed(),
                inGame ? state.handOf(you) : List.of(),
                state.deck().size(),
                opponentHandSize,
                state.discard(),
                state.finished(),
                state.winnerPlayerId()
        );
    }
}
