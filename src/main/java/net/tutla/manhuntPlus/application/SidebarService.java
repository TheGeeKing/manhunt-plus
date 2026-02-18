package net.tutla.manhuntPlus.application;

import net.tutla.manhuntPlus.domain.GameState;
import net.tutla.manhuntPlus.domain.MatchPhase;
import net.tutla.manhuntPlus.domain.MatchSettings;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SidebarService {
    private static final ChatColor[] NONCE_COLORS = {
            ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA,
            ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.GRAY,
            ChatColor.DARK_GRAY, ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA,
            ChatColor.RED, ChatColor.LIGHT_PURPLE, ChatColor.YELLOW, ChatColor.WHITE
    };

    private final JavaPlugin plugin;
    private final GameState state;
    private final MatchSettings settings;
    private final CompassService compassService;
    private BukkitTask task;
    private BukkitTask pregameTask;
    private final Set<UUID> pregameSidebarViewers = new HashSet<>();

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

    public void startPregameSidebar() {
        if (pregameTask != null) {
            return;
        }
        pregameTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state.getPhase() == MatchPhase.RUNNING) {
                clearPregameSidebars();
                return;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                updatePregameSidebar(player);
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
            row = truncateColored(row, 40);
            objective.getScore(row).setScore(Math.max(score--, 1));
            if (score <= 0) {
                break;
            }
        }
        viewer.setScoreboard(scoreboard);
        state.getTeamCompassViewers().add(viewerId);
    }

    public void updatePregameSidebar(Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }

        List<String> speedrunners = new ArrayList<>();
        List<String> hunters = new ArrayList<>();
        List<String> unassigned = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline()) {
                continue;
            }
            UUID id = player.getUniqueId();
            if (state.getSpeedrunners().contains(id)) {
                speedrunners.add(player.getName());
            } else if (state.getHunters().contains(id)) {
                hunters.add(player.getName());
            } else {
                unassigned.add(player.getName());
            }
        }
        speedrunners.sort(String.CASE_INSENSITIVE_ORDER);
        hunters.sort(String.CASE_INSENSITIVE_ORDER);
        unassigned.sort(String.CASE_INSENSITIVE_ORDER);

        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("teamsetup", "dummy", "§6Team Setup");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> rows = new ArrayList<>();
        appendSectionRows(rows, "§aSpeedrunners", speedrunners);
        appendSectionRows(rows, "§cHunters", hunters);
        appendSectionRows(rows, "§eUnassigned", unassigned);

        int score = Math.min(rows.size(), 15);
        int nonce = 0;
        for (int i = 0; i < score; i++) {
            String row = withNonce(rows.get(i), nonce++);
            objective.getScore(row).setScore(score - i);
        }
        viewer.setScoreboard(scoreboard);
        pregameSidebarViewers.add(viewer.getUniqueId());
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        clearTeamCompassSidebars();
    }

    public void shutdown() {
        stop();
        if (pregameTask != null) {
            pregameTask.cancel();
            pregameTask = null;
        }
        clearPregameSidebars();
    }

    public void clearTeamCompassSidebars() {
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

    public void clearPregameSidebars() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard main = manager.getMainScoreboard();
        for (UUID id : pregameSidebarViewers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.setScoreboard(main);
            }
        }
        pregameSidebarViewers.clear();
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

    private String truncateColored(String input, int maxLen) {
        StringBuilder result = new StringBuilder();
        int visible = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '§' && i + 1 < input.length()) {
                result.append(c).append(input.charAt(++i));
                continue;
            }
            if (visible >= maxLen) {
                break;
            }
            result.append(c);
            visible++;
        }
        return result.toString();
    }

    private void appendSectionRows(List<String> rows, String header, List<String> players) {
        if (rows.size() >= 15) {
            return;
        }
        rows.add(header);
        if (rows.size() >= 15) {
            return;
        }
        if (players.isEmpty()) {
            rows.add("§8- none");
            return;
        }
        for (String player : players) {
            if (rows.size() >= 15) {
                break;
            }
            rows.add("§f- " + player);
        }
    }

    private String withNonce(String row, int nonce) {
        ChatColor first = NONCE_COLORS[nonce % NONCE_COLORS.length];
        ChatColor second = NONCE_COLORS[(nonce / NONCE_COLORS.length) % NONCE_COLORS.length];
        return truncateColored(row, 40) + first + second;
    }
}
