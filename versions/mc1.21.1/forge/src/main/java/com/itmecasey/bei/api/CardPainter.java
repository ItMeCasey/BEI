package com.itmecasey.bei.api;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the extras of a recipe card: arrows, progress bars, text, an energy meter. BEI draws the
 * card background and the item slots itself; this paints on top of the background and under the
 * items. Coordinates are local to the card area: (0, 0) is its top left and it is
 * {@link RecipeCategory#width()} by {@link RecipeCategory#height()} pixels.
 */
@FunctionalInterface
public interface CardPainter {
    /**
     * @param mouseX the mouse position inside the card area (outside it when the mouse is elsewhere)
     */
    void paint(GuiGraphics graphics, CustomRecipe recipe, int mouseX, int mouseY);

    /** Lines for a tooltip when the mouse is over something this painter drew, or null for none. */
    default @Nullable List<Component> tooltip(CustomRecipe recipe, int mouseX, int mouseY) {
        return null;
    }

    /**
     * Draws over the card's items and slot boxes (a level overlay on a slot, a highlight). Same coordinates as
     * {@link #paint}. Nothing by default.
     */
    default void paintOverlay(GuiGraphics graphics, CustomRecipe recipe, int mouseX, int mouseY) {
    }

    /** A mouse button went down inside the card area; return true when the painter used the click (it then gets drag and release). */
    default boolean mouseClicked(CustomRecipe recipe, int mouseX, int mouseY, int button) {
        return false;
    }

    default boolean mouseReleased(CustomRecipe recipe, int mouseX, int mouseY, int button) {
        return false;
    }

    /** The mouse moved with the button held after a click the painter used; {@code dragX} and {@code dragY} are the move. */
    default boolean mouseDragged(CustomRecipe recipe, int mouseX, int mouseY, int button, double dragX, double dragY) {
        return false;
    }

    /** The wheel turned while the mouse was inside the card area; return true to keep the popup from scrolling. */
    default boolean mouseScrolled(CustomRecipe recipe, int mouseX, int mouseY, double scroll) {
        return false;
    }

    /** Extra tooltip lines for an item shown in one of the card's slots, or null for none. */
    default @Nullable List<Component> slotTooltip(CustomRecipe recipe, net.minecraft.world.item.ItemStack stack) {
        return null;
    }

    /** Where this card wants its plus (transfer) button, {x, y} in card coordinates, or null for BEI's place. */
    default int @Nullable [] transferButton(CustomRecipe recipe) {
        return null;
    }
}
