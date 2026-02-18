package net.tutla.manhuntPlus.application;

import net.tutla.manhuntPlus.domain.GameState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public final class RoleService {
    private final GameState state;

    public RoleService(GameState state) {
        this.state = state;
    }

    public boolean addSpeedrunner(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        if (state.getSpeedrunners().contains(playerId)) {
            return false;
        }
        removeHunter(playerId);
        return state.getSpeedrunners().add(playerId);
    }

    public boolean removeSpeedrunner(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        state.getActiveSpeedrunners().remove(playerId);
        state.getEliminatedSpeedrunners().remove(playerId);
        state.getEliminatedSpeedrunnerDeathLocations().remove(playerId);
        return state.getSpeedrunners().remove(playerId);
    }

    public boolean addHunter(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        if (state.getHunters().contains(playerId)) {
            return false;
        }
        removeSpeedrunner(playerId);
        return state.getHunters().add(playerId);
    }

    public boolean removeHunter(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        state.getFreezeRespawnExemptHunters().remove(playerId);
        state.getTeamCompassViewers().remove(playerId);
        return state.getHunters().remove(playerId);
    }

    public Set<UUID> speedrunners() {
        return Collections.unmodifiableSet(state.getSpeedrunners());
    }

    public Set<UUID> hunters() {
        return Collections.unmodifiableSet(state.getHunters());
    }

    public Set<UUID> activeSpeedrunners() {
        return Collections.unmodifiableSet(state.getActiveSpeedrunners());
    }

    public Player firstOnlineSpeedrunner() {
        return state.getSpeedrunners().stream()
                .map(Bukkit::getPlayer)
                .filter(player -> player != null && player.isOnline())
                .min(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .orElse(null);
    }

    public Player nextOnlineActiveSpeedrunner(UUID excluded) {
        return state.getActiveSpeedrunners().stream()
                .filter(id -> !id.equals(excluded))
                .map(Bukkit::getPlayer)
                .filter(player -> player != null && player.isOnline())
                .findFirst()
                .orElse(null);
    }
}
