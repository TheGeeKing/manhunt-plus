package net.tutla.manhuntPlus.bukkit.commands;

import net.tutla.manhuntPlus.application.MatchService;
import net.tutla.manhuntPlus.application.Messages;
import net.tutla.manhuntPlus.application.RoleService;
import net.tutla.manhuntPlus.application.SidebarService;
import net.tutla.manhuntPlus.domain.GameState;
import net.tutla.manhuntPlus.domain.MatchSettings;
import net.tutla.manhuntPlus.domain.StopReason;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public final class ManhuntCommand implements CommandExecutor, TabCompleter {
    private final MatchService matchService;
    private final RoleService roleService;
    private final SidebarService sidebarService;
    private final MatchSettings settings;
    private final GameState state;
    private final String adminPermission;

    public ManhuntCommand(
            MatchService matchService,
            RoleService roleService,
            SidebarService sidebarService,
            MatchSettings settings,
            GameState state,
            String adminPermission
    ) {
        this.matchService = matchService;
        this.roleService = roleService;
        this.sidebarService = sidebarService;
        this.settings = settings;
        this.state = state;
        this.adminPermission = adminPermission;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player player = CommandSupport.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!CommandSupport.checkPermission(sender, adminPermission)) {
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Messages.info("Usage: /manhunt <start|stop|countdown|startcountdown|teamcompass|prepare|list|help>"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (!matchService.start(player.getLocation())) {
                    player.sendMessage(Messages.error("Cannot start manhunt. Ensure it is not running and teams are configured."));
                }
            }
            case "stop" -> {
                if (!matchService.stop(StopReason.MANUAL)) {
                    player.sendMessage(Messages.error("Manhunt is not running."));
                }
            }
            case "countdown" -> handleCountdown(player, args);
            case "startcountdown" -> handleStartCountdown(player, args);
            case "teamcompass" -> handleTeamCompass(player, args);
            case "prepare" -> matchService.prepare();
            case "list" -> listTeams(player);
            case "help" -> {
                player.sendMessage(Messages.info("/speedrunner add|remove <player>"));
                player.sendMessage(Messages.info("/hunter add|remove <player>"));
                player.sendMessage(Messages.info("/manhunt start|stop|prepare|list"));
                player.sendMessage(Messages.info("/manhunt countdown <minutes>"));
                player.sendMessage(Messages.info("/manhunt startcountdown <seconds>"));
                player.sendMessage(Messages.info("/manhunt teamcompass <on|off>"));
            }
            default -> player.sendMessage(Messages.error("Unknown subcommand."));
        }
        return true;
    }

    private void handleCountdown(Player player, String[] args) {
        if (args.length == 2) {
            try {
                int minutes = Integer.parseInt(args[1]);
                if (minutes < 0) {
                    player.sendMessage(Messages.error("Countdown must be 0 or higher."));
                    return;
                }
                settings.setMaxDurationMinutes(minutes);
                player.sendMessage(minutes == 0
                        ? Messages.info("Countdown disabled.")
                        : Messages.ok("Countdown set to " + minutes + " minute(s)."));
            } catch (NumberFormatException e) {
                player.sendMessage(Messages.error("Invalid number."));
            }
            return;
        }
        player.sendMessage(Messages.info("Current countdown: " + settings.getMaxDurationMinutes() + " minute(s)."));
        player.sendMessage(Messages.info("Usage: /manhunt countdown <minutes>"));
    }

    private void handleStartCountdown(Player player, String[] args) {
        if (args.length == 2) {
            try {
                int seconds = Integer.parseInt(args[1]);
                if (seconds < 0) {
                    player.sendMessage(Messages.error("Start countdown must be 0 or higher."));
                    return;
                }
                settings.setHunterReleaseSeconds(seconds);
                player.sendMessage(seconds == 0
                        ? Messages.info("Hunter start countdown disabled.")
                        : Messages.ok("Hunter start countdown set to " + seconds + " second(s)."));
            } catch (NumberFormatException e) {
                player.sendMessage(Messages.error("Invalid number."));
            }
            return;
        }
        player.sendMessage(Messages.info("Current hunter start countdown: " + settings.getHunterReleaseSeconds() + " second(s)."));
        player.sendMessage(Messages.info("Usage: /manhunt startcountdown <seconds>"));
    }

    private void handleTeamCompass(Player player, String[] args) {
        if (args.length == 2) {
            String value = args[1].toLowerCase();
            if (value.equals("on") || value.equals("true")) {
                sidebarService.enableTeamCompass(true);
                player.sendMessage(Messages.ok("Team compass enabled."));
                return;
            }
            if (value.equals("off") || value.equals("false")) {
                sidebarService.enableTeamCompass(false);
                player.sendMessage(Messages.info("Team compass disabled."));
                return;
            }
            player.sendMessage(Messages.error("Usage: /manhunt teamcompass <on|off>"));
            return;
        }
        player.sendMessage(Messages.info("Team compass is " + (settings.isTeamCompassEnabled() ? "enabled" : "disabled") + "."));
    }

    private void listTeams(Player player) {
        player.sendMessage(Messages.info("Speedrunners:"));
        for (UUID id : roleService.speedrunners()) {
            Player p = Bukkit.getPlayer(id);
            player.sendMessage(" - " + (p == null ? id.toString() : p.getName()));
        }
        player.sendMessage(Messages.info("Hunters:"));
        for (UUID id : roleService.hunters()) {
            Player p = Bukkit.getPlayer(id);
            player.sendMessage(" - " + (p == null ? id.toString() : p.getName()));
        }
        player.sendMessage(Messages.info("Active speedrunners:"));
        for (UUID id : state.getActiveSpeedrunners()) {
            Player p = Bukkit.getPlayer(id);
            player.sendMessage(" - " + (p == null ? id.toString() : p.getName()));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return CommandSupport.streamTab(args[0], Stream.of("help", "start", "stop", "countdown", "startcountdown", "teamcompass", "prepare", "list"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("teamcompass")) {
            return List.of("on", "off");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("countdown") || args[0].equalsIgnoreCase("startcountdown"))) {
            return List.of("0", "5", "10", "15", "30", "60");
        }
        return List.of();
    }
}
