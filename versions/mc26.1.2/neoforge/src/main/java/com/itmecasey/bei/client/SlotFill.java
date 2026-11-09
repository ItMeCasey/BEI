package com.itmecasey.bei.client;

import com.itmecasey.bei.index.BeiRecipe;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * The plain slot-range transfer some mods ask for ("fill the menu's slots from this one on with the recipe's inputs"),
 * done with ordinary slot clicks like BEI's own fill. Used by the JEI bridge for handlers registered by slot range.
 */
public final class SlotFill {
    private SlotFill() {
    }

    private static BeiRecipe recipe(int firstSlot, List<List<ItemStack>> inputs) {
        return new BeiRecipe("bei/slotfill", "bei:slots:" + firstSlot, 0, inputs, List.of(), List.of(), null, null);
    }

    /** Whether the open screen's menu has those slots and the player holds every ingredient. */
    public static boolean can(int firstSlot, List<List<ItemStack>> inputs) {
        return AutoFill.canFill(recipe(firstSlot, inputs));
    }

    /** Fills the slots from the inventory; returns whether it did. */
    public static boolean fill(int firstSlot, List<List<ItemStack>> inputs, boolean max) {
        return AutoFill.fill(recipe(firstSlot, inputs), max);
    }
}
