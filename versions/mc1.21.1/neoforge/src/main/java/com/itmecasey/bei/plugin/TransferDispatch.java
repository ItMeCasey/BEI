package com.itmecasey.bei.plugin;

import com.itmecasey.bei.api.TransferState;
import java.util.List;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/** The choices behind the plus button, free of game types so a unit test can run them. */
public final class TransferDispatch {
    private TransferDispatch() {
    }

    /** What the button does. */
    public enum Mode {
        /** No button. */
        HIDDEN,
        /** A plugin handler's button, clickable. */
        HANDLER,
        /** A plugin handler's button, dimmed with a reason. */
        DISABLED,
        /** BEI's own fill button for the vanilla stations. */
        BUILT_IN
    }

    /** The handler for the menu: of those whose class matches, the most specific; ties go to the first registered. */
    public static <H> @Nullable H find(List<H> handlers, Object menu, Function<H, Class<?>> menuClassOf) {
        H best = null;
        Class<?> bestClass = null;
        for (H handler : handlers) {
            Class<?> type = menuClassOf.apply(handler);
            if (type.isInstance(menu) && (bestClass == null || bestClass.isAssignableFrom(type) && bestClass != type)) {
                best = handler;
                bestClass = type;
            }
        }
        return best;
    }

    /**
     * The button for a recipe.
     *
     * @param kind    the matched handler's answer, or null when no handler matched the menu
     * @param builtIn whether BEI's own fill applies to the recipe
     */
    public static Mode resolve(@Nullable TransferState.Kind kind, boolean builtIn) {
        if (kind == TransferState.Kind.ENABLED) {
            return Mode.HANDLER;
        }
        if (kind == TransferState.Kind.DISABLED) {
            return Mode.DISABLED;
        }
        return builtIn ? Mode.BUILT_IN : Mode.HIDDEN;
    }
}
