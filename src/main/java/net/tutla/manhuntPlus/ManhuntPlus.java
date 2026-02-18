package net.tutla.manhuntPlus;

import net.tutla.manhuntPlus.lootpool.LevellingFactory;
import net.tutla.manhuntPlus.lootpool.LootPool;
import net.tutla.manhuntPlus.lootpool.LootPoolLevelling;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.attribute.Attribute;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.Registry;
import org.bukkit.util.StructureSearchResult;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

public final class ManhuntPlus extends JavaPlugin {
    enum Twist {
        DEFAULT,
        PIG_OP_LOOT,
        MILK_HUNTER_OP_LOOT // not for now cuz of modrinth restrictions
    }

    // runtime shit
    private static ManhuntPlus instance;
    private Boolean started = false;
    public Boolean waitingForStart = false;

    public static final NamespacedKey COMPASS_ID_KEY = new NamespacedKey("manhuntplus", "feetpics");
    // runners/hunters
    private final List<UUID> speedrunners = new ArrayList<>();
    private final List<UUID> playingSpeedrunners = new ArrayList<>();
    private final Set<UUID> eliminatedSpeedrunners = new HashSet<>();
    private final Map<UUID, Location> eliminatedSpeedrunnerDeathLocations = new HashMap<>();
    private final List<UUID> hunters = new ArrayList<>();
    private Location lastEliminatedSpeedrunnerLocation = null;
    // settings
    private Twist twist = Twist.DEFAULT;
    private int timer = 0;
    private int timerTaskId = -1;
    private int countdownLimitMinutes = 0;
    private int startCountdownSeconds = 0;
    private boolean huntersFrozen = false;
    private int huntersFreezeSecondsRemaining = 0;
    private int huntersFreezeTaskId = -1;
    private final Set<UUID> respawnedHuntersDuringFreeze = new HashSet<>();
    private boolean teamCompassEnabled = false;
    private int teamCompassTaskId = -1;
    private final Set<UUID> teamCompassViewers = new HashSet<>();
    private final Map<UUID, Deque<PortalTransition>> speedrunnerPortalTransitions = new HashMap<>();
    // compass shit
    Map<UUID, Player> trackedCompasses = new HashMap<>();
    private static final class PortalTransition {
        private final World.Environment fromEnv;
        private final World.Environment toEnv;
        private final Location fromLocation;
        private final Location toLocation;

        private PortalTransition(World.Environment fromEnv, World.Environment toEnv, Location fromLocation, Location toLocation) {
            this.fromEnv = fromEnv;
            this.toEnv = toEnv;
            this.fromLocation = fromLocation;
            this.toLocation = toLocation;
        }
    }

    public static void updateCompass(ItemStack item, UUID compassId, Player target, Player holder){
        if (item == null || item.getType() != Material.COMPASS) return;
        if (target == null || !target.isOnline()) return;

        CompassMeta meta = (CompassMeta) item.getItemMeta();
        if (meta == null) return;

        String id = meta.getPersistentDataContainer().get(COMPASS_ID_KEY, PersistentDataType.STRING);
        if (id != null && id.equals(compassId.toString())) {
            Location trackingLocation = getInstance().resolveTrackingLocation(holder, target);
            if (trackingLocation != null && !target.isSneaking()){
                meta.setLodestone(trackingLocation);
                meta.setLodestoneTracked(false);
                item.setItemMeta(meta);
            }
        }
    }

    // twist shit
    // ik the code is shit but like im shit at naming
    public static Map<UUID, LootPoolLevelling> playerLootPoolLevels = new HashMap<>();

    public static LootPoolLevelling addPlayerLevellingLootPool(Player player) {
        LootPoolLevelling levelling = new LootPoolLevelling(LevellingFactory.createAllTiers(), 1.25);
        playerLootPoolLevels.put(player.getUniqueId(), levelling);
        return levelling;
    }

    public static void giveLootToLeveller(Player player) {
        LootPoolLevelling pool = playerLootPoolLevels.get(player.getUniqueId());
        if (pool == null) {
            pool = addPlayerLevellingLootPool(player);
        }
        ItemStack loot = pool.getLoot();
        player.getInventory().addItem(loot);
    }


    // loot defs
    private static LootPool basicLootPool;

    // access functions
    public static ManhuntPlus getInstance() {
        return instance;
    }
    public Boolean getStatus() {
        return started;
    }
    public void setStatus(Boolean stat) {
        started = stat;
    }

