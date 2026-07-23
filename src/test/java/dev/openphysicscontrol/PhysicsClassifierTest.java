package dev.openphysicscontrol;

import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class PhysicsClassifierTest {
    @Test
    void classifiesFluidMechanics() {
        assertEquals(Rule.WATER_FLOW, PhysicsClassifier.flow(Material.WATER));
        assertEquals(Rule.WATER_FLOW, PhysicsClassifier.flow(Material.BUBBLE_COLUMN));
        assertEquals(Rule.LAVA_FLOW, PhysicsClassifier.flow(Material.LAVA));
        assertNull(PhysicsClassifier.flow(Material.STONE));
        assertEquals(Rule.FLUID_REACTIONS, PhysicsClassifier.form(Material.WATER, Material.OBSIDIAN));
        assertEquals(Rule.CONCRETE_HARDEN,
            PhysicsClassifier.form(Material.WHITE_CONCRETE_POWDER, Material.WHITE_CONCRETE));
        assertEquals(Rule.FROSTED_ICE, PhysicsClassifier.form(Material.WATER, Material.FROSTED_ICE));
    }

    @Test
    void separatesFadeMechanics() {
        assertEquals(Rule.FIRE_EXTINGUISH, PhysicsClassifier.fade(Material.FIRE, Material.AIR));
        assertEquals(Rule.ICE_MELT, PhysicsClassifier.fade(Material.ICE, Material.WATER));
        assertEquals(Rule.SNOW_MELT, PhysicsClassifier.fade(Material.SNOW, Material.AIR));
        assertEquals(Rule.FROSTED_ICE, PhysicsClassifier.fade(Material.FROSTED_ICE, Material.WATER));
        assertEquals(Rule.FROGSPAWN_HATCH, PhysicsClassifier.fade(Material.FROGSPAWN, Material.AIR));
        assertEquals(Rule.CORAL_FADE, PhysicsClassifier.fade(Material.BRAIN_CORAL, Material.DEAD_BRAIN_CORAL));
        assertEquals(Rule.COPPER_WEATHER,
            PhysicsClassifier.fade(Material.COPPER_BLOCK, Material.EXPOSED_COPPER));
        assertEquals(Rule.GROUND_FADE, PhysicsClassifier.fade(Material.GRASS_BLOCK, Material.DIRT));
    }

    @Test
    void separatesGrowthMechanics() {
        assertEquals(Rule.CROP_GROWTH, PhysicsClassifier.grow(Material.WHEAT, Material.WHEAT));
        assertEquals(Rule.STEM_GROWTH, PhysicsClassifier.grow(Material.PUMPKIN_STEM, Material.PUMPKIN));
        assertEquals(Rule.VERTICAL_PLANT_GROWTH, PhysicsClassifier.grow(Material.SUGAR_CANE, Material.SUGAR_CANE));
        assertEquals(Rule.VINE_GROWTH, PhysicsClassifier.grow(Material.CAVE_VINES, Material.CAVE_VINES));
        assertEquals(Rule.MUSHROOM_GROWTH, PhysicsClassifier.grow(Material.RED_MUSHROOM, Material.RED_MUSHROOM));
        assertEquals(Rule.TREE_GROWTH,
            PhysicsClassifier.grow(Material.MANGROVE_PROPAGULE, Material.MANGROVE_PROPAGULE));
        assertEquals(Rule.AMETHYST_GROWTH,
            PhysicsClassifier.grow(Material.SMALL_AMETHYST_BUD, Material.MEDIUM_AMETHYST_BUD));
        assertEquals(Rule.DRIPSTONE_GROWTH,
            PhysicsClassifier.grow(Material.POINTED_DRIPSTONE, Material.POINTED_DRIPSTONE));
        assertEquals(Rule.TURTLE_EGG_HATCH, PhysicsClassifier.grow(Material.TURTLE_EGG, Material.TURTLE_EGG));

        assertEquals(Rule.FIRE_SPREAD, PhysicsClassifier.spread(Material.FIRE, Material.FIRE));
        assertEquals(Rule.SCULK_SPREAD, PhysicsClassifier.spread(Material.SCULK_CATALYST, Material.SCULK));
        assertEquals(Rule.VINE_GROWTH, PhysicsClassifier.spread(Material.VINE, Material.VINE));
        assertEquals(Rule.PLANT_SPREAD, PhysicsClassifier.spread(Material.GRASS_BLOCK, Material.GRASS_BLOCK));

        assertEquals(Rule.MUSHROOM_GROWTH, PhysicsClassifier.structure(TreeType.RED_MUSHROOM));
        assertEquals(Rule.VERTICAL_PLANT_GROWTH, PhysicsClassifier.structure(TreeType.CHORUS_PLANT));
        assertEquals(Rule.TREE_GROWTH, PhysicsClassifier.structure(TreeType.TREE));
    }

    @Test
    void classifiesPhysicalEntityEffects() {
        assertEquals(Rule.FARMLAND_TRAMPLE, PhysicsClassifier.physicalInteraction(Material.FARMLAND));
        assertEquals(Rule.TURTLE_EGG_TRAMPLE, PhysicsClassifier.physicalInteraction(Material.TURTLE_EGG));
        assertEquals(Rule.DRIPLEAF_TILT, PhysicsClassifier.physicalInteraction(Material.BIG_DRIPLEAF));
        assertNull(PhysicsClassifier.physicalInteraction(Material.STONE));
        assertEquals(Rule.FALL_DAMAGE, PhysicsClassifier.damage(EntityDamageEvent.DamageCause.FALL));
        assertEquals(Rule.DROWNING_DAMAGE, PhysicsClassifier.damage(EntityDamageEvent.DamageCause.DROWNING));
        assertNull(PhysicsClassifier.damage(EntityDamageEvent.DamageCause.FIRE));
    }

    @Test
    void mapsLegacyAggregateSettings() {
        assertEquals("block-fade", RuleStore.legacyPath(Rule.CORAL_FADE));
        assertEquals("plant-growth", RuleStore.legacyPath(Rule.CROP_GROWTH));
        assertEquals("explosions", RuleStore.legacyPath(Rule.EXPLOSION_BLOCK_DAMAGE));
        assertEquals("block-formation", RuleStore.legacyPath(Rule.FLUID_REACTIONS));
        assertNull(RuleStore.legacyPath(Rule.REDSTONE));
    }
}
