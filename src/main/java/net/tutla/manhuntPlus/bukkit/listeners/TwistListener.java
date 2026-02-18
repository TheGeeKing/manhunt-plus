package net.tutla.manhuntPlus.bukkit.listeners;

import net.tutla.manhuntPlus.application.MatchService;
import net.tutla.manhuntPlus.application.TwistService;
import net.tutla.manhuntPlus.domain.GameState;
import net.tutla.manhuntPlus.domain.MatchPhase;
import net.tutla.manhuntPlus.domain.StopReason;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;

public final class TwistListener implements Listener {
    private final GameState state;
    private final TwistService twistService;
    private final MatchService matchService;

    public TwistListener(GameState state, TwistService twistService, MatchService matchService) {
        this.state = state;
        this.twistService = twistService;
        this.matchService = matchService;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntityType() == EntityType.ENDER_DRAGON && state.getPhase() == MatchPhase.RUNNING && !state.getActiveSpeedrunners().isEmpty()) {
            matchService.stop(StopReason.SPEEDRUNNERS_WIN);
            return;
        }
        twistService.applyEntityDeathEffects(event);
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }
        if (twistService.tryMilkHunter(player, target)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        twistService.applyConsumeEffects(event.getPlayer(), event.getItem());
    }
}
