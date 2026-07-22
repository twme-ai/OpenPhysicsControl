# Minecraft physics coverage

This audit defines "physics" as autonomous world simulation, physical entity effects, and block-machine processing that can be controlled through portable Bukkit events. The inventory was derived from the Paper 26.2 and Spigot 26.2 API event classes under `block`, `world`, `weather`, relevant `entity`, and `inventory` packages.

It intentionally excludes direct block break/place and inventory actions (protection-plugin scope), commands and custom plugin mutations, client-side collision prediction, chunk generation, and mob AI movement for which Spigot has no portable cancellable event. All rules are enabled by default.

Evidence codes:

- **MF**: positive and negative behavior observed by Mineflayer on a real Paper server.
- **UT**: material/reason routing covered by a unit test.
- **API**: handler is bound to the listed Bukkit event and compiled against both Paper and Spigot 26.2; deterministic black-box coverage is not yet available.

## Blocks and signals

| Rule | Bukkit event surface | Controlled behavior | Evidence |
|---|---|---|---|
| `gravity` | `BlockPhysicsEvent`, `EntityChangeBlockEvent` | All `Material.hasGravity()` blocks, including sand, gravel, anvils, concrete powder, dragon eggs, scaffolding, and pointed dripstone | MF |
| `block-updates` | `BlockPhysicsEvent` | Neighbor updates and support-dependent block detachment not handled by gravity | MF |
| `water-flow` | `BlockFromToEvent`, `FluidLevelChangeEvent` | Water and bubble-column flow/level changes | MF, UT |
| `lava-flow` | `BlockFromToEvent`, `FluidLevelChangeEvent` | Lava flow and level changes | MF, UT |
| `fluid-reactions` | `BlockFormEvent` | Stone, cobblestone, obsidian, and basalt created by liquids | MF, UT |
| `concrete-harden` | `BlockFormEvent` | Concrete powder becoming concrete | UT |
| `pistons` | `BlockPistonExtendEvent`, `BlockPistonRetractEvent` | Piston extension and retraction | MF |
| `sponge-absorb` | `SpongeAbsorbEvent` | Sponge water removal | MF |
| `dragon-egg-teleport` | `BlockFromToEvent` | Dragon egg teleportation | API |
| `portal-creation` | `PortalCreateEvent` | Nether portals, paired portals, and End platforms | API |
| `redstone` | `BlockRedstoneEvent` | Redstone current changes; restores the old current instead of cancelling a non-cancellable event | MF |
| `sculk-vibrations` | `BlockReceiveGameEvent` | Sculk sensor and shrieker game-event reception | API |
| `tnt-prime` | `TNTPrimeEvent` | TNT priming by redstone, fire, projectiles, and explosions | MF |
| `explosion-block-damage` | `EntityExplodeEvent`, `BlockExplodeEvent` | Clears affected block lists while preserving entity damage and sound | MF |

## Fire, climate, and time

| Rule | Bukkit event surface | Controlled behavior | Evidence |
|---|---|---|---|
| `fire-spread` | `BlockSpreadEvent` | Fire and soul-fire propagation | UT |
| `fire-burn` | `BlockBurnEvent` | Burning blocks away | API |
| `fire-ignite` | `BlockIgniteEvent` | Ignition by fire, lava, lightning, flint, and projectiles | API |
| `fire-extinguish` | `BlockFadeEvent` | Natural fire removal | UT |
| `ice-melt` | `BlockFadeEvent` | Ice melting | UT |
| `snow-melt` | `BlockFadeEvent` | Snow and powder-snow melting | UT |
| `frosted-ice` | `EntityBlockFormEvent`, `BlockFadeEvent` | Frost Walker ice formation and melting | UT |
| `coral-fade` | `BlockFadeEvent` | Living coral becoming dead coral | UT |
| `ground-fade` | `BlockFadeEvent` | Grass, mycelium, dirt paths, and nylium reverting | UT |
| `copper-weather` | `BlockFadeEvent` | Unwaxed copper oxidation | UT |
| `natural-block-form` | `BlockFormEvent` | Remaining natural snow/ice and terrain block formation | UT |
| `farmland-dry` | `MoistureChangeEvent` | Farmland moisture loss | API |
| `cauldron-changes` | `CauldronLevelChangeEvent` | Rain, dripstone, evaporation, extinguishing, bottles, and buckets | API |
| `weather` | `WeatherChangeEvent` | Start and end of rain/snow weather | API |
| `thunder` | `ThunderChangeEvent` | Start and end of thunderstorms | API |
| `lightning` | `LightningStrikeEvent` | Weather, trident, trap, command, and custom lightning | API |
| `time-skip` | `TimeSkipEvent` | Sleep and command time jumps | API |

