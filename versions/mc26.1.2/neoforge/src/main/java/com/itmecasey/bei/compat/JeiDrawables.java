package com.itmecasey.bei.compat;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import com.itmecasey.bei.api.CardPainter;
import com.itmecasey.bei.api.CustomRecipe;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * The drawables JEI plugins ask {@code IGuiHelper} for: textures, animated textures, blank space, item icons, the
 * slot and the recipe arrow. They draw for real so a plugin's own category drawing shows on BEI's recipe card. Only
 * the three calls that touch the game's drawing API (blit, item, fill) differ between Minecraft versions.
 */
final class JeiDrawables {
    private JeiDrawables() {
    }

    // ---- the version-specific drawing calls ---------------------------------------------------------

    static void blit(Object g, Identifier texture, int x, int y, int u, int v, int w, int h, int texW, int texH) {
        if (w > 0 && h > 0) {
            ((GuiGraphicsExtractor) g).blit(RenderPipelines.GUI_TEXTURED, texture, x, y, (float) u, (float) v, w, h, texW, texH);
        }
    }

    static void item(Object g, ItemStack stack, int x, int y) {
        ((GuiGraphicsExtractor) g).item(stack, x, y);
    }

    /** A GUI sprite (from the game's GUI atlas) stretched to the area. */
    static void sprite(Object g, Identifier sprite, int x, int y, int w, int h) {
        if (w > 0 && h > 0) {
            ((GuiGraphicsExtractor) g).blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
        }
    }

    static void drawStatic(IDrawableStatic drawable, Object g, int x, int y, int top, int bottom, int left, int right) {
        drawable.draw((GuiGraphicsExtractor) g, x, y, top, bottom, left, right);
    }

