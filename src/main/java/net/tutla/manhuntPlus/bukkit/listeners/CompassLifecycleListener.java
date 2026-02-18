package net.tutla.manhuntPlus.bukkit.listeners;

import net.tutla.manhuntPlus.application.CompassService;
import net.tutla.manhuntPlus.domain.GameState;
import net.tutla.manhuntPlus.domain.MatchPhase;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class CompassLifecycleListener implements Listener {
    private final JavaPlugin plugin;
    private final GameState state;
    private final CompassService compassService;

    public CompassLifecycleListener(JavaPlugin plugin, GameState state, CompassService compassService) {
        this.plugin = plugin;
        this.state = state;
        this.compassService = compassService;
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!compassService.isTrackingCompass(event.getItemDrop().getItemStack())) {
            return;
        }
        compassService.untrackCompass(event.getItemDrop().getItemStack());
        event.getItemDrop().remove();
        Player player = event.getPlayer();
        if (!isRunningHunter(player)) {
            return;
        }
        ensureHunterCompassNextTick(player);
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!compassService.isTrackingCompass(event.getEntity().getItemStack())) {
            return;
        }
        compassService.untrackCompass(event.getEntity().getItemStack());
        event.getEntity().remove();
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isRunningHunter(player)) {
            return;
        }

        boolean destroyed = false;
        InventoryAction action = event.getAction();

        if ((action == InventoryAction.DROP_ALL_SLOT || action == InventoryAction.DROP_ONE_SLOT)
                && compassService.isTrackingCompass(event.getCurrentItem())) {
            compassService.untrackCompass(event.getCurrentItem());
            event.setCurrentItem(null);
            event.setCancelled(true);
            destroyed = true;
        }

        if (event.getClickedInventory() != null
                && event.getClickedInventory().getType() != org.bukkit.event.inventory.InventoryType.PLAYER
                && compassService.isTrackingCompass(event.getCursor())) {
            compassService.untrackCompass(event.getCursor());
            event.setCursor(null);
            event.setCancelled(true);
            destroyed = true;
        }

        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && event.getClickedInventory() != null
                && event.getClickedInventory().getType() == org.bukkit.event.inventory.InventoryType.PLAYER
                && compassService.isTrackingCompass(event.getCurrentItem())) {
            compassService.untrackCompass(event.getCurrentItem());
            event.setCurrentItem(null);
            event.setCancelled(true);
            destroyed = true;
        }

        if (action == InventoryAction.HOTBAR_SWAP
                && event.getClickedInventory() != null
                && event.getClickedInventory().getType() != org.bukkit.event.inventory.InventoryType.PLAYER
                && event.getHotbarButton() >= 0) {
            var hotbarItem = player.getInventory().getItem(event.getHotbarButton());
            if (compassService.isTrackingCompass(hotbarItem)) {
                compassService.untrackCompass(hotbarItem);
                player.getInventory().setItem(event.getHotbarButton(), null);
                event.setCancelled(true);
                destroyed = true;
            }
        }

        if (destroyed) {
            ensureHunterCompassNextTick(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isRunningHunter(player)) {
            return;
        }
        if (!compassService.isTrackingCompass(event.getOldCursor())) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        boolean touchingTopInventory = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (!touchingTopInventory) {
            return;
        }

        compassService.untrackCompass(event.getOldCursor());
        event.setCursor(null);
        event.setCancelled(true);
        ensureHunterCompassNextTick(player);
    }

    private boolean isRunningHunter(Player player) {
        return state.getPhase() == MatchPhase.RUNNING && state.getHunters().contains(player.getUniqueId());
    }

    private void ensureHunterCompassNextTick(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!isRunningHunter(player) || compassService.playerHasTrackingCompass(player)) {
                return;
            }
            compassService.giveDefaultCompassToHunter(player.getUniqueId());
        });
    }
}