## Plants and growth

| Rule | Bukkit event surface | Controlled behavior | Evidence |
|---|---|---|---|
| `leaf-decay` | `LeavesDecayEvent` | Unsupported leaf decay | API |
| `crop-growth` | `BlockGrowEvent` | Wheat, carrots, potatoes, beetroot, wart, cocoa, berries, torchflowers, and pitcher crops | UT |
| `stem-growth` | `BlockGrowEvent` | Pumpkin and melon stems/fruit | UT |
| `vertical-plant-growth` | `BlockGrowEvent`, `BlockSpreadEvent`, `StructureGrowEvent` | Sugar cane, cactus, bamboo, kelp, and chorus | UT |
| `vine-growth` | `BlockGrowEvent`, `BlockSpreadEvent` | Vines, cave vines, twisting/weeping vines, and glow lichen | UT |
| `mushroom-growth` | `BlockGrowEvent`, `BlockSpreadEvent`, `StructureGrowEvent` | Small mushrooms and giant mushrooms/fungi | UT |
| `tree-growth` | `StructureGrowEvent` | All tree species exposed by `TreeType` | UT |
| `plant-spread` | `BlockSpreadEvent` | Grass, mycelium, and other remaining plant spread | UT |
| `sculk-spread` | `SculkBloomEvent`, `BlockSpreadEvent` | Sculk catalyst bloom and sculk placement | UT |
| `amethyst-growth` | `BlockGrowEvent`, `BlockSpreadEvent` | Amethyst bud and cluster stages | UT |
| `dripstone-growth` | `BlockGrowEvent`, `BlockSpreadEvent` | Pointed dripstone growth | UT |
| `turtle-egg-hatch` | `BlockGrowEvent` | Turtle egg cracking and hatching | UT |
| `frogspawn-hatch` | `BlockFadeEvent` | Frogspawn hatching/removal | UT |
| `bone-meal` | `BlockFertilizeEvent`, bonemeal `StructureGrowEvent` | Player and dispenser fertilization | API |

## Entities and players

| Rule | Bukkit event surface | Controlled behavior | Evidence |
|---|---|---|---|
| `mob-griefing` | `EntityChangeBlockEvent`, `EntityInteractEvent` | Endermen, sheep, villagers, ravagers, withers, doors, crops, and other mob block changes | API |
| `mob-block-form` | `EntityBlockFormEvent` | Snow golem trails, wither roses, and other entity-created blocks | API |
| `farmland-trample` | `PlayerInteractEvent`, `EntityInteractEvent`, `EntityChangeBlockEvent` | Player and entity farmland trampling | UT |
| `turtle-egg-trample` | Same physical interaction events | Turtle egg trampling | UT |
| `dripleaf-tilt` | Same physical interaction events | Big dripleaf tilt caused by entities | UT |
| `natural-mob-spawning` | `CreatureSpawnEvent` with `NATURAL` reason | Natural mob spawning only; commands, spawners, breeding, buckets, and plugins remain allowed | API |
| `mob-breeding` | `EntityEnterLoveModeEvent`, `EntityBreedEvent` | Entry into love mode and animal breeding completion | API |
| `mob-transform` | `EntityTransformEvent`, runtime-selected `CubeMobSplitEvent`/`SlimeSplitEvent`, `PigZapEvent`, `CreeperPowerEvent`, `SheepRegrowWoolEvent` | Replacement transformations, cube-mob splitting, lightning state changes, and wool regrowth | API |
| `beehive-entry` | `EntityEnterBlockEvent` | Bees entering nests and hives | API |
| `item-despawn` | `ItemDespawnEvent` | Dropped item expiry | API |
| `item-merge` | `ItemMergeEvent` | Nearby dropped-item stack merging | API |
| `projectile-launch` | `ProjectileLaunchEvent` | Arrows, tridents, potions, pearls, fireworks, and other projectile launches | API |
| `entity-combust` | `EntityCombustEvent` and subclasses | Entity ignition by sun, blocks, and entities | API |
| `fall-damage` | `EntityDamageEvent` | Fall, elytra wall collision, and falling-block damage | UT |
| `knockback` | Paper or Bukkit knockback event selected at runtime | Entity knockback; uses Paper's current event without linking it on Spigot | API |
| `drowning-damage` | `EntityDamageEvent` | Drowning damage without changing air mechanics | UT |
| `hunger` | `EntityExhaustionEvent`, `FoodLevelChangeEvent` | Player exhaustion accumulation and food-level changes | API |
| `natural-regen` | `EntityRegainHealthEvent` | Saturation and natural regeneration; potions and custom healing remain allowed | API |

