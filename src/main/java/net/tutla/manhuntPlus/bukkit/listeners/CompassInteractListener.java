package net.tutla.manhuntPlus.bukkit.listeners;

import net.tutla.manhuntPlus.application.CompassService;
import net.tutla.manhuntPlus.application.Messages;
import net.tutla.manhuntPlus.domain.GameState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class CompassInteractListener implements Listener {
    private final CompassService compassService;
    private final GameState state;

    public CompassInteractListener(CompassService compassService, GameState state) {
        this.compassService = compassService;
        this.state = state;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        UUID compassId = compassService.readCompassId(item);
        if (compassId == null) {
            return;
        }

        UUID targetId = state.getTrackedCompasses().get(compassId);
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            event.getPlayer().sendMessage(Messages.error("Tracked player is not available."));
            return;
        }
        compassService.refreshItem(item, event.getPlayer());
        event.getPlayer().sendMessage(Messages.ok("Compass calibrated to " + target.getName()));
    }
}
