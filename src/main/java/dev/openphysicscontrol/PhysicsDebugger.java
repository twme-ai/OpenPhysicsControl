package dev.openphysicscontrol;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.hanging.HangingEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.vehicle.VehicleEvent;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reports every event OpenPhysicsControl blocks to the players (or console) that enabled
 * {@code /opc debug}, so an administrator can see which rule stops an action.
 */
final class PhysicsDebugger {
    static final long REPEAT_INTERVAL_MILLIS = 1000;
    private static final UUID CONSOLE = new UUID(0, 0);

    private static final ClassValue<Optional<Method>> CAUSE = accessor("getCause");
    private static final ClassValue<Optional<Method>> REMOVER = accessor("getRemover");

    private final Messages messages;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    PhysicsDebugger(Messages messages) {
        this.messages = messages;
    }

    /** Starts tracing for {@code sender}; a null {@code filter} traces every rule. */
    void enable(CommandSender sender, Rule filter) {
        this.sessions.put(idOf(sender), new Session(sender, filter));
    }

    boolean disable(CommandSender sender) {
        return this.sessions.remove(idOf(sender)) != null;
    }

    void forget(Player player) {
        this.sessions.remove(player.getUniqueId());
    }

    boolean tracing(CommandSender sender) {
        return this.sessions.containsKey(idOf(sender));
    }

    /** Called only after a rule has decided to stop {@code event}; cheap when nobody is tracing. */
    void blocked(Event event, World world, Rule rule, Material material) {
        if (this.sessions.isEmpty() || rule == null) return;
        Target target = target(event, material);
        String details = details(event);
        String eventName = event.getEventName();
        String throttleKey = rule.key() + '|' + eventName;
        long now = System.currentTimeMillis();
        for (Session session : this.sessions.values()) {
            if (session.filter != null && session.filter != rule) continue;
            long suppressed = session.admit(throttleKey, now);
            if (suppressed < 0) continue;
            CommandSender recipient = session.sender;
            String extra = suppressed == 0 ? "" : this.messages.plain(recipient, "debug-suppressed",
                Map.of("count", Long.toString(suppressed)));
            this.messages.send(recipient, "debug-blocked", Map.of(
                "rule", this.messages.plain(recipient, rule.messageKey()),
                "key", rule.key(),
                "event", eventName,
                "world", world == null ? "?" : world.getName(),
                "location", target.location,
                "target", target.name,
                "details", details,
                "suppressed", extra
            ));
        }
    }

    static Target target(Event event, Material material) {
        Block block = null;
        Entity entity = null;
        if (event instanceof BlockEvent blockEvent) block = blockEvent.getBlock();
        else if (event instanceof EntityEvent entityEvent) entity = entityEvent.getEntity();
        else if (event instanceof HangingEvent hangingEvent) entity = hangingEvent.getEntity();
        else if (event instanceof VehicleEvent vehicleEvent) entity = vehicleEvent.getVehicle();
        else if (event instanceof PlayerEvent playerEvent) entity = playerEvent.getPlayer();

        if (block != null) {
            Material type = material != null ? material : block.getType();
            return new Target(type.name().toLowerCase(Locale.ROOT),
                block.getX() + " " + block.getY() + " " + block.getZ());
        }
        if (entity != null) {
            Location at = entity.getLocation();
            return new Target(entityName(entity),
                at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ());
        }
        return new Target(material == null ? "-" : material.name().toLowerCase(Locale.ROOT), "-");
    }

    static String details(Event event) {
        StringBuilder details = new StringBuilder();
        Object cause = invoke(CAUSE, event);
        if (cause instanceof Enum<?> value) details.append(" cause=").append(value.name());
        Object remover = invoke(REMOVER, event);
        if (remover instanceof Entity entity) details.append(" by=").append(entityName(entity));
        return details.toString();
    }

    private static String entityName(Entity entity) {
        String type = entity.getType().name().toLowerCase(Locale.ROOT);
        return entity instanceof Player player ? type + ":" + player.getName() : type;
    }

    private static Object invoke(ClassValue<Optional<Method>> accessor, Event event) {
        Optional<Method> method = accessor.get(event.getClass());
        if (method.isEmpty()) return null;
        try {
            return method.get().invoke(event);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }

    private static ClassValue<Optional<Method>> accessor(String name) {
        return new ClassValue<>() {
            @Override
            protected Optional<Method> computeValue(Class<?> type) {
                try {
                    Method method = type.getMethod(name);
                    return method.getParameterCount() == 0 ? Optional.of(method) : Optional.empty();
                } catch (NoSuchMethodException missing) {
                    return Optional.empty();
                }
            }
        };
    }

    private static UUID idOf(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : CONSOLE;
    }

    record Target(String name, String location) {
    }

    static final class Session {
        private final CommandSender sender;
        private final Rule filter;
        private final Map<String, Throttle> throttles = new ConcurrentHashMap<>();

        Session(CommandSender sender, Rule filter) {
            this.sender = sender;
            this.filter = filter;
        }

        Rule filter() {
            return this.filter;
        }

        /** Returns -1 to stay quiet, otherwise how many repeats were skipped since the last report. */
        long admit(String key, long now) {
            return this.throttles.computeIfAbsent(key, ignored -> new Throttle()).admit(now);
        }
    }

    static final class Throttle {
        private long lastReport = Long.MIN_VALUE;
        private long skipped;

        synchronized long admit(long now) {
            if (lastReport != Long.MIN_VALUE && now - lastReport < REPEAT_INTERVAL_MILLIS) {
                skipped++;
                return -1;
            }
            lastReport = now;
            long reported = skipped;
            skipped = 0;
            return reported;
        }
    }
}
