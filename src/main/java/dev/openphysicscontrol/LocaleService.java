package dev.openphysicscontrol;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LocaleService {
    private static final String FALLBACK = "en";
    private static final Set<String> BUNDLED = Set.of("en", "zh_tw");
    private static final Map<String, Map<String, Set<String>>> REPLACED_BUNDLED_VALUES = Map.of(
        "en", Map.of(
            "rule-changed", Set.of("<prefix> <gray><rule> in <world> is now <state>.</gray>"),
            "state-on", Set.of("enabled", "Enabled", "<green>Enabled</green>"),
            "state-off", Set.of("disabled", "Disabled", "<red>Disabled</red>"),
            "menu-state-on", Set.of("<green>Enabled</green>"),
            "menu-state-off", Set.of("<red>Disabled</red>"),
            "group-blocks", Set.of("<dark_gray>Blocks and signals</dark_gray>"),
            "group-climate", Set.of("<dark_gray>Fire, climate, and time</dark_gray>"),
            "group-growth", Set.of("<dark_gray>Plants and growth</dark_gray>"),
            "group-entities", Set.of("<dark_gray>Entities and players</dark_gray>"),
            "group-machines", Set.of("<dark_gray>Machines and processing</dark_gray>")
        ),
        "zh_tw", Map.of(
            "rule-changed", Set.of("<prefix> <gray><world> 的 <rule> 現在是 <state>。</gray>"),
            "state-on", Set.of("已啟用", "<green>已啟用</green>"),
            "state-off", Set.of("已停用", "<red>已停用</red>"),
            "menu-state-on", Set.of("<green>已啟用</green>"),
            "menu-state-off", Set.of("<red>已停用</red>"),
            "group-blocks", Set.of("<dark_gray>方塊與訊號</dark_gray>"),
            "group-climate", Set.of("<dark_gray>火焰、氣候與時間</dark_gray>"),
            "group-growth", Set.of("<dark_gray>植物與生長</dark_gray>"),
            "group-entities", Set.of("<dark_gray>實體與玩家</dark_gray>"),
            "group-machines", Set.of("<dark_gray>機械與處理</dark_gray>")
        )
    );

    private final JavaPlugin plugin;
    private final Map<UUID, String> preferences = new ConcurrentHashMap<>();
    private volatile Map<String, Map<String, String>> translations = Map.of();
    private volatile String defaultLocale = FALLBACK;
    private volatile boolean followClient = true;
    private File preferencesFile;

    public LocaleService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        this.plugin.saveDefaultConfig();
        this.plugin.reloadConfig();
        this.defaultLocale = normalize(this.plugin.getConfig().getString("default-language", FALLBACK));
        this.followClient = this.plugin.getConfig().getBoolean("follow-client-language", true);

        Map<String, Map<String, String>> loaded = new LinkedHashMap<>();
        for (String locale : BUNDLED) {
            String resource = "lang/" + locale + ".yml";
            File file = new File(this.plugin.getDataFolder(), resource);
            if (!file.isFile()) this.plugin.saveResource(resource, false);
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            try (InputStream input = this.plugin.getResource(resource)) {
                if (input != null) {
                    YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(input, StandardCharsets.UTF_8));
                    if (mergeBundledUpdates(locale, yaml, defaults) > 0) yaml.save(file);
                }
            } catch (IOException exception) {
                this.plugin.getLogger().warning("Unable to update " + resource + ": " + exception.getMessage());
            }
            Map<String, String> phrases = new LinkedHashMap<>();
            for (String key : yaml.getKeys(false)) {
                String value = yaml.getString(key);
                if (value != null) phrases.put(key, value);
            }
            loaded.put(locale, Collections.unmodifiableMap(phrases));
        }
        if (!loaded.containsKey(this.defaultLocale)) this.defaultLocale = FALLBACK;
        this.translations = Collections.unmodifiableMap(loaded);
        loadPreferences();
    }

    public String phrase(String locale, String key) {
        Map<String, String> selected = this.translations.getOrDefault(locale, this.translations.get(FALLBACK));
        String value = selected.get(key);
        if (value == null && !FALLBACK.equals(locale)) value = this.translations.get(FALLBACK).get(key);
        return value == null ? "<red>Missing phrase: " + key + "</red>" : value;
    }

    public String localeOf(CommandSender sender) {
        if (!(sender instanceof Player player)) return this.defaultLocale;
        String preference = this.preferences.get(player.getUniqueId());
        if (preference != null) return preference;
        return this.followClient ? match(player.getLocale()) : this.defaultLocale;
    }

    public boolean select(Player player, String requested) {
        String locale = normalize(requested);
        if (locale.equals("auto")) {
            this.preferences.remove(player.getUniqueId());
        } else if (this.translations.containsKey(locale)) {
            this.preferences.put(player.getUniqueId(), locale);
        } else {
            return false;
        }
        savePreferences();
        return true;
    }

    public Set<String> available() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(this.translations.keySet()));
    }

    private String match(String raw) {
        String locale = normalize(raw);
        if (this.translations.containsKey(locale)) return locale;
        if (locale.equals("zh_hk") || locale.equals("zh_mo")) return "zh_tw";
        int separator = locale.indexOf('_');
        if (separator > 0 && this.translations.containsKey(locale.substring(0, separator))) {
            return locale.substring(0, separator);
        }
        return this.defaultLocale;
    }

    private void loadPreferences() {
        this.preferences.clear();
        this.preferencesFile = new File(this.plugin.getDataFolder(), "player-languages.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.preferencesFile);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String locale = normalize(yaml.getString(key, "auto"));
                if (this.translations.containsKey(locale)) this.preferences.put(uuid, locale);
            } catch (IllegalArgumentException exception) {
                this.plugin.getLogger().warning("Ignoring invalid language preference: " + key);
            }
        }
    }

    private synchronized void savePreferences() {
        YamlConfiguration yaml = new YamlConfiguration();
        this.preferences.forEach((uuid, locale) -> yaml.set(uuid.toString(), locale));
        try {
            yaml.save(this.preferencesFile);
        } catch (IOException exception) {
            this.plugin.getLogger().warning("Unable to save player languages: " + exception.getMessage());
        }
    }

    private static String normalize(String input) {
        if (input == null || input.isBlank()) return "auto";
        return input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    static int mergeMissing(YamlConfiguration target, YamlConfiguration defaults) {
        int added = 0;
        for (String key : defaults.getKeys(false)) {
            if (target.isString(key)) continue;
            String value = defaults.getString(key);
            if (value == null) continue;
            target.set(key, value);
            added++;
        }
        return added;
    }

    static int mergeBundledUpdates(String locale, YamlConfiguration target, YamlConfiguration defaults) {
        int changed = mergeMissing(target, defaults);
        Map<String, Set<String>> replaced = REPLACED_BUNDLED_VALUES.getOrDefault(locale, Map.of());
        for (Map.Entry<String, Set<String>> entry : replaced.entrySet()) {
            String current = target.getString(entry.getKey());
            String updated = defaults.getString(entry.getKey());
            if (current == null || updated == null || !entry.getValue().contains(current) || current.equals(updated)) {
                continue;
            }
            target.set(entry.getKey(), updated);
            changed++;
        }
        return changed;
    }
}
