package net.tutla.manhuntPlus.bootstrap;

import net.tutla.manhuntPlus.application.CompassService;
import net.tutla.manhuntPlus.application.FreezeService;
import net.tutla.manhuntPlus.application.MatchService;
import net.tutla.manhuntPlus.application.RoleService;
import net.tutla.manhuntPlus.application.SidebarService;
import net.tutla.manhuntPlus.application.TwistService;
import net.tutla.manhuntPlus.bukkit.commands.CompassCommand;
import net.tutla.manhuntPlus.bukkit.commands.HunterCommand;
import net.tutla.manhuntPlus.bukkit.commands.ManhuntCommand;
import net.tutla.manhuntPlus.bukkit.commands.RandomVillageCommand;
import net.tutla.manhuntPlus.bukkit.commands.SpeedrunnerCommand;
import net.tutla.manhuntPlus.bukkit.commands.SurroundCommand;
import net.tutla.manhuntPlus.bukkit.commands.TwistCommand;
import net.tutla.manhuntPlus.bukkit.listeners.CompassLifecycleListener;
import net.tutla.manhuntPlus.bukkit.listeners.CompassInteractListener;
import net.tutla.manhuntPlus.bukkit.listeners.FreezeEnforcementListener;
import net.tutla.manhuntPlus.bukkit.listeners.PlayerDeathListener;
import net.tutla.manhuntPlus.bukkit.listeners.PlayerRespawnListener;
import net.tutla.manhuntPlus.bukkit.listeners.PortalListener;
import net.tutla.manhuntPlus.bukkit.listeners.TwistListener;
import net.tutla.manhuntPlus.bukkit.tasks.AutoCalibrationTask;
import net.tutla.manhuntPlus.config.PluginConfig;
import net.tutla.manhuntPlus.domain.GameState;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class ManhuntPlusPlugin extends JavaPlugin {
    private AutoCalibrationTask autoCalibrationTask;
    private SidebarService sidebarService;
    private FreezeService freezeService;

    @Override
    public void onEnable() {
        PluginConfig pluginConfig = PluginConfig.load(this);
        GameState state = new GameState();

        RoleService roleService = new RoleService(state);
        CompassService compassService = new CompassService(this, state);
        sidebarService = new SidebarService(this, state, pluginConfig.getSettings(), compassService);
        freezeService = new FreezeService(this, state, pluginConfig.getSettings());
        TwistService twistService = new TwistService(this, state);
        MatchService matchService = new MatchService(this, state, pluginConfig.getSettings(), roleService, compassService, freezeService, sidebarService);

        registerCommand("compass", new CompassCommand(compassService, roleService, pluginConfig.getPlayPermission()));
        registerCommand("speedrunner", new SpeedrunnerCommand(roleService, pluginConfig.getAdminPermission()));
        registerCommand("hunter", new HunterCommand(roleService, pluginConfig.getAdminPermission()));
        registerCommand("twist", new TwistCommand(twistService, pluginConfig.getAdminPermission()));
        registerCommand("manhunt", new ManhuntCommand(matchService, roleService, sidebarService, pluginConfig.getSettings(), state, pluginConfig.getAdminPermission()));
        registerCommand("surround", new SurroundCommand(roleService, pluginConfig.getSettings(), pluginConfig.getAdminPermission()));
        registerCommand("randomvillage", new RandomVillageCommand(pluginConfig.getAdminPermission()));

        registerListener(new PlayerDeathListener(state, matchService, compassService));
        registerListener(new PlayerRespawnListener(this, state, roleService, compassService, freezeService));
        registerListener(new PortalListener(state, compassService));
        registerListener(new CompassInteractListener(compassService, state));
        registerListener(new CompassLifecycleListener(this, state, compassService));
        registerListener(new FreezeEnforcementListener(state, freezeService, matchService));
        registerListener(new TwistListener(state, twistService, matchService));

        autoCalibrationTask = new AutoCalibrationTask(this, compassService);
        if (pluginConfig.getSettings().isAutoCalibrationEnabled()) {
            autoCalibrationTask.start(pluginConfig.getSettings().getAutoCalibrationIntervalSeconds());
        }

        getLogger().info("ManhuntPlus loaded with layered architecture.");
    }

    @Override
    public void onDisable() {
        if (autoCalibrationTask != null) {
            autoCalibrationTask.stop();
        }
        if (freezeService != null) {
            freezeService.stopHunterFreeze();
        }
        if (sidebarService != null) {
            sidebarService.stop();
        }
        getLogger().info("ManhuntPlus disabled.");
    }

    private void registerListener(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    private void registerCommand(String name, Object executorAndCompleter) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command not found in plugin.yml: " + name);
            return;
        }
        command.setExecutor((org.bukkit.command.CommandExecutor) executorAndCompleter);
        if (executorAndCompleter instanceof org.bukkit.command.TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }
}
