package dev.openphysicscontrol;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;

final class DynamicEntityListeners implements Listener {
    private static final String PAPER_KNOCKBACK = "io.papermc.paper.event.entity.EntityKnockbackEvent";
    private static final String BUKKIT_KNOCKBACK = "org.bukkit.event.entity.EntityKnockbackEvent";
    private static final String SPIGOT_CUBE_SPLIT = "org.bukkit.event.entity.CubeMobSplitEvent";
    private static final String PAPER_CUBE_SPLIT = "org.bukkit.event.entity.SlimeSplitEvent";

    private DynamicEntityListeners() {
    }

    static void register(JavaPlugin plugin, RuleStore rules) {
        registerFirstAvailable(plugin, rules, Rule.KNOCKBACK, "knockback",
            PAPER_KNOCKBACK, BUKKIT_KNOCKBACK);
        registerFirstAvailable(plugin, rules, Rule.MOB_TRANSFORM, "cube-mob splitting",
            SPIGOT_CUBE_SPLIT, PAPER_CUBE_SPLIT);
    }

    @SuppressWarnings("unchecked")
    private static void registerFirstAvailable(
        JavaPlugin plugin,
        RuleStore rules,
        Rule rule,
        String description,
        String... candidates
    ) {
        ClassLoader loader = plugin.getClass().getClassLoader();
        Class<? extends Event> eventType = null;
        for (String candidate : candidates) {
            try {
                eventType = (Class<? extends Event>) loader.loadClass(candidate);
                break;
            } catch (ClassNotFoundException unavailable) {
                // Try the equivalent event exposed by the other supported API.
            }
        }
        if (eventType == null) {
            plugin.getLogger().warning("No compatible " + description + " event is available; control is disabled.");
            return;
        }

        Listener listener = new DynamicEntityListeners();
        EventExecutor executor = (ignored, event) -> {
            if (!(event instanceof EntityEvent entityEvent) || !(event instanceof Cancellable cancellable)) return;
            if (!rules.enabled(entityEvent.getEntity().getWorld(), rule)) cancellable.setCancelled(true);
        };
        plugin.getServer().getPluginManager().registerEvent(
            eventType, listener, EventPriority.HIGHEST, executor, plugin, true);
    }
}
