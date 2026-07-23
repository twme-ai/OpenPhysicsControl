package dev.openphysicscontrol;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class ResourceTest {
    private static final String[] LOCALES = {"en", "zh_tw"};

    @Test
    @SuppressWarnings("unchecked")
    void descriptorKeepsRequestedCompatibility() {
        Map<String, Object> plugin = yaml("plugin.yml");
        assertEquals("1.13", plugin.get("api-version"));
        assertEquals(Boolean.TRUE, plugin.get("folia-supported"));
        Map<String, Object> commands = (Map<String, Object>) plugin.get("commands");
        Map<String, Object> command = (Map<String, Object>) commands.get("openphysics");
        assertEquals(List.of("ophysics", "opc", "pc"), command.get("aliases"));
    }

    @Test
    void allMessagesAreValidMiniMessage() {
        MiniMessage miniMessage = MiniMessage.miniMessage();
        for (String locale : LOCALES) {
            Map<String, Object> phrases = yaml("lang/" + locale + ".yml");
            for (Map.Entry<String, Object> phrase : phrases.entrySet()) {
                String value = String.valueOf(phrase.getValue())
                    .replaceAll("<[a-z-]+>", "value");
                assertNotNull(miniMessage.deserialize(value), locale + ":" + phrase.getKey());
            }
        }
    }

    @Test
    void nestedRulePlaceholdersRemainPlainText() {
        MiniMessage miniMessage = MiniMessage.miniMessage();
        for (String locale : LOCALES) {
            Map<String, Object> phrases = yaml("lang/" + locale + ".yml");
            Set<String> ruleKeys = phrases.keySet().stream()
                .filter(key -> key.startsWith("rule-"))
                .filter(key -> !Set.of("rule-changed", "rule-not-found").contains(key))
                .collect(Collectors.toSet());
            Set<String> expectedRuleKeys = Arrays.stream(Rule.values())
                .map(Rule::messageKey)
                .collect(Collectors.toSet());
            assertEquals(expectedRuleKeys, ruleKeys, locale + " rule labels");
            for (String key : ruleKeys) {
                String rendered = miniMessage.stripTags(String.valueOf(phrases.get(key)));
                assertFalse(rendered.contains("<"), locale + ":" + key);
            }
            for (String key : new String[]{"state-on", "state-off"}) {
                String rendered = miniMessage.stripTags(String.valueOf(phrases.get(key)));
                assertFalse(rendered.contains("<"), locale + ":" + key);
            }
        }
    }

    @Test
    void localeUpgradeAddsMissingKeysWithoutReplacingOverrides() {
        YamlConfiguration installed = new YamlConfiguration();
        installed.set("prefix", "custom prefix");
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.set("prefix", "bundled prefix");
        bundled.set("new-rule", "new label");

        assertEquals(1, LocaleService.mergeMissing(installed, bundled));
        assertEquals("custom prefix", installed.getString("prefix"));
        assertEquals("new label", installed.getString("new-rule"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yaml(String resource) {
        try (InputStream input = ResourceTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return new Yaml().load(input);
        } catch (Exception exception) {
            throw new AssertionError("Unable to load " + resource, exception);
        }
    }
}
