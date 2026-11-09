package com.itmecasey.bei.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** What JEI plugins hid through the runtime: item ids and recipe categories. */
class JeiHiddenTest {
    @BeforeEach
    void reset() {
        JeiHidden.clear();
    }

    @Test
    void hiddenItemsListTheirIdsAndCanBeShownAgain() {
        assertTrue(JeiHidden.hideItem("minecraft:stone"));
        assertFalse(JeiHidden.hideItem("minecraft:stone"));
        assertEquals(List.of("minecraft:stone"), JeiHidden.itemSpecs());
        assertTrue(JeiHidden.showItem("minecraft:stone"));
        assertEquals(List.of(), JeiHidden.itemSpecs());
    }

    @Test
    void nothingIsHiddenByDefault() {
        assertFalse(JeiHidden.categoryHidden("minecraft:smelting", "bei:smelting"));
    }

    @Test
    void aCategoryIsHiddenByRecipeTypeOrCategoryId() {
        JeiHidden.hideCategory("minecraft:smelting");
        JeiHidden.hideCategory("ae2:inscriber");
        assertTrue(JeiHidden.categoryHidden("minecraft:smelting", null));
        assertTrue(JeiHidden.categoryHidden(null, "ae2:inscriber"));
        assertFalse(JeiHidden.categoryHidden("minecraft:blasting", "bei:blasting"));
    }

    @Test
    void hidingCraftingHidesEveryCraftingType() {
        JeiHidden.hideCategory("minecraft:crafting");
        assertTrue(JeiHidden.categoryHidden("minecraft:crafting_shaped", null));
        assertTrue(JeiHidden.categoryHidden("minecraft:crafting_special_firework", null));
        assertFalse(JeiHidden.categoryHidden("minecraft:smelting", null));
    }

    @Test
    void shownCategoryIsNoLongerHidden() {
        JeiHidden.hideCategory("minecraft:smelting");
        JeiHidden.showCategory("minecraft:smelting");
        assertFalse(JeiHidden.categoryHidden("minecraft:smelting", null));
    }
}
