package com.itmecasey.bei.plugin;

import com.itmecasey.bei.Bei;
import com.itmecasey.bei.api.BeiPlugin;
import com.itmecasey.bei.api.CustomRecipe;
import com.itmecasey.bei.api.IBeiPlugin;
import com.itmecasey.bei.api.IRecipeRegistration;
import com.itmecasey.bei.api.RecipeCategory;
import com.itmecasey.bei.index.BeiRecipe;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.objectweb.asm.Type;

/**
 * Finds the classes marked {@link BeiPlugin} in every installed mod, makes one of each and lets it
 * register categories and recipes. The scan reads the class files Forge has already indexed, so a
 * plugin class is not loaded unless its required mod is there. A plugin that fails to load or
 * throws is logged and skipped; it never stops the others or the game. Client only.
 */
public final class PluginHost {
    private static final String ANNOTATION = Type.getDescriptor(BeiPlugin.class);

    /** One plugin class found by the scan and the mod it needs, if any. */
    private record Found(String className, String requiredMod) {
    }

    private static List<IBeiPlugin> plugins = List.of();
    private static final Map<String, RecipeCategory> CATEGORIES = new LinkedHashMap<>();

    private PluginHost() {
    }

    private static boolean loaded;

    /**
     * Finds and creates the plugins and runs their category registration, the first time a world is
     * joined. It waits that long because item stacks (which categories hold) cannot be made while the
     * game is still starting.
     */
    public static synchronized void loadOnce() {
        if (!loaded) {
            loaded = true;
            load();
        }
    }

    private static void load() {
        List<IBeiPlugin> loaded = new ArrayList<>();
        for (Found found : scan()) {
            if (!found.requiredMod().isEmpty() && !ModList.get().isLoaded(found.requiredMod())) {
                continue;
            }
            try {
                Class<?> type = Class.forName(found.className(), true, PluginHost.class.getClassLoader());
                loaded.add((IBeiPlugin) type.getDeclaredConstructor().newInstance());
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                Bei.LOGGER.warn("Could not load the BEI plugin {}", found.className(), e);
            }
        }
        plugins = List.copyOf(loaded);
        for (IBeiPlugin plugin : plugins) {
            try {
                plugin.registerCategories(category -> CATEGORIES.put(category.id().toString(), category));
            } catch (RuntimeException | LinkageError e) {
                Bei.LOGGER.warn("The BEI plugin {} failed to register its categories", plugin.getClass().getName(), e);
            }
        }
        TransferHost.clear();
        for (IBeiPlugin plugin : plugins) {
            try {
                plugin.registerTransfers(TransferHost::register);
            } catch (RuntimeException | LinkageError e) {
                Bei.LOGGER.warn("The BEI plugin {} failed to register its transfer handlers", plugin.getClass().getName(), e);
            }
        }
        com.itmecasey.bei.client.SearchAccess search = new com.itmecasey.bei.client.SearchAccess();
        for (IBeiPlugin plugin : plugins) {
            try {
                plugin.registerSearch(search);
            } catch (RuntimeException | LinkageError e) {
                Bei.LOGGER.warn("The BEI plugin {} failed to take the search access", plugin.getClass().getName(), e);
            }
        }
        Bei.LOGGER.info("Loaded {} BEI plugin(s) with {} recipe categories", plugins.size(), CATEGORIES.size());
    }

    private static List<Found> scan() {
        List<Found> found = new ArrayList<>();
        for (ModFileScanData data : ModList.get().getAllScanData()) {
            for (ModFileScanData.AnnotationData annotation : data.getAnnotations()) {
                if (annotation.annotationType().getDescriptor().equals(ANNOTATION)
                        && annotation.targetType() == java.lang.annotation.ElementType.TYPE) {
                    Object required = annotation.annotationData().get("value");
                    found.add(new Found(annotation.clazz().getClassName(), required instanceof String id ? id : ""));
                }
            }
        }
        return found;
    }

    /** Asks every plugin for its recipes and returns them in BEI's own form; called each time the recipe list is built. */
    public static synchronized List<BeiRecipe> contribute() {
        List<BeiRecipe> out = new ArrayList<>();
        int[] counter = {0};
        IRecipeRegistration registration = (category, recipe) -> {
            try {
                CATEGORIES.putIfAbsent(category.id().toString(), category);
                out.add(convert(category, recipe, counter[0]++));
            } catch (RuntimeException e) {
                Bei.LOGGER.warn("Skipped a bad recipe of {}", category.id(), e);
            }
        };
        for (IBeiPlugin plugin : plugins) {
            try {
                plugin.registerRecipes(registration);
            } catch (RuntimeException | LinkageError e) {
                Bei.LOGGER.warn("The BEI plugin {} failed to register recipes", plugin.getClass().getName(), e);
            }
        }
        return out;
    }

    private static BeiRecipe convert(RecipeCategory category, CustomRecipe recipe, int number) {
        List<List<ItemStack>> inputs = new ArrayList<>();
        for (List<ItemStack> slot : recipe.inputs()) {
            List<ItemStack> usable = new ArrayList<>();
            for (ItemStack stack : slot) {
                if (!stack.isEmpty()) {
                    usable.add(stack);
                }
            }
            inputs.add(usable);
        }
        String id = recipe.id() != null ? recipe.id() : category.id() + "/" + number;
        return new BeiRecipe(id, category.id().toString(), 0, inputs, List.copyOf(recipe.outputs()),
                List.of(category.station()), category, recipe);
    }
}
