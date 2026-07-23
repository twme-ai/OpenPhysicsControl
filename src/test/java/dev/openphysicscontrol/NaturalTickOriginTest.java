package dev.openphysicscontrol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NaturalTickOriginTest {
    private static final String PAPER_RANDOM_TICK = "(Lnet/minecraft/world/level/block/state/BlockState;"
        + "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;"
        + "Lnet/minecraft/util/RandomSource;)V";
    private static final String SPIGOT_RANDOM_TICK = "(Lnet/minecraft/world/level/block/state/IBlockData;"
        + "Lnet/minecraft/server/level/WorldServer;Lnet/minecraft/core/BlockPosition;"
        + "Lnet/minecraft/util/RandomSource;)V";
    private static final String SPIGOT_BONE_MEAL = "(Lnet/minecraft/server/level/WorldServer;"
        + "Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPosition;"
        + "Lnet/minecraft/world/level/block/state/IBlockData;)V";

    @Test
    void identifiesPaperMangrovePropaguleRandomTick() {
        assertTrue(NaturalTickOrigin.isMangrovePropaguleRandomTick(
            "net.minecraft.world.level.block.MangrovePropaguleBlock", "randomTick", PAPER_RANDOM_TICK));
    }

    @Test
    void identifiesRemappedSpigotMangrovePropaguleRandomTick() {
        assertTrue(NaturalTickOrigin.isMangrovePropaguleRandomTick(
            "net.minecraft.world.level.block.MangrovePropaguleBlock", "b", SPIGOT_RANDOM_TICK));
    }

    @Test
    void rejectsBoneMealCommandsAndOtherRandomTicks() {
        assertFalse(NaturalTickOrigin.isMangrovePropaguleRandomTick(
            "net.minecraft.world.level.block.MangrovePropaguleBlock", "a", SPIGOT_BONE_MEAL));
        assertFalse(NaturalTickOrigin.isMangrovePropaguleRandomTick(
            "net.minecraft.server.commands.SetBlockCommand", "setBlock", PAPER_RANDOM_TICK));
        assertFalse(NaturalTickOrigin.isMangrovePropaguleRandomTick(
            "net.minecraft.world.level.block.CropBlock", "randomTick", PAPER_RANDOM_TICK));
    }
}
