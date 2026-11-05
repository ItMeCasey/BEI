package com.itmecasey.bei.client;

import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * The text lines and graphical parts of an item tooltip as the game's gather step sees them, so a mod
 * that removes a line while it gathers (because it draws the same thing as a component) is obeyed.
 */
final class TooltipLines {
    private TooltipLines() {
    }

    /** The elements the gather event starts from: every line, with the item's image right after the name. */
    static List<Either<FormattedText, TooltipComponent>> elements(List<Component> lines, Optional<TooltipComponent> image) {
        List<Either<FormattedText, TooltipComponent>> elements = new ArrayList<>();
        for (Component line : lines) {
            elements.add(Either.left(line));
        }
        image.ifPresent(component -> TooltipEdit.insertImage(elements, Either.right(component)));
        return elements;
    }

    /** The text lines still in the elements after the gather: the ones no mod removed, plus any a mod added. */
    static List<Component> keptLines(List<Either<FormattedText, TooltipComponent>> elements) {
        return TooltipEdit.texts(elements, element -> element.left()
                .map(text -> text instanceof Component component ? component : Component.literal(text.getString())));
    }
}
