package com.itmecasey.bei.compat;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What JEI plugins hid through the runtime (a mod's own scripts or config calling
 * {@code removeIngredientsAtRuntime} and {@code hideRecipeCategory}). Plain data, so BEI's hiding can read it
 * whether or not the JEI bridge is present.
 */
public final class JeiHidden {
    private static final Set<String> ITEMS = ConcurrentHashMap.newKeySet();
    private static final Set<String> CATEGORIES = ConcurrentHashMap.newKeySet();

    private JeiHidden() {
    }

    /** Hides every form of an item by its registry id; returns whether it was new. */
    public static boolean hideItem(String itemId) {
        return ITEMS.add(itemId);
    }

    public static boolean showItem(String itemId) {
        return ITEMS.remove(itemId);
    }

    /** Item ids to hide, in the same form as a datapack's hidden list. */
    public static List<String> itemSpecs() {
        return List.copyOf(ITEMS);
    }

    /** Hides a recipe category by its JEI uid (a recipe type id such as {@code minecraft:smelting}). */
    public static boolean hideCategory(String uid) {
        return CATEGORIES.add(uid);
    }

    public static boolean showCategory(String uid) {
        return CATEGORIES.remove(uid);
    }

    /** Whether a plugin hid the category of a recipe, by its game recipe type id or its BEI category id. */
    public static boolean categoryHidden(String recipeType, String categoryId) {
        if (CATEGORIES.isEmpty()) {
            return false;
        }
        if (recipeType != null && (CATEGORIES.contains(recipeType)
                || CATEGORIES.contains("minecraft:crafting") && recipeType.startsWith("minecraft:crafting"))) {
            return true;
        }
        return categoryId != null && CATEGORIES.contains(categoryId);
    }

    public static void clear() {
        ITEMS.clear();
        CATEGORIES.clear();
    }
}
