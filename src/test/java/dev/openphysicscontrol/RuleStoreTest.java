package dev.openphysicscontrol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
