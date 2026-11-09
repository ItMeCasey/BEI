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
    /** A variant of an item (a potion, an enchanted book) rather than the item's plain stack. */
    public final boolean variant;
    /** Lowercase tooltip text; null until the background pass reaches this item. */
    public volatile String tooltipLower;
    /** Lowercase names of the creative tabs that list the item, for {@code %tab} searches. */
    public volatile String tabsLower = "";
    /** Position of the item in the creative tabs (the creative tab sort order); large when no tab lists it. */
    public volatile int tabOrder = Integer.MAX_VALUE;
    /** Color words for {@code ^color} searches. */
    public volatile String colorsLower = "";

    public ItemEntry(Item item, ItemStack stack, String id, String mod, String nameLower, Set<String> tags) {
        this(item, stack, id, mod, nameLower, tags, false);
    }

    public ItemEntry(Item item, ItemStack stack, String id, String mod, String nameLower, Set<String> tags, boolean variant) {
        this.variant = variant;
        this.item = item;
        this.stack = stack;
        this.id = id;
        this.mod = mod;
        this.nameLower = nameLower;
        this.tags = tags;
    }
}
