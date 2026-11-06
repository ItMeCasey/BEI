package com.itmecasey.bei.api;

import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Fills a menu's grid from a recipe when the player clicks the plus button in BEI's recipe popup. Register one with
 * {@link ITransferRegistration#register} from {@link IBeiPlugin#registerTransfers}.
 *
 * <p>Both methods run on the client. BEI never sends packets: {@link #transfer} sends whatever the mod needs.
 * When several handlers match the open menu, the one registered for the most specific menu class is used (ties:
 * the first registered). A handler that answers {@link TransferState#hidden()} lets BEI fall back to its own fill
 * button for the vanilla stations.
 *
 * @param <M> the menu class this handler works on
 */
public interface IRecipeTransferHandler<M extends AbstractContainerMenu> {
    /** The menu class the handler is for; it also handles subclasses. */
    Class<M> menuClass();

    /** Decides the button for one recipe; called while the popup draws, so keep it cheap. */
    TransferState check(TransferContext<M> context);

    /**
     * Does the transfer.
     *
     * @return true to close the recipe popup afterward
     */
    boolean transfer(TransferContext<M> context);
}
