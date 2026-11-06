package com.itmecasey.bei.api;

import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * What a transfer handler gets: the open menu, the recipe and the keys held.
 *
 * @param menu    the container menu of the open screen
 * @param recipe  the recipe the plus button belongs to
 * @param shift   Shift is held: fill everything (a whole stack)
 * @param control Control is held: fill the maximum
 * @param <M>     the menu class the handler registered for
 */
public record TransferContext<M extends AbstractContainerMenu>(M menu, TransferRecipe recipe, boolean shift, boolean control) {
}
