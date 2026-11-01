package dev.openphysicscontrol;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private static final List<String> MANGROVE = NaturalTickOrigin.randomTickBlocks(Material.MANGROVE_PROPAGULE);
    private static final List<String> SUGAR_CANE = NaturalTickOrigin.randomTickBlocks(Material.SUGAR_CANE);
    private static final List<String> CACTUS = NaturalTickOrigin.randomTickBlocks(Material.CACTUS);

    @Test
    void identifiesPaperMangrovePropaguleRandomTick() {
        assertTrue(NaturalTickOrigin.isRandomTick(
            "net.minecraft.world.level.block.MangrovePropaguleBlock", "randomTick", PAPER_RANDOM_TICK, MANGROVE));
    }

    @Test
    void identifiesRemappedSpigotMangrovePropaguleRandomTick() {
        assertTrue(NaturalTickOrigin.isRandomTick(
            "net.minecraft.world.level.block.MangrovePropaguleBlock", "b", SPIGOT_RANDOM_TICK, MANGROVE));
    }

    @Test
    void identifiesSugarCaneAndCactusRandomTicksUnderBothNames() {
        assertTrue(NaturalTickOrigin.isRandomTick(
            "net.minecraft.world.level.block.SugarCaneBlock", "randomTick", PAPER_RANDOM_TICK, SUGAR_CANE));
        assertTrue(NaturalTickOrigin.isRandomTick(
            "net.minecraft.world.level.block.BlockReed", "b", SPIGOT_RANDOM_TICK, SUGAR_CANE));
        assertTrue(NaturalTickOrigin.isRandomTick(
            "net.minecraft.world.level.block.CactusBlock", "randomTick", PAPER_RANDOM_TICK, CACTUS));
        assertTrue(NaturalTickOrigin.isRandomTick(
            "net.minecraft.world.level.block.BlockCactus", "b", SPIGOT_RANDOM_TICK, CACTUS));
        assertFalse(NaturalTickOrigin.isRandomTick(
            "net.minecraft.world.level.block.CactusBlock", "randomTick", PAPER_RANDOM_TICK, SUGAR_CANE));
    }

    @Test
    void rejectsBoneMealCommandsAndOtherRandomTicks() {
        assertFalse(NaturalTickOrigin.isRandomTick(
            "net.minecraft.world.level.block.MangrovePropaguleBlock", "a", SPIGOT_BONE_MEAL, MANGROVE));
        assertFalse(NaturalTickOrigin.isRandomTick(
            "net.minecraft.server.commands.SetBlockCommand", "setBlock", PAPER_RANDOM_TICK, MANGROVE));
        assertFalse(NaturalTickOrigin.isRandomTick(
            "net.minecraft.world.level.block.CropBlock", "randomTick", PAPER_RANDOM_TICK, MANGROVE));
        assertEquals(List.of(), NaturalTickOrigin.randomTickBlocks(Material.WHEAT));
    }
}
