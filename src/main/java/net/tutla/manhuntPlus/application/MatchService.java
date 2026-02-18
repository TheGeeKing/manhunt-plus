package net.tutla.manhuntPlus.application;

import net.tutla.manhuntPlus.domain.GameState;
import net.tutla.manhuntPlus.domain.MatchPhase;
import net.tutla.manhuntPlus.domain.MatchSettings;
import net.tutla.manhuntPlus.domain.StopReason;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.UUID;

public final class MatchService {
    private final JavaPlugin plugin;
    private final GameState state;
    private final MatchSettings settings;
    private final RoleService roleService;
    private final CompassService compassService;
    private final FreezeService freezeService;
    private final SidebarService sidebarService;
    private BukkitTask timerTask;

    public MatchService(
            JavaPlugin plugin,
            GameState state,
            MatchSettings settings,
            RoleService roleService,
            CompassService compassService,
            FreezeService freezeService,
            SidebarService sidebarService
    ) {
        this.plugin = plugin;
        this.state = state;
        this.settings = settings;
        this.roleService = roleService;
        this.compassService = compassService;
        this.freezeService = freezeService;
        this.sidebarService = sidebarService;
    }

    public boolean start(Location startLocation) {
        state.setWaitingForStart(false);
        if (state.getPhase() == MatchPhase.RUNNING) {
            return false;
        }
        if (state.getSpeedrunners().isEmpty() || state.getHunters().isEmpty()) {
            return false;
        }

        if (startLocation != null && startLocation.getWorld() != null) {
            startLocation.getWorld().setSpawnLocation(startLocation);
        }
        prepareAllPlayersForStart();

        state.clearRoundState();
        state.setPhase(MatchPhase.RUNNING);
        state.getActiveSpeedrunners().addAll(state.getSpeedrunners());
        startTimer();

        for (UUID hunterId : state.getHunters()) {
            compassService.giveDefaultCompassToHunter(hunterId);
        }
        freezeService.startHunterFreeze();
        if (settings.isTeamCompassEnabled()) {
            sidebarService.start();
        }

        Bukkit.broadcastMessage(Messages.ok("Manhunt started!"));
        return true;
    }

    public boolean stop(StopReason reason) {
        if (state.getPhase() != MatchPhase.RUNNING && state.getPhase() != MatchPhase.PREPARED) {
            return false;
        }

        stopTimer();
        freezeService.stopHunterFreeze();
        sidebarService.stop();
        state.clearRoundState();
        state.setPhase(MatchPhase.ENDED);
        state.setPhase(MatchPhase.IDLE);

        String msg = switch (reason) {
            case MANUAL -> "Manhunt stopped.";
            case ALL_SPEEDRUNNERS_ELIMINATED -> "Hunter(s) have won the Manhunt!";
            case SPEEDRUNNERS_WIN -> "Speedrunner(s) have won the Manhunt!";
            case TIME_LIMIT -> "Time is up! Speedrunners failed to win in time.";
        };
        Bukkit.broadcastMessage(Messages.info(msg));
        return true;
    }

    public void prepare() {
        state.setWaitingForStart(true);
        state.setPhase(MatchPhase.PREPARED);
        Bukkit.broadcastMessage(Messages.info("Waiting for first speedrunner hit to start."));
    }

    public void onSpeedrunnerEliminated(UUID playerId, Location deathLocation) {
        if (playerId == null || !state.getActiveSpeedrunners().contains(playerId)) {
            return;
        }
        state.getActiveSpeedrunners().remove(playerId);
        state.getEliminatedSpeedrunners().add(playerId);
        if (deathLocation != null) {
            state.getEliminatedSpeedrunnerDeathLocations().put(playerId, deathLocation.clone());
            state.setLastEliminatedSpeedrunnerLocation(deathLocation);
        }

        Player player = Bukkit.getPlayer(playerId);
        String name = player == null ? playerId.toString() : player.getName();
        Bukkit.broadcastMessage(Messages.info("Speedrunner " + name + " has been eliminated."));

        if (state.getActiveSpeedrunners().isEmpty()) {
            stop(StopReason.ALL_SPEEDRUNNERS_ELIMINATED);
        } else {
            compassService.refreshAll();
        }
    }

    public boolean isRunning() {
        return state.getPhase() == MatchPhase.RUNNING;
    }

    public boolean isWaitingForStart() {
        return state.isWaitingForStart();
    }

    public void beginOnPreparedHit(Location startLocation) {
        if (state.isWaitingForStart()) {
            start(startLocation);
        }
    }

    public int elapsedSeconds() {
        return state.getElapsedSeconds();
    }

    private void startTimer() {
        stopTimer();
        state.resetElapsedSeconds();
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            state.incrementElapsedSeconds();
            int elapsed = state.getElapsedSeconds();

            if (settings.isBroadcastTime() && elapsed % settings.getBroadcastTimeEverySeconds() == 0) {
                Bukkit.broadcastMessage(Messages.info("Manhunt timer: " + (elapsed / 60) + " minute(s)"));
            }

            if (settings.getMaxDurationMinutes() > 0 && elapsed >= settings.getMaxDurationMinutes() * 60) {
                stop(StopReason.TIME_LIMIT);
            }
        }, 0L, 20L);
    }

    private void stopTimer() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    private void prepareAllPlayersForStart() {
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.LOCATOR_BAR, false);
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.getInventory().clear();
            online.getInventory().setArmorContents(null);
            online.getInventory().setExtraContents(null);
            online.getInventory().setItemInOffHand(null);
            online.updateInventory();

            if (online.getAttribute(Attribute.MAX_HEALTH) != null) {
                online.setHealth(online.getAttribute(Attribute.MAX_HEALTH).getValue());
            }
            online.setFoodLevel(20);
            online.setSaturation(20f);
            online.setFireTicks(0);
            online.setFallDistance(0f);
            online.setGameMode(roleService.hunters().contains(online.getUniqueId()) || roleService.speedrunners().contains(online.getUniqueId())
                    ? GameMode.SURVIVAL : GameMode.SPECTATOR);
            online.removePotionEffect(PotionEffectType.SATURATION);
            online.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 200, 0, false, false, false));
            clearAdvancements(online);
        }
    }

    private void clearAdvancements(Player player) {
        Iterator<Advancement> advancements = Bukkit.advancementIterator();
        while (advancements.hasNext()) {
            Advancement advancement = advancements.next();
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            for (String criteria : progress.getAwardedCriteria()) {
                progress.revokeCriteria(criteria);
            }
        }
    }
}
