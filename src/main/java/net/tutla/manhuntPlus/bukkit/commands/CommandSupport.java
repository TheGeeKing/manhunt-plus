package net.tutla.manhuntPlus.bukkit.commands;

import net.tutla.manhuntPlus.application.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

final class CommandSupport {
    private CommandSupport() {
    }

    static Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(Messages.error("Only players can run this command."));
        return null;
    }

    static boolean checkPermission(CommandSender sender, String permission) {
        if (permission == null || permission.isBlank() || sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage(Messages.error("You do not have permission."));
        return false;
    }

    static List<String> tab(String input, String... values) {
        String lower = input.toLowerCase(Locale.ROOT);
        return Arrays.stream(values).filter(s -> s.startsWith(lower)).toList();
    }

    static List<String> onlinePlayers(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }

    static List<String> empty() {
        return List.of();
    }

    static List<String> streamTab(String input, Stream<String> values) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.filter(s -> s.startsWith(lower)).toList();
    }
}
