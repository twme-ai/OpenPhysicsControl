# Changelog

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
