package com.itmecasey.bei.client;

import com.itmecasey.bei.BeiConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** The look shared by every BEI surface; every color comes from the current {@link Theme}. */
public final class Ui {
    private Ui() {
    }

    private static Theme theme() {
        return Themes.current();
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }

    /** A color with its alpha scaled by the opacity setting, so inner boxes fade with the backdrop. */
    private static int faded(int rgb, float floor) {
        float opacity = Math.max(floor, (float) BeiConfig.opacity());
        return (Math.round(255 * opacity) << 24) | rgb;
    }

    /** The backdrop color, with the player's opacity setting as its alpha. */
    public static int bg() {
        return faded(theme().bg(), 0F);
    }

    public static int card() {
        return faded(theme().card(), 0F);
    }

    public static int cardHover() {
        return faded(theme().cardHover(), 0F);
    }

    public static int field() {
        return faded(theme().field(), 0F);
    }

    public static int accent() {
        return opaque(theme().accent());
    }

    /** A very light wash of the accent color, for cells that belong to an expanded group. */
    public static int accentFaint() {
        return 0x26000000 | theme().accent();
    }

    /** A tooltip frame: backdrop and outline, with the corners drawn twice as thick when asked. */
    public static void tipFrame(GuiGraphicsExtractor g, int x, int y, int w, int h, int border, boolean doubleCorners) {
        round(g, x, y, w, h, faded(theme().tooltip(), 0.9F));
        g.outline(x, y, w, h, border);
        if (doubleCorners) {
            // A second line just outside each corner, so the corner is two pixels thick.
            int arm = 5;
            g.fill(x - 1, y - 1, x + arm, y, border);
            g.fill(x - 1, y - 1, x, y + arm, border);
            g.fill(x + w - arm, y - 1, x + w + 1, y, border);
            g.fill(x + w, y - 1, x + w + 1, y + arm, border);
            g.fill(x - 1, y + h, x + arm, y + h + 1, border);
            g.fill(x - 1, y + h - arm, x, y + h + 1, border);
            g.fill(x + w - arm, y + h, x + w + 1, y + h + 1, border);
            g.fill(x + w, y + h - arm, x + w + 1, y + h + 1, border);
        }
    }

    public static int accentSoft() {
        return 0x50000000 | theme().accent();
    }

    public static int text() {
        return opaque(theme().text());
    }

    public static int dim() {
        return opaque(theme().dim());
    }

    public static int good() {
        return opaque(theme().good());
    }

    public static int bad() {
        return opaque(theme().bad());
    }

    public static int warn() {
        return opaque(theme().warn());
    }

    public static int border() {
        return opaque(theme().border());
    }

    /** Text shadow is only for dark backdrops; on a light one it smears the letters. */
    public static boolean shadow() {
        return !light();
    }

    /** Whether the theme's backdrop is light. */
    public static boolean light() {
        int bg = theme().bg();
        return ((bg >> 16 & 0xFF) + (bg >> 8 & 0xFF) + (bg & 0xFF)) / 3 > 140;
    }

    /** White on the accent color, or black when the accent is pale. */
    public static int onAccent() {
        int c = theme().accent();
        return ((c >> 16 & 0xFF) * 3 + (c >> 8 & 0xFF) * 6 + (c & 0xFF)) / 10 > 150 ? 0xFF000000 : 0xFFFFFFFF;
    }

