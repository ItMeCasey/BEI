package com.itmecasey.bei.compat;

import com.itmecasey.bei.Bei;
import com.itmecasey.bei.api.CustomRecipe;
import com.itmecasey.bei.api.RecipeCategory;
import com.itmecasey.bei.index.BeiRecipe;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.objectweb.asm.Type;

/**
 * Runs the recipe-facing part of existing JEI plugins and translates it to BEI stations. This is a
 * deliberately small JEI runtime: calls unrelated to recipe discovery return harmless empty values,
 * and a plugin that needs more of JEI is logged and skipped without affecting other plugins.
 */
public final class JeiBridge {
    private static final String ANNOTATION = Type.getDescriptor(JeiPlugin.class);
    private static final Map<RecipeType<?>, IRecipeCategory<?>> CATEGORIES = new LinkedHashMap<>();
    private static final Map<RecipeType<?>, List<Object>> RECIPES = new LinkedHashMap<>();
    private static final Map<RecipeType<?>, ItemStack> STATIONS = new LinkedHashMap<>();
    private static final Map<IDrawable, ItemStack> ICONS = new IdentityHashMap<>();
    private static List<IModPlugin> plugins = List.of();
    private static boolean loaded;

    private JeiBridge() {
    }

    public static synchronized void loadOnce() {
        if (loaded) {
            return;
        }
        loaded = true;
        List<IModPlugin> found = new ArrayList<>();
        for (String className : scan()) {
            try {
                Class<?> type = Class.forName(className, true, JeiBridge.class.getClassLoader());
                found.add((IModPlugin) type.getDeclaredConstructor().newInstance());
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                Bei.LOGGER.warn("Could not load JEI plugin {} for the BEI bridge", className, e);
            }
        }
        plugins = List.copyOf(found);
        IJeiHelpers helpers = helpers();
        IRecipeCategoryRegistration categories = proxy(IRecipeCategoryRegistration.class, (self, method, args) -> {
            if (method.getName().equals("getJeiHelpers")) {
                return helpers;
            }
            if (method.getName().equals("addRecipeCategories")) {
                for (Object value : values(args[0])) {
                    IRecipeCategory<?> category = (IRecipeCategory<?>) value;
                    CATEGORIES.put(category.getRecipeType(), category);
                }
            }
            return defaultValue(method.getReturnType());
        });
        IRecipeRegistration recipes = proxy(IRecipeRegistration.class, (self, method, args) -> {
            if (method.getName().equals("getJeiHelpers")) {
                return helpers;
            }
            if (method.getName().equals("addRecipes")) {
                RECIPES.computeIfAbsent((RecipeType<?>) args[0], key -> new ArrayList<>())
                        .addAll((List<?>) args[1]);
            }
            return fallback(method.getReturnType());
        });
        IRecipeCatalystRegistration catalysts = proxy(IRecipeCatalystRegistration.class,
                (self, method, args) -> catalystCall(method, args, helpers));
        for (IModPlugin plugin : plugins) {
            run(plugin, "categories", () -> plugin.registerCategories(categories));
            run(plugin, "recipes", () -> plugin.registerRecipes(recipes));
            run(plugin, "catalysts", () -> plugin.registerRecipeCatalysts(catalysts));
        }
        Bei.LOGGER.info("Loaded {} JEI plugin(s) with {} bridged categories", plugins.size(), CATEGORIES.size());
    }

    public static synchronized List<BeiRecipe> contribute() {
        loadOnce();
        List<BeiRecipe> out = new ArrayList<>();
        for (Map.Entry<RecipeType<?>, IRecipeCategory<?>> entry : CATEGORIES.entrySet()) {
            RecipeType<?> type = entry.getKey();
            IRecipeCategory<?> category = entry.getValue();
            List<Object> recipes = RECIPES.getOrDefault(type, List.of());
            if (recipes.isEmpty()) {
                continue;
            }
            try {
                bridgeCategory(type, category, recipes, out);
            } catch (RuntimeException | LinkageError e) {
                Bei.LOGGER.warn("Could not bridge JEI category {}", type.getUid(), e);
            }
        }
        return out;
    }

