package net.tutla.manhuntPlus.application;

import net.tutla.manhuntPlus.domain.GameState;
import net.tutla.manhuntPlus.domain.MatchSettings;
import net.tutla.manhuntPlus.domain.MatchPhase;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public final class FreezeService {
    private final JavaPlugin plugin;
    private final GameState state;
    private final MatchSettings settings;
    private BukkitTask freezeTask;

    public FreezeService(JavaPlugin plugin, GameState state, MatchSettings settings) {
        this.plugin = plugin;
        this.state = state;
        this.settings = settings;
    }

    public void startHunterFreeze() {
        stopHunterFreeze();
        int countdown = settings.getHunterReleaseSeconds();
        if (countdown <= 0) {
            return;
        }
        state.setHuntersFrozen(true);
        state.setHuntersFreezeSecondsRemaining(countdown);
        freezeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 20L);
    }

    public void stopHunterFreeze() {
        state.setHuntersFrozen(false);
        state.setHuntersFreezeSecondsRemaining(0);
        state.getFreezeRespawnExemptHunters().clear();
        if (freezeTask != null) {
            freezeTask.cancel();
            freezeTask = null;
        }
        clearProgressBars();
    }

    public boolean isFrozen(UUID playerId) {
        return state.isHuntersFrozen()
                && state.getHunters().contains(playerId)
                && !state.getFreezeRespawnExemptHunters().contains(playerId);
    }

    public void markRespawnExemptHunter(UUID hunterId) {
        if (hunterId == null || !state.isHuntersFrozen()) {
            return;
        }
        state.getFreezeRespawnExemptHunters().add(hunterId);
        Player player = Bukkit.getPlayer(hunterId);
        if (player != null) {
            player.setLevel(0);
            player.setExp(0f);
        }
    }

    private void tick() {
        if (state.getPhase() != MatchPhase.RUNNING) {
            stopHunterFreeze();
            return;
        }
        int remaining = state.getHuntersFreezeSecondsRemaining();
        int total = settings.getHunterReleaseSeconds();
        float progress = total <= 0 ? 0f : Math.max(0f, Math.min(1f, (float) remaining / total));

        for (UUID id : state.getHunters()) {
            Player hunter = Bukkit.getPlayer(id);
            if (hunter == null || !hunter.isOnline() || state.getFreezeRespawnExemptHunters().contains(id)) {
                continue;
            }
            hunter.setLevel(remaining);
            hunter.setExp(progress);
        }

        for (UUID id : state.getSpeedrunners()) {
            Player speedrunner = Bukkit.getPlayer(id);
            if (speedrunner == null || !speedrunner.isOnline()) {
                continue;
            }
            speedrunner.setLevel(remaining);
            speedrunner.setExp(progress);
        }

        if (remaining <= 0) {
            Bukkit.broadcastMessage(Messages.ok("Hunters released!"));
            stopHunterFreeze();
            return;
        }

        state.decrementHuntersFreezeSecondsRemaining();
    }

    private void clearProgressBars() {
        for (UUID id : state.getHunters()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.setLevel(0);
                player.setExp(0f);
            }
        }
        for (UUID id : state.getSpeedrunners()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.setLevel(0);
                player.setExp(0f);
            }
        }
    }
}
