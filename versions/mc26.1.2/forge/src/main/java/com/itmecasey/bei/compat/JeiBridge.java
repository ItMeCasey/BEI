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
import java.util.Set;
import java.util.HashSet;
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
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.IRecipeCategoriesLookup;
import mezz.jei.api.recipe.IRecipeCatalystLookup;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.IRuntimeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import com.itmecasey.bei.api.CardPainter;
import java.util.concurrent.ConcurrentHashMap;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.objectweb.asm.Type;

/**
 * Runs the recipe-facing part of existing JEI plugins and translates it to BEI stations. This is a
 * deliberately small JEI runtime: calls unrelated to recipe discovery return harmless empty values,
 * and a plugin that needs more of JEI is logged and skipped without affecting other plugins.
 */
public final class JeiBridge {
    private static final String ANNOTATION = Type.getDescriptor(JeiPlugin.class);
    private static final Map<IRecipeType<?>, IRecipeCategory<?>> CATEGORIES = new LinkedHashMap<>();
    private static final Map<IRecipeType<?>, List<Object>> RECIPES = new LinkedHashMap<>();
    private static final Map<IRecipeType<?>, ItemStack> STATIONS = new LinkedHashMap<>();
    private static final Map<IDrawable, ItemStack> ICONS = new IdentityHashMap<>();
    private static List<IModPlugin> plugins = List.of();
    private static boolean loaded;
    /** The BEI recipes made from JEI recipes, by id: what a category's draw call and a transfer handler work on. */
    record Bridged(String typeUid, IRecipeCategory<?> category, Object recipe, List<JeiViews.SlotSpec> slots, List<Drawer> extras,
            List<Drawer> overlays, List<LayoutSlot> layoutSlots, int[] buttonAt) {
        Bridged(String typeUid, IRecipeCategory<?> category, Object recipe, List<JeiViews.SlotSpec> slots, List<Drawer> extras) {
            this(typeUid, category, recipe, slots, extras, new ArrayList<>(), new ArrayList<>(), new int[] {-1, -1});
        }
    }

    /** Something a category's createRecipeExtras added; draws itself on the card. */
    @FunctionalInterface
    interface Drawer {
        void draw(Object graphics, int mouseX, int mouseY) throws ReflectiveOperationException;

        /** A mouse button went down at card coordinates; true when this part used the click. */
        default boolean click(int mouseX, int mouseY, int button) {
            return false;
        }

        default boolean release(int mouseX, int mouseY, int button) {
            return false;
        }

        default boolean drag(int mouseX, int mouseY, int button, double dragX, double dragY) {
            return false;
        }

        default boolean scroll(int mouseX, int mouseY, double delta) {
            return false;
        }

        /** Tooltip lines when the mouse is over this part, or null. */
        default @org.jetbrains.annotations.Nullable List<Component> tooltip(int mouseX, int mouseY) {
            return null;
        }
    }

    private static final Map<String, Bridged> BRIDGED = new LinkedHashMap<>();
    /** A plugin's handler for one screen class: extra areas to keep clear, or ghost-ingredient drop targets. */
    private record GuiHandler(Class<?> screenClass, Object handler) {
    }

    private static final List<GuiHandler> CONTAINER_HANDLERS = new ArrayList<>();
    private static final List<Object> GLOBAL_HANDLERS = new ArrayList<>();
    private static final List<GuiHandler> GHOST_HANDLERS = new ArrayList<>();
    /** A rectangle on a machine screen that opens the recipes of some categories when clicked. */
    private record ClickArea(Class<?> screenClass, int x, int y, int width, int height, List<String> typeUids) {
    }

    private static final List<ClickArea> CLICK_AREAS = new ArrayList<>();
    private static final Map<IRecipeType<?>, List<Object>> HIDDEN_RECIPES = new LinkedHashMap<>();
    private static final Map<IIngredientType<?>, Object> HELPERS = new LinkedHashMap<>();
    private record Info(List<ItemStack> items, List<String> lines) {
    }

    private static final List<Info> INFO = new ArrayList<>();
    /** A plugin's extension for how one vanilla recipe class is shown (crafting, cooking, smithing, brewing). */
    private record Extension(String kind, Class<?> recipeClass, Object extension) {
    }

    private static final List<Extension> EXTENSIONS = new ArrayList<>();
    private static final java.util.Set<String> UNSUPPORTED = ConcurrentHashMap.newKeySet();

    static synchronized Bridged bridged(String id) {
        return BRIDGED.get(id);
    }

    /** A copy of the bridged recipes (development checks). */
    static synchronized List<Bridged> allBridged() {
        return new ArrayList<>(BRIDGED.values());
    }

