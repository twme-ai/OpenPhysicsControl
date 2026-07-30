package dev.openphysicscontrol;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class RuleStore {
    private static final String DEFAULTS_RESOURCE = "default-rules.yml";
    private static final String INVALID_FILE_CHARACTERS = "\\/:*?\"<>|%";
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    private final JavaPlugin plugin;
    private final Map<UUID, Map<Rule, Boolean>> states = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Rule, Map<Material, Boolean>>> materialStates = new ConcurrentHashMap<>();
    private volatile Map<Rule, Boolean> defaults = allDefaults();
    private volatile Map<Rule, Map<Material, Boolean>> materialDefaults = Map.of();

    public RuleStore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void reload() {
        Defaults loadedDefaults = loadDefaults();
        this.defaults = loadedDefaults.rules();
        this.materialDefaults = loadedDefaults.materialOverrides();
        this.states.clear();
        this.materialStates.clear();
        for (World world : this.plugin.getServer().getWorlds()) load(world);
    }

    public synchronized void load(World world) {
        File file = file(world);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        EnumMap<Rule, Boolean> values = new EnumMap<>(Rule.class);
        boolean changed = false;
        for (Rule rule : Rule.values()) {
            String path = rule.key();
            String legacyPath = legacyPath(rule);
            boolean value = yaml.isBoolean(path)
                ? yaml.getBoolean(path)
                : legacyPath != null && yaml.isBoolean(legacyPath)
                    ? yaml.getBoolean(legacyPath)
                    : this.defaults.getOrDefault(rule, rule.defaultEnabled());
            values.put(rule, value);
            if (!yaml.isBoolean(path)) {
                yaml.set(path, value);
                changed = true;
            }
        }
        this.states.put(world.getUID(), Collections.unmodifiableMap(values));
        this.materialStates.put(world.getUID(), mergeMaterialOverrides(
            this.materialDefaults, parseMaterialOverrides(yaml, "material-overrides", this.plugin.getLogger()::warning,
                Material::isBlock)));
        if (changed) save(yaml, file);
    }

    public void unload(World world) {
        this.states.remove(world.getUID());
        this.materialStates.remove(world.getUID());
    }

    public boolean enabled(World world, Rule rule) {
        Map<Rule, Boolean> worldRules = this.states.get(world.getUID());
        if (worldRules == null) {
            load(world);
            worldRules = this.states.get(world.getUID());
        }
        return worldRules.getOrDefault(rule, this.defaults.getOrDefault(rule, rule.defaultEnabled()));
    }

    public boolean enabled(World world, Rule rule, Material material) {
        if (material == null) return enabled(world, rule);
        if (!this.states.containsKey(world.getUID())) load(world);
        Map<Material, Boolean> overrides = this.materialStates.getOrDefault(world.getUID(), Map.of()).get(rule);
        if (overrides != null && overrides.containsKey(material)) return overrides.get(material);
        return enabled(world, rule);
    }

    public int materialOverrideCount(World world, Rule rule) {
        if (!this.states.containsKey(world.getUID())) load(world);
        return this.materialStates.getOrDefault(world.getUID(), Map.of()).getOrDefault(rule, Map.of()).size();
    }

    public synchronized boolean set(World world, Rule rule, Boolean requested) {
        Map<Rule, Boolean> current = this.states.get(world.getUID());
        if (current == null) {
            load(world);
            current = this.states.get(world.getUID());
        }
        boolean value = requested == null
            ? !current.getOrDefault(rule, this.defaults.getOrDefault(rule, rule.defaultEnabled()))
            : requested;
        EnumMap<Rule, Boolean> updated = new EnumMap<>(current);
        updated.put(rule, value);
        this.states.put(world.getUID(), Collections.unmodifiableMap(updated));

        File file = file(world);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set(rule.key(), value);
        save(yaml, file);
        return value;
    }

    public synchronized boolean setMaterial(World world, Rule rule, Material material, boolean requested) {
        if (!material.isBlock()) throw new IllegalArgumentException(material + " is not a block material");
        if (!this.states.containsKey(world.getUID())) load(world);
        Map<Rule, Map<Material, Boolean>> updated = mutableMaterialOverrides(
            this.materialStates.getOrDefault(world.getUID(), Map.of()));
        updated.computeIfAbsent(rule, ignored -> new HashMap<>()).put(material, requested);
        this.materialStates.put(world.getUID(), freezeMaterialOverrides(updated));

        File file = file(world);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set(materialPath(rule, material), requested);
        save(yaml, file);
        return requested;
    }

    public synchronized void clearMaterial(World world, Rule rule, Material material) {
        if (!material.isBlock()) throw new IllegalArgumentException(material + " is not a block material");
        if (!this.states.containsKey(world.getUID())) load(world);
        Map<Rule, Map<Material, Boolean>> updated = mutableMaterialOverrides(
            this.materialStates.getOrDefault(world.getUID(), Map.of()));
        Map<Material, Boolean> ruleOverrides = updated.get(rule);
        if (ruleOverrides != null) {
            ruleOverrides.remove(material);
            if (ruleOverrides.isEmpty()) updated.remove(rule);
        }
        Boolean defaultValue = this.materialDefaults.getOrDefault(rule, Map.of()).get(material);
        if (defaultValue != null) updated.computeIfAbsent(rule, ignored -> new HashMap<>()).put(material, defaultValue);
        this.materialStates.put(world.getUID(), freezeMaterialOverrides(updated));

        File file = file(world);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set(materialPath(rule, material), null);
        save(yaml, file);
    }

    private Defaults loadDefaults() {
        File file = new File(this.plugin.getDataFolder(), DEFAULTS_RESOURCE);
        if (!file.isFile()) this.plugin.saveResource(DEFAULTS_RESOURCE, false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        EnumMap<Rule, Boolean> values = new EnumMap<>(Rule.class);
        boolean changed = false;
        for (Rule rule : Rule.values()) {
            boolean value = yaml.isBoolean(rule.key()) ? yaml.getBoolean(rule.key()) : rule.defaultEnabled();
            values.put(rule, value);
            if (!yaml.isBoolean(rule.key())) {
                yaml.set(rule.key(), value);
                changed = true;
            }
        }
        if (!yaml.isSet("material-overrides")) {
            yaml.set("material-overrides", Map.of());
            changed = true;
        }
        if (changed) save(yaml, file);
        return new Defaults(Collections.unmodifiableMap(values),
            parseMaterialOverrides(yaml, "material-overrides", this.plugin.getLogger()::warning, Material::isBlock));
    }

    static Map<Rule, Map<Material, Boolean>> parseMaterialOverrides(YamlConfiguration yaml, String path) {
        return parseMaterialOverrides(yaml, path, Material::isBlock);
    }

    static Map<Rule, Map<Material, Boolean>> parseMaterialOverrides(
        YamlConfiguration yaml, String path, Predicate<Material> isBlock) {
        return parseMaterialOverrides(yaml, path, ignored -> { }, isBlock);
    }

    private static Map<Rule, Map<Material, Boolean>> parseMaterialOverrides(
        YamlConfiguration yaml, String path, Consumer<String> warn, Predicate<Material> isBlock) {
        ConfigurationSection root = yaml.getConfigurationSection(path);
        if (root == null) return Map.of();
        Map<Rule, Map<Material, Boolean>> parsed = new EnumMap<>(Rule.class);
        for (String rawRule : root.getKeys(false)) {
            Rule rule;
            try {
                rule = Rule.parse(rawRule);
            } catch (IllegalArgumentException exception) {
                warn.accept("Ignoring unknown material override rule '" + rawRule + "' in " + path + ".");
                continue;
            }
            ConfigurationSection materials = root.getConfigurationSection(rawRule);
            if (materials == null) {
                warn.accept("Ignoring material overrides for " + rawRule + "; it must be a YAML section.");
                continue;
            }
            Map<Material, Boolean> values = new HashMap<>();
            for (String rawMaterial : materials.getKeys(false)) {
                Material material = blockMaterial(rawMaterial, isBlock);
                if (material == null) {
                    warn.accept("Ignoring unknown or non-block material '" + rawMaterial + "' for " + rawRule + ".");
                    continue;
                }
                if (!materials.isBoolean(rawMaterial)) {
                    warn.accept("Ignoring non-boolean material override " + rawRule + "." + rawMaterial + ".");
                    continue;
                }
                values.put(material, materials.getBoolean(rawMaterial));
            }
            if (!values.isEmpty()) parsed.put(rule, values);
        }
        return freezeMaterialOverrides(parsed);
    }

    static Material blockMaterial(String input) {
        return blockMaterial(input, Material::isBlock);
    }

    private static Material blockMaterial(String input, Predicate<Material> isBlock) {
        try {
            Material material = Material.valueOf(input.trim().toUpperCase(Locale.ROOT));
            return isBlock.test(material) ? material : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String materialPath(Rule rule, Material material) {
        return "material-overrides." + rule.key() + "." + material.name();
    }

    private static Map<Rule, Map<Material, Boolean>> mergeMaterialOverrides(
        Map<Rule, Map<Material, Boolean>> base, Map<Rule, Map<Material, Boolean>> overrides) {
        Map<Rule, Map<Material, Boolean>> merged = mutableMaterialOverrides(base);
        for (Map.Entry<Rule, Map<Material, Boolean>> entry : overrides.entrySet()) {
            merged.computeIfAbsent(entry.getKey(), ignored -> new HashMap<>()).putAll(entry.getValue());
        }
        return freezeMaterialOverrides(merged);
    }

    private static Map<Rule, Map<Material, Boolean>> mutableMaterialOverrides(
        Map<Rule, Map<Material, Boolean>> source) {
        Map<Rule, Map<Material, Boolean>> copy = new EnumMap<>(Rule.class);
        for (Map.Entry<Rule, Map<Material, Boolean>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return copy;
    }

    private static Map<Rule, Map<Material, Boolean>> freezeMaterialOverrides(
        Map<Rule, Map<Material, Boolean>> source) {
        Map<Rule, Map<Material, Boolean>> frozen = new EnumMap<>(Rule.class);
        for (Map.Entry<Rule, Map<Material, Boolean>> entry : source.entrySet()) {
            frozen.put(entry.getKey(), Collections.unmodifiableMap(new HashMap<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(frozen);
    }

    private File file(World world) {
        File directory = new File(this.plugin.getDataFolder(), "worlds");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Unable to create " + directory);
        }
        File named = new File(directory, worldFileName(world.getName()));
        File legacy = new File(directory, world.getUID() + ".yml");
        migrateLegacyPhysicsControl(world, named, legacy);
        boolean migration = !named.exists() && legacy.isFile();
        try {
            File resolved = resolveWorldFile(directory, world.getName(), world.getUID());
            if (migration) {
                this.plugin.getLogger().info("Migrated world rules from " + legacy.getName()
                    + " to " + resolved.getName() + ".");
            }
            return resolved;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to migrate world rules for " + world.getName(), exception);
        }
    }

    private void migrateLegacyPhysicsControl(World world, File named, File uuidFile) {
        if (named.exists() || uuidFile.isFile()) return;
        File pluginsDirectory = this.plugin.getDataFolder().getParentFile();
        if (pluginsDirectory == null) return;
        try {
            LegacyPhysicsControlMigrator.MigrationResult result = LegacyPhysicsControlMigrator.migrate(
                pluginsDirectory, world.getName(), named);
            if (!result.sourceFound()) return;
            if (result.importedRules() == 0) {
                this.plugin.getLogger().warning("Found legacy PhysicsControl settings for world " + world.getName()
                    + " but no supported rules to import from " + result.source().getPath() + ".");
            } else {
                this.plugin.getLogger().info("Imported " + result.importedRules()
                    + " rule values for world " + world.getName() + " from legacy PhysicsControl settings "
                    + result.source().getPath() + ". The source file was preserved.");
            }
            if (!result.unsupportedTriggers().isEmpty()) {
                this.plugin.getLogger().warning("Legacy PhysicsControl triggers without a matching OpenPhysicsControl rule"
                    + " for world " + world.getName() + ": "
                    + String.join(", ", result.unsupportedTriggers()) + ".");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to migrate legacy PhysicsControl rules for " + world.getName(), exception);
        }
    }

    private void save(YamlConfiguration yaml, File file) {
        try {
            yaml.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save " + file, exception);
        }
    }

    static String legacyPath(Rule rule) {
        return switch (rule) {
            case FIRE_EXTINGUISH, ICE_MELT, SNOW_MELT, FROSTED_ICE, CORAL_FADE,
                GROUND_FADE, COPPER_WEATHER -> "block-fade";
            case CROP_GROWTH, STEM_GROWTH, VERTICAL_PLANT_GROWTH, VINE_GROWTH,
                MUSHROOM_GROWTH, AMETHYST_GROWTH, DRIPSTONE_GROWTH,
                TURTLE_EGG_HATCH, FROGSPAWN_HATCH -> "plant-growth";
            case EXPLOSION_BLOCK_DAMAGE -> "explosions";
            case FLUID_REACTIONS, CONCRETE_HARDEN, NATURAL_BLOCK_FORM -> "block-formation";
            default -> null;
        };
    }

    static File resolveWorldFile(File directory, String worldName, UUID worldId) throws IOException {
        File named = new File(directory, worldFileName(worldName));
        File legacy = new File(directory, worldId + ".yml");
        if (!named.exists() && legacy.isFile()) Files.move(legacy.toPath(), named.toPath());
        return named;
    }

    static String worldFileName(String worldName) {
        if (worldName == null || worldName.isEmpty()) {
            throw new IllegalArgumentException("World name cannot be empty");
        }
        boolean dotPath = worldName.equals(".") || worldName.equals("..");
        boolean reserved = isWindowsReserved(worldName);
        StringBuilder encoded = new StringBuilder(worldName.length());
        for (int offset = 0; offset < worldName.length();) {
            int codePoint = worldName.codePointAt(offset);
            int width = Character.charCount(codePoint);
            boolean trailing = offset + width == worldName.length();
            boolean unsafe = dotPath || Character.isISOControl(codePoint)
                || INVALID_FILE_CHARACTERS.indexOf(codePoint) >= 0
                || trailing && (codePoint == '.' || codePoint == ' ')
                || offset == 0 && reserved;
            if (unsafe) {
                appendEncoded(encoded, codePoint);
            } else {
                encoded.appendCodePoint(codePoint);
            }
            offset += width;
        }
        return encoded + ".yml";
    }

    private static boolean isWindowsReserved(String worldName) {
        String trimmed = worldName.stripTrailing();
        int extension = trimmed.indexOf('.');
        String stem = extension < 0 ? trimmed : trimmed.substring(0, extension);
        return WINDOWS_RESERVED_NAMES.contains(stem.toUpperCase(Locale.ROOT));
    }

    private static void appendEncoded(StringBuilder target, int codePoint) {
        byte[] bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
        for (byte value : bytes) {
            int unsigned = Byte.toUnsignedInt(value);
            target.append('%').append(HEX[unsigned >>> 4]).append(HEX[unsigned & 0x0F]);
        }
    }

    private static Map<Rule, Boolean> allDefaults() {
        EnumMap<Rule, Boolean> values = new EnumMap<>(Rule.class);
        for (Rule rule : Rule.values()) values.put(rule, rule.defaultEnabled());
        return Collections.unmodifiableMap(values);
    }

    private record Defaults(Map<Rule, Boolean> rules, Map<Rule, Map<Material, Boolean>> materialOverrides) {
    }
}