    public Map<UUID, Player> getTrackedCompasses(){
        return trackedCompasses;
    }
    public List<UUID> getPlayingSpeedrunners(){
        return playingSpeedrunners;
    }
    public List<UUID> getSpeedrunners(){
        return speedrunners;
    }
    public List<UUID> getHunters(){
        return hunters;
    }
    public boolean isEliminatedSpeedrunner(UUID playerId) {
        return eliminatedSpeedrunners.contains(playerId);
    }
    public void markSpeedrunnerEliminated(Player player) {
        eliminatedSpeedrunners.add(player.getUniqueId());
    }
    public void clearEliminatedSpeedrunners() {
        eliminatedSpeedrunners.clear();
        eliminatedSpeedrunnerDeathLocations.clear();
    }
    public void setEliminatedSpeedrunnerDeathLocation(UUID playerId, Location location) {
        if (playerId == null || location == null) return;
        eliminatedSpeedrunnerDeathLocations.put(playerId, location.clone());
    }
    public Location getEliminatedSpeedrunnerDeathLocation(UUID playerId) {
        Location location = eliminatedSpeedrunnerDeathLocations.get(playerId);
        return location == null ? null : location.clone();
    }
    public void recordSpeedrunnerPortalTransition(Player player, Location from, Location to) {
        if (player == null || from == null || to == null) return;
        if (from.getWorld() == null || to.getWorld() == null) return;

        World.Environment fromEnv = from.getWorld().getEnvironment();
        World.Environment toEnv = to.getWorld().getEnvironment();
        if (fromEnv == toEnv) return;

        Deque<PortalTransition> transitions = speedrunnerPortalTransitions.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
        transitions.addLast(new PortalTransition(fromEnv, toEnv, from.clone(), to.clone()));
        while (transitions.size() > 64) {
            transitions.removeFirst();
        }
    }

