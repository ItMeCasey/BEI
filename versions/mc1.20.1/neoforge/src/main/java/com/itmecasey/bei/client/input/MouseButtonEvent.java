package com.itmecasey.bei.client.input;

/** A mouse click as the screen events report it in 1.20.1, shaped like the newer game's event object. */
public record MouseButtonEvent(double x, double y, int button, int modifiers) {
}
