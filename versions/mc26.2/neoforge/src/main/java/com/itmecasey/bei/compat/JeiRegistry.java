package com.itmecasey.bei.compat;

import com.itmecasey.bei.Bei;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IAdvancedRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What the bridge keeps for the registration calls that are not recipes: custom ingredient types, search aliases, mod
 * names, extra items, recipe manager plugins, category decorators and recipe button factories, plus the ingredient
 * manager and recipe layout drawables plugins ask for at runtime. Everything is reached through JEI's interfaces by
 * name, so the same code serves every game version BEI supports.
 */
final class JeiRegistry {
    /** One ingredient type a plugin registered, with the helper, renderer, codec and the ingredients it listed. */
    record Registered(IIngredientType<?> type, List<Object> ingredients, @Nullable Object helper, @Nullable Object renderer,
            @Nullable Object codec) {
    }

    static final Map<IIngredientType<?>, Registered> TYPES = new LinkedHashMap<>();
    static final List<Object> LISTENERS = new ArrayList<>();
    static final List<Object> MANAGER_PLUGINS = new ArrayList<>();
    static final Map<IRecipeType<?>, List<Object>> TYPED_PLUGINS = new LinkedHashMap<>();
    static final Map<IRecipeType<?>, List<Object>> DECORATORS = new LinkedHashMap<>();
    static final List<Object> BUTTON_FACTORIES = new ArrayList<>();
    static final List<Object> SEARCH_STORAGES = new ArrayList<>();

    private JeiRegistry() {
    }

    static synchronized void clear() {
        TYPES.clear();
        LISTENERS.clear();
        MANAGER_PLUGINS.clear();
        TYPED_PLUGINS.clear();
        DECORATORS.clear();
        BUTTON_FACTORIES.clear();
        SEARCH_STORAGES.clear();
        JeiAliases.clear();
    }

    // ---- ingredient types --------------------------------------------------------------------------

    /** IModIngredientRegistration.register(type, ingredients, helper, renderer[, codec]). */
    static synchronized void registerType(Object[] args) {
        if (args.length < 4 || !(args[0] instanceof IIngredientType<?> type)) {
            return;
        }
        List<Object> list = new ArrayList<>(JeiBridge.values(args[1]));
        TYPES.put(type, new Registered(type, list, args[2], args[3], args.length > 4 ? args[4] : null));
    }

    static @Nullable Registered typeOf(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        for (Registered registered : TYPES.values()) {
            if (registered.type().getIngredientClass().isInstance(value)) {
                return registered;
            }
        }
        return null;
    }

