package com.itmecasey.bei.api;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    void paint(GuiGraphicsExtractor graphics, CustomRecipe recipe, int mouseX, int mouseY);

    /** Lines for a tooltip when the mouse is over something this painter drew, or null for none. */
    default @Nullable List<Component> tooltip(CustomRecipe recipe, int mouseX, int mouseY) {
        return null;
    }
}
