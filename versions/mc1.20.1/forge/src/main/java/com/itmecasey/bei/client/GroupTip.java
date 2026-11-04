package com.itmecasey.bei.client;

import com.itmecasey.bei.index.ItemEntry;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.itmecasey.bei.client.Draw;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

/**
 * The tooltip of a group: its name and an icon for every item in it, in a frame with doubled
 * corners. Hold Shift while hovering to freeze it in place; it stays until Shift is released, so the mouse
 * can cross the gap into it to see each item's own tooltip and click one.
 */
final class GroupTip {
    private static final int CELL = 18;
    private static final int MAX_COLS = 8;
    private static final int MAX_SHOWN = 40;

    private static @Nullable Groups.Group frozen;
    private static int frozenX;
    private static int frozenY;
    /** Layout of the box drawn last frame, for hit tests. */
    private static int boxX;
    private static int boxY;
    private static int boxW;
    private static int boxH;
    private static int gridX;
    private static int gridY;
    private static int columns;
    private static List<ItemEntry> shown = List.of();

    private GroupTip() {
    }

    static boolean isFrozen() {
        return frozen != null;
    }

    static boolean insideFrozen(double mx, double my) {
        return frozen != null && Ui.in(mx, my, boxX, boxY, boxW, boxH);
    }

    /** The item icon under the mouse in the frozen tooltip, if any. */
    static @Nullable ItemEntry itemAt(double mx, double my) {
        if (frozen == null || !Ui.in(mx, my, gridX, gridY, columns * CELL, ((shown.size() + columns - 1) / columns) * CELL)) {
            return null;
        }
        int index = (int) ((my - gridY) / CELL) * columns + (int) ((mx - gridX) / CELL);
        return index >= 0 && index < shown.size() ? shown.get(index) : null;
    }

    /** Draws the tooltip for the hovered group, or the frozen one. Returns whether it drew anything. */
    static boolean render(Draw g, Font font, @Nullable Groups.Group hovered, int mx, int my) {
        boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        // Once frozen it stays until Shift is let go, wherever the mouse is.
        if (frozen != null && !shift) {
            frozen = null;
        }
        Groups.Group group = frozen != null ? frozen : hovered;
        if (group == null) {
            return false;
        }
        List<ItemEntry> members = group.members;
        shown = members.subList(0, Math.min(MAX_SHOWN, members.size()));
        columns = Math.max(1, Math.min(MAX_COLS, shown.size()));
        int rows = (shown.size() + columns - 1) / columns;
        boolean more = members.size() > shown.size();
        String title = group.name + " (" + members.size() + ")";
        String hint = I18n.get("bei.group.hint");
        int inner = Math.max(Math.max(font.width(title), columns * CELL), frozen == null ? font.width(hint) : 0);
        boxW = inner + 8;
        boxH = 4 + 10 + 2 + rows * CELL + (more ? 10 : 0) + (frozen == null ? 10 : 0) + 4;
        if (frozen != null) {
            boxX = frozenX;
            boxY = frozenY;
        } else {
            boxX = mx + 12;
            boxY = my - 12;
            if (boxX + boxW > g.guiWidth()) {
                boxX = Math.max(2, mx - boxW - 12);
            }
            boxY = Math.max(2, Math.min(boxY, g.guiHeight() - boxH - 2));
            if (shift) {
                frozen = group;
                frozenX = boxX;
                frozenY = boxY;
            }
        }
        gridX = boxX + 4;
        gridY = boxY + 16;

        g.nextStratum();
        Ui.tipFrame(g, boxX, boxY, boxW, boxH, Ui.accent(), true);
        g.text(font, title, boxX + 4, boxY + 4, Ui.accent(), Ui.shadow());
        ItemEntry hoveredItem = frozen != null ? itemAt(mx, my) : null;
        for (int i = 0; i < shown.size(); i++) {
            int x = gridX + (i % columns) * CELL;
            int y = gridY + (i / columns) * CELL;
            if (shown.get(i) == hoveredItem) {
                Ui.round(g, x, y, CELL, CELL, Ui.accentSoft());
            }
            g.item(shown.get(i).stack, x + 1, y + 1);
        }
        int textY = gridY + rows * CELL + 1;
        if (more) {
            g.text(font, I18n.get("bei.group.more", members.size() - shown.size()), boxX + 4, textY, Ui.dim(), false);
            textY += 10;
        }
        if (frozen == null) {
            g.text(font, hint, boxX + 4, textY, Ui.dim(), false);
        }
        if (hoveredItem != null) {
            Ui.tip(g, font, hoveredItem.stack, mx, my);
        }
        return true;
    }
}
