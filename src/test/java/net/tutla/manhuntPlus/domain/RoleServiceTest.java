package net.tutla.manhuntPlus.domain;

import net.tutla.manhuntPlus.application.RoleService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleServiceTest {
    @Test
    void samePlayerCannotBeHunterAndSpeedrunner() {
        GameState state = new GameState();
        RoleService roles = new RoleService(state);
        UUID playerId = UUID.randomUUID();

        assertTrue(roles.addSpeedrunner(playerId));
        assertFalse(roles.addHunter(playerId));
        assertTrue(roles.removeSpeedrunner(playerId));
        assertTrue(roles.addHunter(playerId));
        assertFalse(roles.addSpeedrunner(playerId));
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