    /** Whether the left mouse button is down right now, read straight from the window. */
    public static boolean leftDown() {
        var game = Minecraft.getInstance();
        return game.mouseHandler.isLeftPressed();
    }

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** A plain filled rectangle with square corners. */
    public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
    }

    /** A square-cornered panel with a thin accent line along its top edge. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        round(g, x - 1, y - 1, w + 2, h + 2, light() ? 0x40000000 : 0x60000000);
        round(g, x, y, w, h, bg());
        g.fill(x, y, x + w, y + 1, accent());
    }

    /** A flat button; the caller handles clicks with {@link #in}. */
    public static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String label,
                              boolean hover, boolean active) {
        round(g, x, y, w, h, active ? accent() : hover ? cardHover() : card());
        g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, active ? onAccent() : text(), false);
    }

    /** A pill switch, 24 wide and 12 tall. */
    public static void toggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
        round(g, x, y, 24, 12, on ? accent() : border());
        round(g, on ? x + 13 : x + 1, y + 1, 10, 10, 0xFFFFFFFF);
    }

    /** A square checkbox with a tick when on. */
    public static void checkbox(GuiGraphicsExtractor g, int x, int y, boolean on) {
        round(g, x, y, 11, 11, on ? accent() : border());
        round(g, x + 1, y + 1, 9, 9, on ? accent() : field());
        if (on) {
            int[][] tick = {{2, 5}, {3, 6}, {4, 7}, {5, 6}, {6, 5}, {7, 4}, {8, 3}};
            for (int[] p : tick) {
                g.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 2, onAccent());
            }
        }
    }

    /** A small gear drawn from pixels, so it does not depend on a font glyph. */
    public static void gear(GuiGraphicsExtractor g, int x, int y, int color) {
        for (int dy = -5; dy <= 5; dy++) {
            for (int dx = -5; dx <= 5; dx++) {
                int d2 = dx * dx + dy * dy;
                boolean ring = d2 >= 7 && d2 <= 16;
                boolean tooth = (dx == 0 || dy == 0 || Math.abs(dx) == Math.abs(dy)) && d2 >= 17 && d2 <= 28;
                if (ring || tooth) {
                    g.fill(x + 5 + dx, y + 5 + dy, x + 6 + dx, y + 6 + dy, color);
                }
            }
        }
    }

    // ---- tooltips -------------------------------------------------------------------------------

    /** A one-line tooltip. */
    public static void tip(GuiGraphicsExtractor g, Font font, Component text, int mx, int my) {
        tip(g, font, List.of(text), mx, my);
    }

    /** A tooltip of plain lines, in the theme's colors. */
    public static void tip(GuiGraphicsExtractor g, Font font, List<Component> lines, int mx, int my) {
        box(g, font, lines, null, border(), text(), mx, my);
    }

    /**
     * An item's tooltip: its icon beside the name, then every line the game would show (so attack
     * damage, armor and the other attribute lines are there), durability, and with F3+H on the
     * item's mod. The border takes the item's rarity color.
     */
    public static void tip(GuiGraphicsExtractor g, Font font, ItemStack stack, int mx, int my) {
        Minecraft game = Minecraft.getInstance();
        if (game.level == null || game.player == null || stack.isEmpty()) {
            return;
        }
        boolean advanced = game.options.advancedItemTooltips;
        List<Component> lines = new ArrayList<>(stack.getTooltipLines(Item.TooltipContext.of(game.level), game.player,
                advanced ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL));
        // The game puts a blank line after the name; with the divider under the name it only looks like a gap.
        while (lines.size() > 1 && lines.get(1).getString().isEmpty()) {
            lines.remove(1);
        }
        if (!advanced && stack.isDamageableItem()) {
            int max = stack.getMaxDamage();
            lines.add(Component.translatable("item.durability", max - stack.getDamageValue(), max));
        }
        if (advanced) {
            lines.add(Component.literal(modName(stack)).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
        }
        Integer rarity = stack.getRarity() == Rarity.COMMON ? null : net.minecraft.network.chat.Style.EMPTY.applyFormat(stack.getRarity().color()).getColor().getValue();
        if (rarity != null && !lines.isEmpty()) {
            lines.set(0, lines.get(0).copy().withStyle(stack.getRarity().color()));
        }
        int rarityColor = rarity != null ? 0xFF000000 | rarity : border();
        box(g, font, lines, stack, rarityColor, rarity != null ? rarityColor : text(), mx, my);
    }

    private static String modName(ItemStack stack) {
        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        if (namespace.equals("minecraft")) {
            return "Minecraft";
        }
        return ModList.getModContainerById(namespace).map(container -> container.getModInfo().getDisplayName())
                .orElse(namespace.substring(0, 1).toUpperCase(Locale.ROOT) + namespace.substring(1));
    }

    /**
     * Draws a tooltip box next to the mouse. BEI draws its own because the game's deferred tooltip
     * is collected before mod render events run, so one queued from here would never appear.
     */
    private static void box(GuiGraphicsExtractor g, Font font, List<Component> lines, @Nullable ItemStack icon,
                            int borderColor, int nameColor, int mx, int my) {
        if (lines.isEmpty()) {
            return;
        }
        int width = 0;
        for (int i = 0; i < lines.size(); i++) {
            width = Math.max(width, font.width(lines.get(i)) + (icon != null && i == 0 ? 22 : 0));
        }
        int height;
        if (icon != null) {
            height = 16 + (lines.size() > 1 ? 6 + (lines.size() - 1) * 10 - 1 : 0) - (lines.size() > 1 ? 2 : 0);
        } else {
            height = lines.size() * 10 - 1;
        }
        int x = mx + 12;
        int y = my - 12;
        if (x + width + 8 > g.guiWidth()) {
            x = Math.max(2, mx - width - 16);
        }
        y = Math.max(4, Math.min(y, g.guiHeight() - height - 8));
        g.nextStratum();
        round(g, x - 4, y - 4, width + 8, height + 8, faded(theme().tooltip(), 0.9F));
        g.outline(x - 4, y - 4, width + 8, height + 8, borderColor);
        int textY = y;
        if (icon != null) {
            g.item(icon, x, y);
            g.text(font, lines.get(0), x + 22, y + 4, nameColor, shadow());
            if (lines.size() > 1) {
                g.fill(x, y + 19, x + width, y + 20, borderColor);
            }
            textY = y + 23;
        }
        for (int i = icon != null ? 1 : 0; i < lines.size(); i++) {
            g.text(font, lines.get(i), x, textY, text(), shadow());
            textY += 10;
        }
    }
}
