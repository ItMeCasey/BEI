package com.itmecasey.bei.client;

import com.itmecasey.bei.BeiConfig;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.itmecasey.bei.client.Draw;
import com.itmecasey.bei.client.input.KeyEvent;
import com.itmecasey.bei.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The gear popup: BEI's settings as switches and checkboxes, a backdrop opacity slider, steppers
 * for a custom ingredient grid and a corner picker, applied as soon as they change. Drag the
 * title bar to move it.
 */
public final class SettingsPopup {
    private static final int ROW = 17;
    private static final int PW = 230;
    private static final int SLIDER_W = 80;

    private static final String[] KEYS = {"browser", "tray", "hud", "corner", "ender", "opacity", "browser_bg", "card_bg", "slot_bg",
            "custom", "columns", "rows", "theme", "cheat_target", "variants", "open_project"};
    private static final int ROWS = KEYS.length;
    private static final int OPACITY = 5;
    private static final int COLUMNS = 10;
    private static final int GRID_ROWS = 11;
    private static final int THEME = 12;
    private static final int CHEAT = 13;

    private static final WindowPos POS = new WindowPos("settings");
    private static boolean open;
    private static boolean sliding;
    private static int ph;

    private SettingsPopup() {
    }

    public static boolean isOpen() {
        return open;
    }

    public static void toggle() {
        RecipePopup.close();
        open = !open;
    }

    public static void close() {
        if (sliding) {
            BeiConfig.save();
        }
        open = false;
        sliding = false;
    }

    /** Which rows draw a checkbox (the rest are switches, sliders or steppers). */
    private static boolean isCheckbox(int row) {
        return row >= 6 && row <= 9;
    }

    private static boolean value(int row) {
        return switch (row) {
            case 0 -> BeiConfig.showBrowser();
            case 1 -> BeiConfig.tagTray();
            case 2 -> BeiConfig.hudVisible();
            case 4 -> BeiConfig.countEnderChest();
            case 6 -> BeiConfig.browserBackground();
            case 7 -> BeiConfig.cardBackground();
            case 8 -> BeiConfig.slotBackground();
            case 9 -> BeiConfig.customGrid();
            case 14 -> BeiConfig.itemVariants();
            default -> false;
        };
    }

    private static void flip(int row) {
        switch (row) {
            case 0 -> BeiConfig.setShowBrowser(!BeiConfig.showBrowser());
            case 1 -> BeiConfig.setTagTray(!BeiConfig.tagTray());
            case 2 -> BeiConfig.setHudVisible(!BeiConfig.hudVisible());
            case 3 -> {
                BeiConfig.Corner[] all = BeiConfig.Corner.values();
                BeiConfig.setHudCorner(all[(BeiConfig.hudCorner().ordinal() + 1) % all.length]);
            }
            case 4 -> BeiConfig.setCountEnderChest(!BeiConfig.countEnderChest());
            case 6 -> BeiConfig.setBrowserBackground(!BeiConfig.browserBackground());
            case 7 -> BeiConfig.setCardBackground(!BeiConfig.cardBackground());
            case 8 -> BeiConfig.setSlotBackground(!BeiConfig.slotBackground());
            case 9 -> BeiConfig.setCustomGrid(!BeiConfig.customGrid());
            case 12 -> BeiConfig.setTheme(Themes.next());
            case 13 -> BeiConfig.setCheatTarget(BeiConfig.cheatTarget() == BeiConfig.CheatTarget.CURSOR
                    ? BeiConfig.CheatTarget.INVENTORY : BeiConfig.CheatTarget.CURSOR);
            case 14 -> {
                BeiConfig.setItemVariants(!BeiConfig.itemVariants());
                com.itmecasey.bei.index.ItemIndex.rebuild();
                BrowserPanel.invalidate();
            }
            case 15 -> {
                close();
                Minecraft.getInstance().setScreen(new ProjectScreen(Minecraft.getInstance().screen));
            }
            default -> {
            }
        }
    }

    private static int rowY(int row) {
        return POS.y() + 28 + row * ROW;
    }

    private static int stepX(int px, boolean plus) {
        return plus ? px + PW - 8 - 13 : px + PW - 8 - 13 - 30 - 13;
    }

    private static int sliderX(int px) {
        return px + PW - 12 - SLIDER_W;
    }

    private static void setOpacityFrom(double mouseX, int px) {
        BeiConfig.setOpacity(0.3 + 0.7 * (mouseX - sliderX(px)) / SLIDER_W);
    }

