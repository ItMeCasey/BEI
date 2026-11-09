package com.itmecasey.bei.index;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SortOrderTest {
    private static ItemEntry entry(String id, String name, int order) {
        ItemEntry entry = new ItemEntry(null, null, id, id.substring(0, id.indexOf(':')), name, Set.of());
        entry.tabOrder = order;
        return entry;
    }

    private final ItemEntry zinc = entry("b_mod:zinc", "zinc", 2);
    private final ItemEntry apple = entry("z_mod:apple", "apple", 0);
    private final ItemEntry brick = entry("b_mod:brick", "brick", 1);
    private final ItemEntry hidden = entry("a_mod:secret", "secret", Integer.MAX_VALUE);
    private final List<ItemEntry> all = List.of(zinc, apple, brick, hidden);

    @Test
    void creativeTabOrderPutsItemsWithoutATabLast() {
        assertEquals(List.of(apple, brick, zinc, hidden), SortOrder.CREATIVE_TAB.sort(all));
    }

    @Test
    void nameOrderIsAlphabetical() {
        assertEquals(List.of(apple, brick, hidden, zinc), SortOrder.NAME.sort(all));
    }

    @Test
    void modOrderSortsByModThenName() {
        assertEquals(List.of(hidden, brick, zinc, apple), SortOrder.MOD.sort(all));
    }

    @Test
    void keysRoundTripAndCycle() {
        assertEquals(SortOrder.NAME, SortOrder.of("name"));
        assertEquals(SortOrder.CREATIVE_TAB, SortOrder.of("nonsense"));
        assertEquals(SortOrder.NAME, SortOrder.CREATIVE_TAB.next());
        assertEquals(SortOrder.CREATIVE_TAB, SortOrder.MOD.next());
    }
}