    /** Whether a JEI category supplies the richer station view for this game recipe type. */
    public static synchronized boolean handlesRecipeType(String recipeType) {
        return CATEGORIES.keySet().stream().anyMatch(type -> type.getUid().toString().equals(recipeType));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void bridgeCategory(RecipeType<?> type, IRecipeCategory category, List<Object> recipes,
            List<BeiRecipe> out) {
        List<LayoutSlot> first = layout(category, recipes.get(0));
        ItemStack station = STATIONS.get(type);
        if ((station == null || station.isEmpty()) && ICONS.containsKey(category.getIcon())) {
            station = ICONS.get(category.getIcon());
        }
        if (station == null || station.isEmpty()) {
            Bei.LOGGER.debug("JEI category {} has no item catalyst; it cannot be a BEI station", type.getUid());
            return;
        }
        RecipeCategory.Builder builder = RecipeCategory.builder(type.getUid())
                .title(category.getTitle())
                .station(station.copy())
                .size(Math.max(18, category.getWidth()), Math.max(18, category.getHeight()));
        for (LayoutSlot slot : first) {
            if (slot.role == RecipeIngredientRole.INPUT) {
                builder.input(slot.x, slot.y);
            } else if (slot.role == RecipeIngredientRole.OUTPUT) {
                builder.output(slot.x, slot.y);
            }
        }
        RecipeCategory beiCategory = builder.build();
        int number = 0;
        for (Object recipe : recipes) {
            List<LayoutSlot> slots = layout(category, recipe);
            CustomRecipe.Builder converted = CustomRecipe.builder().id(recipeId(category, recipe, type, number++));
            for (LayoutSlot slot : slots) {
                if (slot.role == RecipeIngredientRole.INPUT) {
                    converted.input(slot.items);
                }
            }
            boolean output = false;
            for (LayoutSlot slot : slots) {
                if (slot.role == RecipeIngredientRole.OUTPUT) {
                    for (ItemStack stack : slot.items) {
                        if (!stack.isEmpty()) {
                            converted.output(stack.copy());
                            output = true;
                        }
                    }
                }
            }
            if (output) {
                CustomRecipe custom = converted.data(recipe).build();
                out.add(new BeiRecipe(custom.id(), type.getUid().toString(), 0, custom.inputs(), custom.outputs(),
                        List.of(station.copy()), beiCategory, custom));
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<LayoutSlot> layout(IRecipeCategory category, Object recipe) {
        List<LayoutSlot> slots = new ArrayList<>();
        IRecipeLayoutBuilder builder = proxy(IRecipeLayoutBuilder.class, (self, method, args) -> {
            if (method.getName().equals("addSlot")) {
                LayoutSlot slot = new LayoutSlot((RecipeIngredientRole) args[0]);
                slots.add(slot);
                return slot.proxy();
            }
            if (method.getName().equals("addInvisibleIngredients")) {
                return new LayoutSlot((RecipeIngredientRole) args[0]).proxy();
            }
            return fluentOrDefault(self, method);
        });
        category.setRecipe(builder, recipe, emptyFocuses());
        return slots;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String recipeId(IRecipeCategory category, Object recipe, RecipeType<?> type, int number) {
        try {
            ResourceLocation id = category.getRegistryName(recipe);
            if (id != null) {
                return id.toString();
            }
        } catch (RuntimeException ignored) {
        }
        return type.getUid() + "/jei/" + number;
    }

    private static Object catalystCall(Method method, Object[] args, IJeiHelpers helpers) {
        if (method.getName().equals("getJeiHelpers")) {
            return helpers;
        }
        if ((method.getName().equals("addCraftingStation") || method.getName().equals("addCraftingStations"))
                && args[0] instanceof RecipeType<?> type) {
            // JEI 29 and later: the catalyst helpers all end here.
            for (Object value : values(args[args.length - 1])) {
                ItemStack stack = item(value);
                if (!stack.isEmpty()) {
                    STATIONS.putIfAbsent(type, stack.copy());
                }
            }
            return fallback(method.getReturnType());
        }
        if (method.getName().equals("addRecipeCatalysts") && args[0] instanceof RecipeType<?> type) {
            for (Object value : values(args[args.length - 1])) {
                ItemStack stack = item(value);
                if (!stack.isEmpty()) {
                    STATIONS.putIfAbsent(type, stack.copy());
                }
            }
        } else if (method.getName().equals("addRecipeCatalyst") && args[0] instanceof ItemLike item) {
            ItemStack stack = new ItemStack(item);
            for (Object value : values(args[1])) {
                STATIONS.putIfAbsent((RecipeType<?>) value, stack.copy());
            }
        } else if (method.getName().equals("addRecipeCatalyst") && args.length == 3) {
            ItemStack stack = item(args[1]);
            for (Object value : values(args[2])) {
                STATIONS.putIfAbsent((RecipeType<?>) value, stack.copy());
            }
        }
        return fallback(method.getReturnType());
    }

    private static IJeiHelpers helpers() {
        IGuiHelper gui = proxy(IGuiHelper.class, (self, method, args) -> {
            if (method.getName().equals("createDrawableIngredient") && args.length >= 2) {
                ItemStack stack = item(args[args.length - 1]);
                IDrawable drawable = drawable(18, 18);
                if (!stack.isEmpty()) {
                    ICONS.put(drawable, stack.copy());
                }
                return drawable;
            }
            if (method.getName().equals("createDrawableItemStack") && args[0] instanceof ItemStack stack) {
                IDrawable drawable = drawable(18, 18);
                ICONS.put(drawable, stack.copy());
                return drawable;
            }
            if (method.getName().equals("createBlankDrawable")) {
                return drawable((int) args[0], (int) args[1]);
            }
            if (IDrawable.class.isAssignableFrom(method.getReturnType())) {
                // The caller expects this exact subtype (animated, scrollable, ...), so build that one.
                return proxy(method.getReturnType(), (inner, call, callArgs) -> switch (call.getName()) {
                    case "getWidth", "getHeight" -> 18;
                    default -> fallback(call.getReturnType());
                });
            }
            return fallback(method.getReturnType());
        });
        return proxy(IJeiHelpers.class, (self, method, args) -> {
            if (method.getName().equals("getGuiHelper")) {
                return gui;
            }
            if (method.getName().equals("getRecipeType")) {
                return Optional.empty();
            }
            if (method.getName().equals("getAllRecipeTypes")) {
                return CATEGORIES.keySet().stream();
            }
            return fallback(method.getReturnType());
        });
    }

    private static IDrawableStatic drawable(int width, int height) {
        return proxy(IDrawableStatic.class, (self, method, args) -> switch (method.getName()) {
            case "getWidth" -> width;
            case "getHeight" -> height;
            default -> fallback(method.getReturnType());
        });
    }

    private static IFocusGroup emptyFocuses() {
        return proxy(IFocusGroup.class, (self, method, args) -> switch (method.getName()) {
            case "isEmpty" -> true;
            case "getAllFocuses" -> List.of();
            case "getFocuses" -> java.util.stream.Stream.empty();
            default -> fallback(method.getReturnType());
        });
    }

    private static List<String> scan() {
        List<String> found = new ArrayList<>();
        for (ModFileScanData data : ModList.get().getAllScanData()) {
            for (ModFileScanData.AnnotationData annotation : data.getAnnotations()) {
                if (annotation.annotationType().getDescriptor().equals(ANNOTATION)
                        && annotation.targetType() == java.lang.annotation.ElementType.TYPE) {
                    found.add(annotation.clazz().getClassName());
                }
            }
        }
        return found;
    }

    private static void run(IModPlugin plugin, String phase, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError e) {
            Bei.LOGGER.warn("JEI plugin {} failed while registering {} with BEI", plugin.getClass().getName(), phase, e);
        }
    }

    private static ItemStack item(Object value) {
        if (value instanceof ItemStack stack) {
            return stack;
        }
        if (value instanceof ItemLike item) {
            return new ItemStack(item);
        }
        if (value instanceof ITypedIngredient<?> typed) {
            return item(typed.getIngredient());
        }
        return ItemStack.EMPTY;
    }

    private static List<Object> values(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value.getClass().isArray()) {
            List<Object> out = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) {
                out.add(Array.get(value, i));
            }
            return out;
        }
        return List.of(value);
    }

    private static Object fluentOrDefault(Object self, Method method) {
        return method.getReturnType().isInstance(self) ? self : fallback(method.getReturnType());
    }

    private static Object fallback(Class<?> type) {
        if (type.isInterface()) {
            return proxy(type, (self, method, args) -> fluentOrDefault(self, method));
        }
        return defaultValue(type);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        if (type == Optional.class) return Optional.empty();
        if (type == List.class) return List.of();
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, BridgeHandler handler) {
        InvocationHandler invocation = (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "BEI JEI bridge " + type.getSimpleName();
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self == args[0];
                    default -> null;
                };
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(self, method, args == null ? new Object[0] : args);
            }
            return handler.invoke(self, method, args == null ? new Object[0] : args);
        };
        return (T) Proxy.newProxyInstance(JeiBridge.class.getClassLoader(), new Class<?>[]{type}, invocation);
    }

