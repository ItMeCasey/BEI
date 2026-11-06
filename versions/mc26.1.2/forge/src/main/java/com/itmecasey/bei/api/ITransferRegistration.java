package com.itmecasey.bei.api;

/** Where a plugin registers its transfer handlers. */
public interface ITransferRegistration {
    /** Adds a handler for the menu class it names. */
    void register(IRecipeTransferHandler<?> handler);
}
