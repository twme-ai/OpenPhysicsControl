package dev.openphysicscontrol;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class LegacyPhysicsControlMigrator {
    private static final String LEGACY_PLUGIN_DIRECTORY = "PhysicsControl";
    private static final Map<String, List<Rule>> MAPPINGS = mappings();

    private LegacyPhysicsControlMigrator() {
    }

    static MigrationResult migrate(File pluginsDirectory, String worldName, File destination) throws IOException {
        if (destination.exists()) return MigrationResult.notFound();

        File legacyDirectory = new File(pluginsDirectory, LEGACY_PLUGIN_DIRECTORY);
        File triggersFile = new File(new File(legacyDirectory, "triggers"), worldName + ".yml");
        if (triggersFile.isFile()) {
            return migrate(YamlConfiguration.loadConfiguration(triggersFile), triggersFile, destination);
        }

        File oldConfig = new File(legacyDirectory, "config.yml");
        if (!oldConfig.isFile()) return MigrationResult.notFound();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(oldConfig);
        ConfigurationSection world = worldSection(config, worldName);
        return world == null ? MigrationResult.notFound() : migrate(world, oldConfig, destination);
    }

    private static ConfigurationSection worldSection(YamlConfiguration config, String worldName) {
        return config.getConfigurationSection(worldName);
    }

    private static MigrationResult migrate(ConfigurationSection source, File sourceFile, File destination) throws IOException {
        Map<Rule, Boolean> values = new EnumMap<>(Rule.class);
        Set<String> unsupported = new LinkedHashSet<>();
        for (String rawKey : source.getKeys(false)) {
            if (!source.isBoolean(rawKey)) continue;
            String key = normalize(rawKey);
            List<Rule> targets = MAPPINGS.get(key);
            if (targets == null) {
                unsupported.add(rawKey);
                continue;
            }
            boolean enabled = source.getBoolean(rawKey);
            for (Rule target : targets) {
                // The new rule can cover several legacy-specific rules. Never re-enable a legacy-disabled action.
                values.merge(target, enabled, (current, next) -> current && next);
            }
        }

        if (!values.isEmpty()) {
            File parent = destination.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Unable to create " + parent);
            }
            YamlConfiguration target = new YamlConfiguration();
            for (Map.Entry<Rule, Boolean> entry : values.entrySet()) {
                target.set(entry.getKey().key(), entry.getValue());
            }
            target.save(destination);
        }
        return new MigrationResult(sourceFile, values.size(), Set.copyOf(unsupported));
    }

    private static String normalize(String key) {
        return key.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    private static Map<String, List<Rule>> mappings() {
        Map<String, List<Rule>> result = new LinkedHashMap<>();

        put(result, Rule.MOB_GRIEFING,
            "RABBITS_EATING_CARROTS", "VILLAGERS_FARMING", "SHEEPS_EATING_GRASS",
            "SILVERFISHES_HIDING_IN_BLOCKS", "ZOMBIES_BREAK_DOORS", "ENDERMANS_GRIEFING",
            "WITHERS_GRIEFING", "TURTLES_LAYING_EGGS", "FOXES_EATS_FROM_SWEET_BERRY_BUSHES",
            "RAVAGERS_DESTROY_BLOCKS", "POWDER_SNOW_MELTS_FROM_BURNING_ENTITIES");
        put(result, Rule.MOB_BLOCK_FORM,
            "SNOW_GOLEMS_CREATE_SNOW", "WITHER_CREATE_WITHER_ROSE_BLOCKS");
        put(result, "FROGSPAWN_LAYING_AND_SPAWNING", Rule.MOB_GRIEFING, Rule.FROGSPAWN_HATCH);

        put(result, Rule.FIRE_IGNITE, "PLAYERS_FLINT_USAGE");
        put(result, Rule.BONE_MEAL, "BONE_MEAL_USAGE", "PLAYERS_BONE_MEAL_USAGE");

        put(result, Rule.TNT_PRIME, "BURNING_ARROWS_ACTIVATE_TNT");
        put(result, Rule.FARMLAND_TRAMPLE, "FARMLANDS_TRAMPLING");
        put(result, Rule.DRAGON_EGG_TELEPORT, "DRAGON_EGGS_TELEPORTING");
        put(result, Rule.FROSTED_ICE, "FROSTED_ICE_PHYSICS");
        put(result, Rule.TURTLE_EGG_TRAMPLE, "TURTLE_EGGS_TRAMPLING");
        put(result, Rule.DRIPLEAF_TILT, "DRIPLEAFS_LOWERING");

        put(result, Rule.BLOCK_UPDATES,
            "LADDERS_DESTROYING", "SIGNS_DESTROYING", "RAILS_DESTROYING", "TORCHES_DESTROYING",
            "REDSTONE_TORCHES_DESTROYING", "SOUL_TORCHES_DESTROYING", "SAPLINGS_DESTROYING");
        put(result, Rule.GRAVITY,
            "GRAVEL_FALLING", "SAND_FALLING", "ANVILS_FALLING", "DRAGON_EGGS_FALLING",
            "CONCRETE_POWDERS_FALLING", "SCAFFOLDING_FALLING", "POINTED_DRIPSTONES_FALLING");
        put(result, Rule.WATER_FLOW, "WATER_FLOWING");
        put(result, Rule.LAVA_FLOW, "LAVA_FLOWING");

        put(result, Rule.FIRE_SPREAD, "FIRE_SPREADING");
        put(result, Rule.SNOW_MELT, "SNOW_MELTING");
        put(result, Rule.FARMLAND_DRY, "FARMLANDS_DRYING");
        put(result, Rule.ICE_MELT, "ICE_MELTING");
        put(result, Rule.LEAF_DECAY, "LEAVES_DECAY");
        put(result, Rule.GROUND_FADE,
            "GRASS_BLOCKS_FADING", "CRIMSON_NYLIUM_FADING", "WARPED_NYLIUM_FADING");
        put(result, Rule.CORAL_FADE, "CORALS_DRYING");
        put(result, Rule.SCULK_SPREAD, "SCULKS_SPREADING");

        put(result, Rule.PLANT_SPREAD, "GRASS_SPREADING", "MYCELIUM_SPREADING");
        put(result, Rule.MUSHROOM_GROWTH, "LITTLE_MUSHROOMS_SPREADING", "GIANT_MUSHROOMS_GROWING");
        put(result, Rule.STEM_GROWTH, "PUMPKINS_GROWING", "MELONS_GROWING");
        put(result, Rule.CROP_GROWTH,
            "NETHER_WARTS_GROWING", "COCOAS_GROWING", "WHEAT_GROWING", "POTATOES_GROWING",
            "CARROTS_GROWING", "BEETROOTS_GROWING", "SWEET_BERRIES_GROWING");
        put(result, Rule.AMETHYST_GROWTH, "AMETHYST_CLUSTERS_GROWING");

        put(result, Rule.VERTICAL_PLANT_GROWTH,
            "SUGAR_CANE_GROWING", "CACTUS_GROWING", "CHORUSES_GROWING", "KELPS_GROWING", "BAMBOO_GROWING");
        put(result, Rule.TREE_GROWTH, "TREES_GROWING");
        put(result, Rule.VINE_GROWTH,
            "VINES_GROWING", "WEEPING_VINES_GROWING", "TWISTING_VINES_GROWING", "GLOW_BERRIES_GROWING");
        put(result, Rule.DRIPSTONE_GROWTH, "POINTED_DRIPSTONES_GROWING");

        return Map.copyOf(result);
    }

    private static void put(Map<String, List<Rule>> mappings, Rule target, String... keys) {
        for (String key : keys) mappings.put(key, List.of(target));
    }

    private static void put(Map<String, List<Rule>> mappings, String key, Rule... targets) {
        mappings.put(key, List.of(targets));
    }

    record MigrationResult(File source, int importedRules, Set<String> unsupportedTriggers) {
        static MigrationResult notFound() {
            return new MigrationResult(null, 0, Set.of());
        }

        boolean sourceFound() {
            return this.source != null;
        }
    }
}
