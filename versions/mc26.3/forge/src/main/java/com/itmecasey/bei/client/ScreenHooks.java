package com.itmecasey.bei.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.itmecasey.bei.Bei;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Wires the browser panel and its popups into every container screen (inventory, chests, crafting
 * tables...). While a popup is open it takes all input.
 */
@Mod.EventBusSubscriber(modid = Bei.MODID, value = Dist.CLIENT)
public final class ScreenHooks {
    private ScreenHooks() {
    }

    private static boolean popupOpen() {
        return RecipePopup.isOpen() || SettingsPopup.isOpen();
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> screen) {
            BrowserPanel.layout(screen);
        }
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> screen) {
            int mx = popupOpen() ? -1000 : event.getMouseX();
            int my = popupOpen() ? -1000 : event.getMouseY();
            BrowserPanel.render(screen, event.getGuiGraphics(), mx, my);
            RecipePopup.render(event.getGuiGraphics(), screen.width, screen.height, event.getMouseX(), event.getMouseY());
            SettingsPopup.render(event.getGuiGraphics(), screen.width, screen.height, event.getMouseX(), event.getMouseY());
            Cheats.renderHeld(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    public static boolean onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return false;
        }
        if (SettingsPopup.isOpen()) {
            return SettingsPopup.mouseClicked(event.getInfo());
        }
        if (RecipePopup.isOpen()) {
            return RecipePopup.mouseClicked(event.getInfo());
        }
        if (Cheats.holding()) {
            if (BrowserPanel.mouseClicked(screen, event.getInfo())) {
                return true;
            }
            var slot = screen.getSlotUnderMouse();
            if (slot != null) {
                Cheats.place(screen.getMenu().containerId, slot, MouseButtons.isRight(event.getInfo().button()));
            } else {
                Cheats.clearHeld();
            }
            return true;
        }
        return BrowserPanel.mouseClicked(screen, event.getInfo());
    }

    @SubscribeEvent
    public static boolean onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) {
            return false;
        }
        if (popupOpen()) {
            RecipePopup.mouseScrolled(event.getDeltaY());
            return true;
        }
        return BrowserPanel.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getDeltaY());
    }

    @SubscribeEvent
    public static boolean onKey(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return false;
        }
        Minecraft game = Minecraft.getInstance();
        if (SettingsPopup.isOpen()) {
            return SettingsPopup.keyPressed(event.getInfo());
        }
        if (RecipePopup.isOpen()) {
            return RecipePopup.keyPressed(event.getInfo(), game.mouseHandler.getScaledXPos(game.getWindow()),
                    game.mouseHandler.getScaledYPos(game.getWindow()));
        }
        if (Cheats.holding() && event.getInfo().key() == InputConstants.KEY_ESCAPE) {
            Cheats.clearHeld();
            return true;
        }
        return BrowserPanel.keyPressed(screen, event.getInfo());
    }

    @SubscribeEvent
    public static boolean onChar(ScreenEvent.CharacterTyped.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) {
            return false;
        }
        return popupOpen() || BrowserPanel.charTyped(event.getInfo());
    }
}
