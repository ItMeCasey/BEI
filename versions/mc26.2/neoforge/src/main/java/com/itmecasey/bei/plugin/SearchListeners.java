package com.itmecasey.bei.plugin;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** The listeners of the search box's text, free of game types so a unit test can run them. */
public final class SearchListeners {
    private static final Logger LOG = Logger.getLogger("bei");

    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    /** Starts calling {@code listener} on changes. */
    public void add(Consumer<String> listener) {
        listeners.add(listener);
    }

    /** Stops calling {@code listener}. */
    public void remove(Consumer<String> listener) {
        listeners.remove(listener);
    }

    /** Tells every listener the text changed; a listener that throws is logged and the rest still run. */
    public void fire(String text) {
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(text);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "A BEI search listener failed", e);
            }
        }
    }
}
