package com.itmecasey.bei.api;

import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * A recipe as a transfer handler sees it.
 *
 * @param id      the recipe's id (a game recipe id, or the plugin's own id for a custom recipe)
 * @param type    the recipe kind: {@code ShapedCrafting}, {@code ShapelessCrafting}, {@code Furnace}, {@code Stonecutter},
 *                {@code Smithing}, or the id of a plugin category
 * @param width   grid width of a shaped recipe, 0 when the ingredients have no layout; slot {@code i} of a shaped recipe
 *                sits at column {@code i % width}, row {@code i / width}
 * @param inputs  one list of acceptable stacks per ingredient slot (an empty list is a blank slot)
 * @param outputs what the recipe makes
 */
public record TransferRecipe(String id, String type, int width, List<List<ItemStack>> inputs, List<ItemStack> outputs) {
}
