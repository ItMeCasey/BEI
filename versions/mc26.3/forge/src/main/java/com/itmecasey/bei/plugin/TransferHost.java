package com.itmecasey.bei.plugin;

import com.itmecasey.bei.Bei;
import com.itmecasey.bei.api.IRecipeTransferHandler;
import com.itmecasey.bei.api.TransferContext;
import com.itmecasey.bei.api.TransferRecipe;
import com.itmecasey.bei.api.TransferState;
import com.itmecasey.bei.index.BeiRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

/** Holds the plugins' recipe transfer handlers and answers the plus button. Client only. */
public final class TransferHost {
    private static final List<IRecipeTransferHandler<?>> HANDLERS = new ArrayList<>();

    private TransferHost() {
    }

    /** The plus button of one recipe in the open menu. */
    public record View(TransferDispatch.Mode mode, @Nullable Component reason) {
    }

    /** Registers a handler; a broken handler is skipped when it answers. */
    public static synchronized void register(IRecipeTransferHandler<?> handler) {
        HANDLERS.add(handler);
    }

    /** Forgets every handler (the plugins register again when they are loaded). */
    static synchronized void clear() {
        HANDLERS.clear();
    }

    private static @Nullable IRecipeTransferHandler<?> handlerFor(AbstractContainerMenu menu) {
        return TransferDispatch.find(HANDLERS, menu, IRecipeTransferHandler::menuClass);
    }

    private static TransferRecipe view(BeiRecipe recipe) {
        return new TransferRecipe(recipe.id(), recipe.type(), recipe.width(), recipe.inputs(), recipe.outputs());
    }

    @SuppressWarnings("unchecked")
    private static <M extends AbstractContainerMenu> TransferState check(IRecipeTransferHandler<M> handler,
                                                                        AbstractContainerMenu menu, BeiRecipe recipe,
                                                                        boolean shift, boolean control) {
        return handler.check(new TransferContext<>((M) menu, view(recipe), shift, control));
    }

    /** What the plus button of {@code recipe} should be in {@code menu}; {@code builtIn} says whether BEI's own fill applies. */
    public static View view(AbstractContainerMenu menu, BeiRecipe recipe, boolean builtIn, boolean shift, boolean control) {
        IRecipeTransferHandler<?> handler = handlerFor(menu);
        TransferState state = null;
        if (handler != null) {
            try {
                state = check(handler, menu, recipe, shift, control);
            } catch (RuntimeException | LinkageError e) {
                Bei.LOGGER.warn("A BEI transfer handler for {} failed", handler.menuClass().getName(), e);
            }
        }
        TransferDispatch.Mode mode = TransferDispatch.resolve(state == null ? null : state.kind(), builtIn);
        return new View(mode, state == null ? null : state.reason());
    }

    /** Runs the handler for the open menu; returns whether the popup should close. */
    @SuppressWarnings("unchecked")
    public static <M extends AbstractContainerMenu> boolean transfer(AbstractContainerMenu menu, BeiRecipe recipe,
                                                                     boolean shift, boolean control) {
        IRecipeTransferHandler<M> handler = (IRecipeTransferHandler<M>) handlerFor(menu);
        if (handler == null) {
            return false;
        }
        try {
            return handler.transfer(new TransferContext<>((M) menu, view(recipe), shift, control));
        } catch (RuntimeException | LinkageError e) {
            Bei.LOGGER.warn("A BEI transfer handler for {} failed", handler.menuClass().getName(), e);
            return false;
        }
    }
}
