package dev.openphysicscontrol;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.Wall;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlacementConnectionsTest {
    @Test
    void recognizesOnlySupportedStructuralConnections() {
        assertTrue(PlacementConnections.supports(Material.OAK_FENCE));
        assertTrue(PlacementConnections.supports(Material.COBBLESTONE_WALL));
        assertTrue(PlacementConnections.supports(Material.OAK_STAIRS));
        assertTrue(PlacementConnections.supports(Material.OAK_FENCE_GATE));
        assertTrue(PlacementConnections.supports(Material.GLASS_PANE));
        assertTrue(PlacementConnections.supports(Material.IRON_BARS));
        assertTrue(PlacementConnections.supports(Material.CHEST));
        assertTrue(PlacementConnections.supports(Material.TRAPPED_CHEST));

        assertFalse(PlacementConnections.supports(Material.VINE));
        assertFalse(PlacementConnections.supports(Material.REDSTONE_WIRE));
        assertFalse(PlacementConnections.supports(Material.OAK_LOG));
    }

    @Test
    void clearsFenceAndPaneHorizontalFaces() {
        EnumSet<BlockFace> faces = EnumSet.of(BlockFace.NORTH, BlockFace.WEST, BlockFace.UP);
        MultipleFacing fence = proxy(MultipleFacing.class, (instance, method, arguments) -> switch (method.getName()) {
            case "getAllowedFaces" -> EnumSet.of(
                BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP);
            case "hasFace" -> faces.contains((BlockFace) arguments[0]);
            case "setFace" -> {
                if ((Boolean) arguments[1]) faces.add((BlockFace) arguments[0]);
                else faces.remove((BlockFace) arguments[0]);
                yield null;
            }
            default -> null;
        });

        assertTrue(PlacementConnections.disconnect(Material.OAK_FENCE, fence));
        assertEquals(EnumSet.of(BlockFace.UP), faces);
        assertFalse(PlacementConnections.disconnect(Material.OAK_FENCE, fence));
    }

    @Test
    void resetsWallStairsGateAndChestShapes() {
        EnumMap<BlockFace, Wall.Height> heights = new EnumMap<>(BlockFace.class);
        for (BlockFace face : EnumSet.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            heights.put(face, Wall.Height.LOW);
        }
        Wall wall = proxy(Wall.class, (instance, method, arguments) -> switch (method.getName()) {
            case "getHeight" -> heights.get(arguments[0]);
            case "setHeight" -> heights.put((BlockFace) arguments[0], (Wall.Height) arguments[1]);
            default -> null;
        });
        assertTrue(PlacementConnections.disconnect(Material.COBBLESTONE_WALL, wall));
        assertTrue(heights.values().stream().allMatch(height -> height == Wall.Height.NONE));

        AtomicReference<Stairs.Shape> stairShape = new AtomicReference<>(Stairs.Shape.OUTER_LEFT);
        Stairs stairs = proxy(Stairs.class, (instance, method, arguments) -> switch (method.getName()) {
            case "getShape" -> stairShape.get();
            case "setShape" -> {
                stairShape.set((Stairs.Shape) arguments[0]);
                yield null;
            }
            default -> null;
        });
        assertTrue(PlacementConnections.disconnect(Material.OAK_STAIRS, stairs));
        assertEquals(Stairs.Shape.STRAIGHT, stairShape.get());

        AtomicBoolean inWall = new AtomicBoolean(true);
        Gate gate = proxy(Gate.class, (instance, method, arguments) -> switch (method.getName()) {
            case "isInWall" -> inWall.get();
            case "setInWall" -> {
                inWall.set((Boolean) arguments[0]);
                yield null;
            }
            default -> null;
        });
        assertTrue(PlacementConnections.disconnect(Material.OAK_FENCE_GATE, gate));
        assertFalse(inWall.get());

        AtomicReference<Chest.Type> chestType = new AtomicReference<>(Chest.Type.LEFT);
        Chest chest = proxy(Chest.class, (instance, method, arguments) -> switch (method.getName()) {
            case "getType" -> chestType.get();
            case "setType" -> {
                chestType.set((Chest.Type) arguments[0]);
                yield null;
            }
            default -> null;
        });
        assertTrue(PlacementConnections.disconnect(Material.CHEST, chest));
        assertEquals(Chest.Type.SINGLE, chestType.get());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
