package dev.openphysicscontrol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RuleStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void keepsNormalWorldNamesReadableAndEncodesUnsafeNames() {
        assertEquals("world.yml", RuleStore.worldFileName("world"));
        assertEquals("生存 世界.yml", RuleStore.worldFileName("生存 世界"));
        assertEquals("..%2Fnether.yml", RuleStore.worldFileName("../nether"));
        assertEquals("100%25.yml", RuleStore.worldFileName("100%"));
        assertEquals("%43ON.yml", RuleStore.worldFileName("CON"));
    }

    @Test
    void migratesUuidFileWithoutOverwritingAWorldNameFile() throws Exception {
        UUID firstId = UUID.randomUUID();
        Path firstLegacy = this.temporaryDirectory.resolve(firstId + ".yml");
        Files.writeString(firstLegacy, "gravity: false\n");

        File migrated = RuleStore.resolveWorldFile(this.temporaryDirectory.toFile(), "survival", firstId);
        assertEquals("survival.yml", migrated.getName());
        assertEquals("gravity: false\n", Files.readString(migrated.toPath()));
        assertFalse(Files.exists(firstLegacy));

        UUID secondId = UUID.randomUUID();
        Path secondLegacy = this.temporaryDirectory.resolve(secondId + ".yml");
        Path named = this.temporaryDirectory.resolve("creative.yml");
        Files.writeString(secondLegacy, "gravity: false\n");
        Files.writeString(named, "gravity: true\n");

        File selected = RuleStore.resolveWorldFile(this.temporaryDirectory.toFile(), "creative", secondId);
        assertEquals("gravity: true\n", Files.readString(selected.toPath()));
        assertTrue(Files.exists(secondLegacy));
    }

    @Test
    void parsesExactBlockMaterialOverrides() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("material-overrides.gravity.SAND", true);
        yaml.set("material-overrides.gravity.GRAVEL", false);
        yaml.set("material-overrides.crop-growth.WHEAT", false);
        yaml.set("material-overrides.crop-growth.BONE_MEAL", true);
        yaml.set("material-overrides.unknown-rule.STONE", true);

        var overrides = RuleStore.parseMaterialOverrides(yaml, "material-overrides",
            material -> material != Material.BONE_MEAL);
        assertEquals(Boolean.TRUE, overrides.get(Rule.GRAVITY).get(Material.SAND));
        assertEquals(Boolean.FALSE, overrides.get(Rule.GRAVITY).get(Material.GRAVEL));
        assertEquals(Boolean.FALSE, overrides.get(Rule.CROP_GROWTH).get(Material.WHEAT));
        assertFalse(overrides.containsKey(Rule.BONE_MEAL));
    }

    @Test
    void allWorldsUpdatesSavedUnloadedWorldsButSkipsLoadedOnes() throws Exception {
        Path loaded = this.temporaryDirectory.resolve("world.yml");
        Path unloaded = this.temporaryDirectory.resolve("archive.yml");
        Files.writeString(loaded, "hanging-entity-detachment: false\n");
        Files.writeString(unloaded, "hanging-entity-detachment: false\ngravity: false\n");
        Files.writeString(this.temporaryDirectory.resolve("notes.txt"), "untouched\n");

        int changed = RuleStore.applyToSavedWorldFiles(this.temporaryDirectory.toFile(), Set.of("world.yml"),
            Rule.HANGING_ENTITY_DETACHMENT, true);

        assertEquals(1, changed);
        assertEquals("hanging-entity-detachment: false\n", Files.readString(loaded));
        YamlConfiguration archive = YamlConfiguration.loadConfiguration(unloaded.toFile());
        assertTrue(archive.getBoolean("hanging-entity-detachment"));
        assertFalse(archive.getBoolean("gravity", true));
        assertEquals("untouched\n", Files.readString(this.temporaryDirectory.resolve("notes.txt")));
    }

    @Test
    void allWorldsToleratesAMissingWorldsDirectory() {
        assertEquals(0, RuleStore.applyToSavedWorldFiles(this.temporaryDirectory.resolve("missing").toFile(),
            Set.of(), Rule.GRAVITY, false));
    }
}
