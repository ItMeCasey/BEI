package com.itmecasey.bei.api;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Where a storage source reports what it holds; see {@link IStorageSource}. */
public interface IItemCounter {
    /**
     * Adds {@code count} of {@code item}. The count may be far more than a stack holds (a storage network holds
     * millions); the total of an item is capped at {@link Integer#MAX_VALUE}. Zero and negative counts are ignored.
     */
    void add(Item item, long count);

    /** Adds the stack's item and count; an empty stack is ignored. */
    default void add(ItemStack stack) {
        if (!stack.isEmpty()) {
            add(stack.getItem(), stack.getCount());
        }
    }
}
