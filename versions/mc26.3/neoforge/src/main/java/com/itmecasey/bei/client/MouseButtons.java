package com.itmecasey.bei.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.jetbrains.annotations.Nullable;

/**
 * Mouse button numbers. Minecraft 26.3 renumbered them (left is 1, middle 2, right 3), so clicks are compared
 * with the game's own constants. BEI's plugin API keeps the JEI numbering (0 left, 1 right, 2 middle).
 */
public final class MouseButtons {
    private MouseButtons() {
    }

    public static boolean isLeft(int button) {
        return button == InputConstants.MOUSE_BUTTON_LEFT;
    }

    public static boolean isRight(int button) {
        return button == InputConstants.MOUSE_BUTTON_RIGHT;
    }

    /** What a click on an item opens: false for recipes (left), true for uses (right), null for any other button. */
    public static @Nullable Boolean usesFor(int button) {
        if (isLeft(button)) {
            return Boolean.FALSE;
        }
        return isRight(button) ? Boolean.TRUE : null;
    }

    /** A game button as the plugin API numbers it (0 left, 1 right, 2 middle). */
    public static int toApi(int button) {
        if (isLeft(button)) {
            return 0;
        }
        if (isRight(button)) {
            return 1;
        }
        return button == InputConstants.MOUSE_BUTTON_MIDDLE ? 2 : button;
    }

    /** A plugin API button as the game numbers it. */
    public static int fromApi(int button) {
        return switch (button) {
            case 0 -> InputConstants.MOUSE_BUTTON_LEFT;
            case 1 -> InputConstants.MOUSE_BUTTON_RIGHT;
            case 2 -> InputConstants.MOUSE_BUTTON_MIDDLE;
            default -> button;
        };
    }
}
