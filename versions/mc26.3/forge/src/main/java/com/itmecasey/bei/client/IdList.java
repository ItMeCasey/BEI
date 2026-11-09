package com.itmecasey.bei.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** An ordered, saved list of item ids: the base of the bookmarks and of the items hidden in edit mode. */
public final class IdList {
    private final String key;
    private Set<String> ids;

    public IdList(String key) {
        this.key = key;
    }

    private Set<String> ids() {
        if (ids == null) {
            ids = new LinkedHashSet<>(ClientData.list(key));
        }
        return ids;
    }

    public synchronized boolean contains(String id) {
        return ids().contains(id);
    }

    public synchronized List<String> all() {
        return new ArrayList<>(ids());
    }

    public synchronized boolean isEmpty() {
        return ids().isEmpty();
    }

    /** Adds the id, or removes it if it is already there; returns whether it is in the list now. */
    public synchronized boolean toggle(String id) {
        boolean added = ids().add(id);
        if (!added) {
            ids().remove(id);
        }
        ClientData.setList(key, new ArrayList<>(ids()));
        return added;
    }

    /** Forgets what was read, so the next call reads the file again (unit tests). */
    synchronized void reload() {
        ids = null;
    }
}
