# Migrating From Dymeth PhysicsControl

OpenPhysicsControl can import per-world settings from [Dymeth/PhysicsControl](https://github.com/Dymeth/PhysicsControl) automatically. It reads the current legacy layout at `plugins/PhysicsControl/triggers/<world-name>.yml` and the pre-1.1 layout that stored a world section in `plugins/PhysicsControl/config.yml`.

## Upgrade procedure

1. Stop the server and remove the old PhysicsControl JAR. Keep its `plugins/PhysicsControl` data directory.
2. Install OpenPhysicsControl and start the server.
3. On first load of each world, OpenPhysicsControl writes `plugins/OpenPhysicsControl/worlds/<world-name>.yml` from its legacy data when no named OpenPhysicsControl file or UUID-era OpenPhysicsControl file exists.
4. Check the startup log for the imported-rule count and any triggers that have no equivalent rule.

The legacy source is never moved, edited, or deleted. Existing OpenPhysicsControl world files always win, so a second start is idempotent and cannot overwrite newer configuration.

## Rule mapping

The old plugin exposed material- and actor-specific switches while OpenPhysicsControl groups shared Bukkit event surfaces into broader rules. Every supported legacy `false` is therefore preserved: when several old switches feed one new rule, a single `false` makes the new rule `false`. This can stop a wider set of actions than the old narrow switch, but it never re-enables a behavior the old configuration had stopped.

| OpenPhysicsControl rule | Imported legacy triggers |
|---|---|
| `mob-griefing` | Rabbit, villager, sheep, silverfish, zombie, enderman, wither, turtle-laying, fox, ravager, frogspawn placement, and burning-entity powder-snow triggers |
| `mob-block-form` | Snow golem snow and wither rose creation |
| `frogspawn-hatch` | Frogspawn laying and spawning |
| `fire-ignite` | Player flint usage |
| `bone-meal` | `PLAYERS_BONE_MEAL_USAGE` and the older `BONE_MEAL_USAGE` name |
| `tnt-prime` | Burning arrows activating TNT |
| `farmland-trample`, `turtle-egg-trample`, `dripleaf-tilt`, `dragon-egg-teleport`, `frosted-ice` | Corresponding legacy physical-interaction triggers |
| `block-updates` | Ladders, signs, rails, torches, redstone torches, soul torches, and saplings losing support |
| `gravity` | Gravel, sand, anvils, dragon eggs, concrete powder, scaffolding, and pointed dripstone falling |
| `water-flow`, `lava-flow` | Water and lava flowing |
| `fire-spread`, `snow-melt`, `farmland-dry`, `ice-melt`, `leaf-decay`, `coral-fade`, `ground-fade`, `sculk-spread` | Corresponding natural world-change triggers |
| `plant-spread`, `mushroom-growth`, `stem-growth`, `crop-growth`, `amethyst-growth` | Grass/mycelium, mushrooms, melons/pumpkins, crop, and amethyst triggers |
| `vertical-plant-growth`, `tree-growth`, `vine-growth`, `dripstone-growth` | Cane/cactus/chorus/kelp/bamboo, trees, vine variants/glow berries, and pointed dripstone triggers |

The following legacy values are intentionally reported rather than silently mapped because OpenPhysicsControl has no behaviorally equivalent rule: `END_PORTAL_FRAMES_FILLING`, `GLOW_BERRIES_PICKING`, and `BLOCK_HIT_PROJECTILES_REMOVING`. Legacy internal settings `DEBUG_MESSAGES`, `ALLOW_UNRECOGNIZED_ACTIONS`, and `IGNORED_STATE` are also not imported.
