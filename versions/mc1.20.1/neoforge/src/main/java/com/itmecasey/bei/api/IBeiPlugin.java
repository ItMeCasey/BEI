package com.itmecasey.bei.api;

/**
 * The entry point of a BEI plugin; see {@link BeiPlugin}. BEI makes one instance per plugin class
 * on the client. {@link #registerCategories} runs once, the first time the player joins a world
 * (item stacks cannot be made before then, so build them in these methods or later, not while
 * the game starts);
 * {@link #registerRecipes} runs each time the recipe list is built (on joining a world, and again
 * when the server's recipes arrive), so a plugin can read recipe data that depends on the world.
 */
public interface IBeiPlugin {
    /** Registers the kinds of recipe this plugin adds, and how their cards look. */
    default void registerCategories(ICategoryRegistration registration) {
    }

    /** Adds recipes of the registered categories. Anything added here also shows in "uses" lookups and the Master Project tracker. */
    default void registerRecipes(IRecipeRegistration registration) {
    }

    /**
     * Registers handlers that fill a menu's grid from a recipe when the player clicks the plus button in the recipe
     * popup. Runs once, with {@link #registerCategories}.
     */
    default void registerTransfers(ITransferRegistration registration) {
    }

    /**
     * Gives the plugin access to BEI's search box, once, with {@link #registerTransfers}. Keep the object to read or
     * set the text or to listen for changes.
     */
    default void registerSearch(ISearchAccess search) {
    }
}