    static @org.jetbrains.annotations.Nullable Class<?> ifaceClass(String name) {
        try {
            return Class.forName(name, false, JeiBridge.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /** Whether the stack is the crafting station a plugin registered for a recipe type. */
    static synchronized boolean isStation(IRecipeType<?> type, ItemStack stack) {
        ItemStack station = STATIONS.get(type);
        return station != null && !stack.isEmpty() && ItemStack.isSameItem(station, stack);
    }

    /** Set once the recipes have been handed to BEI's index; recipes plugins add after that go in one by one. */
    private static boolean contributed;

    private static final java.util.Map<String, java.lang.reflect.Method> IFACE_METHODS = new ConcurrentHashMap<>();

    /** A method of a JEI interface by name and parameter count, so it is called through the public interface. */
    static @org.jetbrains.annotations.Nullable Method ifaceMethod(String type, String name, int parameters) {
        return IFACE_METHODS.computeIfAbsent(type + "#" + name + "#" + parameters, key -> {
            try {
                return JeiTransfers.find(Class.forName(type, false, JeiBridge.class.getClassLoader()), name, parameters);
            } catch (ClassNotFoundException | LinkageError e) {
                return null;
            }
        });
    }

    /** x, y, width, height of a Rect2i-like object, whichever accessor names this version has. */
    private static int @org.jetbrains.annotations.Nullable [] rectOf(Object rect) {
        if (rect == null) {
            return null;
        }
        String[][] names = {{"getX", "getY", "getWidth", "getHeight"}, {"x", "y", "width", "height"}};
        for (String[] set : names) {
            try {
                int[] out = new int[4];
                for (int i = 0; i < 4; i++) {
                    out[i] = ((Number) rect.getClass().getMethod(set[i]).invoke(rect)).intValue();
                }
                return out;
            } catch (ReflectiveOperationException | RuntimeException e) {
                // Try the other accessor names.
            }
        }
        return null;
    }

    /** The area objects plugins ask to keep clear on this screen (JEI exclusion zones), as the plugins returned them. */
    static synchronized List<Object> extraAreaObjects(Object screen) {
        List<Object> out = new ArrayList<>();
        Method container = ifaceMethod("mezz.jei.api.gui.handlers.IGuiContainerHandler", "getGuiExtraAreas", 1);
        for (GuiHandler handler : CONTAINER_HANDLERS) {
            if (container != null && handler.screenClass().isInstance(screen)) {
                try {
                    if (container.invoke(handler.handler(), screen) instanceof java.util.Collection<?> areas) {
                        out.addAll(areas);
                    }
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    Bei.LOGGER.debug("A JEI exclusion handler failed", e);
                }
            }
        }
        Method global = ifaceMethod("mezz.jei.api.gui.handlers.IGlobalGuiHandler", "getGuiExtraAreas", 0);
        for (Object handler : GLOBAL_HANDLERS) {
            if (global != null) {
                try {
                    if (global.invoke(handler) instanceof java.util.Collection<?> areas) {
                        out.addAll(areas);
                    }
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    Bei.LOGGER.debug("A JEI global exclusion handler failed", e);
                }
            }
        }
        return out;
    }

    /**
     * Where BEI's browser panel may start: the right edge of the screen's own area, pushed right by any area a plugin
     * says to keep clear (a side tab or machine panel) that sticks out to the right of it.
     */
    public static int extraRight(Object screen, int guiRight) {
        int right = guiRight;
        for (Object area : extraAreaObjects(screen)) {
            int[] rect = rectOf(area);
            if (rect != null && rect[0] >= guiRight - 4) {
                right = Math.max(right, rect[0] + rect[2]);
            }
        }
        return right;
    }

    /** The ghost-ingredient handlers registered for this screen. */
    static synchronized List<Object> ghostHandlers(Object screen) {
        List<Object> out = new ArrayList<>();
        for (GuiHandler handler : GHOST_HANDLERS) {
            if (handler.screenClass().isInstance(screen)) {
                out.add(handler.handler());
            }
        }
        return out;
    }

    /**
     * A click on a machine screen: if it lands on a recipe click area a plugin registered (the progress arrow of a
     * machine, say), opens that category's recipes. Returns whether the click was used.
     */
    public static boolean clickAreas(Object screen, int guiLeft, int guiTop, double mouseX, double mouseY) {
        double relX = mouseX - guiLeft;
        double relY = mouseY - guiTop;
        synchronized (JeiBridge.class) {
            for (ClickArea area : CLICK_AREAS) {
                if (area.screenClass().isInstance(screen) && relX >= area.x() && relY >= area.y()
                        && relX < area.x() + area.width() && relY < area.y() + area.height() && !area.typeUids().isEmpty()) {
                    com.itmecasey.bei.client.ViewerFacade.showCategories(area.typeUids());
                    return true;
                }
            }
        }
        Method areas = ifaceMethod("mezz.jei.api.gui.handlers.IGuiContainerHandler", "getGuiClickableAreas", 3);
        Method areaRect = ifaceMethod("mezz.jei.api.gui.handlers.IGuiClickableArea", "getArea", 0);
        Method click = ifaceMethod("mezz.jei.api.gui.handlers.IGuiClickableArea", "onClick", 2);
        if (areas == null || areaRect == null || click == null) {
            return false;
        }
        List<GuiHandler> handlers;
        synchronized (JeiBridge.class) {
            handlers = List.copyOf(CONTAINER_HANDLERS);
        }
        for (GuiHandler handler : handlers) {
            if (!handler.screenClass().isInstance(screen)) {
                continue;
            }
            try {
                if (!(areas.invoke(handler.handler(), screen, relX, relY) instanceof java.util.Collection<?> found)) {
                    continue;
                }
                for (Object clickable : found) {
                    int[] rect = rectOf(areaRect.invoke(clickable));
                    if (rect == null || relX < rect[0] || relY < rect[1] || relX >= rect[0] + rect[2] || relY >= rect[1] + rect[3]) {
                        continue;
                    }
                    Object focusFactory = proxy(Class.forName("mezz.jei.api.recipe.IFocusFactory", false, JeiBridge.class.getClassLoader()),
                            (self, method, callArgs) -> fluentOrDefault(self, method));
                    Object gui = JeiScreens.adapter(Class.forName("mezz.jei.api.runtime.IRecipesGui", false, JeiBridge.class.getClassLoader()), null);
                    click.invoke(clickable, focusFactory, gui);
                    return true;
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                Bei.LOGGER.debug("A JEI clickable area failed", e);
            }
        }
        return false;
    }

    /** Where this item may be dropped on this screen according to the plugins' ghost-ingredient handlers. */
    public static List<GhostTarget> ghostTargets(Object screen, ItemStack stack) {
        List<GhostTarget> out = new ArrayList<>();
        Method targets = ifaceMethod("mezz.jei.api.gui.handlers.IGhostIngredientHandler", "getTargetsTyped", 3);
        Method complete = ifaceMethod("mezz.jei.api.gui.handlers.IGhostIngredientHandler", "onComplete", 0);
        Method area = ifaceMethod("mezz.jei.api.gui.handlers.IGhostIngredientHandler$Target", "getArea", 0);
        Method accept = ifaceMethod("mezz.jei.api.gui.handlers.IGhostIngredientHandler$Target", "accept", 1);
        if (targets == null || area == null || accept == null) {
            return out;
        }
        for (Object handler : ghostHandlers(screen)) {
            try {
                Object list = targets.invoke(handler, screen, JeiViews.typed(stack.copy()), true);
                if (!(list instanceof List<?> found)) {
                    continue;
                }
                for (Object target : found) {
                    int[] rect = rectOf(area.invoke(target));
                    if (rect == null) {
                        continue;
                    }
                    out.add(new GhostTarget(rect[0], rect[1], rect[2], rect[3], dropped -> {
                        try {
                            accept.invoke(target, dropped);
                        } catch (ReflectiveOperationException | RuntimeException e) {
                            Bei.LOGGER.debug("A JEI ghost target refused a drop", e);
                        }
                    }, () -> {
                        try {
                            if (complete != null) {
                                complete.invoke(handler);
                            }
                        } catch (ReflectiveOperationException | RuntimeException e) {
                            Bei.LOGGER.debug("A JEI ghost handler failed to finish", e);
                        }
                    }));
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                Bei.LOGGER.debug("A JEI ghost-ingredient handler failed", e);
            }
        }
        return out;
    }

    /** Registers a recipe click area by hand (unit tests). */
    static synchronized void putClickArea(Class<?> screenClass, int x, int y, int width, int height, List<String> typeUids) {
        CLICK_AREAS.add(new ClickArea(screenClass, x, y, width, height, typeUids));
    }

    /** Registers a gui or ghost-ingredient handler by hand (unit tests). */
    static synchronized void putGuiHandler(boolean ghost, Class<?> screenClass, Object handler) {
        (ghost ? GHOST_HANDLERS : CONTAINER_HANDLERS).add(new GuiHandler(screenClass, handler));
    }

    /** Registers a vanilla category extension by hand (unit tests). */
    static synchronized void putExtension(String kind, Class<?> recipeClass, Object extension) {
        EXTENSIONS.add(new Extension(kind, recipeClass, extension));
    }

    // ---- development checks (environment BEI_JEI_SELFTEST) -----------------------------------------

    private static boolean devStarted;
    private static IRecipeRegistration lastRecipeRegistration;

    /** The recipe registration plugins got, for development checks. */
    static IRecipeRegistration recipeRegistration() {
        return lastRecipeRegistration;
    }

    /** Puts the information page a plugin just registered into the recipe index (development checks). */
    static synchronized void devIndexInfo() {
        if (INFO.isEmpty()) {
            return;
        }
        Info info = INFO.get(INFO.size() - 1);
        com.itmecasey.bei.index.RecipeIndex.addRecipes(new ArrayList<>(ModJsCompat.infoPages("jei/info/dev" + INFO.size(), info.items(), info.lines())));
    }

    /** A stand-in JEI transfer handler: records that the adapter handed it a recipe object. */
    static final class DevHandler {
        private final Class<?> menu;
        final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        volatile String lastRecipeClass = "";

        DevHandler(Class<?> menu) {
            this.menu = menu;
        }

        public Class<?> getContainerClass() {
            return menu;
        }

        public Object transferRecipe(Object container, Object recipe, Object slots, Object player, boolean maxTransfer, boolean doTransfer) {
            calls.incrementAndGet();
            lastRecipeClass = recipe == null ? "null" : recipe.getClass().getName();
            Bei.LOGGER.info("BEI-CHECK dev transfer handler called: recipe object {} doTransfer={}", lastRecipeClass, doTransfer);
            return null;
        }
    }

    /** Registers a dev handler for a menu class (crafting recipes) and returns it. */
    static DevHandler devHandler(Class<?> menuClass) {
        DevHandler handler = new DevHandler(menuClass);
        JeiTransfers.add(handler, "minecraft:crafting");
        JeiTransfers.install();
        return handler;
    }

    /** Starts the development check once the world is joined, when BEI_JEI_SELFTEST is set. */
    private static void startDevCheck() {
        if (devStarted || System.getenv("BEI_JEI_SELFTEST") == null) {
            return;
        }
        devStarted = true;
        Thread thread = new Thread(() -> {
            for (int i = 0; i < 120; i++) {
                net.minecraft.client.Minecraft game = net.minecraft.client.Minecraft.getInstance();
                if (game != null && game.player != null && game.level != null) {
                    try {
                        Thread.sleep(8000);
                    } catch (InterruptedException e) {
                        return;
                    }
                    game.execute(JeiBridge::devCheck);
                    return;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "BEI-dev-check");
        thread.setDaemon(true);
        thread.start();
    }

    /** Runs a dev transfer handler and a dev category extension through the real adapters and logs what they saw. */
    static void devCheck() {
        try {
            net.minecraft.client.Minecraft game = net.minecraft.client.Minecraft.getInstance();
            var menu = game.player.inventoryMenu;
            DevHandler handler = devHandler(menu.getClass());
            BeiRecipe recipe = new BeiRecipe("minecraft:stick", "ShapedCrafting", 1,
                    List.of(List.of(new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS)), List.of(new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS))),
                    List.of(new ItemStack(net.minecraft.world.item.Items.STICK, 4)), List.of());
            var view = com.itmecasey.bei.plugin.TransferHost.view(menu, recipe, false, false, false);
            boolean done = com.itmecasey.bei.plugin.TransferHost.transfer(menu, recipe, false, false);
            Bei.LOGGER.info("BEI-CHECK dev transfer: button mode {} transfer={} handlerCalls={} recipe object {}", view.mode(), done,
                    handler.calls.get(), handler.lastRecipeClass);
            Object extension = proxyFirst(ifaceClass("mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension"), (self, method, args) -> {
                if (method.getName().equals("setRecipe") && method.getParameterCount() >= 3) {
                    Object builder = null;
                    for (Object arg : args) {
                        if (arg != null && JeiTransfers.find(arg.getClass(), "addSlot", 3) != null) {
                            builder = arg;
                        }
                    }
                    Bei.LOGGER.info("BEI-CHECK dev extension setRecipe with {} arguments, builder {}", method.getParameterCount(), builder != null);
                    // Reflective calls: a direct call into the JEI interfaces would trip Forge's class loader constraints here.
                    devAdd(JeiCards.call(builder, "addSlot", RecipeIngredientRole.INPUT, 0, 0), new ItemStack(net.minecraft.world.item.Items.COAL));
                    devAdd(JeiCards.call(builder, "addSlot", RecipeIngredientRole.OUTPUT, 40, 0), new ItemStack(net.minecraft.world.item.Items.DIAMOND));
                    return null;
                }
                return method.isDefault() ? DEFER : fallback(method.getReturnType());
            });
            putExtension("crafting", net.minecraft.world.item.crafting.ShapedRecipe.class, extension);
            BeiRecipe changed = extendedById("minecraft:stick", recipe);
            Bei.LOGGER.info("BEI-CHECK server holder: {} extension: {}", serverHolderNote, extendedNote);
            Bei.LOGGER.info("BEI-CHECK dev category extension on the game recipe: {}", changed == null ? "not applied"
                    : "applied, output " + changed.outputs().get(0).getItem() + " input " + changed.inputs().get(0).get(0).getItem());
        } catch (RuntimeException | LinkageError e) {
            Bei.LOGGER.warn("BEI-CHECK dev check failed", e);
        }
    }

    /** Adds a stack to a slot builder by reflection, with whichever method this JEI API version has (addItemStack, or add on 26.x). */
    private static void devAdd(Object slot, ItemStack stack) {
        if (slot == null) {
            Bei.LOGGER.info("BEI-CHECK dev extension got no slot builder");
            return;
        }
        for (String name : new String[] {"addItemStack", "add"}) {
            for (java.lang.reflect.Method method : slot.getClass().getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 1) {
                    try {
                        method.invoke(slot, stack);
                        return;
                    } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError e) {
                        Bei.LOGGER.info("BEI-CHECK dev extension {}({}) failed: {} (stack loader {}, parameter loader {})", name, method.getParameterTypes()[0].getName(), e.toString(),
                                stack.getClass().getClassLoader(), method.getParameterTypes()[0].getClassLoader());
                    }
                }
            }
        }
        Bei.LOGGER.info("BEI-CHECK dev extension found no way to add a stack to {}", slot.getClass().getName());
    }

    /** Marks recipes as handed to the index, so later additions go in one by one (unit tests). */
    static synchronized void putContributed(boolean value) {
        contributed = value;
    }

    /** Registers a bridged recipe by hand (unit tests). */
    static synchronized void putBridged(String id, Bridged bridged) {
        BRIDGED.put(id, bridged);
    }

    /** Logs once that a JEI call has no BEI equivalent yet; the call is answered with an empty value. */
    /** Calls BEI accepts and needs nothing from: they describe JEI's own screens, or what BEI already does itself. */
    private static final java.util.Set<String> ACCEPTED = java.util.Set.of(
            "ISubtypeRegistration.registerSubtypeInterpreter", "IGuiHandlerRegistration.addGuiScreenHandler",
            "IRuntimeRegistration.setRecipesGui", "IRuntimeRegistration.setIngredientListOverlay",
            "IRuntimeRegistration.setBookmarkOverlay", "IRuntimeRegistration.setBookmarkManager",
            "IRuntimeRegistration.setIngredientFilter", "IRecipeRegistration.getIngredientManager",
            "IRecipeRegistration.getIngredientVisibility", "IRecipeCatalystRegistration.getIngredientManager",
            "IModIngredientRegistration.getSubtypeManager", "IModIngredientRegistration.getColorHelper",
            "IAdvancedRegistration.getJeiFeatures", "IAdvancedRegistration.getRecipeManagerPluginHelper",
            "IRuntimeRegistration.getSearchStorageFactory", "IRuntimeRegistration.getSearchStorageBuilderFactory",
            "IAdvancedSearchRegistration.getDefaultSearchStorageBuilderFactory");

    static void unsupported(Class<?> type, Method method) {
        if (ACCEPTED.contains(type.getSimpleName() + "." + method.getName())) {
            return;
        }
        if (UNSUPPORTED.add(type.getSimpleName() + "." + method.getName())) {
            Bei.LOGGER.info("JEI bridge: {}.{} is not supported and was ignored (see docs/PORTING.md)", type.getSimpleName(), method.getName());
        }
    }

    private static <T> T stub(Class<T> type, IJeiHelpers helpers) {
        return proxy(type, (self, method, args) -> {
            if (method.getName().equals("getJeiHelpers")) {
                return helpers;
            }
            unsupported(type, method);
            return fluentOrDefault(self, method);
        });
    }

    private JeiBridge() {
    }

    /**
     * Forgets what the plugins registered and runs them again. Called when the game's recipes arrive: plugins such as
     * Applied Energistics read the recipe manager while they register, and it is empty until then.
     */
    public static synchronized void reload() {
        loaded = false;
        CATEGORIES.clear();
        RECIPES.clear();
        STATIONS.clear();
        ICONS.clear();
        HELPERS.clear();
        INFO.clear();
        BRIDGED.clear();
        CONTAINER_HANDLERS.clear();
        GLOBAL_HANDLERS.clear();
        GHOST_HANDLERS.clear();
        CLICK_AREAS.clear();
        HIDDEN_RECIPES.clear();
        EXTENSIONS.clear();
        JeiRegistry.clear();
        contributed = false;
        plugins = List.of();
        JeiHidden.clear();
        JeiTransfers.clear();
        loadOnce();
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
                RECIPES.computeIfAbsent((IRecipeType<?>) args[0], key -> new ArrayList<>())
                        .addAll((List<?>) args[1]);
                return null;
            }
            if (method.getName().equals("addIngredientInfo") && args.length >= 3) {
                List<ItemStack> stacks = new ArrayList<>();
                for (Object value : values(args[0])) {
                    ItemStack stack = item(value);
                    if (!stack.isEmpty()) {
                        stacks.add(stack.copy());
                    }
                }
                List<String> lines = new ArrayList<>();
                for (Object value : values(args[args.length - 1])) {
                    if (value instanceof Component component) {
                        lines.add(component.getString());
                    }
                }
                if (!stacks.isEmpty() && !lines.isEmpty()) {
                    INFO.add(new Info(stacks, lines));
                }
                return null;
            }
            unsupported(IRecipeRegistration.class, method);
            return fallback(method.getReturnType());
        });
        lastRecipeRegistration = recipes;
        IRecipeCatalystRegistration catalysts = proxy(IRecipeCatalystRegistration.class,
                (self, method, args) -> catalystCall(method, args, helpers));
        ISubtypeRegistration subtypes = stub(ISubtypeRegistration.class, helpers);
        IModIngredientRegistration ingredientTypes = proxy(IModIngredientRegistration.class, (self, method, args) -> {
            if (method.getName().equals("getJeiHelpers")) {
                return helpers;
            }
            if (method.getName().equals("register") && args.length >= 3 && args[0] instanceof IIngredientType<?> type) {
                HELPERS.put(type, args[2]);
                JeiRegistry.registerType(args);
                return null;
            }
            unsupported(IModIngredientRegistration.class, method);
            return fluentOrDefault(self, method);
        });
        IVanillaCategoryExtensionRegistration extensions = proxy(IVanillaCategoryExtensionRegistration.class, (self, method, args) -> {
            String name = method.getName();
            String kind = switch (name) {
                case "getCraftingCategory" -> "crafting";
                case "getSmeltingCategory", "getBlastingCategory", "getSmokingCategory", "getCampfireCookingCategory" -> "cooking";
                case "getSmithingCategory" -> "smithing";
                case "getBrewingCategory" -> "brewing";
                default -> null;
            };
            if (name.equals("getJeiHelpers")) {
                return helpers;
            }
            if (name.equals("addCookingCategoryExtension") && args.length == 2 && args[0] instanceof Class<?> recipeClass) {
                EXTENSIONS.add(new Extension("cooking", recipeClass, args[1]));
                return null;
            }
            if (kind == null) {
                unsupported(IVanillaCategoryExtensionRegistration.class, method);
                return fluentOrDefault(self, method);
            }
            return proxy(method.getReturnType(), (inner, call, callArgs) -> {
                if (call.getName().equals("addExtension") && callArgs.length == 2 && callArgs[0] instanceof Class<?> recipeClass) {
                    EXTENSIONS.add(new Extension(kind, recipeClass, callArgs[1]));
                    return null;
                }
                unsupported(method.getReturnType(), call);
                return fluentOrDefault(inner, call);
            });
        });
        IRecipeTransferRegistration transfers = proxy(IRecipeTransferRegistration.class,
                (self, method, args) -> transferCall(method, args, helpers));
        IGuiHandlerRegistration guiHandlers = proxy(IGuiHandlerRegistration.class, (self, method, args) -> {
            switch (method.getName()) {
                case "getJeiHelpers":
                    return helpers;
                case "addGuiContainerHandler":
                case "addGenericGuiContainerHandler":
                    if (args.length == 2 && args[0] instanceof Class<?> screen && args[1] != null) {
                        CONTAINER_HANDLERS.add(new GuiHandler(screen, args[1]));
                    }
                    return null;
                case "addGlobalGuiHandler":
                    if (args.length == 1 && args[0] != null) {
                        GLOBAL_HANDLERS.add(args[0]);
                    }
                    return null;
                case "addRecipeClickArea":
                    if (args.length >= 6 && args[0] instanceof Class<?> screen) {
                        List<String> uids = new ArrayList<>();
                        for (Object type : values(args[args.length - 1])) {
                            if (type instanceof IRecipeType<?> recipeType) {
                                uids.add(recipeType.getUid().toString());
                            }
                        }
                        CLICK_AREAS.add(new ClickArea(screen, (int) args[1], (int) args[2], (int) args[3], (int) args[4], uids));
                    }
                    return null;
                case "addGhostIngredientHandler":
                    if (args.length == 2 && args[0] instanceof Class<?> screen && args[1] != null) {
                        GHOST_HANDLERS.add(new GuiHandler(screen, args[1]));
                    }
                    return null;
                default:
                    unsupported(IGuiHandlerRegistration.class, method);
                    return fluentOrDefault(self, method);
            }
        });
        IAdvancedRegistration advanced = JeiRegistry.advanced(helpers);
        IJeiRuntime early = runtime(helpers);
        IRuntimeRegistration runtimeRegistration = proxy(IRuntimeRegistration.class, (self, method, args) -> {
            switch (method.getName()) {
                case "getJeiHelpers":
                    return helpers;
                case "getRecipeManager":
                case "getIngredientManager":
                case "getScreenHelper":
                case "getRecipeTransferManager":
                case "getEditModeConfig":
                case "getIngredientVisibility":
                    return JeiCards.call(early, method.getName());
                default:
                    unsupported(IRuntimeRegistration.class, method);
                    return fluentOrDefault(self, method);
            }
        });
        // JEI runs each phase for every plugin before the next phase starts.
        phase("item subtypes", plugin -> plugin.registerItemSubtypes(subtypes));
        phase("ingredients", plugin -> plugin.registerIngredients(ingredientTypes));
        phase("categories", plugin -> plugin.registerCategories(categories));
        phase("vanilla category extensions", plugin -> plugin.registerVanillaCategoryExtensions(extensions));
        phase("recipes", plugin -> plugin.registerRecipes(recipes));
        phase("catalysts", plugin -> plugin.registerRecipeCatalysts(catalysts));
        phase("transfer handlers", plugin -> plugin.registerRecipeTransferHandlers(transfers));
        phase("gui handlers", plugin -> plugin.registerGuiHandlers(guiHandlers));
        phase("advanced", plugin -> plugin.registerAdvanced(advanced));
        phase("runtime registration", plugin -> plugin.registerRuntime(runtimeRegistration));
        mergeManagerPlugins();
        for (String name : List.of("registerFluidSubtypes", "registerExtraIngredients", "registerIngredientAliases", "registerModInfo",
                "registerAdvancedSearch", "configureJei", "onConfigManagerAvailable")) {
            reflectivePhase(name, helpers);
        }
        if (JeiAliases.hasExtra()) {
            com.itmecasey.bei.index.ItemIndex.addExtras(JeiAliases.extraStacks());
        }
        JeiTransfers.install();
        IJeiRuntime runtime = runtime(helpers);
        for (IModPlugin plugin : plugins) {
            run(plugin, "runtime", () -> plugin.onRuntimeAvailable(runtime));
        }
        if (!plugins.isEmpty()) {
            // A plugin may have hidden items; make the browser work its hidden list out again.
            com.itmecasey.bei.client.Groups.refresh();
        }
        Bei.LOGGER.info("Loaded {} JEI plugin(s) with {} bridged categories", plugins.size(), CATEGORIES.size());
    }

    /**
     * Calls a plugin lifecycle method BEI has no use for with logging stubs for its parameters, so what the plugin
     * does there is reported once instead of being skipped unseen. Found by name because the set of these methods
     * differs between JEI versions.
     */
    private static void reflectivePhase(String name, IJeiHelpers helpers) {
        for (IModPlugin plugin : plugins) {
            for (Method method : plugin.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getDeclaringClass() == Object.class) {
                    continue;
                }
                Object[] args = new Object[method.getParameterCount()];
                boolean usable = true;
                for (int i = 0; i < args.length; i++) {
                    Class<?> type = method.getParameterTypes()[i];
                    if (!type.isInterface()) {
                        usable = false;
                        break;
                    }
                    Object special = JeiRegistry.searchRegistration(type, helpers);
                    if (special == null && type.getSimpleName().equals("IJeiConfigManager")) {
                        special = JeiRegistry.configManager(type);
                    }
                    args[i] = special != null ? special : proxy(type, (self, call, callArgs) -> {
                        if (call.getName().equals("getJeiHelpers")) {
                            return helpers;
                        }
                        unsupported(type, call);
                        return fluentOrDefault(self, call);
                    });
                }
                if (usable) {
                    run(plugin, name, () -> {
                        try {
                            method.invoke(plugin, args);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw new IllegalStateException(e.getCause());
                        } catch (IllegalAccessException e) {
                            throw new IllegalStateException(e);
                        }
                    });
                    break;
                }
            }
        }
    }

