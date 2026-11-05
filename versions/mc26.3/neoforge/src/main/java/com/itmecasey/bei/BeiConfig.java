package com.itmecasey.bei;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings, stored in {@code bei-client.toml}: what is shown and the current Master Project. */
public final class BeiConfig {
    /** Which screen corner the project HUD hangs from. */
    public enum Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    /** Where cheat-mode items go. */
    public enum CheatTarget {
        CURSOR, INVENTORY
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue SHOW_BROWSER = BUILDER
            .comment("Show the item browser along the right edge of inventory screens.")
            .define("showBrowser", true);
    private static final ModConfigSpec.BooleanValue TAG_TRAY = BUILDER
            .comment("Show the strip of category icons that apply a filter with one click.")
            .define("tagTray", false);
    private static final ModConfigSpec.EnumValue<Corner> HUD_CORNER = BUILDER
            .comment("Which corner of the screen the Master Project HUD is pinned to.")
            .defineEnum("hudCorner", Corner.TOP_RIGHT);
    private static final ModConfigSpec.BooleanValue HUD_VISIBLE = BUILDER
            .comment("Show the Master Project HUD while a project is set.")
            .define("hudVisible", true);
    private static final ModConfigSpec.BooleanValue ENDER_CHEST = BUILDER
            .comment("Count items in your ender chest as already owned when tracking a project.")
            .define("countEnderChest", false);
    private static final ModConfigSpec.EnumValue<CheatTarget> CHEAT_TARGET = BUILDER
            .comment("Where cheat mode puts clicked items: on the cursor or in the inventory.")
            .defineEnum("cheatTarget", CheatTarget.CURSOR);
    private static final ModConfigSpec.ConfigValue<String> THEME = BUILDER
            .comment("Color theme: bei:dark, bei:light, or the id of a datapack theme such as mypack:neon.")
            .define("theme", "bei:dark");
    private static final ModConfigSpec.DoubleValue OPACITY = BUILDER
            .comment("How solid the backdrop of BEI's panels and popups is, from 0.3 (faint) to 1.")
            .defineInRange("opacity", 0.95, 0.3, 1.0);
    private static final ModConfigSpec.BooleanValue SLOT_BACKGROUND = BUILDER
            .comment("Draw a dark box behind each item slot in the recipe popup.")
            .define("slotBackground", true);
    private static final ModConfigSpec.BooleanValue BROWSER_BACKGROUND = BUILDER
            .comment("Draw a backdrop behind the item browser panel.")
            .define("browserBackground", true);
    private static final ModConfigSpec.BooleanValue CARD_BACKGROUND = BUILDER
            .comment("Draw a panel behind each recipe in the recipe popup.")
            .define("cardBackground", true);
    private static final ModConfigSpec.BooleanValue CUSTOM_GRID = BUILDER
            .comment("Use the columns and rows below for the item browser grid and recipe ingredients.")
            .define("customIngredientGrid", false);
    private static final ModConfigSpec.IntValue INGREDIENT_COLUMNS = BUILDER
            .comment("Columns of ingredient slots when the custom grid is on.")
            .defineInRange("ingredientColumns", 3, 1, 30);
    private static final ModConfigSpec.IntValue INGREDIENT_ROWS = BUILDER
            .comment("Rows of ingredient slots when the custom grid is on (columns widen if the recipe needs more room).")
            .defineInRange("ingredientRows", 3, 1, 30);
    private static final ModConfigSpec.IntValue RECIPE_X = BUILDER.comment("Left edge of the recipe popup; -1 centers it.")
            .defineInRange("recipePopupX", -1, -1, 10000);
    private static final ModConfigSpec.IntValue RECIPE_Y = BUILDER.comment("Top edge of the recipe popup; -1 centers it.")
            .defineInRange("recipePopupY", -1, -1, 10000);
    private static final ModConfigSpec.IntValue SETTINGS_X = BUILDER.comment("Left edge of the settings popup; -1 centers it.")
            .defineInRange("settingsPopupX", -1, -1, 10000);
    private static final ModConfigSpec.IntValue SETTINGS_Y = BUILDER.comment("Top edge of the settings popup; -1 centers it.")
            .defineInRange("settingsPopupY", -1, -1, 10000);
    private static final ModConfigSpec.ConfigValue<String> PROJECT_ITEM = BUILDER
            .comment("Item id of the Master Project (empty for none).")
            .define("projectItem", "");
    private static final ModConfigSpec.ConfigValue<String> PROJECT_RECIPE = BUILDER
            .comment("Recipe id the Master Project must use for its own item (empty for the best choice).")
            .define("projectRecipe", "");
    private static final ModConfigSpec.IntValue PROJECT_COUNT = BUILDER
            .comment("How many of the project item to make.")
            .defineInRange("projectCount", 1, 1, 9999);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private BeiConfig() {
    }

    public static boolean showBrowser() {
        return !SPEC.isLoaded() || SHOW_BROWSER.get();
    }

