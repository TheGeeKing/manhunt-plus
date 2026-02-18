package net.tutla.manhuntPlus.bukkit.listeners;

import net.tutla.manhuntPlus.application.CompassService;
import net.tutla.manhuntPlus.application.FreezeService;
import net.tutla.manhuntPlus.application.Messages;
import net.tutla.manhuntPlus.application.RoleService;
import net.tutla.manhuntPlus.domain.GameState;
import net.tutla.manhuntPlus.domain.MatchPhase;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class PlayerRespawnListener implements Listener {
    private final JavaPlugin plugin;
    private final GameState state;
    private final RoleService roleService;
    private final CompassService compassService;
    private final FreezeService freezeService;

    public PlayerRespawnListener(
            JavaPlugin plugin,
            GameState state,
            RoleService roleService,
            CompassService compassService,
            FreezeService freezeService
    ) {
        this.plugin = plugin;
        this.state = state;
        this.roleService = roleService;
        this.compassService = compassService;
        this.freezeService = freezeService;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (state.getPhase() != MatchPhase.RUNNING) {
            return;
        }
        if (state.getHunters().contains(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                compassService.giveDefaultCompassToHunter(player.getUniqueId());
                freezeService.markRespawnExemptHunter(player.getUniqueId());
            }, 1L);
            return;
        }

        if (!state.getSpeedrunners().contains(player.getUniqueId()) || !state.getEliminatedSpeedrunners().contains(player.getUniqueId())) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.setGameMode(GameMode.SPECTATOR);
            Location deathLoc = state.getEliminatedSpeedrunnerDeathLocations().get(player.getUniqueId());
            if (deathLoc != null) {
                player.teleport(deathLoc);
            }
            startSpectateCountdown(player);
        }, 1L);
    }

    private void startSpectateCountdown(Player player) {
        new BukkitRunnable() {
            int secondsLeft = 10;

            @Override
            public void run() {
                if (!player.isOnline() || state.getPhase() != MatchPhase.RUNNING) {
                    cancel();
                    return;
                }
                if (secondsLeft > 0) {
                    player.sendTitle("§7You were eliminated", "§eTeleporting in " + secondsLeft + "s", 0, 25, 0);
                    secondsLeft--;
                    return;
                }
                Player target = roleService.nextOnlineActiveSpeedrunner(player.getUniqueId());
                if (target != null) {
                    player.teleport(target.getLocation());
                    player.sendTitle("§aTeleported", "§7Now spectating " + target.getName(), 5, 35, 10);
                } else {
                    player.sendMessage(Messages.error("No active speedrunner is online to spectate."));
                }
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
}
