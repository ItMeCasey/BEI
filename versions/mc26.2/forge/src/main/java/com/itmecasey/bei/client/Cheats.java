package com.itmecasey.bei.client;

import com.itmecasey.bei.BeiConfig;
import com.itmecasey.bei.net.GiveChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Cheat mode: clicking an item in the browser either puts it in your inventory or "holds" it.
 * A held item is not a real stack; BEI only remembers which item and how many, draws it just
 * below the mouse pointer, and when you click a slot asks the server to put that many in it
 * (left click all, right click one). Nothing is ever spawned in the world, and no commands are
 * used. The server only agrees for creative players, game masters, and the owner of a private
 * singleplayer world. The switch is for this session only.
 */
final class Cheats {
    private static boolean enabled;
    private static @Nullable Item held;
    private static int heldCount;

    private Cheats() {
    }

    static boolean enabled() {
        return enabled;
    }

    static boolean holding() {
        return enabled && held != null;
    }

    static void toggle() {
        Minecraft game = Minecraft.getInstance();
        if (!enabled && !ServerCheck.canGive()) {
            game.gui.hud.setOverlayMessage(Component.translatable("bei.cheat.no_server"), false);
            return;
        }
        enabled = !enabled;
        clearHeld();
        if (game.player != null) {
            game.gui.hud.setOverlayMessage(Component.translatable(enabled ? "bei.cheat.on" : "bei.cheat.off"), false);
        }
    }

    static void clearHeld() {
        held = null;
        heldCount = 0;
    }

    /** A click on an item in the browser: hold one more (or a full stack), or put it in the inventory. */
    static void give(Item item, boolean fullStack) {
        if (!ServerCheck.canGive()) {
            enabled = false;
            clearHeld();
            Minecraft.getInstance().gui.hud.setOverlayMessage(Component.translatable("bei.cheat.no_server"), false);
            return;
        }
        int max = item.getDefaultInstance().getMaxStackSize();
        if (BeiConfig.cheatTarget() == BeiConfig.CheatTarget.INVENTORY) {
            GiveChannel.toInventory(item, fullStack ? max : 1);
            return;
        }
        if (held == item) {
            heldCount = fullStack ? max : Math.min(max, heldCount + 1);
        } else {
            held = item;
            heldCount = fullStack ? max : 1;
        }
    }

    /** A click on a slot while holding: asks the server to put the held items in it. */
    static void place(int containerId, Slot slot, boolean justOne) {
        if (held == null) {
            return;
        }
        ItemStack probe = held.getDefaultInstance();
        probe.setCount(1);
        ItemStack existing = slot.getItem();
        if (!slot.mayPlace(probe) || (!existing.isEmpty() && existing.getItem() != held)) {
            return;
        }
        int limit = Math.min(held.getDefaultInstance().getMaxStackSize(), slot.getMaxStackSize(probe));
        int room = limit - (existing.isEmpty() ? 0 : existing.getCount());
        int placed = Math.min(justOne ? 1 : heldCount, room);
        if (placed <= 0) {
            return;
        }
        GiveChannel.toSlot(held, placed, containerId, slot.index);
        heldCount -= placed;
        if (heldCount <= 0) {
            clearHeld();
        }
    }

    /** Draws the held item and its count just below the mouse pointer. */
    static void renderHeld(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!holding()) {
            return;
        }
        ItemStack shown = held.getDefaultInstance();
        shown.setCount(heldCount);
        g.nextStratum();
        int x = mouseX + 2;
        int y = mouseY + 12;
        g.item(shown, x, y);
        if (heldCount > 1) {
            // The count is drawn by hand, smaller than the game's and always with a shadow.
            var font = Minecraft.getInstance().font;
            String text = Integer.toString(heldCount);
            float scale = 0.7F;
            g.pose().pushMatrix();
            g.pose().translate(x + 17 - font.width(text) * scale, y + 16 - 8 * scale);
            g.pose().scale(scale, scale);
            g.text(font, text, 0, 0, 0xFFFFFFFF, true);
            g.pose().popMatrix();
        }
    }
}
