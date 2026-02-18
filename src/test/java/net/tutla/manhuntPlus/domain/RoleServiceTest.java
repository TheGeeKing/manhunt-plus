package net.tutla.manhuntPlus.domain;

import net.tutla.manhuntPlus.application.RoleService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleServiceTest {
    @Test
    void addingRoleSwitchesPlayerFromOtherRole() {
        GameState state = new GameState();
        RoleService roles = new RoleService(state);
        UUID playerId = UUID.randomUUID();

        assertTrue(roles.addSpeedrunner(playerId));
        assertFalse(roles.hunters().contains(playerId));

        assertTrue(roles.addHunter(playerId));
        assertFalse(roles.speedrunners().contains(playerId));
        assertTrue(roles.hunters().contains(playerId));

        assertTrue(roles.addSpeedrunner(playerId));
        assertTrue(roles.speedrunners().contains(playerId));
        assertFalse(roles.hunters().contains(playerId));
    }

    @Test
    void exposedRoleSetsAreUnmodifiable() {
        GameState state = new GameState();
        RoleService roles = new RoleService(state);
        UUID playerId = UUID.randomUUID();

        assertThrows(UnsupportedOperationException.class, () -> roles.speedrunners().add(playerId));
        assertThrows(UnsupportedOperationException.class, () -> roles.hunters().add(playerId));
        assertThrows(UnsupportedOperationException.class, () -> roles.activeSpeedrunners().add(playerId));
    }
}
