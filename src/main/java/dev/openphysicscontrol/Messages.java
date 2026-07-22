package dev.openphysicscontrol;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public final class Messages {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.builder()
        .character(LegacyComponentSerializer.SECTION_CHAR)
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build();
    private final LocaleService locales;

    public Messages(LocaleService locales) {
        this.locales = locales;
    }

    public void send(CommandSender recipient, String key, Map<String, String> placeholders) {
        recipient.sendMessage(render(recipient, key, placeholders));
    }

    public void send(CommandSender recipient, String key) {
        send(recipient, key, Map.of());
    }

    public String render(CommandSender recipient, String key, Map<String, String> placeholders) {
        return render(this.locales.localeOf(recipient), key, placeholders);
    }

    public String render(String locale, String key, Map<String, String> placeholders) {
        String input = this.locales.phrase(locale, key);
        input = input.replace("<prefix>", this.locales.phrase(locale, "prefix"));
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            input = input.replace("<" + entry.getKey() + ">", escape(entry.getValue()));
        }
        Component component = this.miniMessage.deserialize(input);
        return this.legacy.serialize(component);
    }

    public String plain(CommandSender recipient, String key) {
        String locale = this.locales.localeOf(recipient);
        String input = this.locales.phrase(locale, key)
            .replace("<prefix>", this.locales.phrase(locale, "prefix"));
        return this.miniMessage.stripTags(input);
    }

    public void name(ItemMeta meta, CommandSender recipient, String key, Map<String, String> placeholders) {
        meta.setDisplayName(render(recipient, key, placeholders));
    }

    public void lore(ItemMeta meta, CommandSender recipient, List<String> keys) {
        meta.setLore(keys.stream().map(key -> render(recipient, key, Map.of())).toList());
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("<", "\\<");
    }
}
