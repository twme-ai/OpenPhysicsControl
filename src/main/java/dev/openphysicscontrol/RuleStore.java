package dev.openphysicscontrol;

import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RuleStore {
    private final JavaPlugin plugin;
    private final Map<UUID, Map<Rule, Boolean>> states = new ConcurrentHashMap<>();

    public RuleStore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
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
                    : true;
            values.put(rule, value);
            if (!yaml.isBoolean(path)) {
                yaml.set(path, value);
                changed = true;
            }
        }
        this.states.put(world.getUID(), Collections.unmodifiableMap(values));
        if (changed) save(yaml, file);
    }

    public void unload(World world) {
        this.states.remove(world.getUID());
    }

    public boolean enabled(World world, Rule rule) {
        Map<Rule, Boolean> worldRules = this.states.get(world.getUID());
        if (worldRules == null) {
            load(world);
            worldRules = this.states.get(world.getUID());
        }
        return worldRules.getOrDefault(rule, true);
    }

    public synchronized boolean set(World world, Rule rule, Boolean requested) {
        Map<Rule, Boolean> current = this.states.get(world.getUID());
        if (current == null) {
            load(world);
            current = this.states.get(world.getUID());
        }
        boolean value = requested == null ? !current.getOrDefault(rule, true) : requested;
        EnumMap<Rule, Boolean> updated = new EnumMap<>(current);
        updated.put(rule, value);
        this.states.put(world.getUID(), Collections.unmodifiableMap(updated));

        File file = file(world);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set(rule.key(), value);
        save(yaml, file);
        return value;
    }

    private File file(World world) {
        File directory = new File(this.plugin.getDataFolder(), "worlds");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Unable to create " + directory);
        }
        return new File(directory, world.getUID() + ".yml");
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
}
