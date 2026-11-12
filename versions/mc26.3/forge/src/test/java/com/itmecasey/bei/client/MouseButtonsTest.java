package com.itmecasey.bei.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;

class MouseButtonsTest {
    @Test
    void leftClickOpensRecipes() {
        assertEquals(Boolean.FALSE, MouseButtons.usesFor(InputConstants.MOUSE_BUTTON_LEFT));
    }

    @Test
    void rightClickOpensUses() {
        assertEquals(Boolean.TRUE, MouseButtons.usesFor(InputConstants.MOUSE_BUTTON_RIGHT));
    }

    @Test
    void otherButtonsOpenNothing() {
        assertNull(MouseButtons.usesFor(InputConstants.MOUSE_BUTTON_MIDDLE));
        assertNull(MouseButtons.usesFor(InputConstants.MOUSE_BUTTON_4));
    }

    @Test
    void pluginApiKeepsJeiNumbers() {
        assertEquals(0, MouseButtons.toApi(InputConstants.MOUSE_BUTTON_LEFT));
        assertEquals(1, MouseButtons.toApi(InputConstants.MOUSE_BUTTON_RIGHT));
        assertEquals(InputConstants.MOUSE_BUTTON_LEFT, MouseButtons.fromApi(0));
        assertEquals(InputConstants.MOUSE_BUTTON_RIGHT, MouseButtons.fromApi(1));
    }
}
