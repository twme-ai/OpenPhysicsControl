package dev.openphysicscontrol;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class BlockTaskScheduler {
    private final JavaPlugin plugin;
    private final Object regionScheduler;
    private final Method regionExecute;

    BlockTaskScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
        Object scheduler = null;
        Method execute = null;
        try {
            scheduler = plugin.getServer().getClass().getMethod("getRegionScheduler").invoke(plugin.getServer());
            execute = scheduler.getClass().getMethod(
                "execute", Plugin.class, World.class, int.class, int.class, Runnable.class);
        } catch (NoSuchMethodException exception) {
            // Spigot uses its main-thread scheduler.
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Unable to initialize the region scheduler", exception);
        }
        this.regionScheduler = scheduler;
        this.regionExecute = execute;
    }

    void nextTick(Location location, Runnable task) {
        if (this.regionScheduler == null) {
            this.plugin.getServer().getScheduler().runTask(this.plugin, task);
            return;
        }
        try {
            this.regionExecute.invoke(this.regionScheduler, this.plugin, location.getWorld(),
                location.getBlockX(), location.getBlockZ(), task);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Unable to schedule a block-region task", exception);
        }
    }
}
