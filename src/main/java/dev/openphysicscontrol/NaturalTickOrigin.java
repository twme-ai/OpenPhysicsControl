package dev.openphysicscontrol;

import org.bukkit.Material;

import java.util.List;
import java.util.regex.Pattern;

final class NaturalTickOrigin {
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(
        StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final Pattern RANDOM_TICK_DESCRIPTOR = Pattern.compile(
        "^\\(Lnet/minecraft/world/level/block/state/[^;]+;"
            + "Lnet/minecraft/server/level/[^;]+;"
            + "Lnet/minecraft/core/[^;]+;"
            + "Lnet/minecraft/util/RandomSource;\\)V$");

    private NaturalTickOrigin() {
    }

    /**
     * Server block classes whose random tick advances the material's age, under Mojang and Spigot names.
     */
    static List<String> randomTickBlocks(Material material) {
        return switch (material) {
            case MANGROVE_PROPAGULE -> List.of("MangrovePropaguleBlock");
            case SUGAR_CANE -> List.of("SugarCaneBlock", "BlockReed");
            case CACTUS -> List.of("CactusBlock", "BlockCactus");
            default -> List.of();
        };
    }

    static boolean isRandomTickOf(Material material) {
        List<String> blocks = randomTickBlocks(material);
        return !blocks.isEmpty() && STACK_WALKER.walk(frames -> frames.anyMatch(frame ->
            isRandomTick(frame.getClassName(), frame.getMethodName(), frame.getDescriptor(), blocks)));
    }

    static boolean isRandomTick(String className, String methodName, String descriptor, List<String> blocks) {
        return blocks.stream().anyMatch(block -> className.endsWith("." + block))
            && (methodName.equals("randomTick") || RANDOM_TICK_DESCRIPTOR.matcher(descriptor).matches());
    }
}