    @FunctionalInterface
    private interface BridgeHandler {
        Object invoke(Object self, Method method, Object[] args) throws Throwable;
    }

    private static final class LayoutSlot {
        private final RecipeIngredientRole role;
        private final List<ItemStack> items = new ArrayList<>();
        private int x;
        private int y;

        private LayoutSlot(RecipeIngredientRole role) {
            this.role = role;
        }

        private IRecipeSlotBuilder proxy() {
            return JeiBridge.proxy(IRecipeSlotBuilder.class, (self, method, args) -> {
                switch (method.getName()) {
                    case "setPosition" -> {
                        x = (int) args[0];
                        y = (int) args[1];
                    }
                    case "addIngredient" -> add(args[1]);
                    case "addIngredients", "addIngredientsUnsafe", "addTypedIngredients", "addOptionalTypedIngredients" -> {
                        for (Object value : values(args[args.length - 1])) {
                            if (value instanceof Optional<?> optional) {
                                optional.ifPresent(this::add);
                            } else {
                                add(value);
                            }
                        }
                    }
                    case "getWidth", "getHeight" -> {
                        return 18;
                    }
                    default -> {
                    }
                }
                return fluentOrDefault(self, method);
            });
        }

        private void add(Object value) {
            ItemStack stack = item(value);
            if (!stack.isEmpty()) {
                items.add(stack.copy());
            }
        }
    }
}
