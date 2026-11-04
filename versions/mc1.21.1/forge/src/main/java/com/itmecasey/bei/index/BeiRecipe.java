package com.itmecasey.bei.index;

import com.itmecasey.bei.api.CustomRecipe;
import com.itmecasey.bei.api.RecipeCategory;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A recipe boiled down to what a viewer needs.
 *
 * @param inputs   one list of acceptable stacks per slot (empty list for a blank slot)
 * @param width    grid width for shaped recipes, 0 when the slots have no layout
 * @param outputs  what the recipe makes
 * @param stations blocks the recipe is made in (crafting table, furnace)
 * @param category the plugin category of a custom recipe, null for the game's own recipes
 * @param custom   the plugin's recipe, null for the game's own recipes
 * @param recipeType the game's recipe type id (such as {@code minecraft:smelting}) when the server sent it
 */
public record BeiRecipe(String id, String type, int width, List<List<ItemStack>> inputs, List<ItemStack> outputs,
                        List<ItemStack> stations, @Nullable RecipeCategory category, @Nullable CustomRecipe custom,
                        @Nullable String recipeType) {
    /** A recipe from a plugin. */
    public BeiRecipe(String id, String type, int width, List<List<ItemStack>> inputs, List<ItemStack> outputs,
                     List<ItemStack> stations, @Nullable RecipeCategory category, @Nullable CustomRecipe custom) {
        this(id, type, width, inputs, outputs, stations, category, custom, null);
    }

    /** The same recipe with its type id set. */
    public BeiRecipe withRecipeType(@Nullable String recipeType) {
        return new BeiRecipe(id, type, width, inputs, outputs, stations, category, custom, recipeType);
    }

    /** A recipe of the game's own types. */
    public BeiRecipe(String id, String type, int width, List<List<ItemStack>> inputs, List<ItemStack> outputs,
                     List<ItemStack> stations) {
        this(id, type, width, inputs, outputs, stations, null, null);
    }

    /** Whether this is a plain crafting recipe, which the tracker prefers. */
    public boolean isCrafting() {
        return type.equals("ShapedCrafting") || type.equals("ShapelessCrafting");
    }
}