    /** A typed ingredient: a value and its type, as JEI hands them around. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static ITypedIngredient<?> typed(IIngredientType type, Object value) {
        if (type == mezz.jei.api.constants.VanillaTypes.ITEM_STACK && value instanceof ItemStack stack) {
            return JeiViews.typed(stack);
        }
        return JeiBridge.proxy(ITypedIngredient.class, (self, method, args) -> switch (method.getName()) {
            case "getType" -> type;
            case "getIngredient" -> args.length == 0 ? value : Optional.of(value);
            case "normalize" -> self;
            case "getItemStack" -> Optional.empty();
            default -> JeiBridge.fluentOrDefault(self, method);
        });
    }

    private static List<ITypedIngredient<?>> typedAll(Registered registered) {
        List<ITypedIngredient<?>> out = new ArrayList<>();
        for (Object value : registered.ingredients()) {
            out.add(typed(registered.type(), value));
        }
        return out;
    }

    /** JEI's helper for item stacks, answered from BEI's own naming. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static Object itemHelper() {
        Class<?> type = JeiBridge.ifaceClass("mezz.jei.api.ingredients.IIngredientHelper");
        if (type == null) {
            return null;
        }
        return JeiBridge.proxyFirst((Class) type, (self, method, args) -> {
            ItemStack stack = args.length > 0 && args[0] instanceof ItemStack s ? s : ItemStack.EMPTY;
            net.minecraft.resources.Identifier key = stack.isEmpty() ? null
                    : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            return switch (method.getName()) {
                case "getIngredientType" -> mezz.jei.api.constants.VanillaTypes.ITEM_STACK;
                case "getDisplayName" -> stack.getHoverName().getString();
                case "getUniqueId", "getUid", "getWildcardId" -> key == null ? "" : key.toString();
                case "getDisplayModId" -> key == null ? "" : key.getNamespace();
                case "getIdentifier" -> key;
                case "copyIngredient", "normalizeIngredient" -> stack.copy();
                case "isValidIngredient", "isIngredientOnServer" -> !stack.isEmpty();
                case "getAmount" -> stack.getCount();
                case "getErrorInfo" -> String.valueOf(stack);
                case "getCheatItemStack" -> stack.copy();
                default -> JeiBridge.DEFER;
            };
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object itemRenderer() {
        Class<?> type = JeiBridge.ifaceClass("mezz.jei.api.ingredients.IIngredientRenderer");
        if (type == null) {
            return null;
        }
        return JeiBridge.proxyFirst((Class) type, (self, method, args) -> switch (method.getName()) {
            case "getWidth", "getHeight" -> 16;
            case "getTooltip" -> args.length > 0 && args[0] instanceof ItemStack stack ? List.of(stack.getHoverName()) : List.of();
            default -> JeiBridge.DEFER;
        });
    }

    private static @Nullable Object itemCodec() {
        try {
            return ItemStack.class.getField("CODEC").get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static void fireListeners(String method, Registered registered, List<Object> values) {
        if (registered.helper() == null || values.isEmpty()) {
            return;
        }
        List<ITypedIngredient<?>> typed = new ArrayList<>();
        for (Object value : values) {
            typed.add(typed(registered.type(), value));
        }
        for (Object listener : List.copyOf(LISTENERS)) {
            Method call = JeiTransfers.find(listener.getClass(), method, 2);
            if (call == null) {
                continue;
            }
            try {
                call.invoke(listener, registered.helper(), typed);
            } catch (ReflectiveOperationException | RuntimeException e) {
                Bei.LOGGER.debug("A JEI ingredient listener failed", e);
            }
        }
    }

    /** The ingredient manager plugins get in onRuntimeAvailable and through the runtime registration. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static mezz.jei.api.runtime.IIngredientManager ingredientManager() {
        return JeiBridge.proxy(mezz.jei.api.runtime.IIngredientManager.class, (self, method, args) -> {
            String name = method.getName();
            boolean items = args.length > 0 && args[0] == mezz.jei.api.constants.VanillaTypes.ITEM_STACK;
            Registered registered = args.length > 0 ? TYPES.get(args[0]) : null;
            switch (name) {
                case "addIngredientsAtRuntime":
                case "removeIngredientsAtRuntime":
                case "refreshIngredientsAtRuntime": {
                    if (args.length < 2) {
                        return null;
                    }
                    boolean hide = name.equals("removeIngredientsAtRuntime");
                    if (registered != null) {
                        List<Object> values = JeiBridge.values(args[1]);
                        synchronized (JeiRegistry.class) {
                            if (name.startsWith("add")) {
                                registered.ingredients().addAll(values);
                            } else if (hide) {
                                registered.ingredients().removeAll(values);
                            }
                        }
                        fireListeners(name.startsWith("add") ? "onIngredientsAdded" : hide ? "onIngredientsRemoved" : "onIngredientsUpdated",
                                registered, values);
                        return null;
                    }
                    if (name.startsWith("refresh")) {
                        return null;
                    }
                    int count = 0;
                    for (Object value : JeiBridge.values(args[1])) {
                        ItemStack stack = JeiBridge.item(value);
                        if (!stack.isEmpty()) {
                            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                            if (hide ? JeiHidden.hideItem(id) : JeiHidden.showItem(id)) {
                                count++;
                            }
                        }
                    }
                    if (count > 0) {
                        Bei.LOGGER.debug("A JEI plugin {} {} item(s)", hide ? "hid" : "showed", count);
                    }
                    return null;
                }
                case "getAllIngredients":
                    return items ? JeiBridge.allItemStacks() : registered == null ? List.of() : List.copyOf(registered.ingredients());
                case "getAllItemStacks":
                    return JeiBridge.allItemStacks();
                case "getAllTypedIngredients": {
                    if (items) {
                        List<ITypedIngredient<?>> all = new ArrayList<>();
                        for (ItemStack stack : JeiBridge.allItemStacks()) {
                            all.add(JeiViews.typed(stack));
                        }
                        return all;
                    }
                    return registered == null ? List.of() : typedAll(registered);
                }
                case "getRegisteredIngredientTypes": {
                    List<Object> types = new ArrayList<>();
                    types.add(mezz.jei.api.constants.VanillaTypes.ITEM_STACK);
                    types.addAll(TYPES.keySet());
                    return types;
                }
                case "getIngredientTypeForUid": {
                    String uid = String.valueOf(args[0]);
                    if (uid.equals(mezz.jei.api.constants.VanillaTypes.ITEM_STACK.getUid())) {
                        return Optional.of(mezz.jei.api.constants.VanillaTypes.ITEM_STACK);
                    }
                    for (IIngredientType<?> type : TYPES.keySet()) {
                        if (type.getUid().equals(uid)) {
                            return Optional.of(type);
                        }
                    }
                    return Optional.empty();
                }
                case "getIngredientType":
                case "getIngredientTypeChecked": {
                    Object subject = args[0];
                    IIngredientType<?> found = null;
                    boolean byClass = subject instanceof Class<?>;
                    if (subject instanceof ItemStack || byClass && ItemStack.class.isAssignableFrom((Class<?>) subject)) {
                        found = mezz.jei.api.constants.VanillaTypes.ITEM_STACK;
                    } else {
                        for (Registered each : TYPES.values()) {
                            Class<?> ingredientClass = each.type().getIngredientClass();
                            if (byClass ? ingredientClass.isAssignableFrom((Class<?>) subject) : ingredientClass.isInstance(subject)) {
                                found = each.type();
                                break;
                            }
                        }
                    }
                    if (name.equals("getIngredientType")) {
                        return found;
                    }
                    return Optional.ofNullable(found);
                }
                case "getIngredientHelper":
                case "getIngredientRenderer":
                case "getIngredientCodec": {
                    Registered target = registered != null ? registered : typeOf(args[0]);
                    boolean item = items || args[0] instanceof ItemStack;
                    if (target != null) {
                        return name.equals("getIngredientHelper") ? target.helper()
                                : name.equals("getIngredientRenderer") ? target.renderer() : target.codec();
                    }
                    if (item) {
                        return name.equals("getIngredientHelper") ? itemHelper()
                                : name.equals("getIngredientRenderer") ? itemRenderer() : itemCodec();
                    }
                    return null;
                }
                case "getRegisteringPluginUid":
                    return Optional.empty();
                case "createTypedIngredient": {
                    // (type, value, normalize) is the one the others call.
                    IIngredientType<?> type = args.length >= 2 && args[0] instanceof IIngredientType<?> t ? t : null;
                    Object value = type != null ? args[1] : args.length > 0 ? args[0] : null;
                    if (type == null) {
                        type = value instanceof ItemStack ? mezz.jei.api.constants.VanillaTypes.ITEM_STACK
                                : value == null || typeOf(value) == null ? null : typeOf(value).type();
                    }
                    if (type == null || value == null || value instanceof ItemStack stack && stack.isEmpty()) {
                        return Optional.empty();
                    }
                    return Optional.of(typed((IIngredientType) type, value));
                }
                case "normalizeTypedIngredient":
                    return args[0];
                case "getIngredientByUid":
                case "getTypedIngredientByUid": {
                    Registered target = registered;
                    String uid = String.valueOf(args[1]);
                    if (target != null && target.helper() != null) {
                        Method unique = JeiTransfers.find(target.helper().getClass(), "getUniqueId", 2);
                        for (Object value : target.ingredients()) {
                            try {
                                if (unique != null && uid.equals(String.valueOf(unique.invoke(target.helper(), value, uidContext(unique))))) {
                                    return Optional.of(name.equals("getIngredientByUid") ? value : typed(target.type(), value));
                                }
                            } catch (ReflectiveOperationException | RuntimeException e) {
                                // This ingredient has no usable id.
                            }
                        }
                    }
                    if (items) {
                        for (ItemStack stack : JeiBridge.allItemStacks()) {
                            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(uid)) {
                                return Optional.of(name.equals("getIngredientByUid") ? stack : JeiViews.typed(stack));
                            }
                        }
                    }
                    return Optional.empty();
                }
                case "getIngredientAliases": {
                    ItemStack stack = JeiBridge.item(args[0]);
                    return stack.isEmpty() ? List.of()
                            : JeiAliases.listOfItem(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                }
                case "registerIngredientListener": {
                    if (args[0] != null) {
                        synchronized (JeiRegistry.class) {
                            LISTENERS.add(args[0]);
                        }
                    }
                    return null;
                }
                case "getClickableIngredientFactory":
                    return clickableFactory(method.getReturnType());
                case "createClickableIngredient": {
                    // (type, value, area, important) or (value, area, important)
                    boolean typedForm = args.length == 4;
                    Object value = typedForm ? args[1] : args[0];
                    Object area = typedForm ? args[2] : args[1];
                    IIngredientType<?> type = typedForm ? (IIngredientType<?>) args[0]
                            : value instanceof ItemStack ? mezz.jei.api.constants.VanillaTypes.ITEM_STACK
                                    : typeOf(value) == null ? null : typeOf(value).type();
                    return type == null || value == null ? Optional.empty() : Optional.of(clickable(type, value, area));
                }
                case "getIngredientTypeWithSubtypesFromBase":
                    return args[0] instanceof net.minecraft.world.item.Item
                            ? Optional.of(mezz.jei.api.constants.VanillaTypes.ITEM_STACK) : Optional.empty();
                default:
                    JeiBridge.unsupported(mezz.jei.api.runtime.IIngredientManager.class, method);
                    return JeiBridge.fallback(method.getReturnType());
            }
        });
    }

    private static @Nullable Object uidContext(Method unique) {
        try {
            Class<?> type = unique.getParameterTypes()[1];
            return type.getField("Ingredient").get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object clickable(IIngredientType type, Object value, Object area) {
        Class<?> clickType = JeiBridge.ifaceClass("mezz.jei.api.runtime.IClickableIngredient");
        ITypedIngredient<?> typed = typed(type, value);
        return JeiBridge.proxy((Class) clickType, (self, method, args) -> switch (method.getName()) {
            case "getTypedIngredient" -> typed;
            case "getArea" -> area;
            case "getIngredientType" -> type;
            case "getIngredient" -> value;
            default -> JeiBridge.fluentOrDefault(self, method);
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object clickableFactory(Class<?> factoryType) {
        return JeiBridge.proxy((Class) factoryType, (self, method, args) -> {
            if (!method.getName().equals("createBuilder")) {
                return JeiBridge.fluentOrDefault(self, method);
            }
            Object value = args.length == 2 ? args[1] : args[0] instanceof ITypedIngredient<?> t ? t.getIngredient() : args[0];
            IIngredientType<?> type = args.length == 2 ? (IIngredientType<?>) args[0]
                    : args[0] instanceof ITypedIngredient<?> t ? t.getType() : mezz.jei.api.constants.VanillaTypes.ITEM_STACK;
            return JeiBridge.proxy((Class) method.getReturnType(), (inner, call, callArgs) -> {
                if (!call.getName().equals("buildWithArea")) {
                    return JeiBridge.fluentOrDefault(inner, call);
                }
                Object area = callArgs.length == 1 ? callArgs[0] : null;
                if (area == null) {
                    Class<?> rect = rectClass();
                    area = rect == null ? null
                            : JeiCards.newRect(rect, (int) callArgs[0], (int) callArgs[1], (int) callArgs[2], (int) callArgs[3]);
                }
                return Optional.of(clickable(type, value, area));
            });
        });
    }

    static @Nullable Class<?> rectClass() {
        Method area = JeiBridge.ifaceMethod("mezz.jei.api.runtime.IClickableIngredient", "getArea", 0);
        return area == null ? null : area.getReturnType();
    }

    // ---- aliases, mod names, extra ingredients, search --------------------------------------------

    /** A registration object for the lifecycle phases that widen the search; null for other types. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static @Nullable Object searchRegistration(Class<?> type, IJeiHelpers helpers) {
        switch (type.getSimpleName()) {
            case "IIngredientAliasRegistration":
                return JeiBridge.proxyFirst((Class) type, (self, method, args) -> {
                    if (!method.getName().startsWith("addAlias")) {
                        return method.isDefault() ? JeiBridge.DEFER : JeiBridge.fluentOrDefault(self, method);
                    }
                    List<String> texts = new ArrayList<>();
                    List<Object> subjects = new ArrayList<>();
                    for (Object arg : args) {
                        if (arg == null || arg instanceof IIngredientType<?>) {
                            continue;
                        }
                        if (arg instanceof String text) {
                            texts.add(text);
                        } else if (arg instanceof Collection<?> collection) {
                            for (Object element : collection) {
                                if (element instanceof String text) {
                                    texts.add(text);
                                } else {
                                    subjects.add(element);
                                }
                            }
                        } else {
                            subjects.add(arg);
                        }
                    }
                    for (Object subject : subjects) {
                        ItemStack stack = subject instanceof net.minecraft.world.level.material.Fluid fluid
                                ? JeiBridge.fluidBucket(fluid, 1000) : JeiBridge.item(subject);
                        if (stack.isEmpty()) {
                            continue;
                        }
                        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        for (String text : texts) {
                            JeiAliases.addItem(id, text);
                            JeiAliases.addItem(id, translate(text));
                        }
                    }
                    return null;
                });
            case "IModInfoRegistration":
                return JeiBridge.proxyFirst((Class) type, (self, method, args) -> {
                    if (!method.getName().equals("addModAliases")) {
                        return method.isDefault() ? JeiBridge.DEFER : JeiBridge.fluentOrDefault(self, method);
                    }
                    String mod = String.valueOf(args[0]);
                    for (int i = 1; i < args.length; i++) {
                        for (Object text : JeiBridge.values(args[i])) {
                            JeiAliases.addMod(mod, String.valueOf(text));
                            JeiAliases.addMod(mod, translate(String.valueOf(text)));
                        }
                    }
                    return null;
                });
            case "IExtraIngredientRegistration":
                return JeiBridge.proxyFirst((Class) type, (self, method, args) -> {
                    String name = method.getName();
                    if (name.equals("addExtraItemStacks") || name.equals("addExtraIngredients")) {
                        Object values = args[args.length - 1];
                        for (Object value : JeiBridge.values(values)) {
                            if (value instanceof ItemStack stack) {
                                JeiAliases.addExtra(stack);
                            }
                        }
                        return null;
                    }
                    return method.isDefault() ? JeiBridge.DEFER : JeiBridge.fluentOrDefault(self, method);
                });
            case "IAdvancedSearchRegistration":
                return JeiBridge.proxyFirst((Class) type, (self, method, args) -> {
                    if (method.getName().equals("replaceSearchStorage")) {
                        // BEI's search is its own; a plugin's storage is kept for plugins that ask the runtime for it.
                        synchronized (JeiRegistry.class) {
                            SEARCH_STORAGES.add(args[0]);
                        }
                        JeiAliases.setStorageFactory(args[0]);
                        return null;
                    }
                    if (method.getName().equals("getJeiHelpers")) {
                        return helpers;
                    }
                    return method.isDefault() ? JeiBridge.DEFER : JeiBridge.fluentOrDefault(self, method);
                });
            default:
                return null;
        }
    }

    private static String translate(String key) {
        try {
            String text = net.minecraft.client.resources.language.I18n.get(key);
            return text == null ? key : text;
        } catch (RuntimeException | LinkageError e) {
            return key;
        }
    }

    // ---- advanced registration ---------------------------------------------------------------------

    @SuppressWarnings({"rawtypes", "unchecked"})
    static IAdvancedRegistration advanced(IJeiHelpers helpers) {
        return JeiBridge.proxy(IAdvancedRegistration.class, (self, method, args) -> {
            switch (method.getName()) {
                case "getJeiHelpers":
                    return helpers;
                case "addRecipeManagerPlugin":
                    if (args[0] != null) {
                        synchronized (JeiRegistry.class) {
                            MANAGER_PLUGINS.add(args[0]);
                        }
                    }
                    return null;
                case "addTypedRecipeManagerPlugin":
                    if (args[0] instanceof IRecipeType<?> type && args[1] != null) {
                        synchronized (JeiRegistry.class) {
                            TYPED_PLUGINS.computeIfAbsent(type, key -> new ArrayList<>()).add(args[1]);
                        }
                    }
                    return null;
                case "addRecipeCategoryDecorator":
                    if (args[0] instanceof IRecipeType<?> type && args[1] != null) {
                        synchronized (JeiRegistry.class) {
                            DECORATORS.computeIfAbsent(type, key -> new ArrayList<>()).add(args[1]);
                        }
                    }
                    return null;
                case "addRecipeButtonFactory":
                    if (args[0] != null) {
                        synchronized (JeiRegistry.class) {
                            BUTTON_FACTORIES.add(args[0]);
                        }
                    }
                    return null;
                case "getRecipeManagerPluginHelper":
                    return JeiBridge.proxy((Class) method.getReturnType(), (inner, call, callArgs) -> {
                        if (call.getName().equals("isRecipeCatalyst") && callArgs[0] instanceof IRecipeType<?> type) {
                            ItemStack focus = JeiBridge.item(JeiCards.call(callArgs[1], "getTypedValue"));
                            return JeiBridge.isStation(type, focus);
                        }
                        return JeiBridge.fluentOrDefault(inner, call);
                    });
                default:
                    JeiBridge.unsupported(IAdvancedRegistration.class, method);
                    return JeiBridge.fluentOrDefault(self, method);
            }
        });
    }

    /** Asks the recipe manager plugins for every recipe they add to a category and returns the ones not yet known. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static List<Object> pluginRecipes(IRecipeType<?> type, IRecipeCategory<?> category, List<Object> known) {
        List<Object> found = new ArrayList<>();
        for (Object plugin : List.copyOf(MANAGER_PLUGINS)) {
            Method all = ifaceCall(plugin, "mezz.jei.api.recipe.advanced.IRecipeManagerPlugin", "getRecipes", 1);
            if (all != null) {
                collect(plugin, all, found, known, category);
            }
        }
        for (Object plugin : List.copyOf(TYPED_PLUGINS.getOrDefault(type, List.of()))) {
            Method all = ifaceCall(plugin, "mezz.jei.api.recipe.advanced.ISimpleRecipeManagerPlugin", "getAllRecipes", 0);
            if (all != null) {
                collect(plugin, all, found, known);
            }
        }
        return found;
    }

    private static @Nullable Method ifaceCall(Object target, String iface, String name, int parameters) {
        Method method = JeiBridge.ifaceMethod(iface, name, parameters);
        return method != null && method.getDeclaringClass().isInstance(target) ? method : null;
    }

    private static void collect(Object plugin, Method method, List<Object> found, List<Object> known, Object... args) {
        try {
            Object result = method.invoke(plugin, args);
            for (Object recipe : JeiBridge.values(result)) {
                if (recipe != null && !known.contains(recipe) && !found.contains(recipe)) {
                    found.add(recipe);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Bei.LOGGER.debug("A JEI recipe manager plugin failed", e);
        }
    }

    /** Recipes the plugins add for one focus, for a lookup that limits its focus. */
    static List<Object> focusedRecipes(IRecipeType<?> type, IRecipeCategory<?> category, Collection<?> focuses) {
        List<Object> found = new ArrayList<>();
        if (category == null) {
            return found;
        }
        for (Object focus : focuses) {
            for (Object plugin : List.copyOf(MANAGER_PLUGINS)) {
                Method focused = ifaceCall(plugin, "mezz.jei.api.recipe.advanced.IRecipeManagerPlugin", "getRecipes", 2);
                if (focused != null) {
                    collect(plugin, focused, found, List.of(), category, focus);
                }
            }
            Object typed = JeiCards.call(focus, "getTypedValue");
            Object role = JeiCards.call(focus, "getRole");
            for (Object plugin : List.copyOf(TYPED_PLUGINS.getOrDefault(type, List.of()))) {
                boolean output = role == RecipeIngredientRole.OUTPUT;
                Method handled = ifaceCall(plugin, "mezz.jei.api.recipe.advanced.ISimpleRecipeManagerPlugin",
                        output ? "isHandledOutput" : "isHandledInput", 1);
                Method recipes = ifaceCall(plugin, "mezz.jei.api.recipe.advanced.ISimpleRecipeManagerPlugin",
                        output ? "getRecipesForOutput" : "getRecipesForInput", 1);
                try {
                    if (handled != null && recipes != null && typed != null && Boolean.TRUE.equals(handled.invoke(plugin, typed))) {
                        collect(plugin, recipes, found, List.of(), typed);
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    Bei.LOGGER.debug("A JEI recipe manager plugin failed", e);
                }
            }
        }
        return found;
    }

    // ---- decorators --------------------------------------------------------------------------------

    static List<Object> decoratorsFor(IRecipeType<?> type) {
        synchronized (JeiRegistry.class) {
            return List.copyOf(DECORATORS.getOrDefault(type, List.of()));
        }
    }

    /** Lets the category's decorators draw over its card. */
    static void decoratePaint(IRecipeCategory<?> category, Object recipe, Object view, Object g, int mx, int my) {
        for (Object decorator : decoratorsFor(category.getRecipeType())) {
            Method draw = ifaceCall(decorator, "mezz.jei.api.recipe.category.extensions.IRecipeCategoryDecorator", "draw", 6);
            if (draw == null) {
                continue;
            }
            try {
                draw.invoke(decorator, recipe, category, view, g, (double) mx, (double) my);
            } catch (ReflectiveOperationException | RuntimeException e) {
                Bei.LOGGER.debug("A JEI category decorator failed to draw", e);
            }
        }
    }

    /** Lets decorators add to or change a card's tooltip lines. */
    @SuppressWarnings("unchecked")
    static List<Component> decorateTooltip(IRecipeCategory<?> category, Object recipe, Object view, int mx, int my, List<Component> lines) {
        List<Component> current = new ArrayList<>(lines);
        for (Object decorator : decoratorsFor(category.getRecipeType())) {
            String iface = "mezz.jei.api.recipe.category.extensions.IRecipeCategoryDecorator";
            try {
                Method existing = ifaceCall(decorator, iface, "decorateExistingTooltips", 6);
                if (existing != null) {
                    Object result = existing.invoke(decorator, current, recipe, category, view, (double) mx, (double) my);
                    if (result instanceof List<?> list && list != current) {
                        current = new ArrayList<>();
                        for (Object line : list) {
                            if (line instanceof Component component) {
                                current.add(component);
                            }
                        }
                    }
                }
                Method add = ifaceCall(decorator, iface, "decorateTooltips", 6);
                if (add != null) {
                    List<Component> added = new ArrayList<>();
                    Object builder = JeiCards.tooltipBuilder(add.getParameterTypes()[0], added);
                    add.invoke(decorator, builder, recipe, category, view, (double) mx, (double) my);
                    current.addAll(added);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                Bei.LOGGER.debug("A JEI category decorator failed on a tooltip", e);
            }
        }
        return current;
    }

    // ---- recipe layout drawables, ingredient suppliers, button factories ---------------------------

    /** createRecipeSlotDrawable(role, ingredients or acceptor consumer, focused indexes, index[, width, height]). */
    @SuppressWarnings("unchecked")
    static Object slotDrawable(Class<?> type, Object[] args) {
        RecipeIngredientRole role = (RecipeIngredientRole) args[0];
        List<ItemStack> shown = new ArrayList<>();
        if (args.length > 1 && args[1] instanceof java.util.function.Consumer<?> consumer) {
            JeiBridge.LayoutSlot slot = new JeiBridge.LayoutSlot(role);
            ((java.util.function.Consumer<Object>) consumer).accept(slot.proxy());
            shown.addAll(slot.items());
        } else if (args.length > 1) {
            for (Object option : JeiBridge.values(args[1])) {
                ItemStack stack = option instanceof Optional<?> optional ? JeiBridge.item(optional.orElse(null)) : JeiBridge.item(option);
                if (!stack.isEmpty()) {
                    shown.add(stack);
                }
            }
        }
        return JeiCards.slotDrawable(type, new JeiViews.SlotSpec(role, shown, null), 0, 0, new ArrayList<>());
    }

    /** What getRecipeIngredients answers: the card's ingredients by role. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static Object ingredientSupplier(Class<?> type, JeiBridge.Bridged bridged, @Nullable Set<?> roles) {
        return JeiBridge.proxy((Class) type, (self, method, args) -> {
            if (method.getName().equals("getIngredients") && args.length == 1) {
                List<ITypedIngredient<?>> out = new ArrayList<>();
                if (roles != null && !roles.contains(args[0])) {
                    return out;
                }
                for (JeiViews.SlotSpec slot : bridged.slots()) {
                    if (slot.role() == args[0]) {
                        for (ItemStack stack : slot.items()) {
                            if (!stack.isEmpty()) {
                                out.add(JeiViews.typed(stack));
                            }
                        }
                    }
                }
                return out;
            }
            if (method.getName().equals("getIngredientStream") || method.getName().equals("getIngredientTypes")) {
                return java.util.stream.Stream.empty();
            }
            return JeiBridge.fluentOrDefault(self, method);
        });
    }

    private static int drawables;

    /** A recipe layout drawable for a bridged card: JEI's way of drawing one recipe wherever the plugin wants. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static Object layoutDrawable(Class<?> type, IRecipeCategory<?> category, JeiBridge.Bridged bridged) {
        int[] at = {0, 0};
        int width = Math.max(18, category.getWidth());
        int height = Math.max(18, category.getHeight());
        Class<?> rect = JeiBridge.ifaceMethod("mezz.jei.api.gui.IRecipeLayoutDrawable", "getRect", 0).getReturnType();
        return JeiBridge.proxyFirst((Class) type, (self, method, args) -> {
            switch (method.getName()) {
                case "setPosition":
                    at[0] = (int) args[0];
                    at[1] = (int) args[1];
                    return null;
                case "drawRecipe":
                    JeiDrawables.translated(args[0], at[0], at[1], () -> {
                        for (JeiBridge.LayoutSlot slot : bridged.layoutSlots()) {
                            JeiDrawables.drawSlotBackground(args[0], slot.x(), slot.y());
                        }
                        JeiBridge.paintCore(category, bridged, args[0], (int) args[1] - at[0], (int) args[2] - at[1]);
                        for (JeiBridge.LayoutSlot slot : bridged.layoutSlots()) {
                            if (!slot.items().isEmpty()) {
                                JeiDrawables.item(args[0], slot.items().get((int) (System.currentTimeMillis() / 1000 % slot.items().size())),
                                        slot.x() + 1, slot.y() + 1);
                            }
                        }
                    });
                    return null;
                case "drawOverlays":
                    List<Component> tips = JeiBridge.tooltipCore(category, bridged, (int) args[1] - at[0], (int) args[2] - at[1]);
                    JeiDrawables.translated(args[0], at[0], at[1], () -> {
                        for (JeiBridge.Drawer overlay : bridged.overlays()) {
                            try {
                                overlay.draw(args[0], (int) args[1] - at[0], (int) args[2] - at[1]);
                            } catch (ReflectiveOperationException | RuntimeException e) {
                                // A broken overlay is skipped.
                            }
                        }
                    });
                    JeiBridge.LayoutSlot hovered = slotAt(bridged, (int) args[1] - at[0], (int) args[2] - at[1]);
                    if (hovered != null && !hovered.items().isEmpty()) {
                        ItemStack stack = hovered.items().get(0);
                        List<Component> lines = new ArrayList<>();
                        lines.add(stack.getHoverName());
                        List<Component> extra = JeiBridge.slotTooltipCore(bridged, stack);
                        if (extra != null) {
                            lines.addAll(extra);
                        }
                        JeiDrawables.tooltip(args[0], lines, (int) args[1], (int) args[2]);
                    } else if (tips != null && (int) args[1] >= at[0] && (int) args[1] < at[0] + width && (int) args[2] >= at[1] && (int) args[2] < at[1] + height) {
                        JeiDrawables.tooltip(args[0], tips, (int) args[1], (int) args[2]);
                    }
                    return null;
                case "isMouseOver":
                    return ((Number) args[0]).doubleValue() >= at[0] && ((Number) args[0]).doubleValue() < at[0] + width
                            && ((Number) args[1]).doubleValue() >= at[1] && ((Number) args[1]).doubleValue() < at[1] + height;
                case "getRect":
                case "getRectWithBorder":
                    return JeiCards.newRect(rect, at[0], at[1], width, height);
                case "getSideButtonArea":
                    return JeiCards.newRect(rect, at[0] + width + 2, at[1] + 13 * (int) args[0], 12, 12);
                case "getRecipeSlotsView":
                    return JeiViews.slots(bridged.slots());
                case "getRecipeCategory":
                    return category;
                case "getRecipe":
                    return bridged.recipe();
                case "getSourceRecipeIds":
                    return List.of();
                case "tick":
                    return null;
                case "getRecipeSlotUnderMouse": {
                    JeiBridge.LayoutSlot slot = slotAt(bridged, ((Number) args[0]).doubleValue() - at[0], ((Number) args[1]).doubleValue() - at[1]);
                    if (slot == null) {
                        return Optional.empty();
                    }
                    Class<?> drawable = JeiBridge.ifaceClass("mezz.jei.api.gui.ingredient.IRecipeSlotDrawable");
                    return Optional.of(JeiCards.slotDrawable(drawable, new JeiViews.SlotSpec(slot.role(), List.copyOf(slot.items()), slot.name()),
                            at[0] + slot.x(), at[1] + slot.y(), new ArrayList<>()));
                }
                case "getIngredientUnderMouse": {
                    JeiBridge.LayoutSlot slot = slotAt(bridged, ((Number) args[0]).doubleValue() - at[0], ((Number) args[1]).doubleValue() - at[1]);
                    if (slot == null || slot.items().isEmpty() || args[2] != mezz.jei.api.constants.VanillaTypes.ITEM_STACK) {
                        return Optional.empty();
                    }
                    return Optional.of(slot.items().get(0));
                }
                case "getInputHandler":
                    return inputHandler(method.getReturnType(), bridged, at, width, height, rect);
                default:
                    return method.isDefault() ? JeiBridge.DEFER : JeiBridge.fluentOrDefault(self, method);
            }
        });
    }

    private static JeiBridge.@Nullable LayoutSlot slotAt(JeiBridge.Bridged bridged, double x, double y) {
        for (JeiBridge.LayoutSlot slot : bridged.layoutSlots()) {
            if (x >= slot.x() && x < slot.x() + 18 && y >= slot.y() && y < slot.y() + 18) {
                return slot;
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object inputHandler(Class<?> type, JeiBridge.Bridged bridged, int[] at, int width, int height, Class<?> rectType) {
        Method areaMethod = JeiBridge.ifaceMethod("mezz.jei.api.gui.inputs.IJeiInputHandler", "getArea", 0);
        Class<?> areaType = areaMethod == null ? rectType : areaMethod.getReturnType();
        return JeiBridge.proxyFirst((Class) type, (self, method, args) -> switch (method.getName()) {
            case "getArea" -> JeiCards.newRect(areaType, at[0], at[1], width, height);
            case "handleInput" -> {
                int button = 0;
                Object key = JeiCards.call(args[2], "getKey");
                Object value = key == null ? null : JeiCards.call(key, "getValue");
                if (value instanceof Integer number) {
                    button = number;
                }
                boolean used = false;
                for (JeiBridge.Drawer part : bridged.extras()) {
                    used |= part.click((int) ((Number) args[0]).doubleValue() - at[0], (int) ((Number) args[1]).doubleValue() - at[1], button);
                }
                yield used;
            }
            case "handleMouseScrolled" -> {
                boolean used = false;
                for (JeiBridge.Drawer part : bridged.extras()) {
                    used |= part.scroll((int) ((Number) args[0]).doubleValue() - at[0], (int) ((Number) args[1]).doubleValue() - at[1],
                            ((Number) args[3]).doubleValue());
                }
                yield used;
            }
            default -> method.isDefault() ? JeiBridge.DEFER : JeiBridge.fallback(method.getReturnType());
        });
    }

    /**
     * Adds the buttons plugins registered a factory for to a bridged card: a small icon button at the card's right edge
     * that runs the plugin's controller when pressed.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void addButtons(IRecipeCategory<?> category, JeiBridge.Bridged bridged) {
        List<Object> factories;
        synchronized (JeiRegistry.class) {
            factories = List.copyOf(BUTTON_FACTORIES);
        }
        if (factories.isEmpty()) {
            return;
        }
        Class<?> layoutType = JeiBridge.ifaceClass("mezz.jei.api.gui.IRecipeLayoutDrawable");
        if (layoutType == null) {
            return;
        }
        int index = 0;
        for (Object factory : factories) {
            Method create = JeiBridge.ifaceMethod("mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory", "createButtonController", 1);
            if (create == null || !create.getDeclaringClass().isInstance(factory)) {
                continue;
            }
            try {
                Object controller = create.invoke(factory, layoutDrawable(layoutType, category, bridged));
                if (controller != null) {
                    bridged.extras().add(new Button(controller, Math.max(18, category.getWidth()) - 13 - 13 * index++, 1));
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                Bei.LOGGER.debug("A JEI recipe button factory failed", e);
            }
        }
    }

    /** One plugin button on a card. */
    private static final class Button implements JeiBridge.Drawer {
        private final Object controller;
        private final int x;
        private final int y;
        private final boolean[] state = {true, true, false};
        private Object icon;

        Button(Object controller, int x, int y) {
            this.controller = controller;
            this.x = x;
            this.y = y;
            Object buttonState = stateProxy();
            if (buttonState != null) {
                JeiCards.call(controller, "initState", buttonState);
                JeiCards.call(controller, "updateState", buttonState);
            }
        }

        private @Nullable Object stateProxy() {
            Class<?> type = JeiBridge.ifaceClass("mezz.jei.api.gui.buttons.IButtonState");
            if (type == null) {
                return null;
            }
            return JeiBridge.proxy(type, (self, method, args) -> {
                switch (method.getName()) {
                    case "setIcon" -> icon = args[0];
                    case "setActive" -> state[0] = (boolean) args[0];
                    case "setVisible" -> state[1] = (boolean) args[0];
                    case "setForcePressed" -> state[2] = (boolean) args[0];
                    default -> {
                    }
                }
                return null;
            });
        }

        @Override
        public void draw(Object g, int mx, int my) throws ReflectiveOperationException {
            if (!state[1]) {
                return;
            }
            JeiDrawables.button(g, x, y, 12, 12, state[0], state[2] || mx >= x && mx < x + 12 && my >= y && my < y + 12);
            if (icon != null) {
                JeiBridge.drawAt(icon, g, x + 6 - Math.max(1, size(icon, "getWidth")) / 2, y + 6 - Math.max(1, size(icon, "getHeight")) / 2);
            }
        }

        private static int size(Object drawable, String method) {
            return JeiCards.call(drawable, method) instanceof Integer value ? value : 0;
        }

        @Override
        public boolean click(int mx, int my, int button) {
            if (!state[1] || !state[0] || mx < x || mx >= x + 12 || my < y || my >= y + 12) {
                return false;
            }
            Method press = JeiBridge.ifaceMethod("mezz.jei.api.gui.buttons.IIconButtonController", "onPress", 1);
            if (press == null) {
                return false;
            }
            try {
                Object input = JeiCards.userInput(press.getParameterTypes()[0], button);
                return Boolean.TRUE.equals(press.invoke(controller, input));
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }

        @Override
        public @Nullable List<Component> tooltip(int mx, int my) {
            if (!state[1] || mx < x || mx >= x + 12 || my < y || my >= y + 12) {
                return null;
            }
            Method tips = JeiBridge.ifaceMethod("mezz.jei.api.gui.buttons.IIconButtonController", "getTooltips", 1);
            if (tips == null) {
                return null;
            }
            List<Component> lines = new ArrayList<>();
            try {
                tips.invoke(controller, JeiCards.tooltipBuilder(tips.getParameterTypes()[0], lines));
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
            return lines.isEmpty() ? null : lines;
        }
    }

    // ---- key mappings and the config manager -------------------------------------------------------

    /** BEI's own key binding behind a JEI key name, or null for the bookmark key (a fixed A) and where BEI's keys are absent. */
    private static @Nullable Object beiKey(String jeiName) {
        String field = switch (jeiName) {
            case "getShowRecipe" -> "SHOW_RECIPES";
            case "getShowUses" -> "SHOW_USES";
            default -> null;
        };
        if (field == null) {
            return null;
        }
        try {
            return Class.forName("com.itmecasey.bei.client.ClientInit", false, JeiRegistry.class.getClassLoader()).getField(field).get(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** JEI's key mappings: BEI's own keys under JEI's names. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static Object keyMappings(Class<?> type) {
        return JeiBridge.proxy((Class) type, (self, method, args) -> {
            String label = switch (method.getName()) {
                case "getShowRecipe" -> "R";
                case "getShowUses" -> "U";
                case "getBookmark" -> "A";
                default -> null;
            };
            if (label == null) {
                return JeiBridge.fluentOrDefault(self, method);
            }
            int code = label.charAt(0);
            Object mapping = beiKey(method.getName());
            return JeiBridge.proxy((Class) method.getReturnType(), (inner, call, callArgs) -> switch (call.getName()) {
                case "isActiveAndMatches" -> {
                    Object value = JeiCards.call(callArgs[0], "getValue");
                    Object bound = mapping == null ? null : JeiCards.call(JeiCards.call(mapping, "getKey"), "getValue");
                    yield value instanceof Integer number && (bound instanceof Integer b ? number.equals(b) : number == code);
                }
                case "isUnbound" -> mapping != null && Boolean.TRUE.equals(JeiCards.call(mapping, "isUnbound"));
                case "getTranslatedKeyMessage" -> {
                    Object message = mapping == null ? null : JeiCards.call(mapping, "getTranslatedKeyMessage");
                    yield message instanceof Component c ? c : Component.literal(label);
                }
                default -> JeiBridge.fallback(call.getReturnType());
            });
        });
    }

    /** JEI's config manager: BEI keeps its own settings, so there are no JEI config files to list. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static Object configManager(Class<?> type) {
        return JeiBridge.proxy((Class) type, (self, method, args) -> switch (method.getName()) {
            case "getConfigFiles" -> List.of();
            default -> JeiBridge.fluentOrDefault(self, method);
        });
    }
}
