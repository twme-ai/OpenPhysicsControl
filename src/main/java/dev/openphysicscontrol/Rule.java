package dev.openphysicscontrol;

import org.bukkit.Material;

import java.util.Locale;

public enum Rule {
    GRAVITY(Group.GRAVITY_AND_LIQUIDS, Material.SAND),
    BLOCK_UPDATES(Group.BUILDING, Material.OBSERVER),
    WATER_FLOW(Group.GRAVITY_AND_LIQUIDS, Material.WATER_BUCKET),
    LAVA_FLOW(Group.GRAVITY_AND_LIQUIDS, Material.LAVA_BUCKET),
    FLUID_REACTIONS(Group.GRAVITY_AND_LIQUIDS, Material.OBSIDIAN),
    CONCRETE_HARDEN(Group.BUILDING, Material.WHITE_CONCRETE),
    PISTONS(Group.BUILDING, Material.PISTON),
    SPONGE_ABSORB(Group.BUILDING, Material.SPONGE),
    DRAGON_EGG_TELEPORT(Group.ENTITIES, Material.DRAGON_EGG),
    PORTAL_CREATION(Group.BUILDING, Material.OBSIDIAN),
    REDSTONE(Group.BUILDING, Material.REDSTONE),
    SCULK_VIBRATIONS(Group.BUILDING, Material.SCULK_SENSOR),
    TNT_PRIME(Group.BUILDING, Material.TNT),
    EXPLOSION_BLOCK_DAMAGE(Group.BUILDING, Material.GUNPOWDER),
    ENTITY_EXPLOSION_PRIME(Group.BUILDING, Material.END_CRYSTAL),

    FIRE_SPREAD(Group.WORLD, Material.FLINT_AND_STEEL),
    FIRE_BURN(Group.WORLD, Material.CAMPFIRE),
    FIRE_IGNITE(Group.WORLD, Material.FIRE_CHARGE),
    FIRE_EXTINGUISH(Group.WORLD, Material.SOUL_CAMPFIRE),
    ICE_MELT(Group.WORLD, Material.ICE),
    SNOW_MELT(Group.WORLD, Material.SNOW_BLOCK),
    FROSTED_ICE(Group.WORLD, Material.PACKED_ICE),
    CORAL_FADE(Group.WORLD, Material.BRAIN_CORAL),
    GROUND_FADE(Group.WORLD, Material.GRASS_BLOCK),
    COPPER_WEATHER(Group.WORLD, Material.COPPER_BLOCK),
    NATURAL_BLOCK_FORM(Group.WORLD, Material.POWDER_SNOW_BUCKET),
    FARMLAND_DRY(Group.WORLD, Material.FARMLAND),
    CAULDRON_CHANGES(Group.WORLD, Material.CAULDRON),
    WEATHER(Group.WORLD, Material.WATER_BUCKET),
    THUNDER(Group.WORLD, Material.LIGHTNING_ROD),
    LIGHTNING(Group.WORLD, Material.LIGHTNING_ROD),
    TIME_SKIP(Group.WORLD, Material.CLOCK),
    LEAF_DECAY(Group.WORLD, Material.OAK_LEAVES),

    CROP_GROWTH(Group.SMALL_PLANTS, Material.WHEAT),
    STEM_GROWTH(Group.SMALL_PLANTS, Material.PUMPKIN),
    VERTICAL_PLANT_GROWTH(Group.TALL_GROWTH, Material.SUGAR_CANE),
    VINE_GROWTH(Group.TALL_GROWTH, Material.VINE),
    MUSHROOM_GROWTH(Group.SMALL_PLANTS, Material.RED_MUSHROOM),
    TREE_GROWTH(Group.TALL_GROWTH, Material.OAK_SAPLING),
    PLANT_SPREAD(Group.SMALL_PLANTS, Material.GRASS_BLOCK),
    SCULK_SPREAD(Group.SMALL_PLANTS, Material.SCULK),
    AMETHYST_GROWTH(Group.SMALL_PLANTS, Material.AMETHYST_CLUSTER),
    DRIPSTONE_GROWTH(Group.TALL_GROWTH, Material.POINTED_DRIPSTONE),
    TURTLE_EGG_HATCH(Group.SMALL_PLANTS, Material.TURTLE_EGG),
    FROGSPAWN_HATCH(Group.SMALL_PLANTS, Material.FROGSPAWN),
    BONE_MEAL(Group.SMALL_PLANTS, Material.BONE_MEAL),

