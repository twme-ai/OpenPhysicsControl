package dev.openphysicscontrol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorldEditPermissionTest {
    @Test
    void operatorsAndAllWorldPermissionAlwaysGrantWorldEdits() {
        assertTrue(OpenPhysicsControlPlugin.grantsWorldEdit(true, false, false, false));
        assertTrue(OpenPhysicsControlPlugin.grantsWorldEdit(false, true, false, false));
    }

    @Test
    void currentWorldPermissionOnlyGrantsThePlayersCurrentWorld() {
        assertTrue(OpenPhysicsControlPlugin.grantsWorldEdit(false, false, true, true));
        assertFalse(OpenPhysicsControlPlugin.grantsWorldEdit(false, false, true, false));
        assertFalse(OpenPhysicsControlPlugin.grantsWorldEdit(false, false, false, true));
    }
}
