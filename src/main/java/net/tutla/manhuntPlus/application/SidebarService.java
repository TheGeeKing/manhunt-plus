package net.tutla.manhuntPlus.application;

import net.tutla.manhuntPlus.domain.GameState;
import net.tutla.manhuntPlus.domain.MatchPhase;
import net.tutla.manhuntPlus.domain.MatchSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.UUID;

public final class SidebarService {
    private final JavaPlugin plugin;
    private final GameState state;
    private final MatchSettings settings;
    private final CompassService compassService;
    private BukkitTask task;

    public SidebarService(JavaPlugin plugin, GameState state, MatchSettings settings, CompassService compassService) {
        this.plugin = plugin;
        this.state = state;
        this.settings = settings;
        this.compassService = compassService;
    }

    public void enableTeamCompass(boolean enabled) {
        settings.setTeamCompassEnabled(enabled);
        if (!enabled) {
            stop();
        } else if (state.getPhase() == MatchPhase.RUNNING) {
            start();
        }
    }

    public void start() {
        stop();
        if (!settings.isTeamCompassEnabled()) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state.getPhase() != MatchPhase.RUNNING || !settings.isTeamCompassEnabled()) {
                stop();
                return;
            }
            for (UUID id : state.getHunters()) {
                updateHunterSidebar(id);
            }
        }, 0L, 20L);
    }

    public void updateHunterSidebar(UUID viewerId) {
        Player viewer = Bukkit.getPlayer(viewerId);
        if (viewer == null || !viewer.isOnline() || !state.getHunters().contains(viewerId)) {
            return;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("teamcompass", "dummy", "§bHunter Team Compass");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int score = Math.min(15, state.getHunters().size() + 2);
        objective.getScore("§7Name | Dir | Dist").setScore(score--);

        for (UUID hunterId : state.getHunters()) {
            Player hunter = Bukkit.getPlayer(hunterId);
            String name = hunter == null ? "Offline" : hunter.getName();
            if (name.length() > 10) {
                name = name.substring(0, 10);
            }

            String direction;
            String distance;
            if (hunter == null || !hunter.isOnline()) {
                direction = "--";
                distance = "OFF";
            } else {
                Location tracked = compassService.resolveTrackingLocation(viewer, hunter);
                if (tracked != null && tracked.getWorld() != null && tracked.getWorld().equals(viewer.getWorld())) {
                    direction = direction(viewer.getLocation(), tracked);
                    distance = Integer.toString((int) Math.round(viewer.getLocation().distance(tracked)));
                } else {
                    direction = "DIM";
                    distance = hunter.getWorld().getEnvironment().name().substring(0, 1);
                }
            }

            String row = "§f" + name + " §8| §e" + direction + " §8| §a" + distance + "§r" + (char) ('a' + (score % 26));
            if (row.length() > 40) {
                row = row.substring(0, 40);
            }
            objective.getScore(row).setScore(Math.max(score--, 1));
            if (score <= 0) {
                break;
            }
        }
        viewer.setScoreboard(scoreboard);
        state.getTeamCompassViewers().add(viewerId);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        clearAll();
    }

    public void clearAll() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard main = manager.getMainScoreboard();
        for (UUID id : state.getTeamCompassViewers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.setScoreboard(main);
            }
        }
        state.getTeamCompassViewers().clear();
    }

    private String direction(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        if (Math.abs(dx) < 0.01d && Math.abs(dz) < 0.01d) {
            return "N";
        }
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        if (angle < 0d) {
            angle += 360d;
        }
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int idx = (int) Math.round(angle / 45d) % 8;
        return dirs[idx];
    }
}
