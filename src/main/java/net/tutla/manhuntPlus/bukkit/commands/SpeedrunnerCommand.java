package net.tutla.manhuntPlus.bukkit.commands;

import net.tutla.manhuntPlus.application.Messages;
import net.tutla.manhuntPlus.application.RoleService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class SpeedrunnerCommand implements CommandExecutor, TabCompleter {
    private final RoleService roleService;
    private final String adminPermission;

    public SpeedrunnerCommand(RoleService roleService, String adminPermission) {
        this.roleService = roleService;
        this.adminPermission = adminPermission;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!CommandSupport.checkPermission(sender, adminPermission)) {
            return true;
        }
        if (args.length != 2 || (!args[0].equalsIgnoreCase("add") && !args[0].equalsIgnoreCase("remove"))) {
            sender.sendMessage(Messages.info("Usage: /speedrunner add|remove <player>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(Messages.error("Player not found or not online."));
            return true;
        }

        if (args[0].equalsIgnoreCase("add")) {
            if (!roleService.addSpeedrunner(target.getUniqueId())) {
                sender.sendMessage(Messages.error("Player is already a speedrunner."));
                return true;
            }
            Bukkit.broadcastMessage(Messages.ok(target.getName() + " is now a speedrunner!"));
            return true;
        }

        if (!roleService.removeSpeedrunner(target.getUniqueId())) {
            sender.sendMessage(Messages.error("Player is not a speedrunner."));
            return true;
        }
        Bukkit.broadcastMessage(Messages.ok(target.getName() + " is no longer a speedrunner!"));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return CommandSupport.tab(args[0], "add", "remove");
        }
        if (args.length == 2) {
            return CommandSupport.onlinePlayers(args[1]);
        }
        return CommandSupport.empty();
    }
}
