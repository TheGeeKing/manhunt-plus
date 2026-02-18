package net.tutla.manhuntPlus.bukkit.listeners;

import net.tutla.manhuntPlus.application.CompassService;
import net.tutla.manhuntPlus.domain.GameState;
import net.tutla.manhuntPlus.domain.MatchPhase;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class PortalListener implements Listener {
    private final GameState state;
    private final CompassService compassService;

    public PortalListener(GameState state, CompassService compassService) {
        this.state = state;
        this.compassService = compassService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortalTravel(PlayerTeleportEvent event) {
        if (state.getPhase() != MatchPhase.RUNNING || event.getTo() == null) {
            return;
        }
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL && cause != PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            return;
        }

        var player = event.getPlayer();
        if (state.getActiveSpeedrunners().contains(player.getUniqueId())) {
            state.recordPortalTransition(player.getUniqueId(), event.getFrom(), event.getTo());
            compassService.refreshForTarget(player.getUniqueId());
        }
        if (state.getHunters().contains(player.getUniqueId())) {
            compassService.refreshForHolder(player.getUniqueId());
        }
    }
}
