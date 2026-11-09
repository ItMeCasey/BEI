package com.itmecasey.bei.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The search prefixes % (creative tab), ^ (color) and & (resource id), next to the existing @ # $ -. */
class SearchPrefixTest {
    private static ItemEntry entry(String id, String name, String tabs, String colors) {
        ItemEntry entry = new ItemEntry(null, null, id, id.substring(0, id.indexOf(':')), name, Set.of());
        entry.tabsLower = tabs;
        entry.colorsLower = colors;
        return entry;
    }

    private final ItemEntry redstone = entry("minecraft:redstone", "redstone dust", "redstone", "red");
    private final ItemEntry sapphire = entry("gems:sapphire", "sapphire", "ingredients gems", "blue");

    @Test
    void percentMatchesTheCreativeTab() {
        assertTrue(Search.parse("%redstone").test(redstone));
        assertTrue(Search.parse("%gems").test(sapphire));
        assertFalse(Search.parse("%gems").test(redstone));
    }

    @Test
    void caretMatchesTheColor() {
        assertTrue(Search.parse("^red").test(redstone));
        assertFalse(Search.parse("^red").test(sapphire));
        assertTrue(Search.parse("^blue").test(sapphire));
    }

    @Test
    void ampersandMatchesTheResourceId() {
        assertTrue(Search.parse("&gems:sap").test(sapphire));
        assertFalse(Search.parse("&gems:sap").test(redstone));
    }

    @Test
    void prefixesCombineAndNegate() {
        List<ItemEntry> all = List.of(redstone, sapphire);
        assertEquals(List.of(sapphire), Search.filter(all, "-^red", null));
        assertEquals(List.of(redstone), Search.filter(all, "%redstone ^red", null));
        assertEquals(List.of(), Search.filter(all, "%gems ^red", null));
    }
}
