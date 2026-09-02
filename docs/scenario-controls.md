# Scenario-oriented controls

OpenPhysicsControl uses broad, composable behavior rules instead of copying every row from the reference spreadsheet into a separate switch.

## Added control surfaces

| Rule | Event surface | Scenarios covered |
|---|---|---|
| `player-block-interactions` | Right-click `PlayerInteractEvent` | Levers, buttons, beds, respawn anchors, brewing stands, campfires, chiseled bookshelves, beacons, anvils, flower pots, bells, lodestones, and other clicked blocks. Per-material overrides narrow the rule to selected block types. |
| `player-entity-interactions` | `PlayerInteractEntityEvent`, `PlayerArmorStandManipulateEvent`, player `VehicleEnterEvent` | Item frames, armor stands, boats, minecarts, and other direct entity interaction or mounting. |
| `hanging-entity-detachment` | `HangingBreakEvent` | Item frames, glow item frames, and paintings removed by lost support, obstruction, explosions, or entities. |
| `block-origin-explosions` | `BlockExplodeEvent` | Bed and respawn-anchor block explosions. Disabling the corresponding block interaction is the stronger pre-explosion control for player use. |
| `sniffer-egg-hatch` | `BlockGrowEvent`, `BlockFadeEvent` | Both sniffer-egg cracking stages and final hatching under one rule. |
| `placed-block-connections` | `BlockPlaceEvent` followed by a no-physics block-data update | Initial connections on newly placed fences, panes/bars, walls, stairs, fence gates, and chests. `block-updates` separately controls whether existing neighboring blocks react. |

The dedicated End portal frame filling rule still runs before the broad player-block rule is evaluated. Glow berry picking is covered by `player-block-interactions` instead of a separate switch. Redstone and machine processing remain separate from whether a player may interact with the block.

## Configuration examples

Keep normal interactions enabled while preventing only selected blocks from being used:

```yaml
player-block-interactions: true

material-overrides:
  player-block-interactions:
    RESPAWN_ANCHOR: false
    WHITE_BED: false
    ANVIL: false
    CHIPPED_ANVIL: false
    DAMAGED_ANVIL: false
```

The broad entity and hanging rules are world-level in this experiment. Entity-type overrides are a possible later extension, but are not implied by the existing material override format.

To keep both sides of a newly placed fence disconnected while leaving this behavior enabled for other materials:

```yaml
block-updates: false
placed-block-connections: true

material-overrides:
  placed-block-connections:
    OAK_FENCE: false
```
