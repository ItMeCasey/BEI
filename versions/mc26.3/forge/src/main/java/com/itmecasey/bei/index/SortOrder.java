package com.itmecasey.bei.index;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** How the item grid is ordered: by creative tab (the default), by name or by mod then name. */
public enum SortOrder {
    CREATIVE_TAB("creative"),
    NAME("name"),
    MOD("mod");

    private final String key;

    SortOrder(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public SortOrder next() {
        SortOrder[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static SortOrder of(String key) {
        for (SortOrder order : values()) {
            if (order.key.equals(key)) {
                return order;
            }
        }
        return CREATIVE_TAB;
    }

    /** A sorted copy; the sort is stable, so items that tie keep the item list's order. */
    public List<ItemEntry> sort(List<ItemEntry> entries) {
        List<ItemEntry> out = new ArrayList<>(entries);
        Comparator<ItemEntry> by = switch (this) {
            case CREATIVE_TAB -> Comparator.comparingInt(entry -> entry.tabOrder);
            case NAME -> Comparator.comparing(entry -> entry.nameLower);
            case MOD -> Comparator.<ItemEntry, String>comparing(entry -> entry.mod).thenComparing(entry -> entry.nameLower);
        };
        out.sort(by);
        return out;
    }
}
