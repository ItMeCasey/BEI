package com.itmecasey.bei.compat;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import com.itmecasey.bei.api.CardPainter;
import com.itmecasey.bei.api.CustomRecipe;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import net.minecraft.resources.ResourceLocation;
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

    static void blit(Object g, ResourceLocation texture, int x, int y, int u, int v, int w, int h, int texW, int texH) {
        if (w > 0 && h > 0) {
            ((GuiGraphics) g).blit(texture, x, y, (float) u, (float) v, w, h, texW, texH);
        }
    }

    static void item(Object g, ItemStack stack, int x, int y) {
        ((GuiGraphics) g).renderItem(stack, x, y);
    }

    static void drawStatic(IDrawableStatic drawable, Object g, int x, int y, int top, int bottom, int left, int right) {
        drawable.draw((GuiGraphics) g, x, y, top, bottom, left, right);
    }

    static void text(Object g, List<net.minecraft.network.chat.FormattedText> lines, int width, int x, int y, int color) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int row = y;
        for (net.minecraft.network.chat.FormattedText line : lines) {
            for (net.minecraft.util.FormattedCharSequence piece : font.split(line, width)) {
                ((GuiGraphics) g).drawString(font, piece, x, row, color, false);
                row += font.lineHeight;
            }
        }
    }

    static void translated(Object g, int x, int y, Runnable action) {
        var pose = ((GuiGraphics) g).pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        try {
            action.run();
        } finally {
            pose.popPose();
        }
    }

    @FunctionalInterface
    interface Paint {
        void paint(Object graphics, CustomRecipe recipe, int mouseX, int mouseY);
    }

    @FunctionalInterface
    interface Tip {
        @Nullable List<Component> tip(CustomRecipe recipe, int mouseX, int mouseY);
    }

    /** A card painter whose parameter types are this version's drawing classes. */
    static CardPainter painter(Paint paint, Tip tip) {
        return new CardPainter() {
            @Override
            public void paint(GuiGraphics graphics, CustomRecipe recipe, int mouseX, int mouseY) {
                paint.paint(graphics, recipe, mouseX, mouseY);
            }

            @Override
            public @Nullable List<Component> tooltip(CustomRecipe recipe, int mouseX, int mouseY) {
                return tip.tip(recipe, mouseX, mouseY);
            }
        };
    }

    static void fill(Object g, int x1, int y1, int x2, int y2, int color) {
        ((GuiGraphics) g).fill(x1, y1, x2, y2, color);
    }

    // ---- the drawables --------------------------------------------------------------------------------

    /** A texture region with optional padding; the base of {@code createDrawable} and {@code drawableBuilder}. */
    record Texture(ResourceLocation texture, int u, int v, int w, int h, int texW, int texH, int top, int bottom, int left, int right) {
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
