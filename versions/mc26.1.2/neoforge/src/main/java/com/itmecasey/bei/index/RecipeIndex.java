package com.itmecasey.bei.index;

import com.itmecasey.bei.Bei;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

/**
 * Recipes the server sent, converted on a background thread into lookups by output and by input.
 * The server sends vanilla {@link RecipeDisplay}s, so any mod's recipe type that supplies a display
 * shows up; types BEI does not know the layout of still list what they make.
 */
public final class RecipeIndex {
    /** A display together with the recipe id it came from, as received. */
    public record Raw(String id, RecipeDisplay display, String typeId) {
    }

    /** Most recipes kept from one server, so a hostile server cannot fill memory. */
    private static final int MAX_RECIPES = 300_000;
    private static final List<Raw> pending = new ArrayList<>();
    private static volatile Map<Item, List<BeiRecipe>> byOutput = Map.of();
    private static volatile Map<Item, List<BeiRecipe>> byInput = Map.of();
    private static volatile int count;

    private RecipeIndex() {
    }

    public static int count() {
        return count;
    }

    public static List<BeiRecipe> producing(Item item) {
        return byOutput.getOrDefault(item, List.of());
    }

    public static List<BeiRecipe> using(Item item) {
        return byInput.getOrDefault(item, List.of());
    }

    /** Every recipe of one recipe type or card category (by id), each once. */
    public static List<BeiRecipe> ofType(String typeUid) {
        java.util.Map<String, BeiRecipe> found = new java.util.LinkedHashMap<>();
        for (List<BeiRecipe> list : byOutput.values()) {
            for (BeiRecipe recipe : list) {
                if (typeUid.equals(recipe.recipeType()) || typeUid.equals(recipe.type())
                        || recipe.category() != null && typeUid.equals(recipe.category().id().toString())) {
                    found.putIfAbsent(recipe.id(), recipe);
                }
            }
        }
        return new java.util.ArrayList<>(found.values());
    }

    /** Replaces the index with these recipes, for tests and tools that have no recipe manager. */
    public static void useRecipes(List<BeiRecipe> recipes) {
        Map<Item, List<BeiRecipe>> out = new HashMap<>();
        Map<Item, List<BeiRecipe>> in = new HashMap<>();
        for (BeiRecipe recipe : recipes) {
            for (ItemStack output : recipe.outputs()) {
                add(out, output.getItem(), recipe);
            }
            for (List<ItemStack> slot : recipe.inputs()) {
                for (ItemStack input : slot) {
                    add(in, input.getItem(), recipe);
                }
            }
        }
        byOutput = out;
        byInput = in;
        count = recipes.size();
    }

    /** Builds the index from plugin recipes alone, for joining a server that sends none; real recipes replace it when they arrive. */
    public static void buildLocal() {
        Thread thread = new Thread(() -> convert(List.of(), null), "BEI-recipes-local");
        thread.setDaemon(true);
        thread.start();
    }

    /** Called on the main thread as batches arrive; {@code done} converts everything received. */
    public static void receive(boolean first, List<Raw> batch, boolean done) {
        List<Raw> snapshot;
        synchronized (pending) {
            if (first) {
                pending.clear();
            }
            int room = MAX_RECIPES - pending.size();
            pending.addAll(room >= batch.size() ? batch : batch.subList(0, Math.max(0, room)));
            if (!done) {
                return;
            }
            snapshot = new ArrayList<>(pending);
        }
        Minecraft game = Minecraft.getInstance();
        if (game.level == null) {
            return;
        }
        com.itmecasey.bei.compat.JeiHooks.reload();
        ContextMap context = SlotDisplayContext.fromLevel(game.level);
        Thread thread = new Thread(() -> convert(snapshot, context), "BEI-recipes");
        thread.setDaemon(true);
        thread.start();
    }

