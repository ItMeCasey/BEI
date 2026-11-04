package com.itmecasey.bei.api;

/** Where a plugin registers its recipe categories. */
public interface ICategoryRegistration {
    /** Registers a category. Its id must be unique; a second category with the same id replaces the first. */
    void register(RecipeCategory category);
}
