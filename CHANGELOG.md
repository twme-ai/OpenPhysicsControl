# Changelog

## 2.8.0-SNAPSHOT

- Removed the dedicated glow berry picking option; player harvesting now follows `player-block-interactions`.
- Added broad player block interaction controls with per-material overrides.
- Added player entity interaction and vehicle-entry controls.
- Added hanging entity detachment controls for item frames and paintings.
- Added block-origin explosion controls for beds and respawn anchors.
- Added a dedicated sniffer egg rule covering cracking and final hatching.
- Added independent control of initial connections on newly placed fences, panes/bars, walls, stairs, fence gates, and chests.
- Kept placement-time connections separate from neighbor `block-updates`, with per-material overrides for both sides of a placement.
- Added a GitHub Actions workflow that tests both Spigot and Paper API profiles and uploads the shaded JAR.
- Added `openphysicscontrol.set.current-world` for delegating rule changes only in a player's current world, across commands and the GUI.

## 2.7.0 - 2026-07-30

- Added exact per-world `Material` overrides while preserving every existing one-click rule value and default-rule inheritance.
- Added `/pc material <rule> <material> <on|off|toggle|clear> [world]`, configuration validation, and localized GUI override counts.
- Applied material overrides to portable block event surfaces, with dedicated partial protection for explosion block lists and whole-action cancellation for piston moves.

## 2.6.0 - 2026-07-23

- Added independent controls for standard/trial spawner output, entity explosion priming, oxygen depletion, fire/heat damage, freezing damage, and vehicle-to-entity collision.
- Kept oxygen recovery independent from oxygen depletion and kept ordinary contact damage outside the fire/heat rule.
- Added Mineflayer coverage for normal spawners, oxygen, fire/freezing, and entity explosions, plus classifier and cross-API coverage for the shared event surfaces.

## 2.5.0 - 2026-07-23

- Added independent controls for End portal frame filling and glow berry picking.
- Restored the legacy optional removal of arrows and tridents that hit blocks, preserving its disabled-by-default behavior.
- Migrated all three corresponding Dymeth PhysicsControl triggers and covered them through Mineflayer behavior tests.

## 2.4.0 - 2026-07-23

- Added automatic per-world migration from Dymeth PhysicsControl, including its pre-1.1 data layout and historical bone-meal trigger name.
- Preserved legacy source files, protected existing OpenPhysicsControl world files, and report unsupported legacy triggers clearly.
- Reorganized the GUI into the legacy-inspired three-row interaction, world, growth, and machines layout.

## 2.3.0 - 2026-07-23

- Made `tree-growth` stop the natural age 0-to-4 maturation of hanging mangrove propagules.
- Kept explicit block-state changes and `bone-meal` behavior independent from the natural maturation control.
- Added a Mineflayer black-box test for blocked and enabled hanging-propagule maturation.

## 2.2.0 - 2026-07-23

- Added `default-rules.yml` with explicit defaults for every physics rule.
- Changed per-world storage to readable world-name files with non-destructive UUID-file migration and safe filename encoding.
- Documented the tested controls and Bukkit event limitation for planted, fertilized, and hanging mangrove propagules.
- Added resource, filename-safety, and world-file migration tests.

## 2.1.0 - 2026-07-23

- Replaced the flat paginated GUI with a centered category menu and centered rule submenus.
- Made rule states explicit as physics running or physics stopped, including upgrade-safe bundled wording migration.
- Switched Paper lightning transformations to the supported `EntityZapEvent` while retaining a runtime Spigot fallback.
- Expanded Mineflayer coverage to navigate all five categories, validate layouts, and toggle the explicit state text.

## 2.0.1 - 2026-07-23

- Added `/pc` as an alias for `/openphysics`.
- Exercised the new alias through the Mineflayer localized-menu player path.

## 2.0.0 - 2026-07-22

- Expanded per-world control from 21 aggregate switches to 71 physics rules.
- Added block updates, fluid reactions, climate, redstone, portals, sculk, spawning, entity effects, and machine processing.
- Added a paginated 54-slot rule menu with localized rule groups.
- Preserved disabled 1.x aggregate settings when migrating world files.
- Added Paper/Spigot classifier tests and ten Mineflayer black-box cases for gravity, block updates, fluids, sponges, TNT, redstone, pistons, and explosion block damage.
- Added a Mineflayer player-path check for Traditional Chinese locale selection and both GUI pages.
- Registered the current Paper/Spigot knockback and cube-mob split events dynamically across their API naming differences.
- Covered hopper pickup, brewing fuel, exhaustion, breeding entry, and distinct mob-state transition events.