    private static void convert(List<Raw> raws, ContextMap context) {
        long start = System.nanoTime();
        Map<Item, List<BeiRecipe>> out = new HashMap<>();
        Map<Item, List<BeiRecipe>> in = new HashMap<>();
        int total = 0;
        for (Raw raw : raws) {
            try {
                BeiRecipe recipe = convert(raw, context);
                if (recipe == null) {
                    continue;
                }
                recipe = recipe.withRecipeType(raw.typeId());
                if (com.itmecasey.bei.compat.ModJsCompat.hiddenCategory(recipe)) {
                    continue;
                }
                if (recipe.recipeType() != null
                        && com.itmecasey.bei.compat.JeiHooks.handlesRecipeType(recipe.recipeType())) {
                    continue;
                }
                total++;
                for (ItemStack stack : recipe.outputs()) {
                    add(out, stack.getItem(), recipe);
                }
                for (List<ItemStack> slot : recipe.inputs()) {
                    for (ItemStack stack : slot) {
                        add(in, stack.getItem(), recipe);
                    }
                }
            } catch (RuntimeException e) {
                Bei.LOGGER.debug("Could not read recipe {}", raw.id(), e);
            }
        }
        List<BeiRecipe> extra = new ArrayList<>(com.itmecasey.bei.plugin.PluginHost.contribute());
        extra.addAll(com.itmecasey.bei.compat.ModJsCompat.infoRecipes());
        for (BeiRecipe recipe : extra) {
            if (com.itmecasey.bei.compat.ModJsCompat.hiddenCategory(recipe)) {
                continue;
            }
            total++;
            for (ItemStack stack : recipe.outputs()) {
                add(out, stack.getItem(), recipe);
            }
            for (List<ItemStack> slot : recipe.inputs()) {
                for (ItemStack stack : slot) {
                    add(in, stack.getItem(), recipe);
                }
            }
        }
        byOutput = out;
        byInput = in;
        count = total;
        Bei.LOGGER.info("Indexed {} recipes in {} ms", total, (System.nanoTime() - start) / 1_000_000);
    }

    private static void add(Map<Item, List<BeiRecipe>> map, Item item, BeiRecipe recipe) {
        List<BeiRecipe> list = map.computeIfAbsent(item, k -> new ArrayList<>());
        if (list.isEmpty() || list.get(list.size() - 1) != recipe) {
            list.add(recipe);
        }
    }

    private static List<ItemStack> stacks(SlotDisplay display, ContextMap context) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack stack : display.resolveForStacks(context)) {
            if (!stack.isEmpty()) {
                list.add(stack);
            }
        }
        return list;
    }

    private static BeiRecipe convert(Raw raw, ContextMap context) {
        RecipeDisplay display = raw.display();
        List<ItemStack> outputs = stacks(display.result(), context);
        if (outputs.isEmpty()) {
            return null;
        }
        List<ItemStack> stations = stacks(display.craftingStation(), context);
        List<List<ItemStack>> inputs = new ArrayList<>();
        int width = 0;
        String type;
        switch (display) {
            case ShapedCraftingRecipeDisplay shaped -> {
                type = "ShapedCrafting";
                width = shaped.width();
                for (SlotDisplay slot : shaped.ingredients()) {
                    inputs.add(stacks(slot, context));
                }
            }
            case ShapelessCraftingRecipeDisplay shapeless -> {
                type = "ShapelessCrafting";
                for (SlotDisplay slot : shapeless.ingredients()) {
                    inputs.add(stacks(slot, context));
                }
            }
            case FurnaceRecipeDisplay furnace -> {
                type = "Furnace";
                inputs.add(stacks(furnace.ingredient(), context));
            }
            case StonecutterRecipeDisplay cutter -> {
                type = "Stonecutter";
                inputs.add(stacks(cutter.input(), context));
            }
            case SmithingRecipeDisplay smithing -> {
                type = "Smithing";
                inputs.add(stacks(smithing.template(), context));
                inputs.add(stacks(smithing.base(), context));
                inputs.add(stacks(smithing.addition(), context));
            }
            default -> type = display.getClass().getSimpleName().replace("RecipeDisplay", "");
        }
        return new BeiRecipe(raw.id(), type, width, inputs, outputs, stations);
    }
}