    public static void setShowBrowser(boolean value) {
        set(SHOW_BROWSER, value);
    }

    public static boolean tagTray() {
        return SPEC.isLoaded() && TAG_TRAY.get();
    }

    public static void setTagTray(boolean value) {
        set(TAG_TRAY, value);
    }

    public static Corner hudCorner() {
        return SPEC.isLoaded() ? HUD_CORNER.get() : Corner.TOP_RIGHT;
    }

    public static void setHudCorner(Corner value) {
        set(HUD_CORNER, value);
    }

    public static boolean hudVisible() {
        return !SPEC.isLoaded() || HUD_VISIBLE.get();
    }

    public static void setHudVisible(boolean value) {
        set(HUD_VISIBLE, value);
    }

    public static boolean countEnderChest() {
        return SPEC.isLoaded() && ENDER_CHEST.get();
    }

    public static void setCountEnderChest(boolean value) {
        set(ENDER_CHEST, value);
    }

    public static CheatTarget cheatTarget() {
        return SPEC.isLoaded() ? CHEAT_TARGET.get() : CheatTarget.CURSOR;
    }

    public static void setCheatTarget(CheatTarget value) {
        set(CHEAT_TARGET, value);
    }

    public static String theme() {
        return SPEC.isLoaded() ? THEME.get() : "bei:dark";
    }

    public static void setTheme(String id) {
        set(THEME, id);
    }

    public static double opacity() {
        return SPEC.isLoaded() ? OPACITY.get() : 0.95;
    }

    /** Sets the opacity without writing the file, so a slider can call it every frame; call {@link #save} after. */
    public static void setOpacity(double value) {
        if (SPEC.isLoaded()) {
            OPACITY.set(Math.max(0.3, Math.min(1.0, value)));
        }
    }

    public static void save() {
        if (SPEC.isLoaded()) {
            SPEC.save();
        }
    }

    public static boolean slotBackground() {
        return !SPEC.isLoaded() || SLOT_BACKGROUND.get();
    }

    public static void setSlotBackground(boolean value) {
        set(SLOT_BACKGROUND, value);
    }

    public static boolean browserBackground() {
        return !SPEC.isLoaded() || BROWSER_BACKGROUND.get();
    }

    public static void setBrowserBackground(boolean value) {
        set(BROWSER_BACKGROUND, value);
    }

    public static boolean cardBackground() {
        return !SPEC.isLoaded() || CARD_BACKGROUND.get();
    }

    public static void setCardBackground(boolean value) {
        set(CARD_BACKGROUND, value);
    }

    public static boolean customGrid() {
        return SPEC.isLoaded() && CUSTOM_GRID.get();
    }

    public static void setCustomGrid(boolean value) {
        set(CUSTOM_GRID, value);
    }

    public static int ingredientColumns() {
        return SPEC.isLoaded() ? INGREDIENT_COLUMNS.get() : 3;
    }

    public static void setIngredientColumns(int value) {
        set(INGREDIENT_COLUMNS, Math.max(1, Math.min(30, value)));
    }

    public static int ingredientRows() {
        return SPEC.isLoaded() ? INGREDIENT_ROWS.get() : 3;
    }

    public static void setIngredientRows(int value) {
        set(INGREDIENT_ROWS, Math.max(1, Math.min(30, value)));
    }

    /** Saved top-left of a popup ("recipe" or "settings"), or {-1, -1} for centered. */
    public static int[] windowPos(String name) {
        if (!SPEC.isLoaded()) {
            return new int[] {-1, -1};
        }
        return name.equals("recipe") ? new int[] {RECIPE_X.get(), RECIPE_Y.get()} : new int[] {SETTINGS_X.get(), SETTINGS_Y.get()};
    }

    public static void setWindowPos(String name, int x, int y) {
        if (!SPEC.isLoaded()) {
            return;
        }
        if (name.equals("recipe")) {
            RECIPE_X.set(x);
            RECIPE_Y.set(y);
        } else {
            SETTINGS_X.set(x);
            SETTINGS_Y.set(y);
        }
        SPEC.save();
    }

    public static String projectItem() {
        return SPEC.isLoaded() ? PROJECT_ITEM.get() : "";
    }

    public static int projectCount() {
        return SPEC.isLoaded() ? PROJECT_COUNT.get() : 1;
    }

    public static String projectRecipe() {
        return SPEC.isLoaded() ? PROJECT_RECIPE.get() : "";
    }

    public static void setProject(String itemId, int count, String recipeId) {
        if (SPEC.isLoaded()) {
            PROJECT_ITEM.set(itemId);
            PROJECT_RECIPE.set(recipeId);
            PROJECT_COUNT.set(Math.max(1, Math.min(9999, count)));
            SPEC.save();
        }
    }

    private static <T> void set(ModConfigSpec.ConfigValue<T> value, T newValue) {
        if (SPEC.isLoaded()) {
            value.set(newValue);
            SPEC.save();
        }
    }
}
