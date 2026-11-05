package com.itmecasey.bei.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** The list handling behind {@link TooltipLines}, free of game types so a unit test can run it. */
final class TooltipEdit {
    private TooltipEdit() {
    }

    /** Puts the item's image right after the first element (the name), or first when there is no name. */
    static <E> void insertImage(List<E> elements, E image) {
        elements.add(Math.min(1, elements.size()), image);
    }

    /** The text of every element that has some, in order; image elements are skipped. */
    static <E, T> List<T> texts(List<E> elements, Function<E, Optional<T>> textOf) {
        List<T> texts = new ArrayList<>();
        for (E element : elements) {
            textOf.apply(element).ifPresent(texts::add);
        }
        return texts;
    }
}
