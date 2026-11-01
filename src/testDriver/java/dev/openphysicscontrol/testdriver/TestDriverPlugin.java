package dev.openphysicscontrol.testdriver;

import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Test-only plugin for the Mineflayer suite.
 *
 * <p>ViaBackwards drops the use-item-on packet of a bridged older client, so the suite asks this plugin to run
 * the same server-side right-click path the packet handler would use. It reaches the Mojang-mapped server
 * through reflection and is never shipped with OpenPhysicsControl.
 */
public final class TestDriverPlugin extends JavaPlugin {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 6 || !args[0].equalsIgnoreCase("use")) {
            sender.sendMessage("Usage: /opctest use <player> <x> <y> <z> <face>");
            return true;
        }
        Player player = getServer().getPlayerExact(args[1]);
        if (player == null) {
            sender.sendMessage("Unknown player " + args[1]);
            return true;
        }
        try {
            int x = Integer.parseInt(args[2]);
            int y = Integer.parseInt(args[3]);
            int z = Integer.parseInt(args[4]);
            BlockFace face = BlockFace.valueOf(args[5].toUpperCase(Locale.ROOT));
            sender.sendMessage("opctest use result " + useItemOn(player, x, y, z, face));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            getLogger().warning("Could not simulate block use: " + failure);
            sender.sendMessage("opctest use failed: " + failure);
        }
        return true;
    }

    private static Object useItemOn(Player player, int x, int y, int z, BlockFace face)
        throws ReflectiveOperationException {
        Object handle = player.getClass().getMethod("getHandle").invoke(player);
        ClassLoader loader = handle.getClass().getClassLoader();
        Class<?> serverPlayer = loader.loadClass("net.minecraft.server.level.ServerPlayer");
        Class<?> level = loader.loadClass("net.minecraft.world.level.Level");
        Class<?> itemStack = loader.loadClass("net.minecraft.world.item.ItemStack");
        Class<?> hand = loader.loadClass("net.minecraft.world.InteractionHand");
        Class<?> vec3 = loader.loadClass("net.minecraft.world.phys.Vec3");
        Class<?> direction = loader.loadClass("net.minecraft.core.Direction");
        Class<?> blockPos = loader.loadClass("net.minecraft.core.BlockPos");
        Class<?> hitResult = loader.loadClass("net.minecraft.world.phys.BlockHitResult");

        Object mainHand = hand.getField("MAIN_HAND").get(null);
        Object item = serverPlayer.getMethod("getItemInHand", hand).invoke(handle, mainHand);
        Object world = serverPlayer.getMethod("level").invoke(handle);
        Object location = vec3.getConstructor(double.class, double.class, double.class).newInstance(
            x + 0.5 + face.getModX() * 0.5, y + 0.5 + face.getModY() * 0.5, z + 0.5 + face.getModZ() * 0.5);
        Object side = direction.getMethod("valueOf", String.class).invoke(null, face.name());
        Object position = blockPos.getConstructor(int.class, int.class, int.class).newInstance(x, y, z);
        Object hit = hitResult.getConstructor(vec3, direction, blockPos, boolean.class)
            .newInstance(location, side, position, false);

        Object gameMode = serverPlayer.getField("gameMode").get(handle);
        Method useItemOn = gameMode.getClass().getMethod(
            "useItemOn", serverPlayer, level, itemStack, hand, hitResult);
        return useItemOn.invoke(gameMode, handle, world, item, mainHand, hit);
    }
}
