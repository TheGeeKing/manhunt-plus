package net.tutla.manhuntPlus.domain;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GameState {
    private MatchPhase phase = MatchPhase.IDLE;
    private TwistType twist = TwistType.DEFAULT;
    private boolean waitingForStart;
    private int elapsedSeconds;

    private boolean huntersFrozen;
    private int huntersFreezeSecondsRemaining;
    private final Set<UUID> freezeRespawnExemptHunters = new HashSet<>();

    private final Set<UUID> speedrunners = new HashSet<>();
    private final Set<UUID> hunters = new HashSet<>();
    private final Set<UUID> activeSpeedrunners = new HashSet<>();
    private final Set<UUID> eliminatedSpeedrunners = new HashSet<>();

    private final Map<UUID, Location> eliminatedSpeedrunnerDeathLocations = new HashMap<>();
    private Location lastEliminatedSpeedrunnerLocation;

    private final Map<UUID, UUID> trackedCompasses = new HashMap<>();
    private final Map<UUID, Deque<PortalTransition>> speedrunnerPortalTransitions = new HashMap<>();
    private final Set<UUID> teamCompassViewers = new HashSet<>();

    public MatchPhase getPhase() {
        return phase;
    }

    public void setPhase(MatchPhase phase) {
        this.phase = phase;
    }

    public TwistType getTwist() {
        return twist;
    }

    public void setTwist(TwistType twist) {
        this.twist = twist;
    }

    public boolean isWaitingForStart() {
        return waitingForStart;
    }

    public void setWaitingForStart(boolean waitingForStart) {
        this.waitingForStart = waitingForStart;
    }

    public int getElapsedSeconds() {
        return elapsedSeconds;
    }

    public void resetElapsedSeconds() {
        this.elapsedSeconds = 0;
    }

    public void incrementElapsedSeconds() {
        this.elapsedSeconds++;
    }

    public boolean isHuntersFrozen() {
        return huntersFrozen;
    }

    public void setHuntersFrozen(boolean huntersFrozen) {
        this.huntersFrozen = huntersFrozen;
    }

    public int getHuntersFreezeSecondsRemaining() {
        return huntersFreezeSecondsRemaining;
    }

    public void setHuntersFreezeSecondsRemaining(int huntersFreezeSecondsRemaining) {
        this.huntersFreezeSecondsRemaining = huntersFreezeSecondsRemaining;
    }

    public void decrementHuntersFreezeSecondsRemaining() {
        if (huntersFreezeSecondsRemaining > 0) {
            this.huntersFreezeSecondsRemaining--;
        }
    }

    public Set<UUID> getFreezeRespawnExemptHunters() {
        return freezeRespawnExemptHunters;
    }

    public Set<UUID> getSpeedrunners() {
        return speedrunners;
    }

    public Set<UUID> getHunters() {
        return hunters;
    }

    public Set<UUID> getActiveSpeedrunners() {
        return activeSpeedrunners;
    }

    public Set<UUID> getEliminatedSpeedrunners() {
        return eliminatedSpeedrunners;
    }

    public Map<UUID, Location> getEliminatedSpeedrunnerDeathLocations() {
        return eliminatedSpeedrunnerDeathLocations;
    }

    public Location getLastEliminatedSpeedrunnerLocation() {
        return lastEliminatedSpeedrunnerLocation;
    }

    public void setLastEliminatedSpeedrunnerLocation(Location lastEliminatedSpeedrunnerLocation) {
        this.lastEliminatedSpeedrunnerLocation = lastEliminatedSpeedrunnerLocation == null
                ? null
                : lastEliminatedSpeedrunnerLocation.clone();
    }

    public Map<UUID, UUID> getTrackedCompasses() {
        return trackedCompasses;
    }

    public Map<UUID, Deque<PortalTransition>> getSpeedrunnerPortalTransitions() {
        return speedrunnerPortalTransitions;
    }

    public Set<UUID> getTeamCompassViewers() {
        return teamCompassViewers;
    }

    public void recordPortalTransition(UUID playerId, Location from, Location to) {
        if (playerId == null || from == null || to == null || from.getWorld() == null || to.getWorld() == null) {
            return;
        }
        World.Environment fromEnv = from.getWorld().getEnvironment();
        World.Environment toEnv = to.getWorld().getEnvironment();
        if (fromEnv == toEnv) {
            return;
        }

        Deque<PortalTransition> transitions = speedrunnerPortalTransitions.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        transitions.addLast(new PortalTransition(fromEnv, toEnv, from.clone(), to.clone()));
        while (transitions.size() > 64) {
            transitions.removeFirst();
        }
    }

    public void clearRoundState() {
        waitingForStart = false;
        resetElapsedSeconds();
        huntersFrozen = false;
        huntersFreezeSecondsRemaining = 0;
        freezeRespawnExemptHunters.clear();
        activeSpeedrunners.clear();
        eliminatedSpeedrunners.clear();
        eliminatedSpeedrunnerDeathLocations.clear();
        speedrunnerPortalTransitions.clear();
        lastEliminatedSpeedrunnerLocation = null;
        trackedCompasses.clear();
    }
}
