package net.tutla.manhuntPlus.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameStateTest {
    @Test
    void clearRoundStateResetsMutableMatchState() {
        GameState state = new GameState();
        UUID speedrunner = UUID.randomUUID();
        UUID hunter = UUID.randomUUID();

        state.setPhase(MatchPhase.RUNNING);
        state.setWaitingForStart(true);
        state.setHuntersFrozen(true);
        state.setHuntersFreezeSecondsRemaining(12);
        state.getSpeedrunners().add(speedrunner);
        state.getHunters().add(hunter);
        state.getActiveSpeedrunners().add(speedrunner);
        state.getEliminatedSpeedrunners().add(speedrunner);
        state.getTrackedCompasses().put(UUID.randomUUID(), speedrunner);

        state.clearRoundState();

        assertFalse(state.isWaitingForStart());
        assertFalse(state.isHuntersFrozen());
        assertEquals(0, state.getHuntersFreezeSecondsRemaining());
        assertTrue(state.getActiveSpeedrunners().isEmpty());
        assertTrue(state.getEliminatedSpeedrunners().isEmpty());
        assertTrue(state.getTrackedCompasses().isEmpty());
        assertTrue(state.getSpeedrunners().contains(speedrunner));
        assertTrue(state.getHunters().contains(hunter));
    }
}
