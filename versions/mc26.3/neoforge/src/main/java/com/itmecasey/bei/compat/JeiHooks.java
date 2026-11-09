package com.itmecasey.bei.compat;

import com.itmecasey.bei.Bei;
import com.itmecasey.bei.index.BeiRecipe;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Safe entry points to the experimental JEI bridge. The bridge is only compiled into test builds
 * (-Pbei_jei_bridge=true), so it is reached by reflection; when it is absent, or anything about it
 * fails, these return empty results and BEI carries on.
 */
public final class JeiHooks {
    private static final Object UNSET = new Object();
    private static Object bridge = UNSET;
    private static boolean failed;

    private JeiHooks() {
    }

    private static synchronized Class<?> bridgeClass() {
        if (bridge == UNSET) {
            try {
                bridge = Class.forName("com.itmecasey.bei.compat.JeiBridge", false, JeiHooks.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError e) {
                bridge = null;
            }
        }
        return failed ? null : (Class<?>) bridge;
    }

    private static synchronized Object call(String name, Class<?>[] types, Object[] args, Object fallback) {
        Class<?> type = bridgeClass();
        if (type == null) {
            return fallback;
        }
        try {
            Method method = type.getMethod(name, types);
            return method.invoke(null, args);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed = true;
            Bei.LOGGER.warn("The JEI bridge failed and is switched off; BEI continues without it", e);
            return fallback;
        }
    }

    public static void loadOnce() {
        call("loadOnce", new Class<?>[0], new Object[0], null);
    }

    /** Runs the JEI plugins again; the game's recipes just arrived. */
    public static void reload() {
        call("reload", new Class<?>[0], new Object[0], null);
    }

    @SuppressWarnings("unchecked")
    public static List<BeiRecipe> contribute() {
        return (List<BeiRecipe>) call("contribute", new Class<?>[0], new Object[0], List.of());
    }

    public static boolean handlesRecipeType(String recipeType) {
        return (Boolean) call("handlesRecipeType", new Class<?>[] {String.class}, new Object[] {recipeType}, Boolean.FALSE);
    }
}
