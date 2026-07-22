package dev.openphysicscontrol;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public final class RulesMenu implements InventoryHolder {
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int PAGE_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final OpenPhysicsControlPlugin plugin;
    private final World world;
    private final Player viewer;
    private final int page;
    private final int pages;
    private final Inventory inventory;
    private final Map<Integer, Rule> slots = new java.util.HashMap<>();

    public RulesMenu(OpenPhysicsControlPlugin plugin, Player viewer, World world) {
        this(plugin, viewer, world, 0);
    }

    private RulesMenu(OpenPhysicsControlPlugin plugin, Player viewer, World world, int requestedPage) {
        this.plugin = plugin;
        this.world = world;
        this.viewer = viewer;
        this.pages = Math.max(1, (Rule.values().length + PAGE_SIZE - 1) / PAGE_SIZE);
        this.page = Math.max(0, Math.min(requestedPage, this.pages - 1));
        String title = plugin.messages().render(viewer, "menu-title", Map.of(
            "world", world.getName(),
            "page", Integer.toString(this.page + 1),
            "pages", Integer.toString(this.pages)
        ));
        this.inventory = plugin.getServer().createInventory(this, 54, title);
        int start = this.page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, Rule.values().length);
        for (int index = start; index < end; index++) {
            int slot = index - start;
            Rule rule = Rule.values()[index];
            this.slots.put(slot, rule);
            refresh(slot, rule);
        }
        refreshNavigation();
    }

    public void click(int rawSlot) {
        if (rawSlot == PREVIOUS_SLOT && this.page > 0) {
            this.viewer.openInventory(new RulesMenu(this.plugin, this.viewer, this.world, this.page - 1).getInventory());
            return;
        }
        if (rawSlot == NEXT_SLOT && this.page + 1 < this.pages) {
            this.viewer.openInventory(new RulesMenu(this.plugin, this.viewer, this.world, this.page + 1).getInventory());
            return;
        }
        Rule rule = this.slots.get(rawSlot);
        if (rule == null) return;
        if (!this.viewer.isOp() && !this.viewer.hasPermission("openphysicscontrol.set")) {
            this.plugin.messages().send(this.viewer, "no-permission");
            return;
        }
        boolean value = this.plugin.rules().set(this.world, rule, null);
        refresh(rawSlot, rule);
        this.plugin.messages().send(this.viewer, "rule-changed", Map.of(
            "rule", this.plugin.messages().plain(this.viewer, rule.messageKey()),
            "world", this.world.getName(),
            "state", this.plugin.messages().plain(this.viewer, value ? "state-on" : "state-off")
        ));
    }

    private void refresh(int slot, Rule rule) {
        boolean enabled = this.plugin.rules().enabled(this.world, rule);
        ItemStack item = new ItemStack(rule.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        this.plugin.messages().name(meta, this.viewer, rule.messageKey(), Map.of());
        this.plugin.messages().lore(meta, this.viewer, List.of(
            rule.group().messageKey(), enabled ? "menu-state-on" : "menu-state-off", "click-toggle"));
        meta.addItemFlags(ItemFlag.values());
        if (!enabled) meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        this.inventory.setItem(slot, item);
    }

    private void refreshNavigation() {
        if (this.page > 0) setButton(PREVIOUS_SLOT, Material.ARROW, "menu-previous", Map.of());
        setButton(PAGE_SLOT, Material.BOOK, "menu-page", Map.of(
            "page", Integer.toString(this.page + 1),
            "pages", Integer.toString(this.pages)
        ));
        if (this.page + 1 < this.pages) setButton(NEXT_SLOT, Material.ARROW, "menu-next", Map.of());
    }

    private void setButton(int slot, Material material, String message, Map<String, String> placeholders) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        this.plugin.messages().name(meta, this.viewer, message, placeholders);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        this.inventory.setItem(slot, item);
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }
}