    private static void phase(String name, java.util.function.Consumer<IModPlugin> action) {
        for (IModPlugin plugin : plugins) {
            run(plugin, name, () -> action.accept(plugin));
        }
    }

    private static Object transferCall(Method method, Object[] args, IJeiHelpers helpers) {
        switch (method.getName()) {
            case "getJeiHelpers":
                return helpers;
            case "getTransferHelper":
                return JeiViews.helper();
            case "addRecipeTransferHandler":
                if (args.length >= 1 && args[0] instanceof mezz.jei.api.recipe.transfer.IRecipeTransferHandler<?, ?> handler) {
                    String uid = args.length >= 2 && args[1] instanceof IRecipeType<?> type ? type.getUid().toString()
                            : handler.getRecipeType().getUid().toString();
                    JeiTransfers.add(handler, uid);
                    return null;
                }
                if (args.length == 7 && args[0] instanceof Class<?> menuClass && args[2] instanceof IRecipeType<?> type) {
                    JeiTransfers.add(new JeiTransfers.Basic(menuClass, (int) args[3], (int) args[4]), type.getUid().toString());
                    return null;
                }
                unsupported(IRecipeTransferRegistration.class, method);
                return null;
            case "addRecipeTransferListener":
                if (args.length == 1 && args[0] != null) {
                    JeiTransfers.addListener(args[0]);
                }
                return null;
            case "addUniversalRecipeTransferHandler":
                if (args.length >= 1 && args[0] != null && JeiTransfers.find(args[0].getClass(), "transferRecipe", 6) != null) {
                    JeiTransfers.add(args[0], "");
                    return null;
                }
                unsupported(IRecipeTransferRegistration.class, method);
                return null;
            default:
                unsupported(IRecipeTransferRegistration.class, method);
                return fallback(method.getReturnType());
        }
    }