## Machines and processing

| Rule | Bukkit event surface | Controlled behavior | Evidence |
|---|---|---|---|
| `dispensers` | `BlockDispenseEvent` | Dispenser and dropper actions | API |
| `hoppers` | `InventoryMoveItemEvent`, `InventoryPickupItemEvent` | Hopper and hopper-minecart container transfer and loose-item pickup | API |
| `furnaces` | `FurnaceBurnEvent`, `FurnaceSmeltEvent`, `BlockCookEvent` | Furnace, blast furnace, and smoker fuel/smelting | API |
| `brewing` | `BrewEvent`, `BrewingStandFuelEvent` | Brewing completion and blaze-powder fuel consumption | API |
| `campfire-cooking` | `BlockCookEvent` | Campfire and soul-campfire cooking | API |
| `crafter` | `CrafterCraftEvent` | Crafter block recipes | API |
| `bell-ring` | `BellRingEvent` | Bell activation | API |
| `note-blocks` | `NotePlayEvent` | Note-block playback | API |

## Mineflayer boundary

[Mineflayer 4.37.1](https://github.com/PrismarineJS/mineflayer) officially supports Minecraft through 1.21.11, not 26.2. The black-box suite therefore runs the same plugin JAR on Paper 1.21.11 build 132 and observes block state over the real protocol. Paper/Spigot 26.2 compilation and Paper/Folia runtime tests cover the current server API separately. When Mineflayer adds 26.2 protocol data, only `TEST_VERSION`, the pinned Paper build, URL, and checksum in `tests/mineflayer/run.mjs` need updating.

Mineflayer cannot reliably observe server-only cancellation such as breeding completion, cauldron reasons, or inventory process events without slow random-tick scenarios. Those remain classified as API coverage rather than being overstated as black-box verified.

## Reviewed exclusions

The following surfaces were inspected but are not presented as world-physics rules:

| Domain | Examples | Reason |
|---|---|---|
| Direct player protection | `BlockBreakEvent`, `BlockPlaceEvent`, inventory clicks, bucket use | Belongs to a protection plugin; blocking it would change ownership/security semantics rather than simulation. |
| Client movement solver | acceleration, friction, jumping, sprinting, climbing, swimming, elytra, collision boxes | Mostly predicted by the client and has no complete portable Spigot event surface. Mineflayer's `prismarine-physics` models it for bots but cannot turn it into a server rule. |
| Mob AI movement | pathfinding, goals, navigation, looking, jumping | Paper exposes some move callbacks, but Spigot does not and per-tick cancellation would be unsuitable for Folia. |
| Generic combat/effects | attacks, potions, poison, magic, armor, death, resurrection | Gameplay/combat scope. Only environmental fall/drowning and knockback controls are included. |
| Explicit travel and posture | player teleport, portal entry, mounting, swimming/gliding toggles | Direct entity/player action rather than autonomous world simulation; portal *creation* remains covered. |
| Chunk generation and data packs | terrain noise, carvers, structures generated with chunks | Occurs during generation rather than runtime physics and cannot be reversed safely by an event cancellation plugin. |
| Paper-only mechanics | `EntityMoveEvent`, `EntityInsideBlockEvent`, compost and dragon-egg Paper events | Excluded from the shared core where no equivalent Spigot 26.2 event exists. Portable Bukkit fallbacks are used when available. |
| Pure presentation | sounds, particles, block display state, maps, signs | No physical state transition. Bell and note-block activation are included because they are redstone-driven machine outputs. |
