package com.partygameonline.game.nob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.partygameonline.game.core.SeededRandomSource;
import com.partygameonline.game.nob.domain.NobBloodline;
import com.partygameonline.game.nob.domain.NobBloodlineType;
import com.partygameonline.game.nob.domain.NobCardInstance;
import com.partygameonline.game.nob.domain.NobGameState;
import com.partygameonline.game.nob.domain.NobPhase;
import com.partygameonline.game.nob.domain.NobPlayerState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NobSetupAndDraftTests {

    @Test
    void rejectsFewerThanFourOrMoreThanElevenPlayers() {
        assertThatThrownBy(() -> NobGameState.bloodlinePool(3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NobGameState.bloodlinePool(12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NobTestSupport.create(1, "a", "b", "c"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 5, 6, 7, 8, 9, 10, 11})
    void bloodlinePoolMatchesPlayerCount(int count) {
        List<NobBloodline> pool = NobGameState.bloodlinePool(count);
        assertThat(pool).hasSize(count);
        int ranks = count / 2;
        assertThat(pool.stream().filter(line -> line.type() == NobBloodlineType.VAMPIRE)).hasSize(ranks);
        assertThat(pool.stream().filter(line -> line.type() == NobBloodlineType.WEREWOLF)).hasSize(ranks);
        long half = pool.stream().filter(line -> line.type() == NobBloodlineType.HALFBLOOD).count();
        assertThat(half).isEqualTo(count % 2);
        Set<String> unique = new HashSet<>();
        for (NobBloodline line : pool) {
            assertThat(unique.add(line.type() + "-" + line.rank())).isTrue();
        }
    }

    @Test
    void dealGivesThreeDraftCardsThenTwoKeptAfterPassLeft() {
        NobGameState state = NobTestSupport.fourPlayers(42);
        assertThat(state.getPhase()).isEqualTo(NobPhase.DRAFT_PICK_1);
        assertThat(state.getPlayers()).hasSize(4);
        for (NobPlayerState player : state.getPlayers()) {
            assertThat(state.getDraftHands().get(player.getPlayerId())).hasSize(3);
            assertThat(player.getCurrentBloodline()).isNotNull();
            assertThat(player.knowsOwnBloodline()).isTrue();
        }
        assertThat(state.getUndealt()).hasSize(21);

        List<NobCardInstance> p1First = List.copyOf(state.getDraftHands().get("p1"));
        List<NobCardInstance> p2First = List.copyOf(state.getDraftHands().get("p2"));
        for (NobPlayerState player : state.getPlayers()) {
            NobTestSupport.apply(state, player.getPlayerId(), NobTestSupport.draft(
                    state.getDraftHands().get(player.getPlayerId()).getFirst().instanceId()
            ));
        }

        assertThat(state.getPhase()).isEqualTo(NobPhase.DRAFT_PICK_2);
        assertThat(state.getPlayers().getFirst().getHand()).hasSize(1);
        List<NobCardInstance> p2Received = state.getDraftHands().get("p2");
        assertThat(p2Received).hasSize(2);
        assertThat(p2Received).extracting(NobCardInstance::instanceId)
                .containsExactlyInAnyOrder(p1First.get(1).instanceId(), p1First.get(2).instanceId());
        assertThat(p2Received).extracting(NobCardInstance::instanceId)
                .doesNotContain(p2First.getFirst().instanceId());

        for (NobPlayerState player : state.getPlayers()) {
            NobTestSupport.apply(state, player.getPlayerId(), NobTestSupport.draft(
                    state.getDraftHands().get(player.getPlayerId()).getFirst().instanceId()
            ));
        }

        if (state.getRoundNumber() == 1 && state.getPhase() != NobPhase.DRAFT_PICK_1) {
            for (NobPlayerState player : state.getPlayers()) {
                assertThat(player.getHand().size() + player.getRevealedCards().size()).isEqualTo(2);
            }
        }
        int inHands = state.getPlayers().stream()
                .mapToInt(player -> player.getHand().size() + player.getRevealedCards().size())
                .sum();
        int draft = state.getDraftHands().values().stream().mapToInt(List::size).sum();
        assertThat(inHands + draft + state.getDiscardPile().size() + state.getUndealt().size()).isEqualTo(33);
        Set<String> ids = new HashSet<>();
        state.getPlayers().forEach(player -> {
            player.getHand().forEach(card -> assertThat(ids.add(card.instanceId())).isTrue());
            player.getRevealedCards().forEach(card -> assertThat(ids.add(card.instanceId())).isTrue());
        });
        state.getDiscardPile().forEach(card -> assertThat(ids.add(card.instanceId())).isTrue());
        state.getUndealt().forEach(card -> assertThat(ids.add(card.instanceId())).isTrue());
    }

    @Test
    void seededRandomIsDeterministic() {
        NobGameState a = NobTestSupport.fourPlayers(99);
        NobGameState b = NobTestSupport.fourPlayers(99);
        assertThat(a.getDraftHands().get("p1").stream().map(NobCardInstance::cardCode).toList())
                .isEqualTo(b.getDraftHands().get("p1").stream().map(NobCardInstance::cardCode).toList());
        assertThat(new SeededRandomSource(3).nextInt(10)).isEqualTo(new SeededRandomSource(3).nextInt(10));
    }

    @Test
    void duplicateCommandIsIdempotentAndStaleVersionIsRejected() {
        NobGameState state = NobTestSupport.fourPlayers(1);
        String cardId = state.getDraftHands().get("p1").getFirst().instanceId();
        var first = new com.partygameonline.game.nob.domain.NobAction(
                com.partygameonline.game.nob.domain.NobAction.DRAFT_PICK, "cmd-1", state.getVersion(), cardId, null, List.of(), null
        );
        NobTestSupport.apply(state, "p1", first);
        int version = state.getVersion();
        assertThat(NobTestSupport.validate(state, "p1", first).valid()).isTrue();
        assertThat(NobRulesEngineApply(state, first)).isEmpty();
        assertThat(state.getVersion()).isEqualTo(version);
        var stale = new com.partygameonline.game.nob.domain.NobAction(
                com.partygameonline.game.nob.domain.NobAction.DRAFT_PICK, "cmd-2", 1, cardId, null, List.of(), null
        );
        assertThat(NobTestSupport.validate(state, "p2", stale).errorCode()).isEqualTo("STALE_VERSION");
    }

    private static java.util.List<com.partygameonline.game.nob.domain.NobEvent> NobRulesEngineApply(
            NobGameState state,
            com.partygameonline.game.nob.domain.NobAction action
    ) {
        return com.partygameonline.game.nob.application.NobRulesEngine.apply(
                state, "p1", action, new SeededRandomSource(1)
        );
    }
}
