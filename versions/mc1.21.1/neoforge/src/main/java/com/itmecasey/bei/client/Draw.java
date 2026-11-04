package com.itmecasey.bei.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * A small wrapper over the game's {@link GuiGraphics} that gives BEI's drawing code the same few
 * calls on every game version: {@code fill}, {@code outline}, {@code text}, {@code item},
 * {@code pose} and {@code nextStratum}. The 26.1.2 build draws through the game's newer extractor
 * object, so keeping these names the same lets the screens be shared almost line for line.
 */
public final class Draw {
    private final GuiGraphics graphics;
    private float depth;

    public Draw(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    public GuiGraphics graphics() {
        return graphics;
    }

    public int guiWidth() {
        return graphics.guiWidth();
    }

    public int guiHeight() {
        return graphics.guiHeight();
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        graphics.fill(x1, y1, x2, y2, color);
    }

    public void outline(int x, int y, int width, int height, int color) {
        graphics.renderOutline(x, y, width, height, color);
    }

    /**
     * Text is lifted above items, which the game draws 150 units forward; without this, a count
     * written over an item's icon would sit behind it.
     */
    public void text(Font font, String text, int x, int y, int color, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.drawString(font, text, x, y, color, shadow);
        graphics.pose().popPose();
    }

    public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.drawString(font, text, x, y, color, shadow);
        graphics.pose().popPose();
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.drawString(font, text, x, y, color, shadow);
        graphics.pose().popPose();
    }

    public void item(ItemStack stack, int x, int y) {
        graphics.renderItem(stack, x, y);
    }

    public void itemDecorations(Font font, ItemStack stack, int x, int y) {
        graphics.renderItemDecorations(font, stack, x, y);
    }

    public void enableScissor(int x1, int y1, int x2, int y2) {
        graphics.enableScissor(x1, y1, x2, y2);
    }

    public void disableScissor() {
        graphics.disableScissor();
    }

    /** Draws everything after this call above everything drawn before it (the game has no layers here, so this lifts the depth). */
    public void nextStratum() {
        graphics.pose().translate(0, 0, 400);
        depth += 400;
    }

    /** Puts the depth back; call when a screen event is done with this object. */
    public void finish() {
        if (depth != 0) {
            graphics.pose().translate(0, 0, -depth);
            depth = 0;
        }
    }

    /** The matrix calls BEI uses, under the names the newer game uses. */
    public Pose pose() {
        return new Pose();
    }

    public final class Pose {
        public void pushMatrix() {
            graphics.pose().pushPose();
        }

        public void popMatrix() {
            graphics.pose().popPose();
        }

        public void translate(float x, float y) {
            graphics.pose().translate(x, y, 0);
        }

        public void scale(float x, float y) {
            graphics.pose().scale(x, y, 1);
        }
    }
}
