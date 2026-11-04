package com.itmecasey.bei.client.input;

/** A key press as the screen events report it in 1.20.1, shaped like the newer game's event object. */
public record KeyEvent(int key, int scancode, int modifiers) {
}
