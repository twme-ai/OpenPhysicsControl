package dev.openphysicscontrol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PhysicsDebuggerTest {
    @Test
    void throttleReportsOncePerIntervalAndCountsHiddenRepeats() {
        PhysicsDebugger.Throttle throttle = new PhysicsDebugger.Throttle();
        assertEquals(0, throttle.admit(10_000));
        assertEquals(-1, throttle.admit(10_100));
        assertEquals(-1, throttle.admit(10_999));
        assertEquals(2, throttle.admit(10_000 + PhysicsDebugger.REPEAT_INTERVAL_MILLIS));
        assertEquals(0, throttle.admit(20_000));
    }
}
