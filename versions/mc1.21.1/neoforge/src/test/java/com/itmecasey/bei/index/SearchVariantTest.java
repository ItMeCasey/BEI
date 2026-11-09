package com.itmecasey.bei.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A plain search word also finds a variant by its tooltip text (the Sharpness book), and nothing else does. */
class SearchVariantTest {
    private static ItemEntry entry(String id, String name, boolean variant, String tooltip) {
        ItemEntry entry = new ItemEntry(null, null, id, "minecraft", name, Set.of(), variant);
        entry.tooltipLower = tooltip;
        return entry;
    }

    @Test
    void variantIsFoundByItsTooltipText() {
        ItemEntry book = entry("minecraft:enchanted_book", "enchanted book", true, "enchanted book\nsharpness v");
        assertTrue(Search.parse("sharpness").test(book));
    }

    @Test
    void plainItemIsNotFoundByTooltipInPlainSearch() {
        ItemEntry sword = entry("minecraft:iron_sword", "iron sword", false, "iron sword\nsharpness");
        assertFalse(Search.parse("sharpness").test(sword));
    }

    @Test
    void variantWithUnreadTooltipOnlyMatchesByName() {
        ItemEntry book = entry("minecraft:enchanted_book", "enchanted book", true, null);
        assertFalse(Search.parse("sharpness").test(book));
        assertTrue(Search.parse("enchanted").test(book));
    }

    @Test
    void filterKeepsOnlyTheMatchingVariant() {
        ItemEntry sharp = entry("minecraft:enchanted_book", "enchanted book", true, "sharpness v");
        ItemEntry mend = entry("minecraft:enchanted_book", "enchanted book", true, "mending");
        assertEquals(List.of(sharp), Search.filter(List.of(sharp, mend), "sharpness", null));
    }
}
