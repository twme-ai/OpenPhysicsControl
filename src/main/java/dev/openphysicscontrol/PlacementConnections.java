package dev.openphysicscontrol;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.Wall;

import java.util.List;

final class PlacementConnections {
    private static final List<BlockFace> HORIZONTAL_FACES = List.of(
        BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    private PlacementConnections() {
    }

    static boolean disconnect(Material material, BlockData data) {
        boolean changed = false;
        if (data instanceof Wall wall) {
            for (BlockFace face : HORIZONTAL_FACES) {
                if (wall.getHeight(face) != Wall.Height.NONE) {
                    wall.setHeight(face, Wall.Height.NONE);
                    changed = true;
                }
            }
            return changed;
        }
        if (data instanceof Stairs stairs) {
            if (stairs.getShape() == Stairs.Shape.STRAIGHT) return false;
            stairs.setShape(Stairs.Shape.STRAIGHT);
            return true;
        }
        if (data instanceof Gate gate) {
            if (!gate.isInWall()) return false;
            gate.setInWall(false);
            return true;
        }
        if (data instanceof Chest chest) {
            if (chest.getType() == Chest.Type.SINGLE) return false;
            chest.setType(Chest.Type.SINGLE);
            return true;
        }
        if (!(data instanceof MultipleFacing facing) || !isStructuralMultipleFacing(material)) return false;
        for (BlockFace face : HORIZONTAL_FACES) {
            if (facing.getAllowedFaces().contains(face) && facing.hasFace(face)) {
                facing.setFace(face, false);
                changed = true;
            }
        }
        return changed;
    }

    static boolean supports(Material material) {
        String name = material.name();
        return name.endsWith("_FENCE")
            || name.endsWith("_FENCE_GATE")
            || name.endsWith("_WALL")
            || name.endsWith("_STAIRS")
            || name.endsWith("_PANE")
            || material == Material.IRON_BARS
            || material == Material.CHEST
            || material == Material.TRAPPED_CHEST;
    }

    private static boolean isStructuralMultipleFacing(Material material) {
        String name = material.name();
        return name.endsWith("_FENCE") || name.endsWith("_PANE") || material == Material.IRON_BARS;
    }
}
