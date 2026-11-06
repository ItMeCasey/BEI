package com.itmecasey.bei.api;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * What a {@link IRecipeTransferHandler} says about one recipe: the plus button is {@link Kind#ENABLED} (clickable),
 * {@link Kind#DISABLED} (shown dimmed, with {@code reason} in its tooltip) or {@link Kind#HIDDEN} (this handler does
 * not apply to the recipe, so BEI shows its own fill button if it has one, or none).
 */
public record TransferState(Kind kind, @Nullable Component reason) {
    /** How the plus button looks. */
    public enum Kind {
        ENABLED, DISABLED, HIDDEN
    }

    /** The recipe can be transferred. */
    public static TransferState enabled() {
        return new TransferState(Kind.ENABLED, null);
    }

    /** The button shows but cannot be clicked; {@code reason} is its tooltip (for example "Missing: Iron Ingot"). */
    public static TransferState disabled(Component reason) {
        return new TransferState(Kind.DISABLED, reason);
    }

    /** This handler does not apply to the recipe: no button from it. */
    public static TransferState hidden() {
        return new TransferState(Kind.HIDDEN, null);
    }
}
