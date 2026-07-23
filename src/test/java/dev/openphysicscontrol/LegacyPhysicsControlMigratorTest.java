package dev.openphysicscontrol;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LegacyPhysicsControlMigratorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void importsModernLegacyWorldRulesWithoutChangingTheSource() throws Exception {
        Path plugins = this.temporaryDirectory.resolve("plugins");
        Path source = plugins.resolve("PhysicsControl/triggers/survival.yml");
        Path destination = plugins.resolve("OpenPhysicsControl/worlds/survival.yml");
        Files.createDirectories(source.getParent());
        String sourceContent = """
            GRAVEL_FALLING: false
            SAND_FALLING: true
            WATER_FLOWING: false
            FROGSPAWN_LAYING_AND_SPAWNING: false
            BONE_MEAL_USAGE: false
            GLOW_BERRIES_PICKING: false
            END_PORTAL_FRAMES_FILLING: false
            BLOCK_HIT_PROJECTILES_REMOVING: true
            """;
        Files.writeString(source, sourceContent);

        LegacyPhysicsControlMigrator.MigrationResult result = LegacyPhysicsControlMigrator.migrate(
            plugins.toFile(), "survival", destination.toFile());

        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(destination.toFile());
        assertTrue(result.sourceFound());
        assertEquals(source.toFile(), result.source());
        assertEquals(8, result.importedRules());
        assertTrue(result.unsupportedTriggers().isEmpty());
        assertFalse(migrated.getBoolean("gravity"));
        assertFalse(migrated.getBoolean("water-flow"));
        assertFalse(migrated.getBoolean("mob-griefing"));
        assertFalse(migrated.getBoolean("frogspawn-hatch"));
        assertFalse(migrated.getBoolean("bone-meal"));
        assertFalse(migrated.getBoolean("glow-berry-picking"));
        assertFalse(migrated.getBoolean("end-portal-frame-filling"));
        assertTrue(migrated.getBoolean("block-hit-projectile-removal"));
        assertEquals(sourceContent, Files.readString(source));
    }

    @Test
    void importsThePreOneOneWorldSectionAndHistoricBoneMealName() throws Exception {
        Path plugins = this.temporaryDirectory.resolve("plugins");
        Path source = plugins.resolve("PhysicsControl/config.yml");
        Path destination = plugins.resolve("OpenPhysicsControl/worlds/world.nether.yml");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            world.nether:
              WHEAT_GROWING: false
              BONE_MEAL_USAGE: false
            """);

        LegacyPhysicsControlMigrator.MigrationResult result = LegacyPhysicsControlMigrator.migrate(
            plugins.toFile(), "world.nether", destination.toFile());

        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(destination.toFile());
        assertTrue(result.sourceFound());
        assertEquals(source.toFile(), result.source());
        assertEquals(2, result.importedRules());
        assertFalse(migrated.getBoolean("crop-growth"));
        assertFalse(migrated.getBoolean("bone-meal"));
    }

    @Test
    void importsEverySupportedCurrentLegacyTrigger() throws Exception {
        Path plugins = this.temporaryDirectory.resolve("plugins");
        Path source = plugins.resolve("PhysicsControl/triggers/world.yml");
        Path destination = plugins.resolve("OpenPhysicsControl/worlds/world.yml");
        Files.createDirectories(source.getParent());
        YamlConfiguration legacy = new YamlConfiguration();
        for (String trigger : List.of(
            "RABBITS_EATING_CARROTS", "VILLAGERS_FARMING", "SHEEPS_EATING_GRASS",
            "SILVERFISHES_HIDING_IN_BLOCKS", "ZOMBIES_BREAK_DOORS", "ENDERMANS_GRIEFING",
            "WITHERS_GRIEFING", "TURTLES_LAYING_EGGS", "FOXES_EATS_FROM_SWEET_BERRY_BUSHES",
            "RAVAGERS_DESTROY_BLOCKS", "POWDER_SNOW_MELTS_FROM_BURNING_ENTITIES",
            "SNOW_GOLEMS_CREATE_SNOW", "WITHER_CREATE_WITHER_ROSE_BLOCKS",
            "FROGSPAWN_LAYING_AND_SPAWNING", "PLAYERS_FLINT_USAGE", "BONE_MEAL_USAGE",
            "PLAYERS_BONE_MEAL_USAGE", "BURNING_ARROWS_ACTIVATE_TNT", "FARMLANDS_TRAMPLING",
            "END_PORTAL_FRAMES_FILLING", "GLOW_BERRIES_PICKING", "BLOCK_HIT_PROJECTILES_REMOVING",
            "DRAGON_EGGS_TELEPORTING", "FROSTED_ICE_PHYSICS", "TURTLE_EGGS_TRAMPLING",
            "DRIPLEAFS_LOWERING", "LADDERS_DESTROYING", "SIGNS_DESTROYING", "RAILS_DESTROYING",
            "TORCHES_DESTROYING", "REDSTONE_TORCHES_DESTROYING", "SOUL_TORCHES_DESTROYING",
            "SAPLINGS_DESTROYING", "GRAVEL_FALLING", "SAND_FALLING", "ANVILS_FALLING",
            "DRAGON_EGGS_FALLING", "CONCRETE_POWDERS_FALLING", "SCAFFOLDING_FALLING",
            "POINTED_DRIPSTONES_FALLING", "WATER_FLOWING", "LAVA_FLOWING", "FIRE_SPREADING",
            "SNOW_MELTING", "FARMLANDS_DRYING", "ICE_MELTING", "LEAVES_DECAY",
            "GRASS_BLOCKS_FADING", "CRIMSON_NYLIUM_FADING", "WARPED_NYLIUM_FADING", "CORALS_DRYING",
            "SCULKS_SPREADING", "GRASS_SPREADING", "MYCELIUM_SPREADING", "LITTLE_MUSHROOMS_SPREADING",
            "GIANT_MUSHROOMS_GROWING", "PUMPKINS_GROWING", "MELONS_GROWING", "NETHER_WARTS_GROWING",
            "COCOAS_GROWING", "WHEAT_GROWING", "POTATOES_GROWING", "CARROTS_GROWING",
            "BEETROOTS_GROWING", "SWEET_BERRIES_GROWING", "AMETHYST_CLUSTERS_GROWING",
            "SUGAR_CANE_GROWING", "CACTUS_GROWING", "CHORUSES_GROWING", "KELPS_GROWING", "BAMBOO_GROWING",
            "TREES_GROWING", "VINES_GROWING", "WEEPING_VINES_GROWING", "TWISTING_VINES_GROWING",
            "GLOW_BERRIES_GROWING", "POINTED_DRIPSTONES_GROWING"
        )) legacy.set(trigger, false);
        legacy.save(source.toFile());

        LegacyPhysicsControlMigrator.MigrationResult result = LegacyPhysicsControlMigrator.migrate(
            plugins.toFile(), "world", destination.toFile());

        Set<Rule> expected = Set.of(
            Rule.MOB_GRIEFING, Rule.MOB_BLOCK_FORM, Rule.FROGSPAWN_HATCH, Rule.FIRE_IGNITE, Rule.BONE_MEAL,
            Rule.END_PORTAL_FRAME_FILLING, Rule.GLOW_BERRY_PICKING, Rule.BLOCK_HIT_PROJECTILE_REMOVAL,
            Rule.TNT_PRIME, Rule.FARMLAND_TRAMPLE, Rule.DRAGON_EGG_TELEPORT, Rule.FROSTED_ICE,
            Rule.TURTLE_EGG_TRAMPLE, Rule.DRIPLEAF_TILT, Rule.BLOCK_UPDATES, Rule.GRAVITY, Rule.WATER_FLOW,
            Rule.LAVA_FLOW, Rule.FIRE_SPREAD, Rule.SNOW_MELT, Rule.FARMLAND_DRY, Rule.ICE_MELT,
            Rule.LEAF_DECAY, Rule.GROUND_FADE, Rule.CORAL_FADE, Rule.SCULK_SPREAD, Rule.PLANT_SPREAD,
            Rule.MUSHROOM_GROWTH, Rule.STEM_GROWTH, Rule.CROP_GROWTH, Rule.AMETHYST_GROWTH,
            Rule.VERTICAL_PLANT_GROWTH, Rule.TREE_GROWTH, Rule.VINE_GROWTH, Rule.DRIPSTONE_GROWTH
        );
        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(destination.toFile());
        assertEquals(expected.size(), result.importedRules());
        assertTrue(result.unsupportedTriggers().isEmpty());
        assertEquals(expected.stream().map(Rule::key).collect(Collectors.toSet()), migrated.getKeys(false));
        for (Rule rule : expected) assertFalse(migrated.getBoolean(rule.key()), rule.key());
    }

    @Test
    void neverOverwritesAnExistingOpenPhysicsControlWorldFile() throws Exception {
        Path plugins = this.temporaryDirectory.resolve("plugins");
        Path source = plugins.resolve("PhysicsControl/triggers/world.yml");
        Path destination = plugins.resolve("OpenPhysicsControl/worlds/world.yml");
        Files.createDirectories(source.getParent());
        Files.createDirectories(destination.getParent());
        Files.writeString(source, "GRAVEL_FALLING: false\n");
        Files.writeString(destination, "gravity: true\n");

        LegacyPhysicsControlMigrator.MigrationResult result = LegacyPhysicsControlMigrator.migrate(
            plugins.toFile(), "world", destination.toFile());

        assertFalse(result.sourceFound());
        assertEquals("gravity: true\n", Files.readString(destination));
        assertTrue(Files.exists(source));
    }
}