    public void addSpeedrunner(Player player){
        if (!speedrunners.contains((player.getUniqueId()))){
            speedrunners.add(player.getUniqueId());
        }
    }
    public void removeSpeedrunner(Player player){
        if (speedrunners.contains((player.getUniqueId()))){
            speedrunners.remove(player.getUniqueId());
        }
    }
    public void removePlayingSpeedrunner(Player player){
        if (playingSpeedrunners.contains((player.getUniqueId()))){
            playingSpeedrunners.remove(player.getUniqueId());
        }
    }
    public void addHunter(Player player){
        if (!hunters.contains((player.getUniqueId()))){
            hunters.add(player.getUniqueId());
        }
    }
    public void removeHunter(Player player){
        hunters.remove(player.getUniqueId());
        if (teamCompassViewers.remove(player.getUniqueId())) {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) {
                player.setScoreboard(manager.getMainScoreboard());
            }
        }
    }

    public Twist getTwist() {
        return twist;
    }
    public void setTwist(Twist twist) {
        this.twist = twist;
    }
    public LootPool getDefaultLoot() {
        return basicLootPool;
    }
    public boolean areHuntersFrozen() {
        return huntersFrozen;
    }
    public int getStartCountdownSeconds() {
        return startCountdownSeconds;
    }
    public boolean isTeamCompassEnabled() {
        return teamCompassEnabled;
    }
    public void setTeamCompassEnabled(boolean enabled) {
        teamCompassEnabled = enabled;
        if (!enabled) {
            stopTeamCompassSidebar();
            return;
        }

        if (started) {
            startTeamCompassSidebar();
        }
    }
    public void setStartCountdownSeconds(int seconds) {
        startCountdownSeconds = Math.max(0, seconds);
    }
    public void markHunterRespawnDuringFreeze(Player hunter) {
        if (hunter == null) return;
        if (!huntersFrozen) return;
        respawnedHuntersDuringFreeze.add(hunter.getUniqueId());
        hunter.setLevel(0);
        hunter.setExp(0f);
    }
    public boolean isHunterRespawnExempt(UUID playerId) {
        return respawnedHuntersDuringFreeze.contains(playerId);
    }
    public void syncHunterFreezeBar(Player hunter) {
        if (hunter == null || !hunter.isOnline()) return;
        if (!huntersFrozen) return;
        if (isHunterRespawnExempt(hunter.getUniqueId())) return;

        float progress = startCountdownSeconds <= 0 ? 0f : (float) huntersFreezeSecondsRemaining / startCountdownSeconds;
        progress = Math.max(0f, Math.min(1f, progress));
        hunter.setLevel(huntersFreezeSecondsRemaining);
        hunter.setExp(progress);
    }

    public void startTimer() {
        if (timerTaskId != -1) return;

        timer = 0;
        timerTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            timer++;

            if (timer % getConfig().getInt("broadcast-time-every") == 0 && getConfig().getBoolean("broadcast-time")){
                Bukkit.broadcastMessage("§eManhunt Timer: " + (timer / 60) + " minute(s)");
            }

            if (countdownLimitMinutes > 0 && timer >= countdownLimitMinutes * 60) {
                Bukkit.broadcastMessage("§cTime's up! Speedrunners failed to win in " + countdownLimitMinutes + " minutes.");
                stopTimer();
            }

        }, 0L, 20L);
    }

    public void stopTimer() {
        if (timerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(timerTaskId);
            timerTaskId = -1;
            Bukkit.broadcastMessage("§cManhunt stopped at " + (timer / 60) + " minute(s).");
        }
    }

    private void clearStartCountdownBar() {
        for (UUID id : hunters) {
            Player hunter = Bukkit.getPlayer(id);
            if (hunter != null && hunter.isOnline()) {
                hunter.setLevel(0);
                hunter.setExp(0f);
            }
        }
        for (UUID id : speedrunners) {
            Player speedrunner = Bukkit.getPlayer(id);
            if (speedrunner != null && speedrunner.isOnline()) {
                speedrunner.setLevel(0);
                speedrunner.setExp(0f);
            }
        }
    }

    private void unfreezeHunters() {
        huntersFrozen = false;
        huntersFreezeSecondsRemaining = 0;
        if (huntersFreezeTaskId != -1) {
            Bukkit.getScheduler().cancelTask(huntersFreezeTaskId);
            huntersFreezeTaskId = -1;
        }
        clearStartCountdownBar();
        respawnedHuntersDuringFreeze.clear();
    }

    private void startHunterReleaseCountdown() {
        unfreezeHunters();
        if (startCountdownSeconds <= 0) return;

        huntersFrozen = true;
        huntersFreezeSecondsRemaining = startCountdownSeconds;
        huntersFreezeTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (!started) {
                unfreezeHunters();
                return;
            }

            for (UUID id : hunters) {
                Player hunter = Bukkit.getPlayer(id);
                if (hunter != null && hunter.isOnline()) {
                    syncHunterFreezeBar(hunter);
                }
            }
            float progress = startCountdownSeconds <= 0 ? 0f : (float) huntersFreezeSecondsRemaining / startCountdownSeconds;
            progress = Math.max(0f, Math.min(1f, progress));
            for (UUID id : speedrunners) {
                Player speedrunner = Bukkit.getPlayer(id);
                if (speedrunner != null && speedrunner.isOnline()) {
                    speedrunner.setLevel(huntersFreezeSecondsRemaining);
                    speedrunner.setExp(progress);
                }
            }

            if (huntersFreezeSecondsRemaining <= 0) {
                unfreezeHunters();
                Bukkit.broadcastMessage("§aHunters released!");
                return;
            }

            huntersFreezeSecondsRemaining--;
        }, 0L, 20L);
    }

    private String getDirectionLabel(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        if (Math.abs(dx) < 0.01 && Math.abs(dz) < 0.01) return "•";

        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        if (angle < 0) angle += 360;
        String[] dirs = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        int index = (int) Math.round(angle / 45.0) % 8;
        return dirs[index];
    }

    private void updateHunterTeamCompassSidebar(Player viewer) {
        if (viewer == null || !viewer.isOnline()) return;
        if (!hunters.contains(viewer.getUniqueId())) return;

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("teamcompass", "dummy", "§bHunter Team Compass");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int score = Math.min(15, hunters.size() + 2);
        objective.getScore("§7Name | Dir | Dist").setScore(score--);

        for (UUID hunterId : hunters) {
            Player hunter = Bukkit.getPlayer(hunterId);
            String name = hunter != null ? hunter.getName() : "Offline";
            if (name.length() > 10) name = name.substring(0, 10);

            String dir;
            String dist;
            if (hunter == null || !hunter.isOnline()) {
                dir = "--";
                dist = "OFF";
            } else {
                Location tracked = resolveTrackingLocation(viewer, hunter);
                if (tracked != null && tracked.getWorld() != null && viewer.getWorld().equals(tracked.getWorld())) {
                    dir = getDirectionLabel(viewer.getLocation(), tracked);
                    dist = Integer.toString((int) Math.round(viewer.getLocation().distance(tracked)));
                } else {
                    dir = "DIM";
                    dist = hunter.getWorld().getEnvironment().name().substring(0, 1);
                }
            }

            String line = "§f" + name + " §8| §e" + dir + " §8| §a" + dist + "§r" + (char) ('a' + (score % 26));
            if (line.length() > 40) line = line.substring(0, 40);
            objective.getScore(line).setScore(Math.max(score--, 1));
            if (score <= 0) break;
        }

        viewer.setScoreboard(scoreboard);
        teamCompassViewers.add(viewer.getUniqueId());
    }

    private void clearTeamCompassSidebar() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard main = manager.getMainScoreboard();

        for (UUID id : teamCompassViewers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.setScoreboard(main);
            }
        }
        teamCompassViewers.clear();
    }

    private void startTeamCompassSidebar() {
        stopTeamCompassSidebar();
        if (!teamCompassEnabled) return;
        if (!started) return;

        teamCompassTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (!started || !teamCompassEnabled) {
                stopTeamCompassSidebar();
                return;
            }

            for (UUID hunterId : hunters) {
                Player viewer = Bukkit.getPlayer(hunterId);
                if (viewer != null && viewer.isOnline()) {
                    updateHunterTeamCompassSidebar(viewer);
                }
            }
        }, 0L, 20L);
    }

    private void stopTeamCompassSidebar() {
        if (teamCompassTaskId != -1) {
            Bukkit.getScheduler().cancelTask(teamCompassTaskId);
            teamCompassTaskId = -1;
        }
        clearTeamCompassSidebar();
    }

    private void clearAllAdvancements(Player player) {
        Iterator<Advancement> advancements = Bukkit.advancementIterator();
        while (advancements.hasNext()) {
            Advancement advancement = advancements.next();
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            for (String criteria : progress.getAwardedCriteria()) {
                progress.revokeCriteria(criteria);
            }
        }
    }

    private void prepareAllPlayersForStart() {
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.LOCATOR_BAR, false);
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.getInventory().clear();
            online.getInventory().setArmorContents(null);
            online.getInventory().setExtraContents(null);
            online.getInventory().setItemInOffHand(null);
            online.updateInventory();

            if (online.getAttribute(Attribute.MAX_HEALTH) != null) {
                online.setHealth(online.getAttribute(Attribute.MAX_HEALTH).getValue());
            }
            online.setFoodLevel(20);
            online.setSaturation(20f);
            online.setFireTicks(0);
            online.setFallDistance(0f);
            online.removePotionEffect(PotionEffectType.SATURATION);
            online.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 200, 0, false, false, false));

            clearAllAdvancements(online);
        }
    }

    public Boolean startManhunt(Location startLocation){
        waitingForStart = false;
        if (!started) {
            if (startLocation != null && startLocation.getWorld() != null) {
                startLocation.getWorld().setSpawnLocation(startLocation);
            }
            prepareAllPlayersForStart();
            setStatus(true);
            startTimer();
            playingSpeedrunners.clear();
            Bukkit.broadcastMessage("§aManhunt started!");
            playingSpeedrunners.addAll(speedrunners);
            clearEliminatedSpeedrunners();
            speedrunnerPortalTransitions.clear();
            lastEliminatedSpeedrunnerLocation = null;
            unfreezeHunters();
            giveCompassesToAllHunters();
            startHunterReleaseCountdown();
            startTeamCompassSidebar();
            return true;
        }
        return false;
    }

    public Boolean stopManhunt(){
        if (started) {
            setStatus(false);
            stopTimer();
            playingSpeedrunners.clear();
            clearEliminatedSpeedrunners();
            speedrunnerPortalTransitions.clear();
            lastEliminatedSpeedrunnerLocation = null;
            unfreezeHunters();
            stopTeamCompassSidebar();
            Bukkit.broadcastMessage("§aManhunt stopped!");
            return true;
        }
        return false;
    }

    public List<Player> getOpponents(Player player) {
        if (hunters.contains(player.getUniqueId())) {
            return getPlayers(speedrunners);
        } else if (speedrunners.contains(player.getUniqueId())) {
            return getPlayers(hunters);
        }
        return Collections.emptyList();
    }

    public List<Player> getPlayers(List<UUID> players){
        List<Player> e = new ArrayList<>();

        for (UUID p : players){
            e.add(Bukkit.getPlayer(p));
        }

        return e;
    }

    // utils
    private Player getDefaultTrackedSpeedrunner() {
        for (UUID id : playingSpeedrunners) {
            Player target = Bukkit.getPlayer(id);
            if (target != null && target.isOnline()) {
                return target;
            }
        }
        return null;
    }

    private PortalTransition getLatestTransitionToEnv(Deque<PortalTransition> transitions, World.Environment env) {
        Iterator<PortalTransition> itr = transitions.descendingIterator();
        while (itr.hasNext()) {
            PortalTransition transition = itr.next();
            if (transition.toEnv == env) {
                return transition;
            }
        }
        return null;
    }

    private Location getFallbackPortalLocation(Deque<PortalTransition> transitions, World.Environment hunterEnv) {
        Iterator<PortalTransition> itr = transitions.descendingIterator();
        while (itr.hasNext()) {
            PortalTransition transition = itr.next();
            if (transition.fromEnv == hunterEnv) {
                return transition.fromLocation.clone();
            }
        }

        itr = transitions.descendingIterator();
        while (itr.hasNext()) {
            PortalTransition transition = itr.next();
            if (transition.toEnv == hunterEnv) {
                return transition.toLocation.clone();
            }
        }
        return null;
    }

    public Location resolveTrackingLocation(Player hunter, Player target) {
        if (target == null || !target.isOnline()) return null;
        if (hunter == null || hunter.getWorld() == null || target.getWorld() == null) {
            return target.getLocation();
        }

        World.Environment hunterEnv = hunter.getWorld().getEnvironment();
        World.Environment targetEnv = target.getWorld().getEnvironment();
        if (hunterEnv == targetEnv) {
            return target.getLocation();
        }

        Deque<PortalTransition> transitions = speedrunnerPortalTransitions.get(target.getUniqueId());
        if (transitions == null || transitions.isEmpty()) {
            return target.getLocation();
        }

        Set<World.Environment> visited = new HashSet<>();
        World.Environment cursor = targetEnv;
        while (visited.add(cursor)) {
            PortalTransition step = getLatestTransitionToEnv(transitions, cursor);
            if (step == null) break;
            if (step.fromEnv == hunterEnv) {
                return step.fromLocation.clone();
            }
            cursor = step.fromEnv;
        }

        Location fallback = getFallbackPortalLocation(transitions, hunterEnv);
        if (fallback != null) {
            return fallback;
        }
        return target.getLocation();
    }

    public Player getNextPlayingSpeedrunner(UUID currentSpeedrunnerId) {
        if (playingSpeedrunners.isEmpty()) return null;

        int startIndex = speedrunners.indexOf(currentSpeedrunnerId);
        if (startIndex < 0) return getDefaultTrackedSpeedrunner();

        int total = speedrunners.size();
        for (int i = 1; i <= total; i++) {
            UUID candidateId = speedrunners.get((startIndex + i) % total);
            if (!playingSpeedrunners.contains(candidateId)) continue;

            Player candidate = Bukkit.getPlayer(candidateId);
            if (candidate != null && candidate.isOnline()) {
                return candidate;
            }
        }

        return getDefaultTrackedSpeedrunner();
    }

    public void setLastEliminatedSpeedrunnerLocation(Location location) {
        if (location == null) return;
        lastEliminatedSpeedrunnerLocation = location.clone();
    }

    public void endManhuntWithAllSpeedrunnersEliminated() {
        Location targetLocation = lastEliminatedSpeedrunnerLocation;
        if (targetLocation != null) {
            targetLocation = targetLocation.clone();
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (targetLocation != null) {
                online.teleport(targetLocation);
            }
            online.setGameMode(GameMode.SPECTATOR);
        }

        stopManhunt();
    }

    public void giveDefaultCompassToHunter(Player hunter) {
        if (hunter == null || !hunter.isOnline()) return;
        if (!hunters.contains(hunter.getUniqueId())) return;

        Player target = getDefaultTrackedSpeedrunner();
        if (target == null) {
            hunter.sendMessage("§cNo playing speedrunner is online to track.");
            return;
        }

        giveCompass(target, hunter);
    }

    public void giveCompassesToAllHunters() {
        for (UUID id : hunters) {
            Player hunter = Bukkit.getPlayer(id);
            if (hunter != null && hunter.isOnline()) {
                giveDefaultCompassToHunter(hunter);
            }
        }
    }

    public void refreshCompassesForTarget(Player target) {
        if (target == null || !target.isOnline()) return;

        for (Map.Entry<UUID, Player> entry : trackedCompasses.entrySet()) {
            UUID compassId = entry.getKey();
            Player trackedTarget = entry.getValue();
            if (trackedTarget == null || !trackedTarget.getUniqueId().equals(target.getUniqueId())) continue;

            for (Player online : Bukkit.getOnlinePlayers()) {
                for (ItemStack item : online.getInventory().getContents()) {
                    updateCompass(item, compassId, target, online);
                }
            }
        }
    }

    public void giveCompass(Player target, Player player){
        if (!playingSpeedrunners.contains(target.getUniqueId())) {
            player.sendMessage("§cPlayer is not a playing speedrunner!");
            return;
        }

        if (target.isOnline()) {
            ItemStack compass = new ItemStack(Material.COMPASS);
            CompassMeta meta = (CompassMeta) compass.getItemMeta();
            if (meta != null) {
                Location loc = target.getLocation();
                meta.setLodestone(loc);
                meta.setLodestoneTracked(false);

                UUID compassId = UUID.randomUUID();
                meta.getPersistentDataContainer().set(COMPASS_ID_KEY, PersistentDataType.STRING, compassId.toString());
                if (getConfig().getBoolean("name-tracking-compass")){
                    meta.setDisplayName("§bTracking §e" + target.getName());
                }
                compass.setItemMeta(meta);
                trackedCompasses.put(compassId, target);
            }

            player.getInventory().addItem(compass);
            player.sendMessage("§bTracking compass given for speedrunner " + target.getName());
        } else {
            player.sendMessage("§cPlayer is not online");
        }
    }

    private Location getSafeVillageTeleport(World world, Location villageCenter) {
        if (world == null || villageCenter == null) return null;
        int[] offsets = {6, -6, 10, -10};
        for (int xOffset : offsets) {
            for (int zOffset : offsets) {
                int x = villageCenter.getBlockX() + xOffset;
                int z = villageCenter.getBlockZ() + zOffset;
                int y = world.getHighestBlockYAt(x, z) + 1;
                Location candidate = new Location(world, x + 0.5, y, z + 0.5);
                if (candidate.getBlock().getType().isAir() && candidate.clone().add(0, 1, 0).getBlock().getType().isAir()) {
                    return candidate;
                }
            }
        }
        int fallbackY = world.getHighestBlockYAt(villageCenter) + 1;
        return new Location(world, villageCenter.getX() + 0.5, fallbackY, villageCenter.getZ() + 0.5);
    }

    private List<Map.Entry<String, Structure>> getVillageStructures() {
        return Registry.STRUCTURE.stream()
                .map(structure -> {
                    NamespacedKey key = Registry.STRUCTURE.getKey(structure);
                    if (key == null) return null;
                    if (!"minecraft".equals(key.getNamespace())) return null;
                    if (!key.getKey().startsWith("village_")) return null;
                    return Map.entry(key.getKey(), structure);
                })
                .filter(Objects::nonNull)
                .toList();
    }


    // actual shi
    @Override
    public void onEnable() {
        instance = this;
        basicLootPool = LootPool.createDefault();
        TwistsHelper helper = new TwistsHelper();

        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new EventListeners(helper), this);
        getLogger().info("Manhunt plugin loaded!");

        new BukkitRunnable() {
            public void run() {
                if (getConfig().getBoolean("auto-calibration")){
                    Iterator<Map.Entry<UUID, Player>> itr = trackedCompasses.entrySet().iterator();
                    while (itr.hasNext()) {
                        Map.Entry<UUID, Player> entry = itr.next();
                        UUID compassId = entry.getKey();
                        Player target = entry.getValue();

                        if (target == null || !target.isOnline()) {
                            itr.remove();
                            continue;
                        }

                        for (Player p : Bukkit.getOnlinePlayers()) {
                            for (ItemStack item : p.getInventory().getContents()) {
                                updateCompass(item, compassId, target, p);
                            }
                        }
                    }
                }

            }
        }.runTaskTimer(this, 0L, getConfig().getLong("auto-calibration-interval")*20);

    }

    @Override
    public void onDisable() {
        stopTeamCompassSidebar();
        getLogger().info("Bye :(");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) return false;

        if (cmd.getName().equalsIgnoreCase("compass")) {
            if (speedrunners.isEmpty()) {
                player.sendMessage("§cNo speedrunner set.");
                return true;
            }

            Player target;
            if (args.length >= 1){
                target = Bukkit.getPlayer(args[0]);
            } else {
                target = Bukkit.getPlayer(speedrunners.getFirst());
            }
            if (target != null){
                giveCompass(target, player);
            } else {
                player.sendMessage("§cPlayer not found");
            }
            return true;
        } else if (cmd.getName().equalsIgnoreCase("speedrunner")){
            if (args.length == 2){
                if (args[0].equalsIgnoreCase("add")){
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target != null && target.isOnline()) {
                        if (hunters.contains(target.getUniqueId())) {
                        player.sendMessage("§cPlayer is a hunter!");
                        return true;
                        }
                        if (!speedrunners.contains(target.getUniqueId())){
                            addSpeedrunner(target);
                            Bukkit.broadcastMessage("§a"+target.getName() + " is now a speedrunner!");
                        } else {
                            player.sendMessage("§cPlayer is already a speedrunner!");
                        }

                    } else {
                        player.sendMessage("§cPlayer not found or not online.");
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("remove")){
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target != null && target.isOnline()) {
                        if (speedrunners.contains(target.getUniqueId())){
                            removeSpeedrunner(target);
                            Bukkit.broadcastMessage("§a"+target.getName() + " is no longer a speedrunner!");
                        } else {
                            player.sendMessage("§cPlayer is not a speedrunner!");
                        }
                    } else {
                        player.sendMessage("§cPlayer not found or not online.");
                    }
                    return true;
                }
            }
            return false;
        } else if (cmd.getName().equalsIgnoreCase("hunter")){

            if (args.length == 2){
                if (args[0].equalsIgnoreCase("add")){
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target != null && target.isOnline()) {
                        if (speedrunners.contains(target.getUniqueId())) {
                        player.sendMessage("§cPlayer is a speedrunner!");
                        return true;
                        }
                        if (!hunters.contains(target.getUniqueId())){
                            addHunter(target);
                            Bukkit.broadcastMessage("§a"+target.getName() + " is now a hunter!");
                        } else {
                            player.sendMessage("§cPlayer is already a hunter!");
                        }

                    } else {
                        player.sendMessage("§cPlayer not found or not online.");
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("remove")){
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target != null && target.isOnline()) {
                        if (hunters.contains(target.getUniqueId())){
                            removeHunter(target);
                            Bukkit.broadcastMessage("§a"+target.getName() + " is no longer a hunter!");
                        } else {
                            player.sendMessage("§cPlayer is not a hunter!");
                        }
                    } else {
                        player.sendMessage("§cPlayer not found or not online.");
                    }
                    return true;
                }
            }
            return false;
        } else if (cmd.getName().equalsIgnoreCase("twist")) {
            if (args.length == 1) {
                try {
                    Twist selected = Twist.valueOf(args[0].toUpperCase());
                    setTwist(selected);
                    player.sendMessage("§aTwist set to: " + selected.name());
                } catch (IllegalArgumentException e) {
                    player.sendMessage("§cUnknown twist: " + args[0]);
                    player.sendMessage("§eAvailable twists: " + Arrays.toString(Twist.values()));
                }
                return true;
            } else {
                player.sendMessage("§eCurrent Twist is "+twist);
                player.sendMessage("§eUsage: /twist <twistName>");
            }
        } else if (cmd.getName().equalsIgnoreCase("manhunt")) {
            if (args.length == 0) {
                player.sendMessage("§eUsage: /manhunt <start|stop|countdown|startcountdown|teamcompass|prepare|list|help>");
                return true;
            }

            switch (args[0].toLowerCase()) { // first time i actually used a switch case my whole life (if statements worked well so like it wasn't necessary but like worth trying)
                case "start" -> {
                    if (!startManhunt(player.getLocation())){
                        player.sendMessage("§cManhunt already running.");
                    }
                }
                case "stop" -> {
                    if (!stopManhunt()){
                        player.sendMessage("§cManhunt is not running.");
                    }
                }
                case "countdown" -> {
                    if (args.length == 2) {
                        try {
                            int mins = Integer.parseInt(args[1]);
                            countdownLimitMinutes = mins;
                            if (mins > 0) {
                                player.sendMessage("§aCountdown set to " + mins + " minute(s).");
                            } else {
                                player.sendMessage("§eCountdown disabled.");
                            }
                        } catch (NumberFormatException e) {
                            player.sendMessage("§cInvalid number.");
                        }
                    } else {
                        if (countdownLimitMinutes > 0)
                            player.sendMessage("§eCurrent countdown limit: " + countdownLimitMinutes + " minute(s).");
                        else
                            player.sendMessage("§eCountdown is disabled.");
                        player.sendMessage("§eUse /manhunt countdown <minutes>, set minutes to 0 to disable");
                    }
                }
                case "startcountdown" -> {
                    if (args.length == 2) {
                        try {
                            int secs = Integer.parseInt(args[1]);
                            if (secs < 0) {
                                player.sendMessage("§cCountdown must be 0 or higher.");
                                return true;
                            }
                            setStartCountdownSeconds(secs);
                            if (secs == 0) {
                                player.sendMessage("§eHunter start countdown disabled.");
                            } else {
                                player.sendMessage("§aHunter start countdown set to " + secs + " second(s).");
                            }
                        } catch (NumberFormatException e) {
                            player.sendMessage("§cInvalid number.");
                        }
                    } else {
                        player.sendMessage("§eCurrent hunter start countdown: " + getStartCountdownSeconds() + " second(s).");
                        player.sendMessage("§eUse /manhunt startcountdown <seconds>, set to 0 to disable");
                    }
                }
                case "teamcompass" -> {
                    if (started) {
                        player.sendMessage("§cSet team compass before starting manhunt.");
                        return true;
                    }
                    if (args.length == 2) {
                        if (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true")) {
                            setTeamCompassEnabled(true);
                            player.sendMessage("§aTeam compass sidebar enabled.");
                        } else if (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("false")) {
                            setTeamCompassEnabled(false);
                            player.sendMessage("§eTeam compass sidebar disabled.");
                        } else {
                            player.sendMessage("§cUsage: /manhunt teamcompass <on|off>");
                        }
                    } else {
                        player.sendMessage("§eTeam compass is currently " + (isTeamCompassEnabled() ? "enabled" : "disabled") + ".");
                        player.sendMessage("§eUse /manhunt teamcompass <on|off>");
                    }
                }
                case "help" -> {
                    player.sendMessage("To use our plugin start adding speedrunners with: /speedrunner add, use /speedrunner remove to remove a speedrunner");
                    player.sendMessage("You can then add the hunters using /hunter add & remove them using /hunter remove");
                    player.sendMessage("To start the manhunt run §e/manhunt start");
                    player.sendMessage("Manhunt countdown (max limit a manhunt can last in minutes) can be set using §e/manhunt cooldown");
                    player.sendMessage("Freeze hunters at start with §e/manhunt startcountdown <seconds>");
                    player.sendMessage("Show hunter teammate direction+distance with §e/manhunt teamcompass <on|off>");
                    player.sendMessage("Read full documentation on Modrinth/Discord/Website");
                }
                case "prepare" -> {
                    waitingForStart = true;
                    Bukkit.broadcastMessage("Waiting for speedrunner first hit");
                }
                case "list" -> {
                    player.sendMessage("§eSpeedrunners:");
                    for (UUID id : speedrunners){
                        player.sendMessage(Bukkit.getPlayer(id).getName());
                    }
                    player.sendMessage("§eHunters:");
                    for (UUID id : hunters){
                        player.sendMessage(Bukkit.getPlayer(id).getName());
                    }
                    player.sendMessage("§ePlaying Speedrunners:");
                    for (UUID id : playingSpeedrunners){
                        player.sendMessage(Bukkit.getPlayer(id).getName());
                    }
                }
                default -> player.sendMessage("§cUnknown subcommand. Use: start, stop, countdown, startcountdown, teamcompass");
            }

            return true;
        } else if (cmd.getName().equalsIgnoreCase("surround")){
            if (speedrunners.isEmpty()) {
                player.sendMessage("§cNo speedrunner set.");
                return true;
            }

            if (args.length >= 1){
                Player target = Bukkit.getPlayer(args[0]);
                if (target != null && target.isOnline()) {
                    if (!speedrunners.contains(target.getUniqueId())) {
                        player.sendMessage("§cPlayer is not a speedrunner!");
                        return true;
                    }

                    Location center = target.getLocation();
                    double radius = getConfig().getDouble("surround-radius");
                    int n = hunters.size();
                    // chatgpt slop, my ass is too stupid to calculate ts
                    for (int i = 0; i < n; i++) {
                        Player p = Bukkit.getPlayer(hunters.get(i));

                        double angle = 2 * Math.PI * i / n;
                        double xOffset = radius * Math.cos(angle);
                        double zOffset = radius * Math.sin(angle);

                        Location newLoc = center.clone().add(xOffset, 0, zOffset);
                        newLoc.setDirection(center.toVector().subtract(newLoc.toVector())); // face center
                        p.teleport(newLoc);
                    }
                    player.sendMessage("§aSurrounded  "+target.getName());
                } else {
                    player.sendMessage("§cPlayer not found or not online");
                }

                return true;
            }

        } else if (cmd.getName().equalsIgnoreCase("randomvillage")) {
            List<Map.Entry<String, Structure>> villageStructures = getVillageStructures();
            if (villageStructures.isEmpty()) {
                player.sendMessage("§cNo village structures found in the structure registry.");
                return true;
            }
            Map.Entry<String, Structure> selectedVillage = villageStructures.get(new Random().nextInt(villageStructures.size()));
            String villageKey = selectedVillage.getKey();
            Structure randomVillage = selectedVillage.getValue();

            World world = player.getWorld();
            StructureSearchResult nearestVillage = world.locateNearestStructure(player.getLocation(), randomVillage, 512, false);
            if (nearestVillage == null) {
                player.sendMessage("§cCouldn't find a " + villageKey.replace('_', ' ') + " nearby.");
                return true;
            }

            Location teleportLocation = getSafeVillageTeleport(world, nearestVillage.getLocation());
            player.teleport(teleportLocation);
            String villageName = villageKey.replace("village_", "").toLowerCase(Locale.ROOT);
            player.sendMessage("§aTeleported near the nearest §e" + villageName + " §avillage.");
            return true;
        }

        return false;
    }
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command cmd, @NotNull String label, String @NotNull [] args) {
        if (cmd.getName().equalsIgnoreCase("twist") && args.length == 1) {
            return Arrays.stream(Twist.values())
                    .map(Enum::name)
                    .map(String::toLowerCase)
                    .filter(name -> name.startsWith(args[0].toLowerCase()))
                    .toList();
        } else if (cmd.getName().equalsIgnoreCase("speedrunner") || cmd.getName().equalsIgnoreCase("hunter")) {
            if (args.length == 1) {
                return Stream.of("add", "remove")
                        .filter(o -> o.startsWith(args[0].toLowerCase()))
                        .toList();
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
        } else if (cmd.getName().equalsIgnoreCase("manhunt")) {
            if (args.length == 1) {
                return Stream.of("help","start", "stop", "countdown", "startcountdown", "teamcompass", "prepare", "list")
                        .filter(s -> s.startsWith(args[0].toLowerCase()))
                        .toList();
            } else if (args.length == 2 && args[0].equalsIgnoreCase("teamcompass")) {
                return List.of("on", "off");
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("countdown") || args[0].equalsIgnoreCase("startcountdown"))) {
                return List.of("0", "5", "10", "15", "30", "60");
            }
        }
        return Collections.emptyList();
    }


}