    static void text(Object g, List<net.minecraft.network.chat.FormattedText> lines, int width, int x, int y, int color) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int row = y;
        for (net.minecraft.network.chat.FormattedText line : lines) {
            for (net.minecraft.util.FormattedCharSequence piece : font.split(line, width)) {
                ((GuiGraphicsExtractor) g).text(font, piece, x, row, color, false);
                row += font.lineHeight;
            }
        }
    }

    static void scissor(Object g, int x1, int y1, int x2, int y2) {
        ((GuiGraphicsExtractor) g).enableScissor(x1, y1, x2, y2);
    }

    static void unscissor(Object g) {
        ((GuiGraphicsExtractor) g).disableScissor();
    }

    static void textLine(Object g, net.minecraft.util.FormattedCharSequence line, int x, int y, int color, boolean shadow) {
        ((GuiGraphicsExtractor) g).text(net.minecraft.client.Minecraft.getInstance().font, line, x, y, color, shadow);
    }

    static void drawSlotBackground(Object g, int x, int y) {
        fill(g, x, y, x + 18, y + 18, 0xFF373737);
        fill(g, x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        fill(g, x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    /** How tall these lines are when wrapped to the width. */
    static int textHeight(List<net.minecraft.network.chat.FormattedText> lines, int width, int spacing) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int rows = 0;
        for (net.minecraft.network.chat.FormattedText line : lines) {
            rows += font.split(line, Math.max(1, width)).size();
        }
        return rows * (font.lineHeight + spacing);
    }

    /** Wrapped, aligned text in a box (the alignments are the names LEFT/CENTER/RIGHT and TOP/CENTER/BOTTOM). */
    static void textBox(Object g, List<net.minecraft.network.chat.FormattedText> lines, int x, int y, int w, int h, int color,
            boolean shadow, int spacing, String horizontal, String vertical) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        List<net.minecraft.util.FormattedCharSequence> rows = new java.util.ArrayList<>();
        for (net.minecraft.network.chat.FormattedText line : lines) {
            rows.addAll(font.split(line, Math.max(1, w)));
        }
        int total = rows.size() * (font.lineHeight + spacing) - (rows.isEmpty() ? 0 : spacing);
        int row = switch (vertical) {
            case "CENTER" -> y + (h - total) / 2;
            case "BOTTOM" -> y + h - total;
            default -> y;
        };
        for (net.minecraft.util.FormattedCharSequence line : rows) {
            int width = font.width(line);
            int left = switch (horizontal) {
                case "CENTER" -> x + (w - width) / 2;
                case "RIGHT" -> x + w - width;
                default -> x;
            };
            textLine(g, line, left, row, color, shadow);
            row += font.lineHeight + spacing;
        }
    }

    static void translated(Object g, int x, int y, Runnable action) {
        var pose = ((GuiGraphicsExtractor) g).pose();
        pose.pushMatrix();
        pose.translate((float) x, (float) y);
        try {
            action.run();
        } finally {
            pose.popMatrix();
        }
    }

    /** What a bridged category does on its card; {@link #painter} wraps it in this version's CardPainter types. */
    interface Behavior {
        void paint(Object graphics, CustomRecipe recipe, int mouseX, int mouseY);

        void overlay(Object graphics, CustomRecipe recipe, int mouseX, int mouseY);

        @Nullable List<Component> tooltip(CustomRecipe recipe, int mouseX, int mouseY);

        boolean click(CustomRecipe recipe, int mouseX, int mouseY, int button);

        boolean release(CustomRecipe recipe, int mouseX, int mouseY, int button);

        boolean drag(CustomRecipe recipe, int mouseX, int mouseY, int button, double dragX, double dragY);

        boolean scroll(CustomRecipe recipe, int mouseX, int mouseY, double scroll);

        @Nullable List<Component> slotTooltip(CustomRecipe recipe, ItemStack stack);

        int @Nullable [] transferButton(CustomRecipe recipe);
    }

    static CardPainter painter(Behavior behavior) {
        return new CardPainter() {
            @Override
            public void paint(GuiGraphicsExtractor graphics, CustomRecipe recipe, int mouseX, int mouseY) {
                behavior.paint(graphics, recipe, mouseX, mouseY);
            }

            @Override
            public void paintOverlay(GuiGraphicsExtractor graphics, CustomRecipe recipe, int mouseX, int mouseY) {
                behavior.overlay(graphics, recipe, mouseX, mouseY);
            }

            @Override
            public @Nullable List<Component> tooltip(CustomRecipe recipe, int mouseX, int mouseY) {
                return behavior.tooltip(recipe, mouseX, mouseY);
            }

            @Override
            public boolean mouseClicked(CustomRecipe recipe, int mouseX, int mouseY, int button) {
                return behavior.click(recipe, mouseX, mouseY, button);
            }

            @Override
            public boolean mouseReleased(CustomRecipe recipe, int mouseX, int mouseY, int button) {
                return behavior.release(recipe, mouseX, mouseY, button);
            }

            @Override
            public boolean mouseDragged(CustomRecipe recipe, int mouseX, int mouseY, int button, double dragX, double dragY) {
                return behavior.drag(recipe, mouseX, mouseY, button, dragX, dragY);
            }

            @Override
            public boolean mouseScrolled(CustomRecipe recipe, int mouseX, int mouseY, double scroll) {
                return behavior.scroll(recipe, mouseX, mouseY, scroll);
            }

            @Override
            public @Nullable List<Component> slotTooltip(CustomRecipe recipe, ItemStack stack) {
                return behavior.slotTooltip(recipe, stack);
            }

            @Override
            public int @Nullable [] transferButton(CustomRecipe recipe) {
                return behavior.transferButton(recipe);
            }
        };
    }

    static void fill(Object g, int x1, int y1, int x2, int y2, int color) {
        ((GuiGraphicsExtractor) g).fill(x1, y1, x2, y2, color);
    }

    /** A small plain button: dark frame, lighter face when hovered or pressed, dimmed when not active. */
    static void button(Object g, int x, int y, int w, int h, boolean active, boolean lit) {
        int face = !active ? 0xFF3A3A3A : lit ? 0xFF7A7A8C : 0xFF5A5A66;
        fill(g, x, y, x + w, y + h, 0xFF101014);
        fill(g, x + 1, y + 1, x + w - 1, y + h - 1, face);
    }

    // ---- the drawables --------------------------------------------------------------------------------

    /** A texture region with optional padding; the base of {@code createDrawable} and {@code drawableBuilder}. */
    record Texture(Identifier texture, int u, int v, int w, int h, int texW, int texH, int top, int bottom, int left, int right) {
        Texture withTextureSize(int width, int height) {
            return new Texture(texture, u, v, w, h, width, height, top, bottom, left, right);
        }

        Texture withPadding(int t, int b, int l, int r) {
            return new Texture(texture, u, v, w, h, texW, texH, t, b, l, r);
        }

        int width() {
            return w + left + right;
        }

        int height() {
            return h + top + bottom;
        }
    }

    static IDrawableStatic texture(Texture t) {
        return JeiBridge.proxy(IDrawableStatic.class, (self, method, args) -> {
            switch (method.getName()) {
                case "getWidth":
                    return t.width();
                case "getHeight":
                    return t.height();
                case "draw":
                    if (args.length == 7) {
                        drawMasked(args[0], t, (int) args[1], (int) args[2], (int) args[3], (int) args[4], (int) args[5], (int) args[6]);
                    } else if (args.length == 3) {
                        drawMasked(args[0], t, (int) args[1], (int) args[2], 0, 0, 0, 0);
                    }
                    return null;
                default:
                    return JeiBridge.fallback(method.getReturnType());
            }
        });
    }

    private static void drawMasked(Object g, Texture t, int x, int y, int maskTop, int maskBottom, int maskLeft, int maskRight) {
        blit(g, t.texture(), x + t.left() + maskLeft, y + t.top() + maskTop, t.u() + maskLeft, t.v() + maskTop,
                t.w() - maskLeft - maskRight, t.h() - maskTop - maskBottom, t.texW(), t.texH());
    }

    /** A drawable that fills the area with a plain color (used for the slot and arrow stand-ins). */
    private static IDrawableStatic painted(int width, int height, Painter painter) {
        return JeiBridge.proxy(IDrawableStatic.class, (self, method, args) -> {
            switch (method.getName()) {
                case "getWidth":
                    return width;
                case "getHeight":
                    return height;
                case "draw":
                    if (args.length == 7) {
                        painter.paint(args[0], (int) args[1], (int) args[2], (int) args[3], (int) args[4], (int) args[5], (int) args[6]);
                    } else if (args.length == 3) {
                        painter.paint(args[0], (int) args[1], (int) args[2], 0, 0, 0, 0);
                    }
                    return null;
                default:
                    return JeiBridge.fallback(method.getReturnType());
            }
        });
    }

    @FunctionalInterface
    private interface Painter {
        void paint(Object g, int x, int y, int maskTop, int maskBottom, int maskLeft, int maskRight);
    }

    static IDrawableStatic blank(int width, int height) {
        return painted(width, height, (g, x, y, t, b, l, r) -> {
        });
    }

    /** The empty inventory slot, drawn the way the game draws one. */
    static IDrawableStatic slot() {
        return painted(18, 18, (g, x, y, t, b, l, r) -> {
            fill(g, x, y, x + 18, y + 18, 0xFF373737);
            fill(g, x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
            fill(g, x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
        });
    }

    /** The arrow between inputs and outputs: 24 by 17, a shaft and a head. */
    static IDrawableStatic arrow(boolean filled) {
        int color = filled ? 0xFFE0E0E0 : 0xFF6B6B6B;
        return painted(24, 17, (g, x, y, top, bottom, left, right) -> {
            int right0 = 24 - right;
            int shaft = Math.max(left, 0);
            fill(g, x + shaft, y + 6 + top, x + Math.min(17, right0), y + 11 - bottom, color);
            for (int i = 0; i < 6; i++) {
                int column = 17 + i;
                if (column >= left && column < right0) {
                    fill(g, x + column, y + 8 - (5 - i), x + column + 1, y + 9 + (5 - i), color);
                }
            }
        });
    }

    /** A GUI sprite as a drawable of the given size. */
    static IDrawableStatic spriteDrawable(Identifier sprite, int width, int height) {
        return painted(width, height, (g, x, y, t, b, l, r) -> sprite(g, sprite, x, y, width, height));
    }

    /** The plus sign between ingredients: 13 by 13. */
    static IDrawableStatic plus() {
        return painted(13, 13, (g, x, y, t, b, l, r) -> {
            fill(g, x + 5, y + 1, x + 8, y + 12, 0xFF8B8B8B);
            fill(g, x + 1, y + 5, x + 12, y + 8, 0xFF8B8B8B);
        });
    }

    /** The fuel flame: 14 by 14, orange when filled. */
    static IDrawableStatic flame(boolean filled) {
        int outer = filled ? 0xFFE08A1E : 0xFF5A5A5A;
        int inner = filled ? 0xFFF7D154 : 0xFF3F3F3F;
        return painted(14, 14, (g, x, y, top, bottom, left, right) -> {
            fill(g, x + 4, y + 1 + top, x + 10, y + 13 - bottom, outer);
            fill(g, x + 2, y + 6 + top, x + 12, y + 13 - bottom, outer);
            fill(g, x + 5, y + 8 + top, x + 9, y + 13 - bottom, inner);
        });
    }

    /** The big output slot of machines: 26 by 26. */
    static IDrawableStatic outputSlot() {
        return painted(26, 26, (g, x, y, t, b, l, r) -> {
            fill(g, x, y, x + 26, y + 26, 0xFF373737);
            fill(g, x + 1, y + 1, x + 26, y + 26, 0xFFFFFFFF);
            fill(g, x + 1, y + 1, x + 25, y + 25, 0xFF8B8B8B);
        });
    }

    /** An item icon as a drawable (16 by 16). */
    static IDrawable itemIcon(ItemStack stack) {
        ItemStack shown = stack.copy();
        return JeiBridge.proxy(IDrawable.class, (self, method, args) -> {
            switch (method.getName()) {
                case "getWidth":
                case "getHeight":
                    return 16;
                case "draw":
                    item(args[0], shown, (int) args[1], (int) args[2]);
                    return null;
                default:
                    return JeiBridge.fallback(method.getReturnType());
            }
        });
    }

    /** A JEI tick timer: counts from 0 to max over the given number of ticks, and back down when it bounces. */
    static mezz.jei.api.gui.ITickTimer timer(int ticks, int max, boolean bounce) {
        return JeiBridge.proxy(mezz.jei.api.gui.ITickTimer.class, (self, method, args) -> {
            switch (method.getName()) {
                case "getValue": {
                    int cycle = Math.max(1, ticks);
                    long clock = (System.currentTimeMillis() / 50) % (bounce ? cycle * 2L : cycle);
                    if (bounce && clock >= cycle) {
                        clock = cycle * 2L - clock - 1;
                    }
                    return (int) (clock * max / cycle);
                }
                case "getMaxValue":
                    return max;
                default:
                    return JeiBridge.fallback(method.getReturnType());
            }
        });
    }

    /** Wraps a static drawable so it fills in over {@code ticks} game ticks, from the given side. */
    static IDrawableAnimated animated(IDrawableStatic base, int ticks, IDrawableAnimated.StartDirection direction, boolean inverted) {
        int max = direction == IDrawableAnimated.StartDirection.TOP || direction == IDrawableAnimated.StartDirection.BOTTOM
                ? base.getHeight() : base.getWidth();
        return animated(base, timer(ticks, max, false), direction, inverted);
    }

    static IDrawableAnimated animated(IDrawableStatic base, Object timer, IDrawableAnimated.StartDirection direction) {
        return animated(base, (mezz.jei.api.gui.ITickTimer) timer, direction, false);
    }

    static IDrawableAnimated animated(IDrawableStatic base, mezz.jei.api.gui.ITickTimer timer,
            IDrawableAnimated.StartDirection direction, boolean inverted) {
        int width = base.getWidth();
        int height = base.getHeight();
        return JeiBridge.proxy(IDrawableAnimated.class, (self, method, args) -> {
            switch (method.getName()) {
                case "getWidth":
                    return width;
                case "getHeight":
                    return height;
                case "draw":
                    int max = Math.max(1, timer.getMaxValue());
                    int value = timer.getValue();
                    if (inverted) {
                        value = max - value;
                    }
                    int maskTop = 0;
                    int maskBottom = 0;
                    int maskLeft = 0;
                    int maskRight = 0;
                    switch (direction) {
                        case TOP -> maskBottom = height - value;
                        case BOTTOM -> maskTop = height - value;
                        case LEFT -> maskRight = width - value;
                        case RIGHT -> maskLeft = width - value;
                    }
                    drawStatic(base, args[0], (int) args[1], (int) args[2], maskTop, maskBottom, maskLeft, maskRight);
                    return null;
                default:
                    return JeiBridge.fallback(method.getReturnType());
            }
        });
    }
}
