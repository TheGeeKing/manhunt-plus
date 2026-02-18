package net.tutla.manhuntPlus.bukkit.commands;

import net.tutla.manhuntPlus.application.CompassService;
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

public final class CompassCommand implements CommandExecutor, TabCompleter {
    private final CompassService compassService;
    private final RoleService roleService;
    private final String playPermission;

    public CompassCommand(CompassService compassService, RoleService roleService, String playPermission) {
        this.compassService = compassService;
        this.roleService = roleService;
        this.playPermission = playPermission;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player player = CommandSupport.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!CommandSupport.checkPermission(player, playPermission)) {
            return true;
        }
        if (roleService.speedrunners().isEmpty()) {
            player.sendMessage(Messages.error("No speedrunner set."));
            return true;
        }

        Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayer(args[0]);
        } else {
            target = roleService.firstOnlineSpeedrunner();
        }
        if (target == null) {
            player.sendMessage(Messages.error("Player not found or not online."));
            return true;
        }
        compassService.giveTrackingCompass(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(Messages.ok("Tracking compass set to " + target.getName()));
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
