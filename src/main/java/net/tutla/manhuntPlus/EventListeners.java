package net.tutla.manhuntPlus;

import net.tutla.manhuntPlus.lootpool.LootPool;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Random;
import java.util.UUID;

import static net.tutla.manhuntPlus.TwistsHelper.getCardinalDirection;

public class EventListeners implements Listener {
    private static TwistsHelper helper;

    public EventListeners(TwistsHelper helper) {
        EventListeners.helper = helper;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location lastLocation = player.getLocation().clone();
        if (ManhuntPlus.getInstance().getHunters().contains(player.getUniqueId())) {
            event.getDrops().removeIf(this::isTrackingCompass);
        }
        if (!ManhuntPlus.getInstance().getPlayingSpeedrunners().contains(player.getUniqueId())) return;

        Bukkit.broadcastMessage("Speedrunner " + player.getName() + " has been eliminated");
        ManhuntPlus.getInstance().setLastEliminatedSpeedrunnerLocation(lastLocation);
        ManhuntPlus.getInstance().markSpeedrunnerEliminated(player);
        ManhuntPlus.getInstance().setEliminatedSpeedrunnerDeathLocation(player.getUniqueId(), lastLocation);
        ManhuntPlus.getInstance().removePlayingSpeedrunner(player);

        boolean allSpeedrunnersEliminated = ManhuntPlus.getInstance().getPlayingSpeedrunners().isEmpty();
        Bukkit.getScheduler().runTaskLater(ManhuntPlus.getInstance(), () -> {
            if (player.isOnline()) {
                player.spigot().respawn();
            }

            if (allSpeedrunnersEliminated) {
                Bukkit.broadcastMessage("§aHunter(s) have won the Manhunt!");
                ManhuntPlus.getInstance().endManhuntWithAllSpeedrunnersEliminated();
            }
        }, 1L);
    }