    public static void render(Draw g, int screenW, int screenH, int mx, int my) {
        if (!open) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        ph = 28 + ROWS * ROW + 4;
        POS.update(mx, my, screenW, screenH, PW, ph);
        int px = POS.x();
        int py = POS.y();
        if (sliding) {
            if (Ui.leftDown()) {
                setOpacityFrom(mx, px);
            } else {
                sliding = false;
                BeiConfig.save();
            }
        }
        g.nextStratum();
        g.fill(0, 0, screenW, screenH, 0x70000000);
        Ui.panel(g, px, py, PW, ph);
        String tip = Ui.in(mx, my, px, py, PW - 24, 22) ? "bei.tip.drag" : null;
        Ui.gear(g, px + 7, py + 6, Ui.accent());
        g.text(font, I18n.get("bei.settings.title"), px + 25, py + 9, Ui.text(), Ui.shadow());
        boolean overClose = Ui.in(mx, my, px + PW - 18, py + 5, 12, 12);
        Ui.round(g, px + PW - 18, py + 5, 12, 12, overClose ? Ui.bad() : Ui.card());
        g.text(font, "×", px + PW - 15, py + 7, Ui.text(), false);
        if (overClose) {
            tip = "bei.tip.close";
        }

        for (int row = 0; row < ROWS; row++) {
            int y = rowY(row);
            boolean gridRow = row == COLUMNS || row == GRID_ROWS;
            boolean enabled = !gridRow || BeiConfig.customGrid();
            boolean hover = Ui.in(mx, my, px + 6, y, PW - 12, ROW - 2);
            Ui.round(g, px + 6, y, PW - 12, ROW - 2, hover && enabled ? Ui.cardHover() : Ui.card());
            g.text(font, I18n.get("bei.settings." + KEYS[row]), px + 12, y + 4, enabled ? Ui.text() : Ui.dim(), false);
            if (hover) {
                tip = "bei.settings." + KEYS[row] + ".tip";
            }
            if (row == THEME) {
                String name = Themes.current().name();
                g.text(font, name, px + PW - 12 - font.width(name), y + 4, Ui.accent(), false);
            } else if (row == CHEAT) {
                String target = I18n.get("bei.cheat." + BeiConfig.cheatTarget().name().toLowerCase(Locale.ROOT));
                g.text(font, target, px + PW - 12 - font.width(target), y + 4, Ui.accent(), false);
            } else if (row == 3) {
                String corner = I18n.get("bei.settings.corner." + BeiConfig.hudCorner().name().toLowerCase(Locale.ROOT));
                g.text(font, corner, px + PW - 12 - font.width(corner), y + 4, Ui.accent(), false);
            } else if (row == OPACITY) {
                int sx = sliderX(px);
                double fraction = (BeiConfig.opacity() - 0.3) / 0.7;
                Ui.round(g, sx, y + 6, SLIDER_W, 2, Ui.field());
                Ui.round(g, sx, y + 6, (int) (SLIDER_W * fraction), 2, Ui.accent());
                Ui.round(g, sx + (int) (SLIDER_W * fraction) - 2, y + 2, 4, 10, Ui.text());
                String percent = Math.round(BeiConfig.opacity() * 100) + "%";
                g.text(font, percent, sx - 6 - font.width(percent), y + 4, Ui.dim(), false);
            } else if (gridRow) {
                int value = row == COLUMNS ? BeiConfig.ingredientColumns() : BeiConfig.ingredientRows();
                int minusX = stepX(px, false);
                int plusX = stepX(px, true);
                Ui.button(g, font, minusX, y + 1, 13, 13, "-", enabled && Ui.in(mx, my, minusX, y + 1, 13, 13), false);
                Ui.button(g, font, plusX, y + 1, 13, 13, "+", enabled && Ui.in(mx, my, plusX, y + 1, 13, 13), false);
                String text = Integer.toString(value);
                g.text(font, text, minusX + 13 + (30 - font.width(text)) / 2, y + 4, enabled ? Ui.accent() : Ui.dim(), false);
            } else if (row == ROWS - 1) {
                g.text(font, "›", px + PW - 18, y + 4, Ui.dim(), false);
            } else if (isCheckbox(row)) {
                Ui.checkbox(g, px + PW - 12 - 11, y + 2, value(row));
            } else {
                Ui.toggle(g, px + PW - 12 - 24, y + 1, value(row));
            }
        }
        if (tip != null && !sliding) {
            Ui.tip(g, font, Component.translatable(tip), mx, my);
        }
    }

    public static boolean mouseClicked(MouseButtonEvent event) {
        if (!open) {
            return false;
        }
        double mx = event.x();
        double my = event.y();
        int px = POS.x();
        int py = POS.y();
        if (!Ui.in(mx, my, px, py, PW, ph) || Ui.in(mx, my, px + PW - 18, py + 5, 12, 12)) {
            close();
            return true;
        }
        if (Ui.in(mx, my, px, py, PW - 24, 22)) {
            POS.startDrag(mx, my);
            return true;
        }
        for (int row = 0; row < ROWS; row++) {
            int y = rowY(row);
            if (!Ui.in(mx, my, px + 6, y, PW - 12, ROW - 2)) {
                continue;
            }
            if (row == OPACITY) {
                sliding = true;
                setOpacityFrom(mx, px);
            } else if (row == COLUMNS || row == GRID_ROWS) {
                int delta = Ui.in(mx, my, stepX(px, true), y + 1, 13, 13) ? 1 : Ui.in(mx, my, stepX(px, false), y + 1, 13, 13) ? -1 : 0;
                if (delta != 0 && BeiConfig.customGrid()) {
                    if (row == COLUMNS) {
                        BeiConfig.setIngredientColumns(BeiConfig.ingredientColumns() + delta);
                    } else {
                        BeiConfig.setIngredientRows(BeiConfig.ingredientRows() + delta);
                    }
                }
            } else {
                flip(row);
            }
            break;
        }
        return true;
    }

    public static boolean keyPressed(KeyEvent event) {
        if (!open) {
            return false;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
        }
        return true;
    }
}
