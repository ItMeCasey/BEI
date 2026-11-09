package com.itmecasey.bei.client;

import java.util.List;

/**
 * Edit mode: while it is on, clicking an item in the browser hides it (and hidden items show dimmed so a click brings
 * them back). What is hidden is saved per profile and works next to the datapack {@code bei_hidden} lists.
 */
public final class EditMode {
    private static final IdList HIDDEN = new IdList("hidden");
    private static boolean on;

    private EditMode() {
    }

    public static boolean on() {
        return on;
    }

    public static void toggle() {
        on = !on;
        Groups.refresh();
    }

    public static boolean hidden(String id) {
        return HIDDEN.contains(id);
    }

    /** Hides the item, or shows it again if it was hidden. */
    public static void flip(String id) {
        HIDDEN.toggle(id);
        Groups.refresh();
    }

    /** The saved hidden ids, in the same form as a datapack hidden list; empty while editing so they stay visible. */
    public static List<String> hiddenSpecs() {
        return on ? List.of() : HIDDEN.all();
    }
}
