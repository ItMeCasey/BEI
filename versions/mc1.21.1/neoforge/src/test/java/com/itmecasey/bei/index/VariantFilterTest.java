package com.itmecasey.bei.index;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class VariantFilterTest {
    /** A fake stack: an item, its data ("" for the default) and nothing else. */
    private record Stack(String item, String data) {
        boolean isVariant() {
            return !data.isEmpty();
        }

        String key() {
            return item + "|" + data;
        }
    }

    @Test
    void onlyNonDefaultStacksAreVariants() {
        List<Stack> all = List.of(new Stack("potion", ""), new Stack("potion", "healing"), new Stack("book", ""));
        assertEquals(List.of(new Stack("potion", "healing")), VariantFilter.select(all, Stack::isVariant, Stack::key));
    }

    @Test
    void theSameVariantInSeveralTabsCountsOnce() {
        List<Stack> all = List.of(new Stack("book", "sharpness 5"), new Stack("book", "mending 1"), new Stack("book", "sharpness 5"));
        assertEquals(List.of(new Stack("book", "sharpness 5"), new Stack("book", "mending 1")),
                VariantFilter.select(all, Stack::isVariant, Stack::key));
    }

    @Test
    void variantsOfDifferentItemsWithTheSameDataStaySeparate() {
        List<Stack> all = List.of(new Stack("potion", "healing"), new Stack("splash_potion", "healing"));
        assertEquals(2, VariantFilter.select(all, Stack::isVariant, Stack::key).size());
    }

    @Test
    void nothingWhenThereAreNoVariants() {
        assertEquals(List.of(), VariantFilter.select(List.of(new Stack("stick", "")), Stack::isVariant, Stack::key));
    }
}
