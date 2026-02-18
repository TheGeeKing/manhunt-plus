package net.tutla.manhuntPlus.bukkit.listeners;

import net.tutla.manhuntPlus.application.FreezeService;
import net.tutla.manhuntPlus.application.MatchService;
import net.tutla.manhuntPlus.domain.GameState;
import net.tutla.manhuntPlus.domain.MatchPhase;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class FreezeEnforcementListener implements Listener {
    private final GameState state;
    private final FreezeService freezeService;
    private final MatchService matchService;

    public FreezeEnforcementListener(GameState state, FreezeService freezeService, MatchService matchService) {
        this.state = state;
        this.freezeService = freezeService;
        this.matchService = matchService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPreparedHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player hitter) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!state.isWaitingForStart()) {
            return;
        }
        if (state.getSpeedrunners().contains(hitter.getUniqueId()) && state.getHunters().contains(victim.getUniqueId())) {
            matchService.beginOnPreparedHit(hitter.getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunterMove(PlayerMoveEvent event) {
        if (state.getPhase() != MatchPhase.RUNNING || !freezeService.isFrozen(event.getPlayer().getUniqueId()) || event.getTo() == null) {
            return;
        }
        if (event.getFrom().getX() == event.getTo().getX()
                && event.getFrom().getY() == event.getTo().getY()
                && event.getFrom().getZ() == event.getTo().getZ()) {
            return;
        }
        Location locked = event.getFrom().clone();
        locked.setYaw(event.getTo().getYaw());
        locked.setPitch(event.getTo().getPitch());
        event.setTo(locked);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunterDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (state.getPhase() == MatchPhase.RUNNING && freezeService.isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
