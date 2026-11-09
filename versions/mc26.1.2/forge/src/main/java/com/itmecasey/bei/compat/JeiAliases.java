package com.itmecasey.bei.compat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.item.ItemStack;

/**
 * What JEI plugins registered to widen the search: alternative names for items, alternative names for mods (so
 * {@code @alias} finds a mod), and extra item stacks to list. Plain data, read by BEI's item index and search whether or
 * not the JEI bridge is present.
 */
public final class JeiAliases {
    private static final Map<String, Set<String>> ITEMS = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> MODS = new ConcurrentHashMap<>();
    private static final List<ItemStack> EXTRA = new ArrayList<>();

    private JeiAliases() {
    }

    /** Adds an alternative name for an item (by registry id); text is kept lowercase. */
    public static void addItem(String itemId, String alias) {
        if (alias != null && !alias.isBlank()) {
            ITEMS.computeIfAbsent(itemId, key -> ConcurrentHashMap.newKeySet()).add(alias.toLowerCase(Locale.ROOT));
        }
    }

    public static void addMod(String modId, String alias) {
        if (alias != null && !alias.isBlank()) {
            MODS.computeIfAbsent(modId, key -> ConcurrentHashMap.newKeySet()).add(alias.toLowerCase(Locale.ROOT));
        }
    }

    /** The aliases of an item as one lowercase string for the search, or "". */
    public static String ofItem(String itemId) {
        Set<String> found = ITEMS.get(itemId);
        return found == null ? "" : String.join(" ", new LinkedHashSet<>(found));
    }

    public static List<String> listOfItem(String itemId) {
        Set<String> found = ITEMS.get(itemId);
        return found == null ? List.of() : new ArrayList<>(found);
    }

    public static String ofMod(String modId) {
        Set<String> found = MODS.get(modId);
        return found == null ? "" : String.join(" ", new LinkedHashSet<>(found));
    }

    public static synchronized void addExtra(ItemStack stack) {
        if (!stack.isEmpty()) {
            EXTRA.add(stack.copy());
        }
    }

    /** Item stacks plugins asked to list besides the registry's items. */
    public static synchronized List<ItemStack> extraStacks() {
        return new ArrayList<>(EXTRA);
    }

    public static synchronized boolean hasExtra() {
        return !EXTRA.isEmpty();
    }

    public static synchronized void clear() {
        ITEMS.clear();
        MODS.clear();
        EXTRA.clear();
    }
}
