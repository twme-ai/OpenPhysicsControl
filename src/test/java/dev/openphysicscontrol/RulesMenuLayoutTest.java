package dev.openphysicscontrol;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RulesMenuLayoutTest {
    @Test
    void centersCategoriesAndEveryCurrentRuleGroup() {
        assertEquals(List.of(11, 12, 13, 14, 15), RulesMenu.centeredSlots(5, 1));
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16),
            RulesMenu.centeredSlots(14, 0));
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14, 15, 16, 17),
            RulesMenu.centeredSlots(17, 0));
        assertEquals(IntStream.range(0, 18).boxed().toList(), RulesMenu.centeredSlots(18, 0));
        assertEquals(List.of(0, 1, 2, 3, 5, 6, 7, 8), RulesMenu.centeredSlots(8, 0));
    }

    @Test
    void reservesACenteredFooterRow() {
        assertEquals(18, RulesMenu.menuSizeFor(8));
        assertEquals(27, RulesMenu.menuSizeFor(14));
        assertEquals(27, RulesMenu.menuSizeFor(17));
        assertEquals(27, RulesMenu.menuSizeFor(18));
        assertThrows(IllegalArgumentException.class, () -> RulesMenu.menuSizeFor(46));
    }
}
