package com.itmecasey.bei.index;

import com.itmecasey.bei.Bei;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.Nullable;

/**
 * Every recipe, indexed by what it makes and by what it uses. In 1.20.1 the client already has the
 * game's whole recipe list, so there is nothing to ask the server for: each recipe is read through
 * its ingredients and result, which also covers modded recipe types that implement the standard
 * recipe interface. The index is built on a background thread when the recipes arrive.
 */
public final class RecipeIndex {
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

    /** Builds the index from plugin recipes alone, before the game's recipes have arrived. */
    public static void buildLocal() {
        start(null, null);
    }

    /** Builds the index from the game's recipes; called when the client receives them. */
    public static void rebuild(RecipeManager manager) {
        Minecraft game = Minecraft.getInstance();
        if (game.getConnection() == null) {
            return;
        }
        start(manager, game.getConnection().registryAccess());
    }

    private static void start(@Nullable RecipeManager manager, @Nullable RegistryAccess access) {
        List<Recipe<?>> holders = manager == null ? List.of() : new ArrayList<>(manager.getRecipes());
        Thread thread = new Thread(() -> convert(holders, access), "BEI-recipes");
        thread.setDaemon(true);
        thread.start();
    }

    private static void convert(List<Recipe<?>> holders, @Nullable RegistryAccess access) {
        long start = System.nanoTime();
        Map<Item, List<BeiRecipe>> out = new HashMap<>();
        Map<Item, List<BeiRecipe>> in = new HashMap<>();
        int total = 0;
        for (Recipe<?> holder : holders) {
            try {
                BeiRecipe recipe = convert(holder, access);
                if (recipe == null || com.itmecasey.bei.compat.ModJsCompat.hiddenCategory(recipe)) {
                    continue;
                }
                total++;
                index(recipe, out, in);
            } catch (RuntimeException e) {
                Bei.LOGGER.debug("Could not read recipe {}", holder.getId(), e);
            }
        }
        List<BeiRecipe> extra = new ArrayList<>(com.itmecasey.bei.plugin.PluginHost.contribute());
        extra.addAll(com.itmecasey.bei.compat.ModJsCompat.infoRecipes());
        for (BeiRecipe recipe : extra) {
            if (com.itmecasey.bei.compat.ModJsCompat.hiddenCategory(recipe)) {
                continue;
            }
            total++;
            index(recipe, out, in);
        }
        byOutput = out;
        byInput = in;
        count = total;
        Bei.LOGGER.info("Indexed {} recipes in {} ms", total, (System.nanoTime() - start) / 1_000_000);
    }

    private static void index(BeiRecipe recipe, Map<Item, List<BeiRecipe>> out, Map<Item, List<BeiRecipe>> in) {
        for (ItemStack stack : recipe.outputs()) {
            add(out, stack.getItem(), recipe);
        }
        for (List<ItemStack> slot : recipe.inputs()) {
            for (ItemStack stack : slot) {
                add(in, stack.getItem(), recipe);
            }
        }
    }

    private static void add(Map<Item, List<BeiRecipe>> map, Item item, BeiRecipe recipe) {
        List<BeiRecipe> list = map.computeIfAbsent(item, k -> new ArrayList<>());
        if (list.isEmpty() || list.get(list.size() - 1) != recipe) {
            list.add(recipe);
        }
    }

    /** The station a vanilla recipe type is made in. */
    private static @Nullable Item stationOf(String typePath) {
        return switch (typePath) {
            case "crafting", "crafting_shaped", "crafting_shapeless", "crafting_transmute" -> Items.CRAFTING_TABLE;
            case "smelting" -> Items.FURNACE;
            case "blasting" -> Items.BLAST_FURNACE;
            case "smoking" -> Items.SMOKER;
            case "campfire_cooking" -> Items.CAMPFIRE;
            case "stonecutting" -> Items.STONECUTTER;
            case "smithing" -> Items.SMITHING_TABLE;
            default -> null;
        };
    }

    /**
     * Smithing recipes do not list their ingredients the standard way in 1.20.1, so they are read
     * from the recipe's own template, base and addition fields (found by type, in declaration order).
     */
    private static List<List<ItemStack>> smithingInputs(Recipe<?> recipe) {
        List<List<ItemStack>> inputs = new ArrayList<>();
        for (java.lang.reflect.Field field : recipe.getClass().getDeclaredFields()) {
            if (field.getType() != Ingredient.class) {
                continue;
            }
            try {
                field.setAccessible(true);
                Ingredient ingredient = (Ingredient) field.get(recipe);
                List<ItemStack> options = new ArrayList<>();
                if (ingredient != null && !ingredient.isEmpty()) {
                    for (ItemStack stack : ingredient.getItems()) {
                        if (!stack.isEmpty()) {
                            options.add(stack);
                        }
                    }
                }
                inputs.add(options);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return List.of();
            }
        }
        return inputs;
    }

    private static @Nullable BeiRecipe convert(Recipe<?> recipe, @Nullable RegistryAccess access) {
        if (access == null) {
            return null;
        }
        ItemStack result = recipe.getResultItem(access);
        if (result.isEmpty()) {
            return null;
        }
        ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        String path = typeId == null ? "unknown" : typeId.getPath();
        boolean vanilla = typeId != null && typeId.getNamespace().equals("minecraft");
        String type;
        int width = 0;
        // In 1.20.1 shaped and shapeless recipes share the one "crafting" type; the class tells them apart.
        if (vanilla && path.startsWith("crafting") && recipe instanceof ShapedRecipe shaped) {
            type = "ShapedCrafting";
            width = shaped.getWidth();
        } else if (vanilla && path.startsWith("crafting")) {
            type = "ShapelessCrafting";
        } else if (vanilla && (path.equals("smelting") || path.equals("blasting") || path.equals("smoking") || path.equals("campfire_cooking"))) {
            type = "Furnace";
        } else if (vanilla && path.equals("stonecutting")) {
            type = "Stonecutter";
        } else if (vanilla && path.equals("smithing")) {
            type = "Smithing";
        } else {
            type = path;
        }
        List<List<ItemStack>> inputs = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            List<ItemStack> options = new ArrayList<>();
            if (!ingredient.isEmpty()) {
                for (ItemStack stack : ingredient.getItems()) {
                    if (!stack.isEmpty()) {
                        options.add(stack);
                    }
                }
            }
            inputs.add(options);
        }
        if (type.equals("Smithing") && inputs.stream().allMatch(List::isEmpty)) {
            inputs = smithingInputs(recipe);
        }
        Item station = vanilla ? stationOf(path) : null;
        List<ItemStack> stations = station == null ? List.of() : List.of(new ItemStack(station));
        return new BeiRecipe(recipe.getId().toString(), type, width, inputs, List.of(result), stations, null, null,
                typeId == null ? null : typeId.toString());
    }
}
