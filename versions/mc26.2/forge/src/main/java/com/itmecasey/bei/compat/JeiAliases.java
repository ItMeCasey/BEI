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

    private static Object storageFactory;
    private static Object storage;
    private static final Map<String, Set<Object>> STORAGE_HITS = new ConcurrentHashMap<>();

    /** A plugin's replacement search storage (an ISearchStorageFactory or ISearchStorageBuilderFactory). */
    public static synchronized void setStorageFactory(Object factory) {
        storageFactory = factory;
        storage = null;
        STORAGE_HITS.clear();
    }

    public static synchronized boolean hasStorage() {
        return storageFactory != null;
    }

    private static synchronized Object storage() {
        if (storage != null || storageFactory == null) {
            return storage;
        }
        try {
            java.lang.reflect.Method plain = JeiBridge.ifaceMethod("mezz.jei.api.search.ISearchStorageFactory", "createSearchStorage", 0);
            java.lang.reflect.Method build = JeiBridge.ifaceMethod("mezz.jei.api.search.ISearchStorageBuilder", "build", 0);
            Object target;
            if (plain != null && plain.getDeclaringClass().isInstance(storageFactory)) {
                target = plain.invoke(storageFactory);
            } else {
                target = JeiBridge.ifaceMethod("mezz.jei.api.search.ISearchStorageBuilderFactory", "create", 0).invoke(storageFactory);
            }
            boolean builder = build != null && build.getDeclaringClass().isInstance(target);
            java.lang.reflect.Method put = JeiBridge.ifaceMethod(builder ? "mezz.jei.api.search.ISearchStorageBuilder"
                    : "mezz.jei.api.search.ISearchStorage", "put", 2);
            for (com.itmecasey.bei.index.ItemEntry entry : com.itmecasey.bei.index.ItemIndex.entries()) {
                put.invoke(target, entry.nameLower, entry);
                put.invoke(target, entry.id, entry);
            }
            storage = builder ? build.invoke(target) : target;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            storageFactory = null;
        }
        return storage;
    }

    /** Whether a plugin's replacement search storage finds this entry for the word. */
    public static boolean storageMatches(Object entry, String word) {
        Set<Object> hits = STORAGE_HITS.get(word);
        if (hits == null) {
            hits = ConcurrentHashMap.newKeySet();
            Object found = storage();
            if (found == null) {
                return false;
            }
            try {
                java.lang.reflect.Method results = JeiBridge.ifaceMethod("mezz.jei.api.search.ISearchStorage", "getSearchResults", 2);
                Set<Object> sink = hits;
                java.util.function.Consumer<java.util.Collection<Object>> collect = sink::addAll;
                results.invoke(found, word, collect);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
            if (STORAGE_HITS.size() > 64) {
                STORAGE_HITS.clear();
            }
            STORAGE_HITS.put(word, hits);
        }
        return hits.contains(entry);
    }

    public static synchronized void clear() {
        storageFactory = null;
        storage = null;
        STORAGE_HITS.clear();
        ITEMS.clear();
        MODS.clear();
        EXTRA.clear();
    }
}
