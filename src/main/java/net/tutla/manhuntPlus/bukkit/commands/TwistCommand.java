package net.tutla.manhuntPlus.bukkit.commands;

import net.tutla.manhuntPlus.application.Messages;
import net.tutla.manhuntPlus.application.TwistService;
import net.tutla.manhuntPlus.domain.TwistType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class TwistCommand implements CommandExecutor, TabCompleter {
    private final TwistService twistService;
    private final String adminPermission;

    public TwistCommand(TwistService twistService, String adminPermission) {
        this.twistService = twistService;
        this.adminPermission = adminPermission;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!CommandSupport.checkPermission(sender, adminPermission)) {
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(Messages.info("Current twist: " + twistService.getTwist().name()));
            sender.sendMessage(Messages.info("Usage: /twist <twist>"));
            return true;
        }
        try {
            TwistType twist = TwistType.valueOf(args[0].toUpperCase(Locale.ROOT));
            twistService.setTwist(twist);
            sender.sendMessage(Messages.ok("Twist set to: " + twist.name()));
        } catch (IllegalArgumentException ignored) {
            sender.sendMessage(Messages.error("Unknown twist: " + args[0]));
            sender.sendMessage(Messages.info("Available: " + Arrays.toString(TwistType.values())));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.stream(TwistType.values())
                    .map(Enum::name)
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
