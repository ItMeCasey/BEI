package com.itmecasey.bei.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TooltipEditTest {
    /** Text elements are plain strings and an image element starts with "#", standing in for the game's Either. */
    private static Optional<String> textOf(String element) {
        return element.startsWith("#") ? Optional.empty() : Optional.of(element);
    }

    @Test
    void imageGoesRightAfterTheName() {
        List<String> elements = new ArrayList<>(List.of("Bread", "Stackable"));
        TooltipEdit.insertImage(elements, "#bar");
        assertEquals(List.of("Bread", "#bar", "Stackable"), elements);
    }

    @Test
    void imageGoesFirstWhenThereAreNoLines() {
        List<String> elements = new ArrayList<>();
        TooltipEdit.insertImage(elements, "#bar");
        assertEquals(List.of("#bar"), elements);
    }

    @Test
    void imagesAreNotLines() {
        assertEquals(List.of("Bread", "Stackable"), TooltipEdit.texts(List.of("Bread", "#bar", "Stackable"), TooltipEditTest::textOf));
    }

    @Test
    void linesAModRemovedWhileGatheringStayRemoved() {
        List<String> elements = new ArrayList<>(List.of("Bread", "Hunger: 5", "Stackable"));
        elements.removeIf(e -> e.startsWith("Hunger"));
        elements.add("#bar");
        assertEquals(List.of("Bread", "Stackable"), TooltipEdit.texts(elements, TooltipEditTest::textOf));
    }

    @Test
    void linesAModAddedWhileGatheringAreKept() {
        List<String> elements = new ArrayList<>(List.of("Bread"));
        elements.add("Added");
        assertEquals(List.of("Bread", "Added"), TooltipEdit.texts(elements, TooltipEditTest::textOf));
    }
}
