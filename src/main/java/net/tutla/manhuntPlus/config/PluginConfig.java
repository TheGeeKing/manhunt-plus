package net.tutla.manhuntPlus.config;

import net.tutla.manhuntPlus.domain.MatchSettings;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PluginConfig {
    private final String adminPermission;
    private final String playPermission;
    private final MatchSettings settings;

    private PluginConfig(String adminPermission, String playPermission, MatchSettings settings) {
        this.adminPermission = adminPermission;
        this.playPermission = playPermission;
        this.settings = settings;
    }

    public static PluginConfig load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        int autoCalibrationInterval = Math.max(1, config.getInt("auto-calibration-interval", 10));
        int broadcastEvery = Math.max(1, config.getInt("broadcast-time-every", 1800));
        double surroundRadius = config.getDouble("surround-radius", 3.0d);
        if (surroundRadius <= 0) {
            surroundRadius = 3.0d;
        }

        MatchSettings settings = new MatchSettings(
                0,
                0,
                config.getBoolean("default-team-compass", false),
                config.getBoolean("auto-calibration", true),
                autoCalibrationInterval,
                config.getBoolean("broadcast-time", true),
                broadcastEvery,
                surroundRadius
        );

        String adminPermission = config.getString("permissions.admin", "manhuntplus.admin");
        String playPermission = config.getString("permissions.play", "manhuntplus.play");
        return new PluginConfig(adminPermission, playPermission, settings);
    }

    public String getAdminPermission() {
        return adminPermission;
    }

    public String getPlayPermission() {
        return playPermission;
    }

    public MatchSettings getSettings() {
        return settings;
    }
}
