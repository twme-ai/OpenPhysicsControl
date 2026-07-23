package dev.openphysicscontrol;

import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Set;

final class PhysicsClassifier {
    private static final Set<String> FLUID_PRODUCTS = Set.of("STONE", "COBBLESTONE", "OBSIDIAN", "BASALT");
    private static final Set<String> CROPS = Set.of(
        "WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART", "COCOA",
        "SWEET_BERRY_BUSH", "TORCHFLOWER_CROP", "PITCHER_CROP"
    );
    private static final Set<String> STEMS = Set.of(
        "PUMPKIN_STEM", "ATTACHED_PUMPKIN_STEM", "PUMPKIN",
        "MELON_STEM", "ATTACHED_MELON_STEM", "MELON"
    );

    private PhysicsClassifier() {
    }

    static Rule flow(Material material) {
        String name = material.name();
        if (name.equals("WATER") || name.equals("BUBBLE_COLUMN")) return Rule.WATER_FLOW;
        if (name.equals("LAVA")) return Rule.LAVA_FLOW;
        return null;
    }

    static Rule form(Material from, Material to) {
        String oldName = from.name();
        String newName = to.name();
        if (isConcretePowder(oldName) && !isConcretePowder(newName)) return Rule.CONCRETE_HARDEN;
        if ((oldName.equals("WATER") || oldName.equals("LAVA") || oldName.equals("AIR"))
            && FLUID_PRODUCTS.contains(newName)) return Rule.FLUID_REACTIONS;
        if (newName.equals("FROSTED_ICE")) return Rule.FROSTED_ICE;
        return Rule.NATURAL_BLOCK_FORM;
    }

    static Rule fade(Material from, Material to) {
        String oldName = from.name();
        String newName = to.name();
        if (oldName.equals("FIRE") || oldName.equals("SOUL_FIRE")) return Rule.FIRE_EXTINGUISH;
        if (oldName.equals("ICE") || oldName.equals("PACKED_ICE") || oldName.equals("BLUE_ICE")) return Rule.ICE_MELT;
        if (oldName.equals("FROSTED_ICE")) return Rule.FROSTED_ICE;
        if (oldName.equals("SNOW") || oldName.equals("SNOW_BLOCK") || oldName.equals("POWDER_SNOW")) return Rule.SNOW_MELT;
        if (oldName.equals("FROGSPAWN")) return Rule.FROGSPAWN_HATCH;
        if (oldName.contains("CORAL") && newName.contains("DEAD")) return Rule.CORAL_FADE;
        if (isCopper(oldName) && isCopper(newName)) return Rule.COPPER_WEATHER;
        if (oldName.equals("GRASS_BLOCK") || oldName.equals("MYCELIUM") || oldName.endsWith("NYLIUM")
            || oldName.equals("DIRT_PATH")) return Rule.GROUND_FADE;
        return Rule.NATURAL_BLOCK_FORM;
    }

    static Rule grow(Material from, Material to) {
        String oldName = from.name();
        String newName = to.name();
        if (oldName.equals("TURTLE_EGG") || newName.equals("TURTLE_EGG")) return Rule.TURTLE_EGG_HATCH;
        if (oldName.equals("MANGROVE_PROPAGULE") || newName.equals("MANGROVE_PROPAGULE")) return Rule.TREE_GROWTH;
        if (isAmethyst(oldName) || isAmethyst(newName)) return Rule.AMETHYST_GROWTH;
        if (oldName.equals("POINTED_DRIPSTONE") || newName.equals("POINTED_DRIPSTONE")) return Rule.DRIPSTONE_GROWTH;
        if (CROPS.contains(oldName) || CROPS.contains(newName)) return Rule.CROP_GROWTH;
        if (STEMS.contains(oldName) || STEMS.contains(newName)) return Rule.STEM_GROWTH;
        if (isVine(oldName) || isVine(newName)) return Rule.VINE_GROWTH;
        if (isMushroom(oldName) || isMushroom(newName)) return Rule.MUSHROOM_GROWTH;
        if (isVerticalPlant(oldName) || isVerticalPlant(newName)) return Rule.VERTICAL_PLANT_GROWTH;
        return Rule.CROP_GROWTH;
    }

    static Rule spread(Material source, Material to) {
        String sourceName = source.name();
        String newName = to.name();
        if (sourceName.equals("FIRE") || sourceName.equals("SOUL_FIRE")
            || newName.equals("FIRE") || newName.equals("SOUL_FIRE")) return Rule.FIRE_SPREAD;
        if (newName.equals("SCULK") || newName.equals("SCULK_VEIN")) return Rule.SCULK_SPREAD;
        if (isAmethyst(sourceName) || isAmethyst(newName)) return Rule.AMETHYST_GROWTH;
        if (sourceName.equals("POINTED_DRIPSTONE") || newName.equals("POINTED_DRIPSTONE")) return Rule.DRIPSTONE_GROWTH;
        if (isVine(sourceName) || isVine(newName)) return Rule.VINE_GROWTH;
        if (isMushroom(sourceName) || isMushroom(newName)) return Rule.MUSHROOM_GROWTH;
        if (isVerticalPlant(sourceName) || isVerticalPlant(newName)) return Rule.VERTICAL_PLANT_GROWTH;
        return Rule.PLANT_SPREAD;
    }

    static Rule structure(TreeType type) {
        String name = type.name();
        if (name.contains("MUSHROOM") || name.endsWith("FUNGUS")) return Rule.MUSHROOM_GROWTH;
        if (name.equals("CHORUS_PLANT")) return Rule.VERTICAL_PLANT_GROWTH;
        return Rule.TREE_GROWTH;
    }

    static Rule physicalInteraction(Material material) {
        return switch (material.name()) {
            case "FARMLAND" -> Rule.FARMLAND_TRAMPLE;
            case "TURTLE_EGG" -> Rule.TURTLE_EGG_TRAMPLE;
            case "BIG_DRIPLEAF" -> Rule.DRIPLEAF_TILT;
            default -> null;
        };
    }

    static Rule damage(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case FALL, FLY_INTO_WALL, FALLING_BLOCK -> Rule.FALL_DAMAGE;
            case DROWNING -> Rule.DROWNING_DAMAGE;
            default -> null;
        };
    }

    private static boolean isConcretePowder(String name) {
        return name.endsWith("CONCRETE_POWDER");
    }

    private static boolean isAmethyst(String name) {
        return name.contains("AMETHYST_BUD") || name.equals("AMETHYST_CLUSTER");
    }

    private static boolean isVine(String name) {
        return name.equals("VINE") || name.contains("VINES") || name.equals("CAVE_VINES")
            || name.equals("CAVE_VINES_PLANT") || name.equals("GLOW_LICHEN");
    }

    private static boolean isMushroom(String name) {
        return name.contains("MUSHROOM") || name.equals("CRIMSON_FUNGUS") || name.equals("WARPED_FUNGUS");
    }

    private static boolean isVerticalPlant(String name) {
        return name.equals("SUGAR_CANE") || name.equals("CACTUS") || name.equals("BAMBOO")
            || name.equals("BAMBOO_SAPLING") || name.equals("KELP") || name.equals("KELP_PLANT")
            || name.equals("CHORUS_FLOWER") || name.equals("CHORUS_PLANT");
    }

    private static boolean isCopper(String name) {
        return name.contains("COPPER") && !name.contains("ORE") && !name.startsWith("WAXED_");
    }
}