    private boolean isTrackingCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) return false;
        if (!(item.getItemMeta() instanceof CompassMeta meta)) return false;
        String id = meta.getPersistentDataContainer().get(ManhuntPlus.COMPASS_ID_KEY, PersistentDataType.STRING);
        return id != null && !id.isBlank();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!ManhuntPlus.getInstance().getStatus()) return;

        if (ManhuntPlus.getInstance().getHunters().contains(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(ManhuntPlus.getInstance(), () -> {
                ManhuntPlus.getInstance().giveDefaultCompassToHunter(player);
                ManhuntPlus.getInstance().markHunterRespawnDuringFreeze(player);
            }, 1L);
            return;
        }

        if (!ManhuntPlus.getInstance().getSpeedrunners().contains(player.getUniqueId())) return;
        if (!ManhuntPlus.getInstance().isEliminatedSpeedrunner(player.getUniqueId())) return;

        Bukkit.getScheduler().runTaskLater(ManhuntPlus.getInstance(), () -> {
            player.setGameMode(GameMode.SPECTATOR);
            Location deathLocation = ManhuntPlus.getInstance().getEliminatedSpeedrunnerDeathLocation(player.getUniqueId());
            if (deathLocation != null) {
                player.teleport(deathLocation);
            }
            startEliminatedSpeedrunnerCountdown(player);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortalTravel(PlayerTeleportEvent event) {
        if (!ManhuntPlus.getInstance().getStatus()) return;
        if (event.getTo() == null) return;
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                && event.getCause() != PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            return;
        }

        Player player = event.getPlayer();
        boolean isSpeedrunner = ManhuntPlus.getInstance().getPlayingSpeedrunners().contains(player.getUniqueId());
        boolean isHunter = ManhuntPlus.getInstance().getHunters().contains(player.getUniqueId());
        if (!isSpeedrunner && !isHunter) return;

        if (isSpeedrunner) {
            ManhuntPlus.getInstance().recordSpeedrunnerPortalTransition(player, event.getFrom(), event.getTo());
            ManhuntPlus.getInstance().refreshCompassesForTarget(player);
        }

        if (isHunter) {
            ManhuntPlus.getInstance().refreshCompassesForHolder(player);
        }
    }

    private void startEliminatedSpeedrunnerCountdown(Player player) {
        final int[] secondsLeft = {10};

        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                if (!ManhuntPlus.getInstance().getStatus()) {
                    cancel();
                    return;
                }

                if (secondsLeft[0] > 0) {
                    player.sendTitle("§7You were eliminated", "§eTeleporting in " + secondsLeft[0] + "s", 0, 25, 0);
                    secondsLeft[0]--;
                    return;
                }

                Player target = ManhuntPlus.getInstance().getNextPlayingSpeedrunner(player.getUniqueId());
                if (target != null && target.isOnline()) {
                    player.teleport(target.getLocation());
                    player.sendTitle("§aTeleported", "§7Now spectating " + target.getName(), 5, 35, 10);
                } else {
                    player.sendMessage("§cNo active speedrunner is online to spectate.");
                }
                cancel();
            }
        }.runTaskTimer(ManhuntPlus.getInstance(), 0L, 20L);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntityType() == EntityType.PIG) {
            if (ManhuntPlus.getInstance().getTwist() != ManhuntPlus.Twist.PIG_OP_LOOT) return;

            Player killer = event.getEntity().getKiller();
            if (killer == null) return;

            if (!ManhuntPlus.getInstance().getPlayingSpeedrunners().contains(killer.getUniqueId())) return;

            event.getDrops().clear();
            LootPool pool = ManhuntPlus.getInstance().getDefaultLoot();

            for (int i = 0; i < 2 + new Random().nextInt(3); i++) {
                ItemStack drop = pool.getRandomLoot();
                if (drop != null) event.getDrops().add(drop);
            }
        } else if (event.getEntityType() == EntityType.ENDER_DRAGON) {
            if (ManhuntPlus.getInstance().getStatus() && !(ManhuntPlus.getInstance().getPlayingSpeedrunners().isEmpty())) {
                Bukkit.broadcastMessage("§aSpeedrunner(s) have won the Manhunt!");
                ManhuntPlus.getInstance().stopManhunt();
            }
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {

        if (ManhuntPlus.getInstance().getTwist() != ManhuntPlus.Twist.MILK_HUNTER_OP_LOOT) return;

        Player player = event.getPlayer();
        if (!(event.getRightClicked() instanceof Player target)) return;
        if (!ManhuntPlus.getInstance().getPlayingSpeedrunners().contains(player.getUniqueId())) return;

        if (player.getInventory().getItemInMainHand().getType() == Material.BUCKET) {


            ItemStack milk = new ItemStack(Material.MILK_BUCKET);
            ItemMeta meta = milk.getItemMeta();

            NamespacedKey key = new NamespacedKey(ManhuntPlus.getInstance(), "milked_from");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, target.getUniqueId().toString());
            meta.setDisplayName("§b" + target.getName() + "§e's Milk");
            milk.setItemMeta(meta);
            player.getInventory().addItem(milk);

            ItemStack mainHandItem = player.getInventory().getItemInMainHand();
            if (mainHandItem.getAmount() > 1) {
                mainHandItem.setAmount(mainHandItem.getAmount() - 1);
                player.getInventory().setItemInMainHand(mainHandItem);
            } else {
                player.getInventory().setItemInMainHand(null);
            }

            target.sendMessage("§aYou have been milked!");
            event.setCancelled(true);
        }

    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.COMPASS) return;
        if (!(item.getItemMeta() instanceof CompassMeta meta)) return;
        String id = meta.getPersistentDataContainer().get(ManhuntPlus.COMPASS_ID_KEY, PersistentDataType.STRING);
        if (id == null) return;
        UUID compassId = UUID.fromString(id);
        Player target = ManhuntPlus.getInstance().getTrackedCompasses().get(compassId);
        if (target == null || !target.isOnline()) {
            event.getPlayer().sendMessage("§cTracked player is not available.");
            return;
        }
        ManhuntPlus.updateCompass(item, compassId, target, event.getPlayer());

        event.getPlayer().sendMessage("§aCompass calibrated to " + target.getName());
    }

    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item.getType() == Material.MILK_BUCKET) {
            ItemMeta meta = item.getItemMeta();
            NamespacedKey key = new NamespacedKey(ManhuntPlus.getInstance(), "milked_from");

            if (meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                String uuid = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                if (uuid != null) {
                    Player target = Bukkit.getPlayer(UUID.fromString(uuid));
                    if (target != null) {
                        helper.tortureHunter(target);
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player hitter = (Player) event.getDamager();
        Player victim = (Player) event.getEntity();
        if (ManhuntPlus.getInstance().getSpeedrunners().contains(hitter.getUniqueId()) && ManhuntPlus.getInstance().getHunters().contains(victim.getUniqueId())) {
            if (ManhuntPlus.getInstance().waitingForStart) {
                ManhuntPlus.getInstance().startManhunt(hitter.getLocation());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunterMove(PlayerMoveEvent event) {
        if (!ManhuntPlus.getInstance().getStatus()) return;
        if (!ManhuntPlus.getInstance().areHuntersFrozen()) return;

        Player player = event.getPlayer();
        if (!ManhuntPlus.getInstance().getHunters().contains(player.getUniqueId())) return;
        if (ManhuntPlus.getInstance().isHunterRespawnExempt(player.getUniqueId())) return;
        if (event.getTo() == null) return;

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
    public void onHunterDamageDuringStartCountdown(EntityDamageEvent event) {
        if (!ManhuntPlus.getInstance().getStatus()) return;
        if (!ManhuntPlus.getInstance().areHuntersFrozen()) return;
        if (!(event.getEntity() instanceof Player hunter)) return;
        if (!ManhuntPlus.getInstance().getHunters().contains(hunter.getUniqueId())) return;
        if (ManhuntPlus.getInstance().isHunterRespawnExempt(hunter.getUniqueId())) return;

        event.setCancelled(true);
    }

    /*@EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        if (ManhuntPlus.getInstance().getTwist() != ManhuntPlus.Twist.SUSSY) return;

        Player croucher = event.getPlayer();
        if (!ManhuntPlus.getInstance().getPlayingSpeedrunners().contains(croucher.getUniqueId())) return;



        for (Player target : ManhuntPlus.getInstance().getPlayers(ManhuntPlus.getInstance().getHunters())) {
            if (!target.getWorld().equals(croucher.getWorld())) continue;
            if (croucher.getLocation().distance(target.getLocation()) > 1.4) continue;
            target.playSound(target.getLocation(), Sound.ENTITY_GHAST_HURT, 1f, 1f);
            Vector targetFacing = target.getLocation().getDirection().normalize();
            Vector toCroucher = croucher.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
            if (getCardinalDirection(croucher) == getCardinalDirection(target)) {
                ManhuntPlus.giveLootToLeveller(croucher);
            }
        }
    }*/

}
