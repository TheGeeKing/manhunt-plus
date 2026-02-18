package net.tutla.manhuntPlus.application;

import net.tutla.manhuntPlus.lootpool.LootPool;
import net.tutla.manhuntPlus.domain.GameState;
import net.tutla.manhuntPlus.domain.TwistType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class TwistService {
    private final JavaPlugin plugin;
    private final GameState state;
    private final LootPool defaultLootPool;
    private final Random random = new Random();
    private final NamespacedKey milkedFromKey;

    public TwistService(JavaPlugin plugin, GameState state) {
        this.plugin = plugin;
        this.state = state;
        this.defaultLootPool = LootPool.createDefault();
        this.milkedFromKey = new NamespacedKey(plugin, "milked_from");
    }

    public TwistType getTwist() {
        return state.getTwist();
    }

    public void setTwist(TwistType twist) {
        state.setTwist(twist);
    }

    public void applyEntityDeathEffects(EntityDeathEvent event) {
        if (event.getEntityType() != EntityType.PIG || state.getTwist() != TwistType.PIG_OP_LOOT) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null || !state.getActiveSpeedrunners().contains(killer.getUniqueId())) {
            return;
        }
        event.getDrops().clear();
        int drops = 2 + random.nextInt(3);
        for (int i = 0; i < drops; i++) {
            ItemStack item = defaultLootPool.getRandomLoot();
            if (item != null) {
                event.getDrops().add(item);
            }
        }
    }

    public boolean tryMilkHunter(Player speedrunner, Player hunter) {
        if (state.getTwist() != TwistType.MILK_HUNTER_OP_LOOT) {
            return false;
        }
        if (!state.getActiveSpeedrunners().contains(speedrunner.getUniqueId())) {
            return false;
        }
        if (speedrunner.getInventory().getItemInMainHand().getType() != Material.BUCKET) {
            return false;
        }

        ItemStack milk = new ItemStack(Material.MILK_BUCKET);
        ItemMeta meta = milk.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(milkedFromKey, PersistentDataType.STRING, hunter.getUniqueId().toString());
            meta.setDisplayName("§b" + hunter.getName() + "§e's Milk");
            milk.setItemMeta(meta);
        }
        ItemStack hand = speedrunner.getInventory().getItemInMainHand();
        if (hand.getAmount() == 1) {
            speedrunner.getInventory().setItemInMainHand(milk);
        } else {
            Map<Integer, ItemStack> leftover = speedrunner.getInventory().addItem(milk);
            if (!leftover.isEmpty()) {
                return false;
            }
            hand.setAmount(hand.getAmount() - 1);
            speedrunner.getInventory().setItemInMainHand(hand);
        }

        hunter.sendMessage(Messages.error("You have been milked!"));
        return true;
    }

    public void applyConsumeEffects(Player consumer, ItemStack consumed) {
        if (consumed == null || consumed.getType() != Material.MILK_BUCKET) {
            return;
        }
        ItemMeta meta = consumed.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(milkedFromKey, PersistentDataType.STRING)) {
            return;
        }
        String uuid = meta.getPersistentDataContainer().get(milkedFromKey, PersistentDataType.STRING);
        if (uuid == null) {
            return;
        }
        try {
            UUID hunterId = UUID.fromString(uuid);
            Player target = Bukkit.getPlayer(hunterId);
            if (target != null) {
                tortureHunter(target);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void tortureHunter(Player hunter) {
        int choice = random.nextInt(8);
        switch (choice) {
            case 0 -> hunter.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 2 * 60 * 20, 4));
            case 1 -> hunter.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 2 * 60 * 20, 4));
            case 2 -> hunter.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60 * 20, 0));
            case 3 -> hunter.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 4 * 60 * 20, 7));
            case 4 -> createSinkhole(hunter);
            case 5 -> hunter.setVelocity(new Vector(0, 10, 0));
            case 6 -> clearHalfInventory(hunter);
            case 7 -> hunter.setHealth(Math.min(hunter.getHealth(), 10.0d));
            default -> {
            }
        }
        hunter.getWorld().playSound(hunter.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.0f);
    }

    private void createSinkhole(Player player) {
        final var world = player.getWorld();
        final var loc = player.getLocation();
        final int originX = loc.getBlockX();
        final int originZ = loc.getBlockZ();
        final int minY = world.getMinHeight();
        final int[] nextY = {loc.getBlockY()};
        final int yLevelsPerTick = 20;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (nextY[0] < minY) {
                    cancel();
                    return;
                }

                int endY = Math.max(minY, nextY[0] - (yLevelsPerTick - 1));
                for (int y = nextY[0]; y >= endY; y--) {
                    for (int x = -1; x <= 1; x++) {
                        for (int z = -1; z <= 1; z++) {
                            world.getBlockAt(originX + x, y, originZ + z).setType(Material.AIR, false);
                        }
                    }
                }

                nextY[0] = endY - 1;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void clearHalfInventory(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() != Material.AIR) {
                slots.add(i);
            }
        }
        Collections.shuffle(slots);
        int removeCount = slots.size() / 2;
        for (int i = 0; i < removeCount; i++) {
            player.getInventory().setItem(slots.get(i), null);
        }
    }
}
