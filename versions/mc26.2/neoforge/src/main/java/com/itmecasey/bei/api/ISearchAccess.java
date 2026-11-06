package com.itmecasey.bei.api;

import java.util.function.Consumer;

/**
 * BEI's item browser search box, for mods that mirror it with their own (a terminal's search field, a second viewer).
 * Handed to a plugin by {@link IBeiPlugin#registerSearch}. Use it on the client thread only.
 */
public interface ISearchAccess {
    /** The text in the search box right now. */
    String getText();

    /**
     * Sets the search text and refreshes the results, as if the player had typed it. Does nothing when the text is
     * already the same, so two mods mirroring each other do not loop.
     */
    void setText(String text);

    /** Calls {@code listener} with the new text each time it changes, whether typed or set through {@link #setText}. */
    void addListener(Consumer<String> listener);

    /** Stops calling a listener added with {@link #addListener}. */
    void removeListener(Consumer<String> listener);
}
