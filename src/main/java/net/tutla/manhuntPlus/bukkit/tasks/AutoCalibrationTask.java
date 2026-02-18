package net.tutla.manhuntPlus.bukkit.tasks;

import net.tutla.manhuntPlus.application.CompassService;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class AutoCalibrationTask {
    private final JavaPlugin plugin;
    private final CompassService compassService;
    private BukkitTask task;

    public AutoCalibrationTask(JavaPlugin plugin, CompassService compassService) {
        this.plugin = plugin;
        this.compassService = compassService;
    }

    public void start(int intervalSeconds) {
        stop();
        long period = Math.max(1L, intervalSeconds) * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Bukkit.getOnlinePlayers().forEach(player -> {
                for (ItemStack item : player.getInventory().getContents()) {
                    compassService.refreshItem(item, player);
                }
            });
        }, 0L, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
