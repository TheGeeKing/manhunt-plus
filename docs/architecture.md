# ManhuntPlus Architecture

## Layered Structure

- `net.tutla.manhuntPlus.bootstrap`
  - Plugin entrypoint and dependency wiring.
- `net.tutla.manhuntPlus.domain`
  - Core game state and enums (`GameState`, `MatchPhase`, `TwistType`).
- `net.tutla.manhuntPlus.application`
  - Match orchestration and gameplay services (`MatchService`, `RoleService`, `CompassService`, `FreezeService`, `SidebarService`, `TwistService`).
- `net.tutla.manhuntPlus.bukkit.commands`
  - Command adapters only.
- `net.tutla.manhuntPlus.bukkit.listeners`
  - Event adapters only.
- `net.tutla.manhuntPlus.bukkit.tasks`
  - Managed repeating tasks.

## Dependency Flow

`bootstrap -> application -> domain`  
`bukkit commands/listeners -> application -> domain`

No domain class depends on Bukkit plugin lifecycle wiring.

## Responsibilities

- `ManhuntPlusPlugin`
  - Loads config, instantiates services, registers commands/listeners.
- `GameState`
  - Holds all mutable runtime match state.
- `MatchSettings`
  - Holds validated runtime settings.
- `MatchService`
  - Controls match phases and timer lifecycle.
- `RoleService`
  - Manages team assignments and role constraints.
- `CompassService`
  - Compass PDC mapping, calibration, and cross-dimension hints.
- `FreezeService`
  - Hunter start freeze behavior and movement/damage restrictions.
- `SidebarService`
  - Team compass scoreboard lifecycle.
- `TwistService`
  - Twist selection and twist-specific gameplay effects.
