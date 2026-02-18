package net.tutla.manhuntPlus.domain;

import org.bukkit.Location;
import org.bukkit.World;

public record PortalTransition(
        World.Environment fromEnvironment,
        World.Environment toEnvironment,
        Location fromLocation,
        Location toLocation
) {
}
