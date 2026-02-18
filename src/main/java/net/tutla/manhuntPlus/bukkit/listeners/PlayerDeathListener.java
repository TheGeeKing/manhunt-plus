package net.tutla.manhuntPlus.bukkit.listeners;

import net.tutla.manhuntPlus.application.CompassService;
import net.tutla.manhuntPlus.application.MatchService;
import net.tutla.manhuntPlus.domain.GameState;
import net.tutla.manhuntPlus.domain.MatchPhase;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class PlayerDeathListener implements Listener {
    private final GameState state;
    private final MatchService matchService;
    private final CompassService compassService;

    public PlayerDeathListener(GameState state, MatchService matchService, CompassService compassService) {
        this.state = state;
        this.matchService = matchService;
        this.compassService = compassService;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (state.getHunters().contains(player.getUniqueId())) {
            event.getDrops().removeIf(compassService::isTrackingCompass);
        }
        if (state.getPhase() != MatchPhase.RUNNING || !state.getActiveSpeedrunners().contains(player.getUniqueId())) {
            return;
        }
        matchService.onSpeedrunnerEliminated(player.getUniqueId(), player.getLocation());
    }
}
