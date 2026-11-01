package dev.openphysicscontrol;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;

final class DynamicEntityListeners implements Listener {
    private static final String PAPER_KNOCKBACK = "io.papermc.paper.event.entity.EntityKnockbackEvent";
    private static final String BUKKIT_KNOCKBACK = "org.bukkit.event.entity.EntityKnockbackEvent";
    private static final String SPIGOT_CUBE_SPLIT = "org.bukkit.event.entity.CubeMobSplitEvent";
    private static final String PAPER_CUBE_SPLIT = "org.bukkit.event.entity.SlimeSplitEvent";
    private static final String PAPER_ENTITY_ZAP = "com.destroystokyo.paper.event.entity.EntityZapEvent";
    private static final String SPIGOT_PIG_ZAP = "org.bukkit.event.entity.PigZapEvent";
    private static final String PAPER_ENTITY_BREAK = "io.papermc.paper.event.entity.EntityBreakEvent";
    private static final String PAPER_ENTITY_COLLISION = "io.papermc.paper.event.entity.EntityCollideWithEntityEvent";
    private static final String PAPER_VAULT_STATE = "io.papermc.paper.event.block.VaultChangeStateEvent";

    private static final ClassValue<Method> COLLIDING_ENTITIES = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                return type.getMethod("getEntities");
            } catch (NoSuchMethodException missing) {
                throw new IllegalStateException(type.getName() + " no longer exposes getEntities()", missing);
            }
        }
    };

    private DynamicEntityListeners() {
    }

    static void register(JavaPlugin plugin, RuleStore rules) {
        registerFirstAvailable(plugin, rules, Rule.KNOCKBACK, "knockback",
            PAPER_KNOCKBACK, BUKKIT_KNOCKBACK);
        registerFirstAvailable(plugin, rules, Rule.MOB_TRANSFORM, "cube-mob splitting",
            SPIGOT_CUBE_SPLIT, PAPER_CUBE_SPLIT);
        registerFirstAvailable(plugin, rules, Rule.MOB_TRANSFORM, "entity lightning transformations",
            PAPER_ENTITY_ZAP, SPIGOT_PIG_ZAP);
        // Non-hanging block-attached entities such as 26.3 cushions only report detachment through Paper.
        registerFirstAvailable(plugin, rules, Rule.HANGING_ENTITY_DETACHMENT, "cushion detachment",
            PAPER_ENTITY_BREAK);
        registerFirstAvailable(plugin, rules, Rule.ENTITY_COLLISIONS, "entity-to-entity collision",
            DynamicEntityListeners::firstCollidingEntityWorld, PAPER_ENTITY_COLLISION);
        // Only stop transitions that start a vault cycle; unlocking or ejecting vaults finish so keys are never lost.
        registerFirstAvailable(plugin, rules, Rule.TRIAL_VAULTS, "vault activation",
            DynamicEntityListeners::vaultCycleStartWorld, PAPER_VAULT_STATE);
    }

    private static void registerFirstAvailable(
        JavaPlugin plugin,
        RuleStore rules,
        Rule rule,
        String description,
        String... candidates
    ) {
        registerFirstAvailable(plugin, rules, rule, description, event -> event instanceof EntityEvent entityEvent
            ? entityEvent.getEntity().getWorld() : null, candidates);
    }

    private static World firstCollidingEntityWorld(Event event) {
        try {
            if (COLLIDING_ENTITIES.get(event.getClass()).invoke(event) instanceof List<?> entities
                && !entities.isEmpty() && entities.getFirst() instanceof Entity entity) return entity.getWorld();
        } catch (ReflectiveOperationException | IllegalStateException unavailable) {
            // Leave the collision untouched if Paper changes the event shape.
        }
        return null;
    }

    private static World vaultCycleStartWorld(Event event) {
        if (!(event instanceof BlockEvent blockEvent)) return null;
        try {
            String current = String.valueOf(event.getClass().getMethod("getCurrentState").invoke(event));
            String next = String.valueOf(event.getClass().getMethod("getNewState").invoke(event));
            if (current.equals("INACTIVE") && next.equals("ACTIVE") || next.equals("UNLOCKING")) {
                return blockEvent.getBlock().getWorld();
            }
        } catch (ReflectiveOperationException unavailable) {
            // Leave the vault untouched if Paper changes the event shape.
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void registerFirstAvailable(
        JavaPlugin plugin,
        RuleStore rules,
        Rule rule,
        String description,
        Function<Event, World> worldOf,
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
            if (!(event instanceof Cancellable cancellable)) return;
            World world = worldOf.apply(event);
            if (world != null && !rules.enabled(world, rule)) cancellable.setCancelled(true);
        };
        plugin.getServer().getPluginManager().registerEvent(
            eventType, listener, EventPriority.HIGHEST, executor, plugin, true);
    }
}