    /**
     * The small runtime plugins get in onRuntimeAvailable. It supports what scripts and configs use to tidy the
     * viewer: removing and re-adding item ingredients and hiding recipe categories, wired to BEI's own hiding.
     * Everything else answers with harmless empty values.
     */
    static IJeiRuntime runtime(IJeiHelpers helpers) {
        IRecipeManager recipes = proxyFirst(IRecipeManager.class, (self, method, args) -> {
            if (method.getName().equals("hideRecipeCategory") && args[0] instanceof IRecipeType<?> type) {
                JeiHidden.hideCategory(type.getUid().toString());
                Bei.LOGGER.debug("A JEI plugin hid the category {}", type.getUid());
                return null;
            }
            if (method.getName().equals("unhideRecipeCategory") && args[0] instanceof IRecipeType<?> type) {
                JeiHidden.showCategory(type.getUid().toString());
                return null;
            }
            if (method.getName().equals("createRecipeCategoryLookup")) {
                return categoryLookup();
            }
            if (method.getName().equals("addRecipes") && args.length == 2 && args[0] instanceof IRecipeType<?> type) {
                // Recipes added while the runtime starts are indexed with the others, which are built right after;
                // later ones are bridged and added to the index straight away.
                addLate(type, values(args[1]));
                return null;
            }
            if ((method.getName().equals("hideRecipes") || method.getName().equals("unhideRecipes")) && args.length == 2
                    && args[0] instanceof IRecipeType<?> type) {
                List<Object> shown = RECIPES.computeIfAbsent(type, key -> new ArrayList<>());
                List<Object> hidden = HIDDEN_RECIPES.computeIfAbsent(type, key -> new ArrayList<>());
                List<Object> changed = new ArrayList<>();
                for (Object recipe : values(args[1])) {
                    if (method.getName().equals("hideRecipes") && shown.remove(recipe)) {
                        hidden.add(recipe);
                        changed.add(recipe);
                    } else if (method.getName().equals("unhideRecipes") && hidden.remove(recipe)) {
                        shown.add(recipe);
                        changed.add(recipe);
                    }
                }
                if (contributed && !changed.isEmpty()) {
                    synchronized (JeiBridge.class) {
                        if (method.getName().equals("hideRecipes")) {
                            java.util.Set<String> ids = new HashSet<>();
                            for (Map.Entry<String, Bridged> entry : BRIDGED.entrySet()) {
                                if (changed.contains(entry.getValue().recipe())) {
                                    ids.add(entry.getKey());
                                }
                            }
                            BRIDGED.keySet().removeAll(ids);
                            com.itmecasey.bei.index.RecipeIndex.removeRecipes(ids);
                        } else {
                            bridgeLate(type, changed);
                        }
                    }
                }
                return null;
            }
            if (method.getName().equals("getRecipeCategory") && args.length == 1 && args[0] instanceof IRecipeType<?> type) {
                return CATEGORIES.get(type);
            }
            if (method.getName().equals("getRecipeType") && args.length >= 1) {
                String uid = String.valueOf(args[0]);
                for (IRecipeType<?> type : CATEGORIES.keySet()) {
                    if (type.getUid().toString().equals(uid)) {
                        return Optional.of(type);
                    }
                }
                return Optional.empty();
            }
            if (method.getName().equals("createRecipeLookup") && args[0] instanceof IRecipeType<?> type) {
                return recipeLookup(type);
            }
            if (method.getName().startsWith("createRecipeLayoutDrawable") && args.length >= 2
                    && args[0] instanceof IRecipeCategory<?> category) {
                Bridged made = assemble(category.getRecipeType(), category, args[1], "jei/drawable");
                Class<?> layoutType = ifaceClass("mezz.jei.api.gui.IRecipeLayoutDrawable");
                Object drawable = layoutType == null ? null : JeiRegistry.layoutDrawable(layoutType, category, made);
                return method.getReturnType() == Optional.class ? Optional.ofNullable(drawable) : drawable;
            }
            if (method.getName().equals("createRecipeSlotDrawable") && args.length >= 2) {
                return JeiRegistry.slotDrawable(method.getReturnType(), args);
            }
            if (method.getName().equals("getRecipeIngredients") && args.length >= 2 && args[0] instanceof IRecipeCategory<?> category) {
                Bridged made = assemble(category.getRecipeType(), category, args[1], "jei/ingredients");
                return JeiRegistry.ingredientSupplier(method.getReturnType(), made, args.length > 2 && args[2] instanceof Set<?> roles ? roles : null);
            }
            if (method.getName().equals("getRecipeButtonControllerFactories")) {
                synchronized (JeiBridge.class) {
                    return List.copyOf(JeiRegistry.BUTTON_FACTORIES);
                }
            }
            if (method.getName().equals("createRecipeCatalystLookup") && args[0] instanceof IRecipeType<?> type) {
                ItemStack station = STATIONS.get(type);
                return proxy(IRecipeCatalystLookup.class, (inner, call, callArgs) -> call.getName().equals("get")
                        ? (station == null ? java.util.stream.Stream.empty() : java.util.stream.Stream.of(JeiViews.typed(station)))
                        : fluentOrDefault(inner, call));
            }
            if (method.isDefault()) {
                return DEFER;
            }
            unsupported(IRecipeManager.class, method);
            return fallback(method.getReturnType());
        });
        IIngredientManager ingredients = JeiRegistry.ingredientManager();
        return proxyFirst(IJeiRuntime.class, (self, method, args) -> {
            switch (method.getName()) {
                case "getRecipeManager":
                    return recipes;
                case "getIngredientManager":
                    return ingredients;
                case "getJeiHelpers":
                    return helpers;
                case "getKeyMappings":
                    return JeiRegistry.keyMappings(method.getReturnType());
                case "getConfigManager":
                    return JeiRegistry.configManager(method.getReturnType());
                case "getRecipeTransferManager":
                    return JeiTransfers.manager(method.getReturnType());
                default:
                    Object adapter = JeiScreens.adapter(method.getReturnType(), helpers);
                    if (adapter != null) {
                        return adapter;
                    }
                    if (method.isDefault()) {
                        return DEFER;
                    }
                    unsupported(IJeiRuntime.class, method);
                    return fallback(method.getReturnType());
            }
        });
    }

    private static volatile List<ItemStack> itemStacks;

