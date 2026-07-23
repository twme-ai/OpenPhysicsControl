package dev.openphysicscontrol;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BellRingEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.block.SculkBloomEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreeperPowerEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityEnterBlockEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.SheepRegrowWoolEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.inventory.Inventory;

public final class PhysicsEvents implements Listener {
    private final RuleStore rules;

    public PhysicsEvents(RuleStore rules) {
        this.rules = rules;
    }

    private void control(Cancellable event, World world, Rule rule) {
        if (rule != null && !this.rules.enabled(world, rule)) event.setCancelled(true);
    }

    private boolean disabled(World world, Rule rule) {
        return !this.rules.enabled(world, rule);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void physics(BlockPhysicsEvent event) {
        Material material = event.getBlock().getType();
        Rule rule = material.hasGravity() || event.getChangedType().hasGravity()
            ? Rule.GRAVITY : Rule.BLOCK_UPDATES;
        control(event, event.getBlock().getWorld(), rule);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void flow(BlockFromToEvent event) {
        Rule rule = event.getBlock().getType() == Material.DRAGON_EGG
            ? Rule.DRAGON_EGG_TELEPORT : PhysicsClassifier.flow(event.getBlock().getType());
        control(event, event.getBlock().getWorld(), rule);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void fluidLevel(FluidLevelChangeEvent event) {
        control(event, event.getBlock().getWorld(), PhysicsClassifier.flow(event.getBlock().getType()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void spread(BlockSpreadEvent event) {
        control(event, event.getBlock().getWorld(), PhysicsClassifier.spread(
            event.getSource().getType(), event.getNewState().getType()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void burn(BlockBurnEvent event) {
        control(event, event.getBlock().getWorld(), Rule.FIRE_BURN);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void ignite(BlockIgniteEvent event) {
        control(event, event.getBlock().getWorld(), Rule.FIRE_IGNITE);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void decay(LeavesDecayEvent event) {
        control(event, event.getBlock().getWorld(), Rule.LEAF_DECAY);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void fade(BlockFadeEvent event) {
        control(event, event.getBlock().getWorld(), PhysicsClassifier.fade(
            event.getBlock().getType(), event.getNewState().getType()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void moisture(MoistureChangeEvent event) {
        control(event, event.getBlock().getWorld(), Rule.FARMLAND_DRY);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void grow(BlockGrowEvent event) {
        control(event, event.getBlock().getWorld(), PhysicsClassifier.grow(
            event.getBlock().getType(), event.getNewState().getType()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void trees(StructureGrowEvent event) {
        if (event.isFromBonemeal() && disabled(event.getWorld(), Rule.BONE_MEAL)) {
            event.setCancelled(true);
            return;
        }
        control(event, event.getWorld(), PhysicsClassifier.structure(event.getSpecies()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void fertilize(BlockFertilizeEvent event) {
        control(event, event.getBlock().getWorld(), Rule.BONE_MEAL);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void sculkBloom(SculkBloomEvent event) {
        control(event, event.getBlock().getWorld(), Rule.SCULK_SPREAD);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void change(EntityChangeBlockEvent event) {
        Rule rule;
        if (event.getEntity() instanceof FallingBlock) {
            rule = Rule.GRAVITY;
        } else {
            rule = PhysicsClassifier.physicalInteraction(event.getBlock().getType());
            if (rule == null) rule = Rule.MOB_GRIEFING;
        }
        control(event, event.getBlock().getWorld(), rule);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void interact(EntityInteractEvent event) {
        Rule rule = PhysicsClassifier.physicalInteraction(event.getBlock().getType());
        control(event, event.getBlock().getWorld(), rule == null ? Rule.MOB_GRIEFING : rule);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void mobForm(EntityBlockFormEvent event) {
        Rule rule = event.getNewState().getType() == Material.FROSTED_ICE
            ? Rule.FROSTED_ICE : Rule.MOB_BLOCK_FORM;
        control(event, event.getBlock().getWorld(), rule);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void form(BlockFormEvent event) {
        if (event instanceof EntityBlockFormEvent) return;
        control(event, event.getBlock().getWorld(), PhysicsClassifier.form(
            event.getBlock().getType(), event.getNewState().getType()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void entityExplosion(EntityExplodeEvent event) {
        if (disabled(event.getLocation().getWorld(), Rule.EXPLOSION_BLOCK_DAMAGE)) event.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void blockExplosion(BlockExplodeEvent event) {
        if (disabled(event.getBlock().getWorld(), Rule.EXPLOSION_BLOCK_DAMAGE)) event.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void trample(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL || event.getClickedBlock() == null) return;
        control(event, event.getClickedBlock().getWorld(),
            PhysicsClassifier.physicalInteraction(event.getClickedBlock().getType()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void pistonExtend(BlockPistonExtendEvent event) {
        control(event, event.getBlock().getWorld(), Rule.PISTONS);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void pistonRetract(BlockPistonRetractEvent event) {
        control(event, event.getBlock().getWorld(), Rule.PISTONS);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void dispense(BlockDispenseEvent event) {
        control(event, event.getBlock().getWorld(), Rule.DISPENSERS);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void absorb(SpongeAbsorbEvent event) {
        control(event, event.getBlock().getWorld(), Rule.SPONGE_ABSORB);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void portal(PortalCreateEvent event) {
        control(event, event.getWorld(), Rule.PORTAL_CREATION);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void redstone(BlockRedstoneEvent event) {
        if (disabled(event.getBlock().getWorld(), Rule.REDSTONE)) event.setNewCurrent(event.getOldCurrent());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void vibration(BlockReceiveGameEvent event) {
        control(event, event.getBlock().getWorld(), Rule.SCULK_VIBRATIONS);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void prime(TNTPrimeEvent event) {
        control(event, event.getBlock().getWorld(), Rule.TNT_PRIME);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void cauldron(CauldronLevelChangeEvent event) {
        control(event, event.getBlock().getWorld(), Rule.CAULDRON_CHANGES);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void weather(WeatherChangeEvent event) {
        control(event, event.getWorld(), Rule.WEATHER);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void thunder(ThunderChangeEvent event) {
        control(event, event.getWorld(), Rule.THUNDER);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void lightning(LightningStrikeEvent event) {
        control(event, event.getWorld(), Rule.LIGHTNING);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void timeSkip(TimeSkipEvent event) {
        control(event, event.getWorld(), Rule.TIME_SKIP);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void naturalSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            control(event, event.getLocation().getWorld(), Rule.NATURAL_MOB_SPAWNING);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void breed(EntityBreedEvent event) {
        control(event, event.getEntity().getWorld(), Rule.MOB_BREEDING);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void enterLoveMode(EntityEnterLoveModeEvent event) {
        control(event, event.getEntity().getWorld(), Rule.MOB_BREEDING);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void transform(EntityTransformEvent event) {
        control(event, event.getEntity().getWorld(), Rule.MOB_TRANSFORM);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void creeperPower(CreeperPowerEvent event) {
        control(event, event.getEntity().getWorld(), Rule.MOB_TRANSFORM);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void regrowWool(SheepRegrowWoolEvent event) {
        control(event, event.getEntity().getWorld(), Rule.MOB_TRANSFORM);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void enterBlock(EntityEnterBlockEvent event) {
        Material material = event.getBlock().getType();
        if (material == Material.BEEHIVE || material == Material.BEE_NEST) {
            control(event, event.getBlock().getWorld(), Rule.BEEHIVE_ENTRY);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void despawn(ItemDespawnEvent event) {
        control(event, event.getLocation().getWorld(), Rule.ITEM_DESPAWN);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void merge(ItemMergeEvent event) {
        control(event, event.getEntity().getWorld(), Rule.ITEM_MERGE);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void projectile(ProjectileLaunchEvent event) {
        control(event, event.getEntity().getWorld(), Rule.PROJECTILE_LAUNCH);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void combust(EntityCombustEvent event) {
        control(event, event.getEntity().getWorld(), Rule.ENTITY_COMBUST);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void damage(EntityDamageEvent event) {
        control(event, event.getEntity().getWorld(), PhysicsClassifier.damage(event.getCause()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void hunger(FoodLevelChangeEvent event) {
        control(event, event.getEntity().getWorld(), Rule.HUNGER);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void exhaustion(EntityExhaustionEvent event) {
        control(event, event.getEntity().getWorld(), Rule.HUNGER);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void regeneration(EntityRegainHealthEvent event) {
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
            || event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN) {
            control(event, event.getEntity().getWorld(), Rule.NATURAL_REGEN);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void hopper(InventoryMoveItemEvent event) {
        World world = inventoryWorld(event.getInitiator(), event.getSource(), event.getDestination());
        if (world != null) control(event, world, Rule.HOPPERS);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void hopperPickup(InventoryPickupItemEvent event) {
        World world = inventoryWorld(event.getInventory());
        if (world != null) control(event, world, Rule.HOPPERS);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void furnaceBurn(FurnaceBurnEvent event) {
        control(event, event.getBlock().getWorld(), Rule.FURNACES);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void furnaceSmelt(FurnaceSmeltEvent event) {
        control(event, event.getBlock().getWorld(), Rule.FURNACES);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void cook(BlockCookEvent event) {
        String material = event.getBlock().getType().name();
        Rule rule = material.equals("CAMPFIRE") || material.equals("SOUL_CAMPFIRE")
            ? Rule.CAMPFIRE_COOKING : Rule.FURNACES;
        control(event, event.getBlock().getWorld(), rule);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void brew(BrewEvent event) {
        control(event, event.getBlock().getWorld(), Rule.BREWING);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void brewingFuel(BrewingStandFuelEvent event) {
        control(event, event.getBlock().getWorld(), Rule.BREWING);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void craft(CrafterCraftEvent event) {
        control(event, event.getBlock().getWorld(), Rule.CRAFTER);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void bell(BellRingEvent event) {
        control(event, event.getBlock().getWorld(), Rule.BELL_RING);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void note(NotePlayEvent event) {
        control(event, event.getBlock().getWorld(), Rule.NOTE_BLOCKS);
    }

    private static World inventoryWorld(Inventory... inventories) {
        for (Inventory inventory : inventories) {
            if (inventory == null) continue;
            Location location = inventory.getLocation();
            if (location != null && location.getWorld() != null) return location.getWorld();
        }
        return null;
    }
}
