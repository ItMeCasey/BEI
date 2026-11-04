package com.itmecasey.bei.client;

import com.itmecasey.bei.BeiConfig;

/** Where a popup sits, and dragging it by its title bar; the spot is remembered between sessions. */
final class WindowPos {
    private final String name;
    private int x = Integer.MIN_VALUE;
    private int y;
    private boolean dragging;
    private int offX;
    private int offY;

    WindowPos(String name) {
        this.name = name;
    }

    int x() {
        return x;
    }

    int y() {
        return y;
    }

    /** Call every frame before drawing: follows the mouse while dragging, keeps the window on screen. */
    void update(int mouseX, int mouseY, int screenW, int screenH, int w, int h) {
        if (x == Integer.MIN_VALUE) {
            int[] saved = BeiConfig.windowPos(name);
            x = saved[0] < 0 ? (screenW - w) / 2 : saved[0];
            y = saved[1] < 0 ? (screenH - h) / 2 : saved[1];
        }
        if (dragging) {
            if (Ui.leftDown()) {
                x = mouseX - offX;
                y = mouseY - offY;
            } else {
                dragging = false;
                BeiConfig.setWindowPos(name, x, y);
            }
        }
        x = Math.max(0, Math.min(x, screenW - w));
        y = Math.max(0, Math.min(y, screenH - h));
    }

    void startDrag(double mouseX, double mouseY) {
        dragging = true;
        offX = (int) mouseX - x;
        offY = (int) mouseY - y;
    }

    boolean dragging() {
        return dragging;
    }

    /** Puts the window back in the middle of the screen. */
    void reset() {
        x = Integer.MIN_VALUE;
        BeiConfig.setWindowPos(name, -1, -1);
    }
}
