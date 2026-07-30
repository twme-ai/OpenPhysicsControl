package dev.openphysicscontrol;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
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
    private static final Map<String, MaterialTarget> MATERIAL_MAPPINGS = materialMappings();

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
        Map<Rule, Map<Material, Boolean>> materialValues = new EnumMap<>(Rule.class);
        Set<Rule> imported = new LinkedHashSet<>();
        Set<String> unsupported = new LinkedHashSet<>();
        for (String rawKey : source.getKeys(false)) {
            if (!source.isBoolean(rawKey)) continue;
            String key = normalize(rawKey);
            MaterialTarget materialTarget = MATERIAL_MAPPINGS.get(key);
            if (materialTarget != null) {
                boolean enabled = source.getBoolean(rawKey);
                Map<Material, Boolean> overrides = materialValues.computeIfAbsent(
                    materialTarget.rule(), ignored -> new EnumMap<>(Material.class));
                for (Material material : materialTarget.materials()) overrides.put(material, enabled);
                imported.add(materialTarget.rule());
                continue;
            }
            List<Rule> targets = MAPPINGS.get(key);
            if (targets == null) {
                unsupported.add(rawKey);
                continue;
            }
            boolean enabled = source.getBoolean(rawKey);
            for (Rule target : targets) {
                // The new rule can cover several legacy-specific rules. Never re-enable a legacy-disabled action.
                values.merge(target, enabled, (current, next) -> current && next);
                imported.add(target);
            }
        }

        if (!values.isEmpty() || !materialValues.isEmpty()) {
            File parent = destination.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Unable to create " + parent);
            }
            YamlConfiguration target = new YamlConfiguration();
            for (Map.Entry<Rule, Boolean> entry : values.entrySet()) {
                target.set(entry.getKey().key(), entry.getValue());
            }
            for (Map.Entry<Rule, Map<Material, Boolean>> entry : materialValues.entrySet()) {
                for (Map.Entry<Material, Boolean> override : entry.getValue().entrySet()) {
                    target.set("material-overrides." + entry.getKey().key() + "." + override.getKey().name(),
                        override.getValue());
                }
            }
            target.save(destination);
        }
        return new MigrationResult(sourceFile, imported.size(), Set.copyOf(unsupported));
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
        put(result, Rule.END_PORTAL_FRAME_FILLING, "END_PORTAL_FRAMES_FILLING");
        put(result, Rule.GLOW_BERRY_PICKING, "GLOW_BERRIES_PICKING");
        put(result, Rule.BLOCK_HIT_PROJECTILE_REMOVAL, "BLOCK_HIT_PROJECTILES_REMOVING");

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

    private static Map<String, MaterialTarget> materialMappings() {
        Map<String, MaterialTarget> result = new LinkedHashMap<>();

        putMaterial(result, Rule.GRAVITY, "GRAVEL_FALLING", Material.GRAVEL);
        putMaterial(result, Rule.GRAVITY, "SAND_FALLING", Material.SAND, Material.RED_SAND);
        putMaterial(result, Rule.GRAVITY, "ANVILS_FALLING",
            Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL);
        putMaterial(result, Rule.GRAVITY, "DRAGON_EGGS_FALLING", Material.DRAGON_EGG);
        putMaterial(result, Rule.GRAVITY, "CONCRETE_POWDERS_FALLING", materialsEnding("CONCRETE_POWDER"));
        putMaterial(result, Rule.GRAVITY, "SCAFFOLDING_FALLING", Material.SCAFFOLDING);
        putMaterial(result, Rule.GRAVITY, "POINTED_DRIPSTONES_FALLING", Material.POINTED_DRIPSTONE);

        putMaterial(result, Rule.BLOCK_UPDATES, "LADDERS_DESTROYING", Material.LADDER);
        putMaterial(result, Rule.BLOCK_UPDATES, "SIGNS_DESTROYING", materialsContaining("SIGN"));
        putMaterial(result, Rule.BLOCK_UPDATES, "RAILS_DESTROYING",
            Material.RAIL, Material.POWERED_RAIL, Material.DETECTOR_RAIL, Material.ACTIVATOR_RAIL);
        putMaterial(result, Rule.BLOCK_UPDATES, "TORCHES_DESTROYING", Material.TORCH, Material.WALL_TORCH);
        putMaterial(result, Rule.BLOCK_UPDATES, "REDSTONE_TORCHES_DESTROYING",
            Material.REDSTONE_TORCH, Material.REDSTONE_WALL_TORCH);
        putMaterial(result, Rule.BLOCK_UPDATES, "SOUL_TORCHES_DESTROYING",
            Material.SOUL_TORCH, Material.SOUL_WALL_TORCH);
        putMaterial(result, Rule.BLOCK_UPDATES, "SAPLINGS_DESTROYING", materialsEnding("SAPLING"));

        putMaterial(result, Rule.FARMLAND_TRAMPLE, "FARMLANDS_TRAMPLING", Material.FARMLAND);
        putMaterial(result, Rule.DRAGON_EGG_TELEPORT, "DRAGON_EGGS_TELEPORTING", Material.DRAGON_EGG);
        putMaterial(result, Rule.TURTLE_EGG_TRAMPLE, "TURTLE_EGGS_TRAMPLING", Material.TURTLE_EGG);
        putMaterial(result, Rule.DRIPLEAF_TILT, "DRIPLEAFS_LOWERING", Material.BIG_DRIPLEAF);

        putMaterial(result, Rule.SNOW_MELT, "SNOW_MELTING", Material.SNOW, Material.SNOW_BLOCK, Material.POWDER_SNOW);
        putMaterial(result, Rule.FARMLAND_DRY, "FARMLANDS_DRYING", Material.FARMLAND);
        putMaterial(result, Rule.ICE_MELT, "ICE_MELTING", Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE);
        putMaterial(result, Rule.LEAF_DECAY, "LEAVES_DECAY", materialsEnding("LEAVES"));
        putMaterial(result, Rule.GROUND_FADE, "GRASS_BLOCKS_FADING", Material.GRASS_BLOCK);
        putMaterial(result, Rule.GROUND_FADE, "CRIMSON_NYLIUM_FADING", Material.CRIMSON_NYLIUM);
        putMaterial(result, Rule.GROUND_FADE, "WARPED_NYLIUM_FADING", Material.WARPED_NYLIUM);
        putMaterial(result, Rule.CORAL_FADE, "CORALS_DRYING", materialsContaining("CORAL"));

        putMaterial(result, Rule.PLANT_SPREAD, "GRASS_SPREADING", Material.GRASS_BLOCK);
        putMaterial(result, Rule.PLANT_SPREAD, "MYCELIUM_SPREADING", Material.MYCELIUM);
        putMaterial(result, Rule.MUSHROOM_GROWTH, "LITTLE_MUSHROOMS_SPREADING",
            Material.RED_MUSHROOM, Material.BROWN_MUSHROOM);
        putMaterial(result, Rule.MUSHROOM_GROWTH, "GIANT_MUSHROOMS_GROWING",
            Material.RED_MUSHROOM, Material.BROWN_MUSHROOM);
        putMaterial(result, Rule.STEM_GROWTH, "PUMPKINS_GROWING", Material.PUMPKIN_STEM, Material.ATTACHED_PUMPKIN_STEM);
        putMaterial(result, Rule.STEM_GROWTH, "MELONS_GROWING", Material.MELON_STEM, Material.ATTACHED_MELON_STEM);
        putMaterial(result, Rule.CROP_GROWTH, "NETHER_WARTS_GROWING", Material.NETHER_WART);
        putMaterial(result, Rule.CROP_GROWTH, "COCOAS_GROWING", Material.COCOA);
        putMaterial(result, Rule.CROP_GROWTH, "WHEAT_GROWING", Material.WHEAT);
        putMaterial(result, Rule.CROP_GROWTH, "POTATOES_GROWING", Material.POTATOES);
        putMaterial(result, Rule.CROP_GROWTH, "CARROTS_GROWING", Material.CARROTS);
        putMaterial(result, Rule.CROP_GROWTH, "BEETROOTS_GROWING", Material.BEETROOTS);
        putMaterial(result, Rule.CROP_GROWTH, "SWEET_BERRIES_GROWING", Material.SWEET_BERRY_BUSH);
        putMaterial(result, Rule.AMETHYST_GROWTH, "AMETHYST_CLUSTERS_GROWING", materialsContaining("AMETHYST"));
        putMaterial(result, Rule.VERTICAL_PLANT_GROWTH, "SUGAR_CANE_GROWING", Material.SUGAR_CANE);
        putMaterial(result, Rule.VERTICAL_PLANT_GROWTH, "CACTUS_GROWING", Material.CACTUS);
        putMaterial(result, Rule.VERTICAL_PLANT_GROWTH, "CHORUSES_GROWING", Material.CHORUS_FLOWER, Material.CHORUS_PLANT);
        putMaterial(result, Rule.VERTICAL_PLANT_GROWTH, "KELPS_GROWING", Material.KELP, Material.KELP_PLANT);
        putMaterial(result, Rule.VERTICAL_PLANT_GROWTH, "BAMBOO_GROWING", Material.BAMBOO, Material.BAMBOO_SAPLING);
        putMaterial(result, Rule.VINE_GROWTH, "VINES_GROWING", Material.VINE);
        putMaterial(result, Rule.VINE_GROWTH, "WEEPING_VINES_GROWING", Material.WEEPING_VINES, Material.WEEPING_VINES_PLANT);
        putMaterial(result, Rule.VINE_GROWTH, "TWISTING_VINES_GROWING", Material.TWISTING_VINES, Material.TWISTING_VINES_PLANT);
        putMaterial(result, Rule.VINE_GROWTH, "GLOW_BERRIES_GROWING", Material.CAVE_VINES, Material.CAVE_VINES_PLANT);
        putMaterial(result, Rule.DRIPSTONE_GROWTH, "POINTED_DRIPSTONES_GROWING", Material.POINTED_DRIPSTONE);
        return Map.copyOf(result);
    }

    private static void putMaterial(Map<String, MaterialTarget> mappings, Rule rule, String key, Material... materials) {
        mappings.put(key, new MaterialTarget(rule, List.of(materials)));
    }

    private static void putMaterial(Map<String, MaterialTarget> mappings, Rule rule, String key, List<Material> materials) {
        mappings.put(key, new MaterialTarget(rule, List.copyOf(materials)));
    }

    private static List<Material> materialsEnding(String suffix) {
        List<Material> result = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.name().endsWith(suffix)) result.add(material);
        }
        return result;
    }

    private static List<Material> materialsContaining(String fragment) {
        List<Material> result = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.name().contains(fragment)) result.add(material);
        }
        return result;
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

    private record MaterialTarget(Rule rule, List<Material> materials) {
    }
}
