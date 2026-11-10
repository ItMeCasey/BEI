package com.itmecasey.bei.compat;

import com.itmecasey.bei.Bei;
import com.itmecasey.bei.api.TransferContext;
import com.itmecasey.bei.api.TransferRecipe;
import com.itmecasey.bei.api.TransferState;
import com.itmecasey.bei.plugin.TransferHost;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Runs the recipe transfer handlers of JEI plugins behind BEI's plus button. One BEI handler is registered per menu
 * class; it picks the JEI handler whose recipe type matches the recipe, hands it the JEI recipe object and a view of
 * the recipe's slots, and turns its answer into BEI's enabled, dimmed or hidden button.
 */
final class JeiTransfers {
    /** A JEI handler with the recipe type it was registered for ("" for a universal handler). */
    record Registered(Object handler, String typeUid) {
    }

    /** A handler registered by slot range instead of by code: JEI's basic transfer. */
    record Basic(Class<?> menuClass, int firstSlot, int slotCount) {
    }

    private static final List<Registered> HANDLERS = new ArrayList<>();
    private static final List<Object> INSTALLED = new ArrayList<>();
    private static final List<Object> LISTENERS = new ArrayList<>();
    private static int transferIds;
    /** The last answer for a recipe in a menu, kept briefly because the button is asked about every frame. */
    private record Cached(long at, TransferState state) {
    }

    private static final Map<String, Cached> CACHE = new HashMap<>();
    private static final long CACHE_MS = 400;

    private JeiTransfers() {
    }

    static synchronized void add(Object handler, String typeUid) {
        HANDLERS.add(new Registered(handler, typeUid));
    }

    static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    static synchronized void addListener(Object listener) {
        LISTENERS.add(listener);
    }

    static synchronized List<Object> listeners() {
        return List.copyOf(LISTENERS);
    }

    static synchronized void clear() {
        LISTENERS.clear();
        HANDLERS.clear();
        CACHE.clear();
    }

    /** Registers one BEI handler per menu class that a JEI handler was registered for. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static synchronized void install() {
        TransferHost.removeIf(INSTALLED::contains);
        INSTALLED.clear();
        Map<Class<?>, List<Registered>> byMenu = new LinkedHashMap<>();
        for (Registered registered : HANDLERS) {
            try {
                Method containerClass = find(registered.handler().getClass(), "getContainerClass", 0);
                Class<?> menu = registered.handler() instanceof Basic basic ? basic.menuClass()
                        : containerClass == null ? null : (Class<?>) containerClass.invoke(registered.handler());
                if (menu == null) {
                    continue;
                }
                byMenu.computeIfAbsent(menu, key -> new ArrayList<>()).add(registered);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                Bei.LOGGER.warn("A JEI transfer handler has no container class; skipped", e);
            }
        }
        for (Map.Entry<Class<?>, List<Registered>> entry : byMenu.entrySet()) {
            if (!AbstractContainerMenu.class.isAssignableFrom(entry.getKey())) {
                continue;
            }
            Adapter adapter = new Adapter((Class<AbstractContainerMenu>) entry.getKey(), List.copyOf(entry.getValue()));
            INSTALLED.add(adapter);
            TransferHost.register(adapter);
        }
        if (!byMenu.isEmpty()) {
            Bei.LOGGER.info("JEI bridge: {} transfer handler(s) for {} menu class(es)", HANDLERS.size(), byMenu.size());
        }
    }

    private static final class Adapter implements com.itmecasey.bei.api.IRecipeTransferHandler<AbstractContainerMenu> {
        private final Class<AbstractContainerMenu> menuClass;
        private final List<Registered> handlers;

        Adapter(Class<AbstractContainerMenu> menuClass, List<Registered> handlers) {
            this.menuClass = menuClass;
            this.handlers = handlers;
        }

        @Override
        public Class<AbstractContainerMenu> menuClass() {
            return menuClass;
        }

        @Override
        public TransferState check(TransferContext<AbstractContainerMenu> context) {
            String key = context.recipe().id() + "|" + System.identityHashCode(context.menu()) + "|" + context.control();
            long now = System.currentTimeMillis();
            synchronized (CACHE) {
                Cached cached = CACHE.get(key);
                if (cached != null && now - cached.at() < CACHE_MS) {
                    return cached.state();
                }
            }
            TransferState state = compute(context);
            synchronized (CACHE) {
                if (CACHE.size() > 500) {
                    CACHE.clear();
                }
                CACHE.put(key, new Cached(now, state));
            }
            return state;
        }

        private TransferState compute(TransferContext<AbstractContainerMenu> context) {
            Target target = target(context.recipe(), handlers);
            if (target == null) {
                return TransferState.hidden();
            }
            Object answer = run(target, context, false);
            if (answer == NOT_RUN) {
                return TransferState.hidden();
            }
            JeiViews.Failure failure = JeiViews.failure(answer);
            if (failure == null) {
                return TransferState.enabled();
            }
            if (failure.type() == IRecipeTransferError.Type.INTERNAL) {
                return TransferState.hidden();
            }
            return TransferState.disabled(failure.message() != null ? failure.message() : Component.literal("Cannot transfer this recipe"));
        }

        @Override
        public boolean transfer(TransferContext<AbstractContainerMenu> context) {
            Target target = target(context.recipe(), handlers);
            if (target == null) {
                return false;
            }
            Object answer = run(target, context, true);
            return answer != NOT_RUN && JeiViews.failure(answer) == null;
        }
    }

    private static final Object NOT_RUN = new Object();

    /** Lets unit tests run a handler without a player in a world. */
    static boolean allowNoPlayer;

