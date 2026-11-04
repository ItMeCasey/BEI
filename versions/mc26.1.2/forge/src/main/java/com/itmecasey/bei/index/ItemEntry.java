package com.itmecasey.bei.index;

import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** One browsable item with the lowercase text the search looks at. Tooltip text fills in later. */
public final class ItemEntry {
    public final Item item;
    public final ItemStack stack;
    public final String id;
    public final String mod;
    public final String nameLower;
    public final Set<String> tags;
    /** Lowercase tooltip text; null until the background pass reaches this item. */
    public volatile String tooltipLower;

    public ItemEntry(Item item, ItemStack stack, String id, String mod, String nameLower, Set<String> tags) {
        this.item = item;
        this.stack = stack;
        this.id = id;
        this.mod = mod;
        this.nameLower = nameLower;
        this.tags = tags;
    }
}
