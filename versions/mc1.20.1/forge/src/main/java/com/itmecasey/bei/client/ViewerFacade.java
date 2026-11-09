package com.itmecasey.bei.client;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What the JEI bridge needs from BEI's own screens, in one public place: the search text, what is under the mouse in the
 * browser, the bookmarks, hidden items and the recipe popup. Everything here only reads or forwards to BEI's own classes.
 */
public final class ViewerFacade {
    private ViewerFacade() {
    }

    public static String searchText() {
        return BrowserPanel.searchText();
    }

    public static void setSearchText(String text) {
        BrowserPanel.setSearchText(text);
    }

    public static boolean searchFocused() {
        return BrowserPanel.searchFocused();
    }

    public static boolean listShown() {
        return BrowserPanel.isShown();
    }

    /** The item under the mouse in the browser grid, or null. */
    public static @Nullable ItemStack gridStack() {
        return BrowserPanel.gridStackUnderMouse();
    }

    /** The bookmarked item under the mouse, or null. */
    public static @Nullable ItemStack bookmarkStack() {
        return BrowserPanel.bookmarkUnderMouse();
    }

    /** The items the current search lists. */
    public static List<ItemStack> filtered() {
        return BrowserPanel.filteredStacks();
    }

    public static boolean bookmarked(String id) {
        return Bookmarks.contains(id);
    }

    /** Sets whether the item is bookmarked; returns whether anything changed. */
    public static boolean setBookmarked(String id, boolean on) {
        if (Bookmarks.contains(id) == on) {
            return false;
        }
        Bookmarks.toggle(id);
        return true;
    }

    public static boolean hiddenInEditMode(String id) {
        return EditMode.hidden(id);
    }

    public static void setHiddenInEditMode(String id, boolean hidden) {
        if (EditMode.hidden(id) != hidden) {
            EditMode.flip(id);
        }
    }

    /** Opens the recipe popup on every recipe of the first category in the list (a JEI recipe click area or showTypes). */
    public static void showCategories(List<String> typeUids) {
        if (!typeUids.isEmpty()) {
            RecipePopup.openType(typeUids.get(0));
        }
    }

    public static void showRecipes(Item item, boolean uses) {
        RecipePopup.open(item, uses);
    }
}
