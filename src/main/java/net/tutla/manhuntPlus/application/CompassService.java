package net.tutla.manhuntPlus.application;

import net.tutla.manhuntPlus.domain.GameState;
import net.tutla.manhuntPlus.domain.PortalTransition;
import net.tutla.manhuntPlus.domain.Role;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Comparator;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CompassService {
    public static final String COMPASS_PDC = "tracking_compass_id";

    private final JavaPlugin plugin;
    private final GameState state;
    private final NamespacedKey compassIdKey;

    public CompassService(JavaPlugin plugin, GameState state) {
        this.plugin = plugin;
        this.state = state;
        this.compassIdKey = new NamespacedKey(plugin, COMPASS_PDC);
    }

    public NamespacedKey compassIdKey() {
        return compassIdKey;
    }

    public void giveTrackingCompass(UUID holderId, UUID targetId) {
        Player holder = Bukkit.getPlayer(holderId);
        Player target = Bukkit.getPlayer(targetId);
        if (holder == null || target == null || !target.isOnline()) {
            return;
        }
        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        if (meta == null) {
            return;
        }

        UUID compassId = UUID.randomUUID();
        meta.getPersistentDataContainer().set(compassIdKey, PersistentDataType.STRING, compassId.toString());
        meta.setDisplayName("§bTracking Compass §7(" + target.getName() + ")");
        compass.setItemMeta(meta);

        state.getTrackedCompasses().put(compassId, target.getUniqueId());
        updateCompassItem(compass, compassId, holder, target);
        holder.getInventory().addItem(compass);
    }

    public void giveDefaultCompassToHunter(UUID hunterId) {
        Player target = state.getSpeedrunners().stream()
                .map(Bukkit::getPlayer)
                .filter(player -> player != null && player.isOnline())
                .min(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .orElse(null);
        if (target == null) {
            return;
        }
        giveTrackingCompass(hunterId, target.getUniqueId());
    }

    public void refreshForHolder(UUID holderId) {
        Player holder = Bukkit.getPlayer(holderId);
        if (holder == null || !holder.isOnline()) {
            return;
        }
        for (ItemStack item : holder.getInventory().getContents()) {
            refreshItem(item, holder);
        }
    }

    public void refreshForTarget(UUID targetId) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : online.getInventory().getContents()) {
                UUID compassId = readCompassId(item);
                if (compassId == null) {
                    continue;
                }
                UUID tracked = state.getTrackedCompasses().get(compassId);
                if (tracked != null && tracked.equals(targetId)) {
                    Player target = Bukkit.getPlayer(targetId);
                    if (target != null && target.isOnline()) {
                        updateCompassItem(item, compassId, online, target);
                    }
                }
            }
        }
    }

    public void refreshAll() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            refreshForHolder(online.getUniqueId());
        }
    }

    public void refreshItem(ItemStack item, Player holder) {
        UUID compassId = readCompassId(item);
        if (compassId == null) {
            return;
        }
        UUID targetId = state.getTrackedCompasses().get(compassId);
        if (targetId == null) {
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target != null && target.isOnline()) {
            updateCompassItem(item, compassId, holder, target);
        }
    }

    public UUID readCompassId(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !(item.getItemMeta() instanceof CompassMeta meta)) {
            return null;
        }
        String value = meta.getPersistentDataContainer().get(compassIdKey, PersistentDataType.STRING);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public boolean isTrackingCompass(ItemStack item) {
        return readCompassId(item) != null;
    }

    public Location resolveTrackingLocation(Player holder, Player target) {
        if (holder == null || target == null || !target.isOnline()) {
            return null;
        }
        if (holder.getWorld().equals(target.getWorld())) {
            return target.getLocation();
        }

        Optional<Location> crossDimensionHint = portalHint(holder, target);
        if (crossDimensionHint.isPresent()) {
            return crossDimensionHint.get();
        }

        Location eliminated = state.getEliminatedSpeedrunnerDeathLocations().get(target.getUniqueId());
        if (eliminated != null && eliminated.getWorld() != null && eliminated.getWorld().equals(holder.getWorld())) {
            return eliminated.clone();
        }

        Location lastElim = state.getLastEliminatedSpeedrunnerLocation();
        if (lastElim != null && lastElim.getWorld() != null && lastElim.getWorld().equals(holder.getWorld())) {
            return lastElim.clone();
        }

        return null;
    }

    private Optional<Location> portalHint(Player holder, Player target) {
        Deque<PortalTransition> transitions = state.getSpeedrunnerPortalTransitions().get(target.getUniqueId());
        if (transitions == null || transitions.isEmpty()) {
            return Optional.empty();
        }
        World.Environment holderEnv = holder.getWorld().getEnvironment();
        World.Environment targetEnv = target.getWorld().getEnvironment();

        PortalTransition[] items = transitions.toArray(new PortalTransition[0]);
        for (int i = items.length - 1; i >= 0; i--) {
            PortalTransition transition = items[i];
            if (transition.fromEnvironment() == holderEnv && transition.toEnvironment() == targetEnv) {
                return Optional.of(transition.fromLocation().clone());
            }
            if (transition.fromEnvironment() == targetEnv && transition.toEnvironment() == holderEnv) {
                return Optional.of(transition.toLocation().clone());
            }
        }
        return Optional.empty();
    }

    private void updateCompassItem(ItemStack item, UUID compassId, Player holder, Player target) {
        if (item == null || item.getType() != Material.COMPASS || !(item.getItemMeta() instanceof CompassMeta meta)) {
            return;
        }
        String value = meta.getPersistentDataContainer().get(compassIdKey, PersistentDataType.STRING);
        if (value == null || !value.equals(compassId.toString())) {
            return;
        }
        if (target.isSneaking()) {
            return;
        }
        Location tracking = resolveTrackingLocation(holder, target);
        if (tracking == null) {
            return;
        }
        meta.setLodestone(tracking);
        meta.setLodestoneTracked(false);
        item.setItemMeta(meta);
    }

    public Role roleOf(UUID playerId) {
        if (state.getHunters().contains(playerId)) {
            return Role.HUNTER;
        }
        if (state.getSpeedrunners().contains(playerId)) {
            return Role.SPEEDRUNNER;
        }
        return Role.SPECTATOR;
    }

    public Map<UUID, UUID> trackedCompasses() {
        return state.getTrackedCompasses();
    }
}