    /** The JEI handler and recipe object that answer for a BEI recipe. */
    private record Target(Registered registered, Object recipe, List<JeiViews.SlotSpec> slots) {
    }

    private static @Nullable Target target(TransferRecipe recipe, List<Registered> handlers) {
        JeiBridge.Bridged bridged = JeiBridge.bridged(recipe.id());
        String uid;
        Object object;
        List<JeiViews.SlotSpec> slots;
        if (bridged != null) {
            uid = bridged.typeUid();
            object = bridged.recipe();
            slots = bridged.slots();
        } else if (recipe.type().endsWith("Crafting")) {
            uid = "minecraft:crafting";
            object = JeiBridge.vanillaRecipe(recipe);
            slots = new ArrayList<>();
            for (List<ItemStack> input : recipe.inputs()) {
                slots.add(new JeiViews.SlotSpec(RecipeIngredientRole.INPUT, input, null));
            }
            slots.add(new JeiViews.SlotSpec(RecipeIngredientRole.OUTPUT, recipe.outputs(), null));
        } else {
            return null;
        }
        if (object == null) {
            return null;
        }
        for (Registered registered : handlers) {
            if (registered.typeUid().isEmpty() || registered.typeUid().equals(uid)) {
                return new Target(registered, object, slots);
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object run(Target target, TransferContext<AbstractContainerMenu> context, boolean doTransfer) {
        Minecraft game = Minecraft.getInstance();
        Player player = game == null ? null : game.player;
        if (player == null && !allowNoPlayer) {
            return NOT_RUN;
        }
        IRecipeSlotsView view = JeiViews.slots(target.slots());
        try {
            Object handler = target.registered().handler();
            if (handler instanceof Basic basic) {
                List<List<ItemStack>> inputs = new ArrayList<>();
                for (JeiViews.SlotSpec slot : target.slots()) {
                    if (slot.role() == RecipeIngredientRole.INPUT && inputs.size() < basic.slotCount()) {
                        inputs.add(slot.items());
                    }
                }
                boolean ok = doTransfer ? com.itmecasey.bei.client.SlotFill.fill(basic.firstSlot(), inputs, context.control())
                        : com.itmecasey.bei.client.SlotFill.can(basic.firstSlot(), inputs);
                return ok ? null : JeiViews.userError(Component.literal("Missing ingredients"));
            }
            Method transfer = find(handler.getClass(), "transferRecipe", 6);
            if (transfer == null) {
                return NOT_RUN;
            }
            if (!doTransfer || listeners().isEmpty()) {
                return transfer.invoke(handler, context.menu(), target.recipe(), view, player, context.control(), doTransfer);
            }
            Object transferContext = listenerContext(context, target, view, player);
            notifyListeners("beforeRecipeTransfer", transferContext, null);
            Object answer = transfer.invoke(handler, context.menu(), target.recipe(), view, player, context.control(), doTransfer);
            notifyListeners("afterRecipeTransfer", transferContext, JeiViews.failure(answer) == null ? "SUCCESS" : "REJECTED");
            return answer;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Bei.LOGGER.warn("A JEI transfer handler failed on {}", context.recipe().id(), e.getCause());
            return NOT_RUN;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Bei.LOGGER.warn("A JEI transfer handler failed on {}", context.recipe().id(), e);
            return NOT_RUN;
        }
    }

    /** JEI's transfer context for the listeners: what is being transferred, into which menu, by whom. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object listenerContext(TransferContext<AbstractContainerMenu> context, Target target, IRecipeSlotsView view, Player player) {
        Class<?> type = JeiBridge.ifaceClass("mezz.jei.api.recipe.transfer.IRecipeTransferContext");
        if (type == null) {
            return null;
        }
        int id;
        synchronized (JeiTransfers.class) {
            id = ++transferIds;
        }
        String uid = target.registered().typeUid();
        return JeiBridge.proxy((Class) type, (self, method, args) -> switch (method.getName()) {
            case "getTransferId" -> id;
            case "getRecipe" -> target.recipe();
            case "getContainer" -> context.menu();
            case "getRecipeSlots" -> view;
            case "getPlayer" -> player;
            case "isMaxTransfer" -> context.control();
            case "getScreen" -> Minecraft.getInstance() == null ? null : Minecraft.getInstance().screen;
            case "getRecipeType" -> {
                JeiBridge.Bridged bridged = JeiBridge.bridged(context.recipe().id());
                yield bridged == null ? null : bridged.category().getRecipeType();
            }
            default -> JeiBridge.fallback(method.getReturnType());
        });
    }

    private static void notifyListeners(String method, Object transferContext, @Nullable String result) {
        if (transferContext == null) {
            return;
        }
        for (Object listener : listeners()) {
            Method call = find(listener.getClass(), method, result == null ? 1 : 2);
            if (call == null) {
                continue;
            }
            try {
                if (result == null) {
                    call.invoke(listener, transferContext);
                } else {
                    Object value = null;
                    for (Object constant : call.getParameterTypes()[1].getEnumConstants()) {
                        if (constant.toString().equals(result)) {
                            value = constant;
                        }
                    }
                    call.invoke(listener, transferContext, value);
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                Bei.LOGGER.debug("A JEI transfer listener failed", e);
            }
        }
    }

    /** JEI's transfer manager: finds the handler for a menu and category, and lists the listeners. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object manager(Class<?> type) {
        return JeiBridge.proxy((Class) type, (self, method, args) -> {
            if (method.getName().equals("getRecipeTransferListeners")) {
                return listeners();
            }
            if (method.getName().equals("getRecipeTransferHandler") && args.length == 2 && args[1] instanceof mezz.jei.api.recipe.category.IRecipeCategory<?> category) {
                String uid = category.getRecipeType().getUid().toString();
                synchronized (JeiTransfers.class) {
                    for (Registered registered : HANDLERS) {
                        Method container = find(registered.handler().getClass(), "getContainerClass", 0);
                        boolean menuMatches = registered.handler() instanceof Basic basic ? basic.menuClass().isInstance(args[0]) : true;
                        try {
                            if (container != null) {
                                menuMatches = ((Class<?>) container.invoke(registered.handler())).isInstance(args[0]);
                            }
                        } catch (ReflectiveOperationException | RuntimeException e) {
                            menuMatches = false;
                        }
                        if (menuMatches && (registered.typeUid().isEmpty() || registered.typeUid().equals(uid))
                                && !(registered.handler() instanceof Basic)) {
                            return Optional.of(registered.handler());
                        }
                    }
                }
                return Optional.empty();
            }
            return JeiBridge.fallback(method.getReturnType());
        });
    }

    /** Looks up a method by name on an object's class, for the few JEI calls whose signature differs between versions. */
    static @Nullable Method find(Class<?> type, String name, int parameters) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameters) {
                return callable(method);
            }
        }
        return null;
    }

    /** A public method on a non-public class cannot be invoked from here; the same method on a public interface or superclass can. */
    private static Method callable(Method method) {
        if (java.lang.reflect.Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
            return method;
        }
        java.util.ArrayDeque<Class<?>> queue = new java.util.ArrayDeque<>();
        queue.add(method.getDeclaringClass());
        while (!queue.isEmpty()) {
            Class<?> type = queue.poll();
            if (java.lang.reflect.Modifier.isPublic(type.getModifiers())) {
                for (Method candidate : type.getMethods()) {
                    if (candidate.getName().equals(method.getName()) && candidate.getParameterCount() == method.getParameterCount()) {
                        return candidate;
                    }
                }
            }
            if (type.getSuperclass() != null) {
                queue.add(type.getSuperclass());
            }
            queue.addAll(java.util.Arrays.asList(type.getInterfaces()));
        }
        return method;
    }
}
