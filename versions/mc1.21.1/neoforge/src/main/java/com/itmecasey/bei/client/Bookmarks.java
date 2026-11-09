package com.itmecasey.bei.client;

import com.itmecasey.bei.index.ItemEntry;
import com.itmecasey.bei.index.ItemIndex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Items the player bookmarked (key A on an item), shown as a strip under the item grid and saved per profile. */
public final class Bookmarks {
    private static final IdList LIST = new IdList("bookmarks");

    private Bookmarks() {
    }

    public static boolean has() {
        return !LIST.isEmpty();
    }

    public static boolean contains(String id) {
        return LIST.contains(id);
    }

    /** Adds or removes a bookmark; returns whether the item is bookmarked now. */
    public static boolean toggle(String id) {
        return LIST.toggle(id);
    }

    /** The bookmarked items that exist in the item list, in the order they were added. */
    public static List<ItemEntry> entries() {
        Map<String, ItemEntry> plain = new HashMap<>();
        for (ItemEntry entry : ItemIndex.entries()) {
            if (!entry.variant) {
                plain.putIfAbsent(entry.id, entry);
            }
        }
        List<ItemEntry> out = new ArrayList<>();
        for (String id : LIST.all()) {
            ItemEntry entry = plain.get(id);
            if (entry != null) {
                out.add(entry);
            }
        }
        return out;
    }
}
