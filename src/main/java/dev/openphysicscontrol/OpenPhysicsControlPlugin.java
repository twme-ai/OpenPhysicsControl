package dev.openphysicscontrol;

import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OpenPhysicsControlPlugin extends JavaPlugin implements Listener {
    private LocaleService locales;
    private Messages messages;
    private RuleStore rules;

    @Override
    public void onEnable() {
        this.locales = new LocaleService(this);
        this.locales.reload();
        this.messages = new Messages(this.locales);
        this.rules = new RuleStore(this);
        this.rules.reload();
        this.getServer().getPluginManager().registerEvents(this, this);
        this.getServer().getPluginManager().registerEvents(new PhysicsEvents(this.rules), this);
        DynamicEntityListeners.register(this, this.rules);
    }

    public RuleStore rules() {
        return this.rules;
    }

    public Messages messages() {
        return this.messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return openMenu(sender);
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> setRule(sender, args);
            case "language", "lang" -> language(sender, args);
            case "reload" -> reload(sender);
            default -> openMenu(sender);
        };
    }

    private boolean openMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            this.messages.send(sender, "players-only");
            return true;
        }
        if (!player.isOp() && !player.hasPermission("openphysicscontrol.menu")) {
            this.messages.send(player, "no-permission");
            return true;
        }
        player.openInventory(new RulesMenu(this, player, player.getWorld()).getInventory());
        return true;
    }

    private boolean setRule(CommandSender sender, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("openphysicscontrol.set")) {
            this.messages.send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) return false;
        Rule rule;
        try {
            rule = Rule.parse(args[1]);
        } catch (IllegalArgumentException exception) {
            this.messages.send(sender, "rule-not-found", Map.of("rule", args[1]));
            return true;
        }
        Boolean requested = switch (args[2].toLowerCase(Locale.ROOT)) {
            case "on", "true", "enable" -> Boolean.TRUE;
            case "off", "false", "disable" -> Boolean.FALSE;
            case "toggle" -> null;
            default -> {
                this.messages.send(sender, "invalid-state");
                yield null;
            }
        };
        if (!args[2].equalsIgnoreCase("toggle") && requested == null) return true;

        World world;
        if (args.length >= 4) {
            world = this.getServer().getWorld(args[3]);
        } else if (sender instanceof Player player) {
            world = player.getWorld();
        } else {
            return false;
        }
        if (world == null) {
            this.messages.send(sender, "world-not-found", Map.of("world", args.length >= 4 ? args[3] : "?"));
            return true;
        }
        boolean enabled = this.rules.set(world, rule, requested);
        this.messages.send(sender, "rule-changed", Map.of(
            "rule", this.messages.plain(sender, rule.messageKey()),
            "world", world.getName(),
            "state", this.messages.plain(sender, enabled ? "state-on" : "state-off")
        ));
        return true;
    }

    private boolean language(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            this.messages.send(sender, "players-only");
            return true;
        }
        if (args.length < 2) {
            this.messages.send(player, "language-current", Map.of(
                "language", this.locales.localeOf(player),
                "languages", String.join(", ", this.locales.available())
            ));
            return true;
        }
        if (!this.locales.select(player, args[1])) {
            this.messages.send(player, "language-invalid", Map.of(
                "language", args[1],
                "languages", String.join(", ", this.locales.available())
            ));
            return true;
        }
        this.messages.send(player, "language-changed", Map.of("language", this.locales.localeOf(player)));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.isOp() && !sender.hasPermission("openphysicscontrol.reload")) {
            this.messages.send(sender, "no-permission");
            return true;
        }
        this.locales.reload();
        this.rules.reload();
        this.messages.send(sender, "reloaded");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("set", "language", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            for (Rule rule : Rule.values()) options.add(rule.key());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            options.addAll(List.of("on", "off", "toggle"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("set")) {
            for (World world : this.getServer().getWorlds()) options.add(world.getName());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("language") || args[0].equalsIgnoreCase("lang"))) {
            options.add("auto");
            options.addAll(this.locales.available());
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }

    @EventHandler(ignoreCancelled = true)
    public void inventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RulesMenu menu)) return;
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player && event.getRawSlot() < event.getInventory().getSize()) {
            menu.click(event.getRawSlot());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void inventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof RulesMenu) event.setCancelled(true);
    }

    @EventHandler
    public void worldLoad(WorldLoadEvent event) {
        this.rules.load(event.getWorld());
    }

    @EventHandler
    public void worldUnload(WorldUnloadEvent event) {
        this.rules.unload(event.getWorld());
    }
}
