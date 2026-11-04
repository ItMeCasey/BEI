package com.itmecasey.bei.api;

import java.util.Collection;

/** Where a plugin adds recipes. */
public interface IRecipeRegistration {
    /** Adds one recipe of a category. The category is registered on the spot if the plugin has not done so. */
    void add(RecipeCategory category, CustomRecipe recipe);

    /** Adds several recipes of one category. */
    default void addAll(RecipeCategory category, Collection<CustomRecipe> recipes) {
        for (CustomRecipe recipe : recipes) {
            add(category, recipe);
        }
    }
}
