package net.tutla.manhuntPlus.bukkit.commands;

import net.tutla.manhuntPlus.application.Messages;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.generator.structure.Structure;
import org.bukkit.util.StructureSearchResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public final class RandomVillageCommand implements CommandExecutor {
    private final String adminPermission;
    private final Random random = new Random();

    public RandomVillageCommand(String adminPermission) {
        this.adminPermission = adminPermission;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        var player = CommandSupport.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!CommandSupport.checkPermission(player, adminPermission)) {
            return true;
        }

        List<Map.Entry<String, Structure>> villages = villageStructures();
        if (villages.isEmpty()) {
            player.sendMessage(Messages.error("No village structures found in registry."));
            return true;
        }
        Map.Entry<String, Structure> selected = villages.get(random.nextInt(villages.size()));
        StructureSearchResult nearest = player.getWorld().locateNearestStructure(player.getLocation(), selected.getValue(), 512, false);
        if (nearest == null) {
            player.sendMessage(Messages.error("Could not find " + selected.getKey().replace('_', ' ') + " nearby."));
            return true;
        }
        Location tp = safeVillageTeleport(player.getWorld(), nearest.getLocation());
        player.teleport(tp);
        String villageName = selected.getKey().replace("village_", "").toLowerCase(Locale.ROOT);
        player.sendMessage(Messages.ok("Teleported near nearest " + villageName + " village."));
        return true;
    }

    private List<Map.Entry<String, Structure>> villageStructures() {
        return Registry.STRUCTURE.stream()
                .map(structure -> {
                    NamespacedKey key = Registry.STRUCTURE.getKey(structure);
                    if (key == null || !"minecraft".equals(key.getNamespace()) || !key.getKey().startsWith("village_")) {
                        return null;
                    }
                    return Map.entry(key.getKey(), structure);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private Location safeVillageTeleport(World world, Location villageCenter) {
        int baseX = villageCenter.getBlockX();
        int baseZ = villageCenter.getBlockZ();
        for (int r = 0; r <= 8; r++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    int checkX = baseX + x;
                    int checkZ = baseZ + z;
                    int y = world.getHighestBlockYAt(checkX, checkZ);
                    Location ground = new Location(world, checkX + 0.5, y, checkZ + 0.5);
                    if (ground.getBlock().getType().isSolid()) {
                        return ground.add(0, 1, 0);
                    }
                }
            }
        }
        int fallbackY = world.getHighestBlockYAt(villageCenter) + 1;
        return new Location(world, villageCenter.getX() + 0.5, fallbackY, villageCenter.getZ() + 0.5);
    }
}
