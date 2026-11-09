package com.itmecasey.bei.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.bei.compat.JeiAliases;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Names that JEI plugins add for items and mods widen the search; plain data, no JEI needed. */
class AliasSearchTest {
    @AfterEach
    void clean() {
        JeiAliases.clear();
    }

    private static ItemEntry entry(String id, String name) {
        return new ItemEntry(null, null, id, id.substring(0, id.indexOf(':')), name, Set.of());
    }

    @Test
    void anItemAliasFindsTheItem() {
        JeiAliases.addItem("gems:sapphire", "Blue Rock");
        assertTrue(Search.parse("blue rock").test(entry("gems:sapphire", "sapphire")));
        assertTrue(Search.parse("rock").test(entry("gems:sapphire", "sapphire")));
        assertFalse(Search.parse("rock").test(entry("gems:ruby", "ruby")));
        assertEquals("blue rock", JeiAliases.ofItem("gems:sapphire"));
    }

    @Test
    void aModAliasWidensTheAtSearch() {
        JeiAliases.addMod("gems", "Gem Mod");
        assertTrue(Search.parse("@gem").test(entry("gems:sapphire", "sapphire")));
        assertTrue(Search.parse("@gem").test(entry("gems:ruby", "ruby")));
        assertTrue(Search.parse("@gem mod").test(entry("gems:sapphire", "sapphire")) == false);
        assertFalse(Search.parse("@gem").test(entry("other:stone", "stone")));
        assertEquals("gem mod", JeiAliases.ofMod("gems"));
    }

    @Test
    void extraStacksAreKeptAsCopies() {
        assertFalse(JeiAliases.hasExtra());
        JeiAliases.clear();
        assertTrue(JeiAliases.extraStacks().isEmpty());
    }
}
