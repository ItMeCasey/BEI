package com.itmecasey.bei.api;

/**
 * One item slot of a category's card.
 *
 * @param x      pixels from the left of the card area
 * @param y      pixels from the top of the card area
 * @param output whether the slot shows a recipe output (otherwise an input)
 */
public record SlotSpec(int x, int y, boolean output) {
}
