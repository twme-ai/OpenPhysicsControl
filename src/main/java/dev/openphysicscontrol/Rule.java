package dev.openphysicscontrol;

import org.bukkit.Material;

import java.util.Locale;

public enum Rule {
    GRAVITY(Group.BLOCKS, Material.SAND),
    BLOCK_UPDATES(Group.BLOCKS, Material.OBSERVER),
    WATER_FLOW(Group.BLOCKS, Material.WATER_BUCKET),
    LAVA_FLOW(Group.BLOCKS, Material.LAVA_BUCKET),
    FLUID_REACTIONS(Group.BLOCKS, Material.OBSIDIAN),
    CONCRETE_HARDEN(Group.BLOCKS, Material.WHITE_CONCRETE),
    PISTONS(Group.BLOCKS, Material.PISTON),
    SPONGE_ABSORB(Group.BLOCKS, Material.SPONGE),
    DRAGON_EGG_TELEPORT(Group.BLOCKS, Material.DRAGON_EGG),
    PORTAL_CREATION(Group.BLOCKS, Material.OBSIDIAN),
    REDSTONE(Group.BLOCKS, Material.REDSTONE),
    SCULK_VIBRATIONS(Group.BLOCKS, Material.SCULK_SENSOR),
    TNT_PRIME(Group.BLOCKS, Material.TNT),
    EXPLOSION_BLOCK_DAMAGE(Group.BLOCKS, Material.GUNPOWDER),

    FIRE_SPREAD(Group.CLIMATE, Material.FLINT_AND_STEEL),
    FIRE_BURN(Group.CLIMATE, Material.CAMPFIRE),
    FIRE_IGNITE(Group.CLIMATE, Material.FIRE_CHARGE),
    FIRE_EXTINGUISH(Group.CLIMATE, Material.SOUL_CAMPFIRE),
    ICE_MELT(Group.CLIMATE, Material.ICE),
    SNOW_MELT(Group.CLIMATE, Material.SNOW_BLOCK),
    FROSTED_ICE(Group.CLIMATE, Material.PACKED_ICE),
    CORAL_FADE(Group.CLIMATE, Material.BRAIN_CORAL),
    GROUND_FADE(Group.CLIMATE, Material.GRASS_BLOCK),
    COPPER_WEATHER(Group.CLIMATE, Material.COPPER_BLOCK),
    NATURAL_BLOCK_FORM(Group.CLIMATE, Material.POWDER_SNOW_BUCKET),
    FARMLAND_DRY(Group.CLIMATE, Material.FARMLAND),
    CAULDRON_CHANGES(Group.CLIMATE, Material.CAULDRON),
    WEATHER(Group.CLIMATE, Material.WATER_BUCKET),
    THUNDER(Group.CLIMATE, Material.LIGHTNING_ROD),
    LIGHTNING(Group.CLIMATE, Material.LIGHTNING_ROD),
    TIME_SKIP(Group.CLIMATE, Material.CLOCK),

    LEAF_DECAY(Group.GROWTH, Material.OAK_LEAVES),
    CROP_GROWTH(Group.GROWTH, Material.WHEAT),
    STEM_GROWTH(Group.GROWTH, Material.PUMPKIN),
    VERTICAL_PLANT_GROWTH(Group.GROWTH, Material.SUGAR_CANE),
    VINE_GROWTH(Group.GROWTH, Material.VINE),
    MUSHROOM_GROWTH(Group.GROWTH, Material.RED_MUSHROOM),
    TREE_GROWTH(Group.GROWTH, Material.OAK_SAPLING),
    PLANT_SPREAD(Group.GROWTH, Material.GRASS_BLOCK),
    SCULK_SPREAD(Group.GROWTH, Material.SCULK),
    AMETHYST_GROWTH(Group.GROWTH, Material.AMETHYST_CLUSTER),
    DRIPSTONE_GROWTH(Group.GROWTH, Material.POINTED_DRIPSTONE),
    TURTLE_EGG_HATCH(Group.GROWTH, Material.TURTLE_EGG),
    FROGSPAWN_HATCH(Group.GROWTH, Material.FROGSPAWN),
    BONE_MEAL(Group.GROWTH, Material.BONE_MEAL),

    MOB_GRIEFING(Group.ENTITIES, Material.CREEPER_HEAD),
    MOB_BLOCK_FORM(Group.ENTITIES, Material.SNOW_BLOCK),
    FARMLAND_TRAMPLE(Group.ENTITIES, Material.LEATHER_BOOTS),
    TURTLE_EGG_TRAMPLE(Group.ENTITIES, Material.TURTLE_HELMET),
    DRIPLEAF_TILT(Group.ENTITIES, Material.BIG_DRIPLEAF),
    NATURAL_MOB_SPAWNING(Group.ENTITIES, Material.ZOMBIE_SPAWN_EGG),
    MOB_BREEDING(Group.ENTITIES, Material.WHEAT),
    MOB_TRANSFORM(Group.ENTITIES, Material.ROTTEN_FLESH),
    BEEHIVE_ENTRY(Group.ENTITIES, Material.BEEHIVE),
    ITEM_DESPAWN(Group.ENTITIES, Material.CLOCK),
    ITEM_MERGE(Group.ENTITIES, Material.HOPPER),
    PROJECTILE_LAUNCH(Group.ENTITIES, Material.ARROW),
    ENTITY_COMBUST(Group.ENTITIES, Material.BLAZE_POWDER),
    FALL_DAMAGE(Group.ENTITIES, Material.FEATHER),
    KNOCKBACK(Group.ENTITIES, Material.SHIELD),
    DROWNING_DAMAGE(Group.ENTITIES, Material.WATER_BUCKET),
    HUNGER(Group.ENTITIES, Material.ROTTEN_FLESH),
    NATURAL_REGEN(Group.ENTITIES, Material.GOLDEN_APPLE),

    DISPENSERS(Group.MACHINES, Material.DISPENSER),
    HOPPERS(Group.MACHINES, Material.HOPPER),
    FURNACES(Group.MACHINES, Material.FURNACE),
    BREWING(Group.MACHINES, Material.BREWING_STAND),
    CAMPFIRE_COOKING(Group.MACHINES, Material.CAMPFIRE),
    CRAFTER(Group.MACHINES, Material.CRAFTER),
    BELL_RING(Group.MACHINES, Material.BELL),
    NOTE_BLOCKS(Group.MACHINES, Material.NOTE_BLOCK);

    private final Group group;
    private final Material icon;

    Rule(Group group, Material icon) {
        this.group = group;
        this.icon = icon;
    }

    public Group group() {
        return this.group;
    }

    public Material icon() {
        return this.icon;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public String messageKey() {
        return "rule-" + key();
    }

    public static Rule parse(String input) {
        return valueOf(input.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    public enum Group {
        BLOCKS(Material.PISTON),
        CLIMATE(Material.LIGHTNING_ROD),
        GROWTH(Material.OAK_SAPLING),
        ENTITIES(Material.CREEPER_HEAD),
        MACHINES(Material.CRAFTER);

        private final Material icon;

        Group(Material icon) {
            this.icon = icon;
        }

        public Material icon() {
            return this.icon;
        }

        public String messageKey() {
            return "group-" + name().toLowerCase(Locale.ROOT);
        }
    }
}
