package dev.openphysicscontrol;

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

    static boolean isMangrovePropaguleMaturation() {
        return STACK_WALKER.walk(frames -> frames.anyMatch(frame ->
            isMangrovePropaguleRandomTick(
                frame.getClassName(), frame.getMethodName(), frame.getDescriptor())));
    }

    static boolean isMangrovePropaguleRandomTick(String className, String methodName, String descriptor) {
        return className.endsWith(".MangrovePropaguleBlock")
            && (methodName.equals("randomTick") || RANDOM_TICK_DESCRIPTOR.matcher(descriptor).matches());
    }
}
