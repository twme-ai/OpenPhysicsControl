package dev.openphysicscontrol;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RulesMenu implements InventoryHolder {
    private static final int CATEGORY_MENU_SIZE = 27;
    private static final int MAX_CONTENT_ROWS = 5;

    private final OpenPhysicsControlPlugin plugin;
    private final World world;
    private final Player viewer;
    private final Rule.Group group;
    private final Inventory inventory;
    private final int backSlot;
    private final Map<Integer, Rule.Group> groupSlots = new HashMap<>();
    private final Map<Integer, Rule> ruleSlots = new HashMap<>();

    public RulesMenu(OpenPhysicsControlPlugin plugin, Player viewer, World world) {
        this(plugin, viewer, world, null);
    }

    private RulesMenu(OpenPhysicsControlPlugin plugin, Player viewer, World world, Rule.Group group) {
        this.plugin = plugin;
        this.world = world;
        this.viewer = viewer;
        this.group = group;

        if (group == null) {
            this.backSlot = -1;
            String title = plugin.messages().render(viewer, "menu-categories-title", Map.of(
                "world", world.getName()));
            this.inventory = plugin.getServer().createInventory(this, CATEGORY_MENU_SIZE, title);
            populateCategories();
            return;
        }

        List<Rule> groupRules = rulesIn(group);
        int size = menuSizeFor(groupRules.size());
        this.backSlot = size - 5;
        String title = plugin.messages().render(viewer, "menu-rules-title", Map.of(
            "group", plugin.messages().plain(viewer, group.messageKey()),
            "world", world.getName()
        ));
        this.inventory = plugin.getServer().createInventory(this, size, title);
        List<Integer> positions = centeredSlots(groupRules.size(), 0);
        for (int index = 0; index < groupRules.size(); index++) {
            Rule rule = groupRules.get(index);
            int slot = positions.get(index);
            this.ruleSlots.put(slot, rule);
            refreshRule(slot, rule);
        }
        setButton(this.backSlot, Material.ARROW, "menu-back", Map.of());
    }

    public void click(int rawSlot) {
        Rule.Group selectedGroup = this.groupSlots.get(rawSlot);
        if (selectedGroup != null) {
            this.viewer.openInventory(new RulesMenu(
                this.plugin, this.viewer, this.world, selectedGroup).getInventory());
            return;
        }
        if (rawSlot == this.backSlot) {
            this.viewer.openInventory(new RulesMenu(this.plugin, this.viewer, this.world).getInventory());
            return;
        }

        Rule rule = this.ruleSlots.get(rawSlot);
        if (rule == null) return;
        if (!this.viewer.isOp() && !this.viewer.hasPermission("openphysicscontrol.set")) {
            this.plugin.messages().send(this.viewer, "no-permission");
            return;
        }
        boolean enabled = this.plugin.rules().set(this.world, rule, null);
        refreshRule(rawSlot, rule);
        this.plugin.messages().send(this.viewer, "rule-changed", Map.of(
            "rule", this.plugin.messages().plain(this.viewer, rule.messageKey()),
            "world", this.world.getName(),
            "state", this.plugin.messages().plain(this.viewer, enabled ? "state-on" : "state-off")
        ));
    }

    private void populateCategories() {
        Rule.Group[] groups = Rule.Group.values();
        for (Rule.Group category : groups) {
            int slot = category.slot();
            this.groupSlots.put(slot, category);
            setCategory(slot, category);
        }
    }

    private void setCategory(int slot, Rule.Group category) {
        List<Rule> categoryRules = rulesIn(category);
        long running = categoryRules.stream().filter(rule -> this.plugin.rules().enabled(this.world, rule)).count();
        ItemStack item = new ItemStack(category.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        this.plugin.messages().name(meta, this.viewer, category.messageKey(), Map.of());
        meta.setLore(List.of(this.plugin.messages().render(this.viewer, "category-summary", Map.of(
            "running", Long.toString(running),
            "stopped", Long.toString(categoryRules.size() - running),
            "total", Integer.toString(categoryRules.size())
        ))));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        this.inventory.setItem(slot, item);
    }

    private void refreshRule(int slot, Rule rule) {
        boolean enabled = this.plugin.rules().enabled(this.world, rule);
        ItemStack item = new ItemStack(rule.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        this.plugin.messages().name(meta, this.viewer, rule.messageKey(), Map.of());
        this.plugin.messages().lore(meta, this.viewer, List.of(
            enabled ? "menu-state-on" : "menu-state-off",
            enabled ? "click-stop" : "click-resume"));
        meta.addItemFlags(ItemFlag.values());
        meta.setEnchantmentGlintOverride(enabled);
        item.setItemMeta(meta);
        this.inventory.setItem(slot, item);
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

    private static List<Rule> rulesIn(Rule.Group group) {
        return Arrays.stream(Rule.values()).filter(rule -> rule.group() == group).toList();
    }

    static int menuSizeFor(int itemCount) {
        int contentRows = Math.max(1, (itemCount + 8) / 9);
        if (contentRows > MAX_CONTENT_ROWS) {
            throw new IllegalArgumentException("A category cannot contain more than 45 rules");
        }
        return (contentRows + 1) * 9;
    }

    static List<Integer> centeredSlots(int itemCount, int firstRow) {
        if (itemCount < 1 || itemCount > MAX_CONTENT_ROWS * 9 || firstRow < 0) {
            throw new IllegalArgumentException("Invalid centered menu layout");
        }
        int rows = (itemCount + 8) / 9;
        int baseRowSize = itemCount / rows;
        int extraRows = itemCount % rows;
        List<Integer> slots = new ArrayList<>(itemCount);
        for (int row = 0; row < rows; row++) {
            int rowSize = baseRowSize + (row < extraRows ? 1 : 0);
            addCenteredRow(slots, firstRow + row, rowSize);
        }
        return List.copyOf(slots);
    }

    static List<Integer> categorySlots() {
        return Arrays.stream(Rule.Group.values()).map(Rule.Group::slot).toList();
    }

    private static void addCenteredRow(List<Integer> slots, int row, int itemCount) {
        int rowStart = row * 9;
        if ((itemCount & 1) == 1) {
            int start = 4 - itemCount / 2;
            for (int offset = 0; offset < itemCount; offset++) slots.add(rowStart + start + offset);
            return;
        }
        int half = itemCount / 2;
        for (int offset = 4 - half; offset < 4; offset++) slots.add(rowStart + offset);
        for (int offset = 5; offset < 5 + half; offset++) slots.add(rowStart + offset);
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }
}
