package com.itmecasey.bei.compat;

import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;

/**
 * Somewhere a dragged item can be dropped on a machine screen, as a JEI plugin's ghost-ingredient handler reports it:
 * the area in screen coordinates, what to do with the dropped stack, and what to do when the drag is over.
 */
public record GhostTarget(int x, int y, int width, int height, Consumer<ItemStack> accept, Runnable complete) {
    public boolean contains(double px, double py) {
        return px >= x && py >= y && px < x + width && py < y + height;
    }
}