    /** Every item as a default stack, for plugins that walk the ingredient list. */
    static List<ItemStack> allItemStacks() {
        List<ItemStack> all = itemStacks;
        if (all == null) {
            all = new ArrayList<>();
            for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                if (item != net.minecraft.world.item.Items.AIR) {
                    all.add(item.getDefaultInstance());
                }
            }
            itemStacks = all;
        }
        return all;
    }

    /** A recipe lookup: the category's recipes, narrowed to what the plugins' manager plugins add for a focus. */
    private static IRecipeLookup<?> recipeLookup(IRecipeType<?> type) {
        java.util.Collection<Object> focuses = new ArrayList<>();
        boolean[] hiddenToo = {false};
        return proxy(IRecipeLookup.class, (inner, call, callArgs) -> {
            switch (call.getName()) {
                case "limitFocus":
                    focuses.addAll(values(callArgs[0]));
                    return inner;
                case "includeHidden":
                    hiddenToo[0] = true;
                    return inner;
                case "get": {
                    List<Object> all = new ArrayList<>(RECIPES.getOrDefault(type, List.of()));
                    if (hiddenToo[0]) {
                        all.addAll(HIDDEN_RECIPES.getOrDefault(type, List.of()));
                    }
                    for (Object extra : JeiRegistry.focusedRecipes(type, CATEGORIES.get(type), focuses)) {
                        if (!all.contains(extra)) {
                            all.add(extra);
                        }
                    }
                    return all.stream();
                }
                default:
                    return fluentOrDefault(inner, call);
            }
        });
    }

    /** Recipe manager plugins can add recipes to categories on their own; ask them once the plugins have registered. */
    private static void mergeManagerPlugins() {
        for (Map.Entry<IRecipeType<?>, IRecipeCategory<?>> entry : CATEGORIES.entrySet()) {
            List<Object> known = RECIPES.computeIfAbsent(entry.getKey(), key -> new ArrayList<>());
            List<Object> added = JeiRegistry.pluginRecipes(entry.getKey(), entry.getValue(), known);
            if (!added.isEmpty()) {
                Bei.LOGGER.info("BEI JEI: recipe manager plugins added {} recipes to {}", added.size(), entry.getKey().getUid());
            }
            known.addAll(added);
        }
    }

    /** Adds recipes a plugin registered after BEI built its index. */
    private static synchronized void addLate(IRecipeType<?> type, List<Object> added) {
        List<Object> known = RECIPES.computeIfAbsent(type, key -> new ArrayList<>());
        int first = known.size();
        known.addAll(added);
        if (contributed) {
            bridgeLate(type, added, first);
        }
    }

    private static void bridgeLate(IRecipeType<?> type, List<Object> added) {
        bridgeLate(type, added, RECIPES.getOrDefault(type, List.of()).size());
    }

    private static void bridgeLate(IRecipeType<?> type, List<Object> added, int first) {
        IRecipeCategory<?> category = CATEGORIES.get(type);
        if (category == null || added.isEmpty()) {
            return;
        }
        List<BeiRecipe> out = new ArrayList<>();
        try {
            bridgeCategory(type, category, added, out, first);
        } catch (RuntimeException | LinkageError e) {
            Bei.LOGGER.warn("Could not bridge late JEI recipes of {}", type.getUid(), e);
            return;
        }
        com.itmecasey.bei.index.RecipeIndex.addRecipes(out);
    }

    public static synchronized List<BeiRecipe> contribute() {
        if (!loaded) {
            return List.of();
        }
        contributed = true;
        startDevCheck();
        BRIDGED.clear();
        List<BeiRecipe> out = new ArrayList<>();
        int pages = 0;
        for (Info info : INFO) {
            out.addAll(ModJsCompat.infoPages("jei/info/" + pages++, info.items(), info.lines()));
        }
        for (Map.Entry<IRecipeType<?>, IRecipeCategory<?>> entry : CATEGORIES.entrySet()) {
            IRecipeType<?> type = entry.getKey();
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
    private static void bridgeCategory(IRecipeType<?> type, IRecipeCategory category, List<Object> recipes,
            List<BeiRecipe> out) {
        bridgeCategory(type, category, recipes, out, 0);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void bridgeCategory(IRecipeType<?> type, IRecipeCategory category, List<Object> recipes,
            List<BeiRecipe> out, int firstNumber) {
        List<LayoutSlot> first = layout(category, recipes.get(0), new ArrayList<>(), new ArrayList<>(), new int[] {-1, -1});
        ItemStack station = STATIONS.get(type);
        if ((station == null || station.isEmpty()) && ICONS.containsKey(category.getIcon())) {
            station = ICONS.get(category.getIcon());
        }
        if (station == null || station.isEmpty()) {
            Bei.LOGGER.debug("JEI category {} has no item catalyst; it cannot be a BEI station", type.getUid());
            return;
        }
        CardPainter painter = JeiDrawables.painter(new CardBehavior(category));
        RecipeCategory.Builder builder = RecipeCategory.builder(type.getUid())
                .title(category.getTitle())
                .station(station.copy())
                .painter(painter)
                .size(Math.max(18, category.getWidth()), Math.max(18, category.getHeight()));
        for (LayoutSlot slot : first) {
            if (slot.role == RecipeIngredientRole.INPUT) {
                builder.input(slot.x, slot.y);
            } else if (slot.role == RecipeIngredientRole.OUTPUT) {
                builder.output(slot.x, slot.y);
            }
        }
        RecipeCategory beiCategory = builder.build();
        int number = firstNumber;
        for (Object recipe : recipes) {
            Bridged made = assemble(type, category, recipe, null);
            List<LayoutSlot> slots = made.layoutSlots();
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
                BRIDGED.put(custom.id(), made);
                out.add(new BeiRecipe(custom.id(), type.getUid().toString(), 0, custom.inputs(), custom.outputs(),
                        List.of(station.copy()), beiCategory, custom));
            }
        }
    }

    /** Lays one recipe out: its slots, the parts its category draws, slot backgrounds and any plugin buttons. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Bridged assemble(IRecipeType<?> type, IRecipeCategory category, Object recipe, String unused) {
        List<Drawer> extras = new ArrayList<>();
        List<Drawer> overlays = new ArrayList<>();
        int[] buttonAt = {-1, -1};
        List<LayoutSlot> slots = layout(category, recipe, extras, overlays, buttonAt);
        for (LayoutSlot slot : slots) {
            if (slot.background != null) {
                // The slot's own background drawable, under the item the card draws there.
                extras.add((g, mx, my) -> drawAt(slot.background, g, slot.x + slot.backgroundX, slot.y + slot.backgroundY));
            }
            if (slot.role != RecipeIngredientRole.INPUT && slot.role != RecipeIngredientRole.OUTPUT && !slot.items.isEmpty()) {
                // A catalyst or display-only slot is not consumed or made, so the card just draws what it shows.
                extras.add((g, mx, my) -> JeiDrawables.item(g,
                        slot.items.get((int) (System.currentTimeMillis() / 1000 % slot.items.size())), slot.x + 1, slot.y + 1));
            }
        }
        List<JeiViews.SlotSpec> specs = new ArrayList<>();
        for (LayoutSlot slot : slots) {
            specs.add(new JeiViews.SlotSpec(slot.role, List.copyOf(slot.items), slot.name));
        }
        Bridged made = new Bridged(type.getUid().toString(), category, recipe, specs, extras, overlays, slots, buttonAt);
        JeiRegistry.addButtons(category, made);
        return made;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<LayoutSlot> layout(IRecipeCategory category, Object recipe, List<Drawer> extras, List<Drawer> overlays, int[] buttonAt) {
        List<LayoutSlot> slots = new ArrayList<>();
        List<Object[]> slotted = new ArrayList<>();
        IRecipeLayoutBuilder builder = layoutBuilder(slots, slotted, buttonAt);
        category.setRecipe(builder, recipe, emptyFocuses());
        collectExtras(category, recipe, slots, extras, slotted);
        for (LayoutSlot slot : slots) {
            slot.addOverlays(overlays, extras);
        }
        return slots;
    }

    /** A recipe layout builder that collects the slots a category or extension adds. */
    private static IRecipeLayoutBuilder layoutBuilder(List<LayoutSlot> slots, List<Object[]> slotted, int[] buttonAt) {
        return proxy(IRecipeLayoutBuilder.class, (self, method, args) -> {
            if (method.getName().equals("addSlot")) {
                LayoutSlot slot = new LayoutSlot((RecipeIngredientRole) args[0]);
                slots.add(slot);
                return slot.proxy();
            }
            if (method.getName().equals("moveRecipeTransferButton") && args.length == 2) {
                buttonAt[0] = (int) args[0];
                buttonAt[1] = (int) args[1];
                return null;
            }
            if (method.getName().equals("addSlotToWidget") && args.length == 2) {
                LayoutSlot slot = new LayoutSlot((RecipeIngredientRole) args[0]);
                slots.add(slot);
                slotted.add(new Object[] {slot, args[1]});
                return slot.proxy();
            }
            if (method.getName().equals("createFocusLink") || method.getName().equals("setShapeless")) {
                // BEI cycles every slot on one clock, so linked slots stay in step; shape is read from the recipe itself.
                return null;
            }
            if (method.getName().equals("addInvisibleIngredients")) {
                return new LayoutSlot((RecipeIngredientRole) args[0]).proxy();
            }
            return fluentOrDefault(self, method);
        });
    }

    /** Runs the category's createRecipeExtras (JEI 19 and later) and keeps what it adds as things to draw. */
    private static void collectExtras(Object category, Object recipe, List<LayoutSlot> slots, List<Drawer> extras, List<Object[]> slotted) {
        try {
            Class<?> builderType = Class.forName("mezz.jei.api.gui.widgets.IRecipeExtrasBuilder", false, JeiBridge.class.getClassLoader());
            Method create = JeiTransfers.find(category.getClass(), "createRecipeExtras", 3);
            if (create == null) {
                return;
            }
            Object partsBuilder = JeiCards.extrasBuilder(builderType, extras, slots);
            if (partsBuilder != null) {
                create.invoke(category, partsBuilder, recipe, emptyFocuses());
                Method widgetFactory = ifaceMethod("mezz.jei.api.gui.widgets.ISlottedWidgetFactory", "createWidgetForSlots", 3);
                Class<?> drawableType = Class.forName("mezz.jei.api.gui.ingredient.IRecipeSlotDrawable", false, JeiBridge.class.getClassLoader());
                for (Object[] pair : slotted) {
                    LayoutSlot slot = (LayoutSlot) pair[0];
                    if (widgetFactory != null) {
                        List<Object> one = List.of(JeiCards.slotDrawable(drawableType, new JeiViews.SlotSpec(slot.role, List.copyOf(slot.items), slot.name), slot.x, slot.y, new ArrayList<>()));
                        widgetFactory.invoke(pair[1], partsBuilder, recipe, one);
                    }
                }
                return;
            }
            Object builder = proxy(builderType, (self, method, args) -> {
                switch (method.getName()) {
                    case "addDrawable":
                        if (args.length == 3 && args[0] != null) {
                            Object drawable = args[0];
                            int x = (int) args[1];
                            int y = (int) args[2];
                            extras.add((g, mx, my) -> drawAt(drawable, g, x, y));
                        }
                        return fluentOrDefault(self, method);
                    case "addWidget":
                        if (args.length == 1 && args[0] != null) {
                            Object widget = args[0];
                            extras.add((g, mx, my) -> drawWidget(widget, g, mx, my));
                        }
                        return fluentOrDefault(self, method);
                    case "addRecipeArrow":
                    case "addAnimatedRecipeArrow": {
                        boolean animated = method.getName().equals("addAnimatedRecipeArrow");
                        int ticks = animated && args.length > 0 && args[0] instanceof Integer t ? t : 100;
                        int[] at = {0, 0};
                        Object arrow = animated ? JeiDrawables.animated(JeiDrawables.arrow(true), ticks,
                                mezz.jei.api.gui.drawable.IDrawableAnimated.StartDirection.LEFT, false) : JeiDrawables.arrow(true);
                        extras.add((g, mx, my) -> drawAt(arrow, g, at[0], at[1]));
                        return proxy(method.getReturnType(), (inner, call, callArgs) -> {
                            if (call.getName().equals("setPosition") && callArgs.length == 2) {
                                at[0] = (int) callArgs[0];
                                at[1] = (int) callArgs[1];
                            }
                            return fluentOrDefault(inner, call);
                        });
                    }
                    case "addText": {
                        List<net.minecraft.network.chat.FormattedText> lines = new ArrayList<>();
                        for (Object value : values(args.length > 0 ? args[0] : null)) {
                            if (value instanceof net.minecraft.network.chat.FormattedText text) {
                                lines.add(text);
                            }
                        }
                        int width = args.length > 1 && args[1] instanceof Integer w ? w : 100;
                        int[] state = {0, 0, 0xFF808080};
                        extras.add((g, mx, my) -> JeiDrawables.text(g, lines, width, state[0], state[1], state[2]));
                        return proxy(method.getReturnType(), (inner, call, callArgs) -> {
                            if (call.getName().equals("setPosition") && callArgs.length == 2) {
                                state[0] = (int) callArgs[0];
                                state[1] = (int) callArgs[1];
                            } else if (call.getName().equals("setColor") && callArgs.length == 1 && callArgs[0] instanceof Integer color) {
                                state[2] = color | 0xFF000000;
                            }
                            return fluentOrDefault(inner, call);
                        });
                    }
                    default:
                        unsupported(builderType, method);
                        return fluentOrDefault(self, method);
                }
            });
            create.invoke(category, builder, recipe, emptyFocuses());
        } catch (ClassNotFoundException e) {
            // This JEI API has no recipe extras.
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Bei.LOGGER.debug("A JEI category's recipe extras failed", e);
        }
    }

    static void drawAt(Object drawable, Object g, int x, int y) throws ReflectiveOperationException {
        Method draw = ifaceMethod("mezz.jei.api.gui.drawable.IDrawable", "draw", 3);
        if (draw == null) {
            draw = JeiTransfers.find(drawable.getClass(), "draw", 3);
        }
        if (draw != null) {
            draw.invoke(drawable, g, x, y);
        }
    }

    private static void drawWidget(Object widget, Object g, int mx, int my) throws ReflectiveOperationException {
        Method position = JeiTransfers.find(widget.getClass(), "getPosition", 0);
        Method draw = JeiTransfers.find(widget.getClass(), "draw", 3);
        if (position == null || draw == null) {
            return;
        }
        Object at = position.invoke(widget);
        int x = (int) at.getClass().getMethod("x").invoke(at);
        int y = (int) at.getClass().getMethod("y").invoke(at);
        JeiDrawables.translated(g, x, y, () -> {
            try {
                draw.invoke(widget, g, (double) (mx - x), (double) (my - y));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static final java.util.Set<String> DRAW_FAILED = ConcurrentHashMap.newKeySet();

    /** What a bridged category does on BEI's card: draw, take the mouse, add tooltips, place the transfer button. */
    private static final class CardBehavior implements JeiDrawables.Behavior {
        private final IRecipeCategory<?> category;

        CardBehavior(IRecipeCategory<?> category) {
            this.category = category;
        }

        @Override
        public void paint(Object g, com.itmecasey.bei.api.CustomRecipe custom, int mx, int my) {
            paintBridged(category, custom, g, mx, my);
        }

        @Override
        public void overlay(Object g, com.itmecasey.bei.api.CustomRecipe custom, int mx, int my) {
            Bridged bridged = bridged(custom.id());
            if (bridged == null) {
                return;
            }
            for (Drawer overlay : bridged.overlays()) {
                try {
                    overlay.draw(g, mx, my);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // A slot overlay that fails is skipped.
                }
            }
        }

        @Override
        public @org.jetbrains.annotations.Nullable List<Component> tooltip(com.itmecasey.bei.api.CustomRecipe custom, int mx, int my) {
            return tooltipBridged(category, custom, mx, my);
        }

        @Override
        public boolean click(com.itmecasey.bei.api.CustomRecipe custom, int mx, int my, int button) {
            Bridged bridged = bridged(custom.id());
            if (bridged == null) {
                return false;
            }
            List<Drawer> parts = bridged.extras();
            for (int i = parts.size() - 1; i >= 0; i--) {
                if (parts.get(i).click(mx, my, button)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean release(com.itmecasey.bei.api.CustomRecipe custom, int mx, int my, int button) {
            Bridged bridged = bridged(custom.id());
            boolean used = false;
            if (bridged != null) {
                for (Drawer part : bridged.extras()) {
                    used |= part.release(mx, my, button);
                }
            }
            return used;
        }

        @Override
        public boolean drag(com.itmecasey.bei.api.CustomRecipe custom, int mx, int my, int button, double dx, double dy) {
            Bridged bridged = bridged(custom.id());
            boolean used = false;
            if (bridged != null) {
                for (Drawer part : bridged.extras()) {
                    used |= part.drag(mx, my, button, dx, dy);
                }
            }
            return used;
        }

        @Override
        public boolean scroll(com.itmecasey.bei.api.CustomRecipe custom, int mx, int my, double delta) {
            Bridged bridged = bridged(custom.id());
            if (bridged == null) {
                return false;
            }
            List<Drawer> parts = bridged.extras();
            for (int i = parts.size() - 1; i >= 0; i--) {
                if (parts.get(i).scroll(mx, my, delta)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public @org.jetbrains.annotations.Nullable List<Component> slotTooltip(com.itmecasey.bei.api.CustomRecipe custom, ItemStack stack) {
            return slotTooltipBridged(custom, stack);
        }

        @Override
        public int @org.jetbrains.annotations.Nullable [] transferButton(com.itmecasey.bei.api.CustomRecipe custom) {
            Bridged bridged = bridged(custom.id());
            return bridged == null || bridged.buttonAt()[0] < 0 ? null : new int[] {bridged.buttonAt()[0], bridged.buttonAt()[1]};
        }
    }

    /** Extra tooltip lines a plugin's slot tooltip callbacks add for an item shown in one of the card's slots. */
    private static @org.jetbrains.annotations.Nullable List<Component> slotTooltipBridged(com.itmecasey.bei.api.CustomRecipe custom, ItemStack stack) {
        Bridged bridged = bridged(custom.id());
        return bridged == null ? null : slotTooltipCore(bridged, stack);
    }

    static @org.jetbrains.annotations.Nullable List<Component> slotTooltipCore(Bridged bridged, ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < bridged.layoutSlots().size(); i++) {
            LayoutSlot slot = bridged.layoutSlots().get(i);
            boolean shows = false;
            for (ItemStack shown : slot.items) {
                shows |= ItemStack.isSameItem(shown, stack);
            }
            if (!shows || slot.tooltipCallbacks.isEmpty()) {
                continue;
            }
            Object view = JeiViews.slot(bridged.slots().get(i));
            for (Object callback : slot.tooltipCallbacks) {
                Method legacy = ifaceMethod("mezz.jei.api.gui.ingredient.IRecipeSlotTooltipCallback", "onTooltip", 2);
                Method rich = ifaceMethod("mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback", "onRichTooltip", 2);
                try {
                    if (rich != null && JeiTransfers.find(callback.getClass(), "onRichTooltip", 2) != null) {
                        Object builder = proxy(rich.getParameterTypes()[1], (self, method, args) -> {
                            for (Object arg : args) {
                                if (arg instanceof net.minecraft.network.chat.FormattedText text) {
                                    lines.add(text instanceof Component c ? c : Component.literal(text.getString()));
                                }
                            }
                            return fluentOrDefault(self, method);
                        });
                        rich.invoke(callback, view, builder);
                    } else if (legacy != null && JeiTransfers.find(callback.getClass(), "onTooltip", 2) != null) {
                        legacy.invoke(callback, view, lines);
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    Bei.LOGGER.debug("A JEI slot tooltip callback failed", e);
                }
            }
        }
        return lines.isEmpty() ? null : lines;
    }

    /** The category's own drawing on BEI's card: its background, its draw call and what its recipe extras added. */
    private static void paintBridged(IRecipeCategory<?> category, com.itmecasey.bei.api.CustomRecipe custom, Object g, int mx, int my) {
        Bridged bridged = bridged(custom.id());
        if (bridged == null) {
            return;
        }
        paintCore(category, bridged, g, mx, my);
    }

    static void paintCore(IRecipeCategory<?> category, Bridged bridged, Object g, int mx, int my) {
        try {
            IRecipeSlotsView view = JeiViews.slots(bridged.slots());
            Method background = JeiTransfers.find(category.getClass(), "getBackground", 0);
            if (background != null) {
                Object drawable = background.invoke(category);
                if (drawable != null) {
                    drawAt(drawable, g, 0, 0);
                }
            }
            Method draw = JeiTransfers.find(category.getClass(), "draw", 5);
            if (draw != null) {
                draw.invoke(category, bridged.recipe(), view, g, (double) mx, (double) my);
            }
            for (Drawer extra : bridged.extras()) {
                extra.draw(g, mx, my);
            }
            JeiRegistry.decoratePaint(category, bridged.recipe(), view, g, mx, my);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (DRAW_FAILED.add(category.getRecipeType().getUid().toString())) {
                Bei.LOGGER.warn("A JEI category's drawing failed; its card is drawn without it: {}", category.getRecipeType().getUid(), e);
            }
        }
    }

    private static @org.jetbrains.annotations.Nullable List<Component> tooltipBridged(IRecipeCategory<?> category,
            com.itmecasey.bei.api.CustomRecipe custom, int mx, int my) {
        Bridged bridged = bridged(custom.id());
        return bridged == null ? null : tooltipCore(category, bridged, mx, my);
    }

    static @org.jetbrains.annotations.Nullable List<Component> tooltipCore(IRecipeCategory<?> category, Bridged bridged, int mx, int my) {
        List<Drawer> parts = bridged.extras();
        for (int i = parts.size() - 1; i >= 0; i--) {
            List<Component> fromPart = parts.get(i).tooltip(mx, my);
            if (fromPart != null && !fromPart.isEmpty()) {
                return fromPart;
            }
        }
        List<Component> lines = new ArrayList<>();
        try {
            IRecipeSlotsView view = JeiViews.slots(bridged.slots());
            Method strings = JeiTransfers.find(category.getClass(), "getTooltipStrings", 4);
            if (strings != null) {
                Object result = strings.invoke(category, bridged.recipe(), view, (double) mx, (double) my);
                if (result instanceof List<?> list) {
                    for (Object line : list) {
                        if (line instanceof Component component) {
                            lines.add(component);
                        }
                    }
                }
            }
            Method builderMethod = JeiTransfers.find(category.getClass(), "getTooltip", 5);
            if (lines.isEmpty() && builderMethod != null) {
                Object builder = proxy(builderMethod.getParameterTypes()[0], (self, method, args) -> {
                    for (Object arg : args) {
                        if (arg instanceof Component component) {
                            lines.add(component);
                        } else if (arg instanceof java.util.Collection<?> collection) {
                            for (Object element : collection) {
                                if (element instanceof Component component) {
                                    lines.add(component);
                                }
                            }
                        }
                    }
                    return fluentOrDefault(self, method);
                });
                builderMethod.invoke(category, builder, bridged.recipe(), view, (double) mx, (double) my);
            }
            lines.clear();
            lines.addAll(JeiRegistry.decorateTooltip(category, bridged.recipe(), view, mx, my, new ArrayList<>(lines)));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
        return lines.isEmpty() ? null : lines;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String recipeId(IRecipeCategory category, Object recipe, IRecipeType<?> type, int number) {
        try {
            Identifier id = category.getRegistryName(recipe);
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
                && args[0] instanceof IRecipeType<?> type) {
            // JEI 29 and later: the catalyst helpers all end here.
            for (Object value : values(args[args.length - 1])) {
                ItemStack stack = item(value);
                if (!stack.isEmpty()) {
                    STATIONS.putIfAbsent(type, stack.copy());
                }
            }
            return fallback(method.getReturnType());
        }
        if (method.getName().equals("addRecipeCatalysts") && args[0] instanceof IRecipeType<?> type) {
            for (Object value : values(args[args.length - 1])) {
                ItemStack stack = item(value);
                if (!stack.isEmpty()) {
                    STATIONS.putIfAbsent(type, stack.copy());
                }
            }
        } else if (method.getName().equals("addRecipeCatalyst") && args[0] instanceof ItemLike item) {
            ItemStack stack = new ItemStack(item);
            for (Object value : values(args[1])) {
                STATIONS.putIfAbsent((IRecipeType<?>) value, stack.copy());
            }
        } else if (method.getName().equals("addRecipeCatalyst") && args.length == 3) {
            ItemStack stack = item(args[1]);
            for (Object value : values(args[2])) {
                STATIONS.putIfAbsent((IRecipeType<?>) value, stack.copy());
            }
        }
        return fallback(method.getReturnType());
    }

    /**
     * The categories a plugin can see: those bridged from JEI plugins plus one stand-in for every game recipe type
     * (so a script can hide {@code minecraft:smelting} although BEI has no JEI vanilla plugin).
     */
    private static IRecipeCategoriesLookup categoryLookup() {
        return proxy(IRecipeCategoriesLookup.class, (self, method, args) -> {
            if (method.getName().equals("get")) {
                return knownCategories().stream();
            }
            return fluentOrDefault(self, method);
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<IRecipeCategory<?>> knownCategories() {
        List<IRecipeCategory<?>> out = new ArrayList<>(CATEGORIES.values());
        Set<String> bridged = new HashSet<>();
        for (IRecipeCategory<?> category : out) {
            bridged.add(category.getRecipeType().getUid().toString());
        }
        for (Identifier id : net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.keySet()) {
            if (bridged.contains(id.toString())) {
                continue;
            }
            IRecipeType<Object> type = IRecipeType.create(id.getNamespace(), id.getPath(), Object.class);
            out.add(proxy(IRecipeCategory.class, (self, method, args) -> switch (method.getName()) {
                case "getRecipeType" -> type;
                case "getTitle" -> Component.literal(id.toString());
                case "getWidth", "getHeight" -> 18;
                default -> fallback(method.getReturnType());
            }));
        }
        return out;
    }

    static IJeiHelpers helpers() {
        IGuiHelper gui = proxy(IGuiHelper.class, (self, method, args) -> {
            switch (method.getName()) {
                case "createDrawableIngredient": {
                    ItemStack stack = args.length >= 2 ? item(args[args.length - 1]) : ItemStack.EMPTY;
                    IDrawable drawable = stack.isEmpty() ? JeiDrawables.blank(18, 18) : JeiDrawables.itemIcon(stack);
                    if (!stack.isEmpty()) {
                        ICONS.put(drawable, stack.copy());
                    }
                    return drawable;
                }
                case "createDrawableItemStack": {
                    if (args[0] instanceof ItemStack stack) {
                        IDrawable drawable = JeiDrawables.itemIcon(stack);
                        ICONS.put(drawable, stack.copy());
                        return drawable;
                    }
                    break;
                }
                case "createBlankDrawable":
                    return JeiDrawables.blank((int) args[0], (int) args[1]);
                case "createDrawable":
                    if (args[0] instanceof Identifier texture && args.length >= 5) {
                        int texW = args.length >= 7 ? (int) args[5] : 256;
                        int texH = args.length >= 7 ? (int) args[6] : 256;
                        return JeiDrawables.texture(new JeiDrawables.Texture(texture, (int) args[1], (int) args[2], (int) args[3],
                                (int) args[4], texW, texH, 0, 0, 0, 0));
                    }
                    break;
                case "drawableBuilder":
                    if (args[0] instanceof Identifier texture && args.length >= 5) {
                        JeiDrawables.Texture[] current = {new JeiDrawables.Texture(texture, (int) args[1], (int) args[2],
                                (int) args[3], (int) args[4], 256, 256, 0, 0, 0, 0)};
                        return proxy(mezz.jei.api.gui.drawable.IDrawableBuilder.class, (inner, call, callArgs) -> {
                            switch (call.getName()) {
                                case "setTextureSize":
                                    current[0] = current[0].withTextureSize((int) callArgs[0], (int) callArgs[1]);
                                    return inner;
                                case "addPadding":
                                    current[0] = current[0].withPadding((int) callArgs[0], (int) callArgs[1], (int) callArgs[2], (int) callArgs[3]);
                                    return inner;
                                case "build":
                                    return JeiDrawables.texture(current[0]);
                                case "buildAnimated":
                                    if (callArgs.length == 3 && callArgs[0] instanceof Integer ticks) {
                                        return JeiDrawables.animated(JeiDrawables.texture(current[0]), ticks,
                                                (mezz.jei.api.gui.drawable.IDrawableAnimated.StartDirection) callArgs[1], (boolean) callArgs[2]);
                                    }
                                    if (callArgs.length == 2 && callArgs[0] != null) {
                                        return JeiDrawables.animated(JeiDrawables.texture(current[0]), callArgs[0],
                                                (mezz.jei.api.gui.drawable.IDrawableAnimated.StartDirection) callArgs[1]);
                                    }
                                    return JeiDrawables.texture(current[0]);
                                default:
                                    return fluentOrDefault(inner, call);
                            }
                        });
                    }
                    break;
                case "createTickTimer":
                    return JeiDrawables.timer((int) args[0], (int) args[1], (boolean) args[2]);
                case "createAnimatedDrawable":
                    if (args[0] instanceof IDrawableStatic base && args.length == 4) {
                        return JeiDrawables.animated(base, (int) args[1],
                                (mezz.jei.api.gui.drawable.IDrawableAnimated.StartDirection) args[2], (boolean) args[3]);
                    }
                    break;
                case "getSlotDrawable":
                    return JeiDrawables.slot();
                case "getOutputSlot":
                    return JeiDrawables.outputSlot();
                case "getRecipePlusSign":
                    return JeiDrawables.plus();
                case "getRecipeFlameFilled":
                    return JeiDrawables.flame(true);
                case "getRecipeFlameEmpty":
                    return JeiDrawables.flame(false);
                case "createAnimatedRecipeFlame":
                    return JeiDrawables.animated(JeiDrawables.flame(true), (int) args[0],
                            mezz.jei.api.gui.drawable.IDrawableAnimated.StartDirection.TOP, true);
                case "createDrawableItemLike":
                    if (args[0] instanceof ItemLike like) {
                        ItemStack stack = new ItemStack(like);
                        IDrawable drawable = JeiDrawables.itemIcon(stack);
                        ICONS.put(drawable, stack.copy());
                        return drawable;
                    }
                    break;
                case "createDrawableSprite":
                case "createScalableDrawableSprite":
                    if (args.length >= 3 && args[0] instanceof Identifier sprite) {
                        return JeiDrawables.spriteDrawable(sprite, (int) args[1], (int) args[2]);
                    }
                    break;
                case "createCraftingGridHelper":
                    return craftingGridHelper(method.getReturnType());
                case "getRecipeArrow":
                case "getRecipeArrowFilled":
                    return JeiDrawables.arrow(true);
                case "getRecipeArrowEmpty":
                    return JeiDrawables.arrow(false);
                case "createAnimatedRecipeArrow":
                    return JeiDrawables.animated(JeiDrawables.arrow(true), (int) args[0],
                            mezz.jei.api.gui.drawable.IDrawableAnimated.StartDirection.LEFT, false);
                default:
                    break;
            }
            if (method.getName().equals("createRecipeSlotDrawable") && args.length >= 2) {
                return JeiRegistry.slotDrawable(method.getReturnType(), args);
            }
            Object widget = JeiCards.guiWidget(method, args);
            if (widget != null) {
                return widget;
            }
            unsupported(IGuiHelper.class, method);
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

    static IFocusGroup emptyFocuses() {
        return proxy(IFocusGroup.class, (self, method, args) -> switch (method.getName()) {
            case "isEmpty" -> true;
            case "getAllFocuses" -> List.of();
            case "getFocuses" -> java.util.stream.Stream.empty();
            default -> fallback(method.getReturnType());
        });
    }

    private static List<String> scan() {
        List<String> found = new ArrayList<>();
        for (ModFileScanData data : ModList.getAllScanData()) {
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

    /** The stacks inside a game ingredient or item template (versions differ in how they list them), found by reflection. */
    static List<ItemStack> stacksOf(Object value) {
        List<ItemStack> out = new ArrayList<>();
        try {
            Method create = JeiTransfers.find(value.getClass(), "create", 0);
            if (create != null && create.invoke(value) instanceof ItemStack made) {
                out.add(made);
                return out;
            }
            for (String name : new String[] {"getItems", "items"}) {
                Method list = JeiTransfers.find(value.getClass(), name, 0);
                if (list == null) {
                    continue;
                }
                Object result = list.invoke(value);
                java.util.stream.Stream<?> stream = result instanceof java.util.stream.Stream<?> st ? st : values(result).stream();
                stream.forEach(entry -> {
                    if (entry instanceof ItemStack stack) {
                        out.add(stack.copy());
                    } else if (entry != null) {
                        Object held = JeiCards.call(entry, "value");
                        if (held instanceof net.minecraft.world.item.Item item) {
                            out.add(new ItemStack(item));
                        }
                    }
                });
                break;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Nothing usable in it.
        }
        return out;
    }

    static ItemStack item(Object value) {
        if (value instanceof ItemStack stack) {
            return stack;
        }
        if (value instanceof ItemLike item) {
            return new ItemStack(item);
        }
        if (value instanceof ITypedIngredient<?> typed) {
            return item(typed.getIngredient());
        }
        return value == null ? ItemStack.EMPTY : labeled(value);
    }

    /**
     * A non-item ingredient shown as a stack. A fluid becomes its bucket named with the fluid and the amount; any other
     * type a plugin registered becomes a named placeholder using the type's own helper for the name.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ItemStack labeled(Object value) {
        try {
            if (value.getClass().getSimpleName().equals("FluidStack")) {
                Object fluid = value.getClass().getMethod("getFluid").invoke(value);
                long amount = ((Number) value.getClass().getMethod("getAmount").invoke(value)).longValue();
                Object display = value.getClass().getMethod("getDisplayName").invoke(value);
                String name = display instanceof Component component ? component.getString() : String.valueOf(fluid);
                net.minecraft.world.item.Item bucket = ((net.minecraft.world.level.material.Fluid) fluid).getBucket();
                ItemStack stack = new ItemStack(bucket == net.minecraft.world.item.Items.AIR ? net.minecraft.world.item.Items.BUCKET : bucket);
                return named(stack, Component.literal(amount > 0 ? name + " (" + amount + " mB)" : name));
            }
            for (Map.Entry<IIngredientType<?>, Object> entry : HELPERS.entrySet()) {
                if (entry.getKey().getIngredientClass().isInstance(value)) {
                    String name = ((IIngredientHelper) entry.getValue()).getDisplayName(value);
                    return named(new ItemStack(net.minecraft.world.item.Items.PAPER), Component.literal(name));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Bei.LOGGER.debug("Could not show a JEI ingredient of {}", value.getClass().getName(), e);
        }
        return ItemStack.EMPTY;
    }

    /**
     * JEI's crafting grid helper: puts a recipe's inputs into the standard crafting grid slots (18 pixels apart from
     * 1,1) and its output at 95,19, through the layout builder the category passed in.
     */
    private static Object craftingGridHelper(Class<?> type) {
        return proxy(type, (self, method, args) -> {
            Object builder = null;
            List<Object> lists = new ArrayList<>();
            int[] size = {3, 3};
            int ints = 0;
            for (Object arg : args) {
                if (arg instanceof IRecipeLayoutBuilder layout) {
                    builder = layout;
                } else if (arg instanceof java.util.Collection<?> collection) {
                    lists.add(collection);
                } else if (arg instanceof Integer number && ints < 2) {
                    size[ints++] = number;
                }
            }
            String name = method.getName();
            if (!(builder instanceof IRecipeLayoutBuilder layout) || lists.isEmpty()) {
                unsupported(type, method);
                return fallback(method.getReturnType());
            }
            List<Object> slots = new ArrayList<>();
            if (name.contains("Inputs") || name.contains("Ingredients")) {
                List<Object> cells = new ArrayList<>(values(lists.get(0)));
                int width = Math.max(1, size[0]);
                for (int i = 0; i < cells.size(); i++) {
                    IRecipeSlotBuilder slot = layout.addSlot(RecipeIngredientRole.INPUT, 1 + (i % width) * 18, 1 + (i / width) * 18);
                    for (Object option : values(cells.get(i) instanceof Optional<?> maybe ? maybe.orElse(null) : cells.get(i))) {
                        slot.addIngredient(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, item(option));
                    }
                    slots.add(slot);
                }
            } else if (name.contains("Outputs")) {
                IRecipeSlotBuilder slot = layout.addSlot(RecipeIngredientRole.OUTPUT, 95, 19);
                for (Object option : values(lists.get(0))) {
                    slot.addIngredient(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, item(option));
                }
                slots.add(slot);
            } else {
                unsupported(type, method);
                return fallback(method.getReturnType());
            }
            return method.getReturnType() == List.class ? slots : fallback(method.getReturnType());
        });
    }

    /** A fluid as its bucket, named with the fluid and the amount. */
    static ItemStack fluidBucket(net.minecraft.world.level.material.Fluid fluid, long amount) {
        net.minecraft.world.item.Item bucket = fluid.getBucket();
        ItemStack stack = new ItemStack(bucket == net.minecraft.world.item.Items.AIR ? net.minecraft.world.item.Items.BUCKET : bucket);
        String name = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid).getPath();
        try {
            Object type = fluid.getClass().getMethod("getFluidType").invoke(fluid);
            Object description = type.getClass().getMethod("getDescription").invoke(type);
            if (description instanceof Component component) {
                name = component.getString();
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // The fluid keeps its registry name.
        }
        return named(stack, Component.literal(amount > 0 ? name + " (" + amount + " mB)" : name));
    }

    private static ItemStack named(ItemStack stack, Component name) {
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name);
        return stack;
    }

    /** The game recipe a BEI recipe id names, as the object JEI hands to its handlers; null when the client has none. */
    /**
     * The game recipe laid out the way a plugin's category extension says, or null when no extension covers it. The
     * holder is the game's recipe holder (or the recipe itself on versions without holders).
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static synchronized BeiRecipe extended(Object holder, BeiRecipe current) {
        if (EXTENSIONS.isEmpty() || holder == null || current == null) {
            return null;
        }
        Object recipe = JeiCards.call(holder, "value");
        if (recipe == null) {
            recipe = holder;
        }
        for (Extension ext : EXTENSIONS) {
            if (!ext.recipeClass().isInstance(recipe)) {
                extendedNote = "recipe is " + recipe.getClass().getName() + ", extension wants " + ext.recipeClass().getName();
                continue;
            }
            Method setRecipe = null;
            for (Method method : ext.extension().getClass().getMethods()) {
                if (method.getName().equals("setRecipe") && method.getParameterCount() >= 3
                        && (setRecipe == null || method.getParameterCount() > setRecipe.getParameterCount())) {
                    setRecipe = method;
                }
            }
            if (setRecipe == null) {
                extendedNote = "no setRecipe on " + ext.extension().getClass().getName();
                continue;
            }
            List<LayoutSlot> slots = new ArrayList<>();
            IRecipeLayoutBuilder builder = layoutBuilder(slots, new ArrayList<>(), new int[] {-1, -1});
            Object[] args = new Object[setRecipe.getParameterCount()];
            for (int i = 0; i < args.length; i++) {
                Class<?> want = setRecipe.getParameterTypes()[i];
                if (want.isInstance(builder)) {
                    args[i] = builder;
                } else if (want.getSimpleName().equals("ICraftingGridHelper")) {
                    args[i] = craftingGridHelper(want);
                } else if (want.getSimpleName().equals("IFocusGroup")) {
                    args[i] = emptyFocuses();
                } else if (want.isInstance(holder)) {
                    args[i] = holder;
                } else if (want.isInstance(recipe)) {
                    args[i] = recipe;
                }
            }
            try {
                setRecipe.invoke(ext.extension(), args);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                Bei.LOGGER.debug("A JEI category extension failed on {}", current.id(), e);
                extendedNote = "setRecipe failed: " + e + " cause " + e.getCause();
                continue;
            }
            List<List<ItemStack>> inputs = new ArrayList<>();
            List<ItemStack> outputs = new ArrayList<>();
            for (LayoutSlot slot : slots) {
                if (slot.role() == RecipeIngredientRole.INPUT) {
                    inputs.add(List.copyOf(slot.items()));
                } else if (slot.role() == RecipeIngredientRole.OUTPUT) {
                    for (ItemStack stack : slot.items()) {
                        if (!stack.isEmpty()) {
                            outputs.add(stack.copy());
                        }
                    }
                }
            }
            if (outputs.isEmpty()) {
                StringBuilder detail = new StringBuilder("extension laid out no output; slots=" + slots.size());
                for (LayoutSlot slot : slots) {
                    detail.append(" [").append(slot.role()).append(" items=").append(slot.items().size()).append("]");
                }
                extendedNote = detail.toString();
                continue;
            }
            int width = 0;
            if (ext.kind().equals("crafting")) {
                Object w = JeiCards.call(ext.extension(), "getWidth", holder);
                if (!(w instanceof Integer)) {
                    w = JeiCards.call(ext.extension(), "getWidth");
                }
                width = w instanceof Integer number ? Math.max(0, number) : 0;
            }
            String type = switch (ext.kind()) {
                case "crafting" -> width > 0 ? "ShapedCrafting" : "ShapelessCrafting";
                case "cooking" -> "Furnace";
                case "smithing" -> "Smithing";
                default -> current.type();
            };
            return new BeiRecipe(current.id(), type, width, inputs, outputs, current.stations(), null, null, current.recipeType());
        }
        return null;
    }

    /** Like {@link #extended}, for versions that keep no recipe objects on the client: a single-player world's own server has them. */
    public static synchronized BeiRecipe extendedById(String id, BeiRecipe current) {
        if (EXTENSIONS.isEmpty() || current == null) {
            return null;
        }
        Object holder = serverHolder(id);
        return holder == null ? null : extended(holder, current);
    }

    /** The recipe holder for a recipe id from the integrated server of a single-player world, or null. */
    private static String serverHolderNote = "";
    private static String extendedNote = "";

    private static Object serverHolder(String id) {
        try {
            Object server = JeiCards.call(net.minecraft.client.Minecraft.getInstance(), "getSingleplayerServer");
            Object manager = JeiCards.call(server, "getRecipeManager");
            serverHolderNote = "server " + (server == null ? "none" : "found") + ", manager " + (manager == null ? "none" : manager.getClass().getSimpleName());
            Object registry = net.minecraft.core.registries.Registries.class.getField("RECIPE").get(null);
            Identifier location = Identifier.tryParse(id);
            if (manager == null || location == null) {
                return null;
            }
            Object key = net.minecraft.resources.ResourceKey.class.getMethod("create", net.minecraft.resources.ResourceKey.class,
                    Identifier.class).invoke(null, registry, location);
            Method byKey = JeiTransfers.find(manager.getClass(), "byKey", 1);
            Object found = null;
            if (byKey != null) {
                try {
                    found = byKey.invoke(manager, key);
                } catch (IllegalArgumentException e) {
                    found = byKey.invoke(manager, location);
                }
            }
            serverHolderNote += ", byKey " + found;
            return found instanceof Optional<?> optional ? optional.orElse(null) : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            serverHolderNote += " / failed: " + e;
            return null;
        }
    }

    static Object vanillaRecipe(com.itmecasey.bei.api.TransferRecipe recipe) {
        // This version keeps no recipe objects on the client, so the handler gets one built from the recipe BEI received.
        try {
            if (recipe.outputs().isEmpty()) {
                return null;
            }
            ItemStack result = recipe.outputs().get(0);
            var template = new net.minecraft.world.item.ItemStackTemplate(result.getItem(), result.getCount());
            var common = new net.minecraft.world.item.crafting.Recipe.CommonInfo(true);
            var book = new net.minecraft.world.item.crafting.CraftingRecipe.CraftingBookInfo(
                    net.minecraft.world.item.crafting.CraftingBookCategory.MISC, "");
            net.minecraft.world.item.crafting.Recipe<?> built;
            if (recipe.type().equals("ShapedCrafting") && recipe.width() > 0) {
                int width = recipe.width();
                int height = (recipe.inputs().size() + width - 1) / width;
                List<Optional<net.minecraft.world.item.crafting.Ingredient>> cells = new ArrayList<>();
                for (List<ItemStack> options : recipe.inputs()) {
                    cells.add(ingredientOf(options));
                }
                while (cells.size() < width * height) {
                    cells.add(Optional.empty());
                }
                built = new net.minecraft.world.item.crafting.ShapedRecipe(common, book,
                        new net.minecraft.world.item.crafting.ShapedRecipePattern(width, height, cells, Optional.empty()), template);
            } else {
                List<net.minecraft.world.item.crafting.Ingredient> list = new ArrayList<>();
                for (List<ItemStack> options : recipe.inputs()) {
                    ingredientOf(options).ifPresent(list::add);
                }
                built = new net.minecraft.world.item.crafting.ShapelessRecipe(common, book, template, list);
            }
            Identifier id = Identifier.tryParse(recipe.id());
            if (id == null) {
                return null;
            }
            return new net.minecraft.world.item.crafting.RecipeHolder<>(
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id), built);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Optional<net.minecraft.world.item.crafting.Ingredient> ingredientOf(List<ItemStack> options) {
        return options.isEmpty() ? Optional.empty()
                : Optional.of(net.minecraft.world.item.crafting.Ingredient.of(options.stream().map(ItemStack::getItem)));
    }

    static List<Object> values(Object value) {
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

    static Object fluentOrDefault(Object self, Method method) {
        return method.getReturnType().isInstance(self) ? self : fallback(method.getReturnType());
    }

    static Object fallback(Class<?> type) {
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
    /** Returned by a handler used with {@link #proxyFirst} to let the interface's own default method answer. */
    static final Object DEFER = new Object();

    /**
     * Like {@link #proxy}, but the handler is asked before an interface's default methods run, so a default method that
     * would call into JEI's own machinery can be answered from BEI instead. A handler that returns {@link #DEFER} lets the default run.
     */
    static <T> T proxyFirst(Class<T> type, BridgeHandler handler) {
        return proxy(type, handler, true);
    }

    static <T> T proxy(Class<T> type, BridgeHandler handler) {
        return proxy(type, handler, false);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, BridgeHandler handler, boolean handlerFirst) {
        InvocationHandler invocation = (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "BEI JEI bridge " + type.getSimpleName();
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self == args[0];
                    default -> null;
                };
            }
            Object[] callArgs = args == null ? new Object[0] : args;
            if (handlerFirst) {
                Object answer = handler.invoke(self, method, callArgs);
                if (answer != DEFER) {
                    return answer;
                }
                return method.isDefault() ? InvocationHandler.invokeDefault(self, method, callArgs) : fallback(method.getReturnType());
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(self, method, callArgs);
            }
            return handler.invoke(self, method, callArgs);
        };
        // The interface's own loader resolves the classes its methods mention exactly as the interface did.
        ClassLoader loader = type.getClassLoader() != null ? type.getClassLoader() : PROXY_LOADER;
        try {
            return (T) Proxy.newProxyInstance(loader, new Class<?>[]{type}, invocation);
        } catch (IllegalArgumentException e) {
            return (T) Proxy.newProxyInstance(PROXY_LOADER, new Class<?>[]{type}, invocation);
        }
    }

    /**
     * Proxy interfaces mention Minecraft classes. On Forge those live in a loader BEI's own loader cannot find by
     * name, which makes Proxy refuse the interface; this loader asks every loader that might know the class.
     */
    private static final ClassLoader PROXY_LOADER = new ClassLoader(JeiBridge.class.getClassLoader()) {
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            ClassNotFoundException first = null;
            for (ClassLoader loader : new ClassLoader[] {JeiBridge.class.getClassLoader(), ItemStack.class.getClassLoader(),
                    Thread.currentThread().getContextClassLoader(), ClassLoader.getSystemClassLoader()}) {
                if (loader == null) {
                    continue;
                }
                try {
                    return Class.forName(name, false, loader);
                } catch (ClassNotFoundException e) {
                    if (first == null) {
                        first = e;
                    }
                }
            }
            throw first != null ? first : new ClassNotFoundException(name);
        }
    };

    @FunctionalInterface
    interface BridgeHandler {
        Object invoke(Object self, Method method, Object[] args) throws Throwable;
    }

    static final class LayoutSlot {
        private final RecipeIngredientRole role;
        private final List<ItemStack> items = new ArrayList<>();
        private final List<Object> originals = new ArrayList<>();
        private final List<Object> tooltipCallbacks = new ArrayList<>();
        private Object overlay;
        private int overlayX;
        private int overlayY;
        private Object renderer;
        private Object rendererType;
        private long fluidCapacity = -1;
        private boolean fluidShowCapacity;
        private int fluidWidth = 16;
        private int fluidHeight = 16;
        private boolean outputBackground;

        RecipeIngredientRole role() {
            return role;
        }

        List<ItemStack> items() {
            return items;
        }

        String name() {
            return name;
        }

        int x() {
            return x;
        }

        int y() {
            return y;
        }

        private String name;
        private Object background;
        private int backgroundX;
        private int backgroundY;
        private int x;
        private int y;

        LayoutSlot(RecipeIngredientRole role) {
            this.role = role;
        }

        IRecipeSlotBuilder proxy() {
            return JeiBridge.proxy(IRecipeSlotBuilder.class, (self, method, args) -> {
                if (System.getenv("BEI_JEI_SELFTEST") != null && method.getName().startsWith("add")) {
                    Bei.LOGGER.info("BEI-CHECK slot builder call {} with {} argument(s), first {}", method.getName(), args.length,
                            args.length > 0 && args[0] != null ? args[0].getClass().getName() : null);
                }
                switch (method.getName()) {
                    case "setPosition" -> {
                        x = (int) args[0];
                        y = (int) args[1];
                        if (args.length == 6) {
                            // Aligned inside a box: the slot is 18 by 18.
                            int width = (int) args[2];
                            int height = (int) args[3];
                            String horizontal = String.valueOf(args[4]);
                            String vertical = String.valueOf(args[5]);
                            x += horizontal.equals("CENTER") ? (width - 18) / 2 : horizontal.equals("RIGHT") ? width - 18 : 0;
                            y += vertical.equals("CENTER") ? (height - 18) / 2 : vertical.equals("BOTTOM") ? height - 18 : 0;
                        }
                    }
                    case "setOverlay" -> {
                        if (args.length == 3 && args[0] != null) {
                            overlay = args[0];
                            overlayX = (int) args[1];
                            overlayY = (int) args[2];
                        }
                    }
                    case "setCustomRenderer" -> {
                        if (args.length == 2) {
                            rendererType = args[0];
                            renderer = args[1];
                        }
                    }
                    case "setFluidRenderer" -> {
                        if (args.length >= 4) {
                            fluidCapacity = ((Number) args[0]).longValue();
                            fluidShowCapacity = Boolean.TRUE.equals(args[1]);
                            fluidWidth = ((Number) args[2]).intValue();
                            fluidHeight = ((Number) args[3]).intValue();
                        }
                    }
                    case "addTooltipCallback", "addRichTooltipCallback" -> {
                        if (args.length == 1 && args[0] != null) {
                            tooltipCallbacks.add(args[0]);
                        }
                    }
                    case "setOutputSlotBackground" -> outputBackground = true;
                    case "setStandardSlotBackground", "setHoverPadding" -> {
                        // BEI draws its own slot box and hover.
                    }
                    case "setSlotName" -> name = args.length > 0 && args[0] instanceof String text ? text : null;
                    case "setBackground" -> {
                        if (args.length == 3 && args[0] != null) {
                            background = args[0];
                            backgroundX = (int) args[1];
                            backgroundY = (int) args[2];
                        }
                    }
                    case "addFluidStack" -> {
                        if (args.length >= 1 && args[0] instanceof net.minecraft.world.level.material.Fluid fluid) {
                            ItemStack bucket = fluidBucket(fluid, args.length >= 2 && args[1] instanceof Number amount ? amount.longValue() : 0L);
                            if (!bucket.isEmpty()) {
                                items.add(bucket);
                            }
                        }
                    }
                    case "addIngredient" -> add(args[1]);
                    // Newer JEI APIs (26.x) take everything through add(...): items, fluids, ingredients, typed values.
                    case "add" -> addAny(args);
                    // Some JEI API versions have these as plain methods instead of defaults over addIngredient.
                    case "addItemStack", "addItemLike", "addTypedIngredient" -> {
                        if (args.length >= 1 && args[0] != null && !(args[0] instanceof IIngredientType<?>)) {
                            add(args[0]);
                        } else if (args.length >= 2) {
                            add(args[1]);
                        }
                    }
                    case "addItemStacks", "addItemLikes" -> {
                        for (Object value : values(args[args.length - 1])) {
                            add(value);
                        }
                    }
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

        private void addAny(Object[] args) {
            if (args.length == 0 || args[0] == null) {
                return;
            }
            Object first = args[0];
            if (first instanceof IIngredientType<?>) {
                if (args.length > 1) {
                    addAny(new Object[] {args[1]});
                }
            } else if (first instanceof net.minecraft.world.level.material.Fluid fluid) {
                ItemStack bucket = fluidBucket(fluid, args.length >= 2 && args[1] instanceof Number amount ? amount.longValue() : 0L);
                if (!bucket.isEmpty()) {
                    items.add(bucket);
                    originals.add(fluid);
                }
            } else if (first instanceof ItemStack || first instanceof ItemLike || first instanceof ITypedIngredient<?>) {
                add(first);
            } else {
                for (ItemStack stack : stacksOf(first)) {
                    items.add(stack);
                    originals.add(stack);
                }
            }
        }

        private void add(Object value) {
            ItemStack stack = item(value);
            if (!stack.isEmpty()) {
                items.add(stack.copy());
                originals.add(value);
            }
        }

        /**
         * The things a slot draws besides its item: an output background (under the item) and an overlay, a custom
         * renderer or a fluid level (over it).
         */
        void addOverlays(List<Drawer> overlays, List<Drawer> under) {
            LayoutSlot slot = this;
            if (outputBackground) {
                under.add((g, mx, my) -> JeiDrawables.drawStatic(JeiDrawables.outputSlot(), g, slot.x - 4, slot.y - 4, 0, 0, 0, 0));
            }
            if (renderer != null && !originals.isEmpty()) {
                Method render = JeiTransfers.find(renderer.getClass(), "render", 2);
                if (render != null) {
                    overlays.add((g, mx, my) -> JeiDrawables.translated(g, slot.x + 1, slot.y + 1, () -> {
                        try {
                            render.invoke(slot.renderer, g, slot.originals.get((int) (System.currentTimeMillis() / 1000 % slot.originals.size())));
                        } catch (ReflectiveOperationException | RuntimeException e) {
                            // A renderer that fails leaves the item showing.
                        }
                    }));
                }
            }
            if (fluidCapacity > 0 && !originals.isEmpty()) {
                overlays.add((g, mx, my) -> {
                    Object first = slot.originals.get(0);
                    long amount = JeiCards.call(first, "getAmount") instanceof Number n ? n.longValue() : slot.fluidCapacity;
                    int filled = (int) Math.max(1, Math.min(slot.fluidHeight, slot.fluidHeight * amount / Math.max(1, slot.fluidCapacity)));
                    String id = String.valueOf(first).toLowerCase(java.util.Locale.ROOT);
                    int color = id.contains("lava") ? 0xCCE87A1C : 0xCC3F76E4;
                    JeiDrawables.fill(g, slot.x + 1, slot.y + 1 + slot.fluidHeight - filled, slot.x + 1 + slot.fluidWidth, slot.y + 1 + slot.fluidHeight, color);
                });
            }
            if (overlay != null) {
                overlays.add((g, mx, my) -> drawAt(slot.overlay, g, slot.x + slot.overlayX, slot.y + slot.overlayY));
            }
        }
    }
}
