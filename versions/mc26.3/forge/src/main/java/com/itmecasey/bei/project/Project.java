package com.itmecasey.bei.project;

import com.itmecasey.bei.BeiConfig;
import com.itmecasey.bei.index.RecipeIndex;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/** The player's Master Project: which item, how many, and the plan kept fresh against the inventory. */
public final class Project {
    private static Item item;
    private static int count = 1;
    private static @Nullable String recipeId;
    private static Blueprint blueprint;
    private static Plan plan;
    private static int recipesSeen = -1;
    private static int ticks;

    private Project() {
    }

    public static @Nullable Item item() {
        return item;
    }

    /** The recipe the project must use for its own item, or null for the best choice. */
    public static @Nullable String recipeId() {
        return recipeId;
    }

    /** Makes {@code newItem} the project, made with one particular recipe. */
    public static void setRecipe(Item newItem, String recipe) {
        item = newItem;
        recipeId = recipe;
        count = Math.max(1, count);
        save();
        rebuild();
    }

    public static int count() {
        return count;
    }

    public static @Nullable Blueprint blueprint() {
        return blueprint;
    }

    public static @Nullable Plan plan() {
        return plan;
    }

    /** Makes {@code newItem} the project (the same item again clears it). */
    public static void toggle(Item newItem) {
        if (item == newItem) {
            clear();
        } else {
            set(newItem, 1);
        }
    }

    public static void set(Item newItem, int newCount) {
        if (item != newItem) {
            recipeId = null;
        }
        item = newItem;
        count = Math.max(1, Math.min(9999, newCount));
        save();
        rebuild();
    }

    public static void setCount(int newCount) {
        if (item != null) {
            set(item, newCount);
        }
    }

    public static void clear() {
        item = null;
        recipeId = null;
        blueprint = null;
        plan = null;
        save();
    }

    /** Reads the saved project; the tree itself is built once recipes have arrived. */
    public static void load() {
        Identifier id = Identifier.tryParse(BeiConfig.projectItem());
        Item saved = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        item = saved == null || saved == Items.AIR ? null : saved;
        count = BeiConfig.projectCount();
        recipeId = BeiConfig.projectRecipe().isEmpty() ? null : BeiConfig.projectRecipe();
        blueprint = null;
        plan = null;
        recipesSeen = -1;
    }

    private static void save() {
        BeiConfig.setProject(item == null ? "" : BuiltInRegistries.ITEM.getKey(item).toString(), count, recipeId == null ? "" : recipeId);
    }

    private static void rebuild() {
        Minecraft game = Minecraft.getInstance();
        if (item == null || game.player == null) {
            return;
        }
        Map<Item, Integer> owned = InventoryScanner.snapshot(game.player);
        blueprint = Blueprint.build(item, count, owned.keySet(), recipeId);
        plan = Plan.compute(blueprint, owned);
        recipesSeen = RecipeIndex.count();
    }

    /** Called every client tick: rebuilds when recipes changed, re-checks the inventory about once a second. */
    public static void tick() {
        Minecraft game = Minecraft.getInstance();
        if (item == null || game.player == null) {
            return;
        }
        if (blueprint == null || recipesSeen != RecipeIndex.count()) {
            if (RecipeIndex.count() > 0) {
                rebuild();
            }
            return;
        }
        if (++ticks % 20 == 0) {
            plan = Plan.compute(blueprint, InventoryScanner.snapshot(game.player));
        }
    }

    /** Re-checks the inventory right away (after the player changes the count, say). */
    public static void refresh() {
        Minecraft game = Minecraft.getInstance();
        if (blueprint != null && game.player != null) {
            plan = Plan.compute(blueprint, InventoryScanner.snapshot(game.player));
        }
    }
}
