package net.tutla.manhuntPlus.bukkit.commands;

import net.tutla.manhuntPlus.application.Messages;
import net.tutla.manhuntPlus.application.RoleService;
import net.tutla.manhuntPlus.domain.MatchSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public final class SurroundCommand implements CommandExecutor, TabCompleter {
    private final RoleService roleService;
    private final MatchSettings settings;
    private final String adminPermission;

    public SurroundCommand(RoleService roleService, MatchSettings settings, String adminPermission) {
        this.roleService = roleService;
        this.settings = settings;
        this.adminPermission = adminPermission;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player player = CommandSupport.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!CommandSupport.checkPermission(player, adminPermission)) {
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(Messages.info("Usage: /surround <speedrunner>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(Messages.error("Player not found or not online."));
            return true;
        }
        if (!roleService.speedrunners().contains(target.getUniqueId())) {
            player.sendMessage(Messages.error("Player is not a speedrunner."));
            return true;
        }
        int count = roleService.hunters().size();
        if (count == 0) {
            player.sendMessage(Messages.error("No hunters set."));
            return true;
        }

        Location center = target.getLocation();
        double radius = settings.getSurroundRadius();
        int idx = 0;
        for (UUID hunterId : roleService.hunters()) {
            Player hunter = Bukkit.getPlayer(hunterId);
            if (hunter == null || !hunter.isOnline()) {
                continue;
            }
            double angle = 2 * Math.PI * idx++ / count;
            double xOffset = radius * Math.cos(angle);
            double zOffset = radius * Math.sin(angle);
            Location tele = center.clone().add(xOffset, 0, zOffset);
            tele.setDirection(center.toVector().subtract(tele.toVector()));
            hunter.teleport(tele);
        }
        player.sendMessage(Messages.ok("Surrounded " + target.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return CommandSupport.onlinePlayers(args[0]);
        }
        return List.of();
    }
}