    MOB_GRIEFING(Group.MOBS, Material.CREEPER_HEAD),
    MOB_BLOCK_FORM(Group.MOBS, Material.SNOW_BLOCK),
    FARMLAND_TRAMPLE(Group.PLAYERS, Material.LEATHER_BOOTS),
    TURTLE_EGG_TRAMPLE(Group.PLAYERS, Material.TURTLE_HELMET),
    DRIPLEAF_TILT(Group.PLAYERS, Material.BIG_DRIPLEAF),
    END_PORTAL_FRAME_FILLING(Group.PLAYERS, Material.END_PORTAL_FRAME),
    GLOW_BERRY_PICKING(Group.PLAYERS, Material.GLOW_BERRIES),
    NATURAL_MOB_SPAWNING(Group.MOBS, Material.ZOMBIE_SPAWN_EGG),
    SPAWNER_MOB_SPAWNING(Group.MOBS, Material.SPAWNER),
    MOB_BREEDING(Group.MOBS, Material.WHEAT),
    MOB_TRANSFORM(Group.MOBS, Material.ROTTEN_FLESH),
    BEEHIVE_ENTRY(Group.MOBS, Material.BEEHIVE),
    ITEM_DESPAWN(Group.ENTITIES, Material.CLOCK),
    ITEM_MERGE(Group.ENTITIES, Material.HOPPER),
    PROJECTILE_LAUNCH(Group.ENTITIES, Material.ARROW),
    BLOCK_HIT_PROJECTILE_REMOVAL(Group.ENTITIES, Material.ARROW, false),
    ENTITY_COMBUST(Group.ENTITIES, Material.BLAZE_POWDER),
    FALL_DAMAGE(Group.ENTITIES, Material.FEATHER),
    KNOCKBACK(Group.ENTITIES, Material.SHIELD),
    DROWNING_DAMAGE(Group.ENTITIES, Material.WATER_BUCKET),
    OXYGEN_DEPLETION(Group.ENTITIES, Material.CONDUIT),
    FIRE_DAMAGE(Group.ENTITIES, Material.MAGMA_BLOCK),
    FREEZE_DAMAGE(Group.ENTITIES, Material.POWDER_SNOW_BUCKET),
    VEHICLE_ENTITY_COLLISION(Group.ENTITIES, Material.MINECART),
    HUNGER(Group.PLAYERS, Material.ROTTEN_FLESH),
    NATURAL_REGEN(Group.PLAYERS, Material.GOLDEN_APPLE),

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
    private final boolean defaultEnabled;

    Rule(Group group, Material icon) {
        this(group, icon, true);
    }

    Rule(Group group, Material icon, boolean defaultEnabled) {
        this.group = group;
        this.icon = icon;
        this.defaultEnabled = defaultEnabled;
    }

    public Group group() {
        return this.group;
    }

    public Material icon() {
        return this.icon;
    }

    public boolean defaultEnabled() {
        return this.defaultEnabled;
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
        MOBS(Material.ZOMBIE_HEAD, 3),
        PLAYERS(Material.PLAYER_HEAD, 4),
        ENTITIES(Material.ARROW, 5),
        BUILDING(Material.LADDER, 11),
        GRAVITY_AND_LIQUIDS(Material.SAND, 12),
        WORLD(Material.OAK_LEAVES, 13),
        SMALL_PLANTS(Material.MELON, 14),
        TALL_GROWTH(Material.BIRCH_SAPLING, 15),
        MACHINES(Material.CRAFTER, 22);

        private final Material icon;
        private final int slot;

        Group(Material icon, int slot) {
            this.icon = icon;
            this.slot = slot;
        }

        public Material icon() {
            return this.icon;
        }

        public int slot() {
            return this.slot;
        }

        public String messageKey() {
            return "group-" + name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }
}
