package com.itmecasey.bei.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.itmecasey.bei.Bei;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Wires the browser panel and its popups into every container screen (inventory, chests, crafting
 * tables...). While a popup is open it takes all input.
 */
@EventBusSubscriber(modid = Bei.MODID, value = Dist.CLIENT)
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
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (handleClick(event)) {
            event.setCanceled(true);
        }
    }

    private static boolean handleClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return false;
        }
        if (SettingsPopup.isOpen()) {
            return SettingsPopup.mouseClicked(event.getMouseButtonEvent());
        }
        if (RecipePopup.isOpen()) {
            return RecipePopup.mouseClicked(event.getMouseButtonEvent());
        }
        if (Cheats.holding()) {
            if (BrowserPanel.mouseClicked(screen, event.getMouseButtonEvent())) {
                return true;
            }
            var slot = Ui.hoveredSlot(screen);
            if (slot != null) {
                Cheats.place(screen.getMenu().containerId, slot, MouseButtons.isRight(event.getMouseButtonEvent().button()));
            } else {
                Cheats.clearHeld();
            }
            return true;
        }
        return BrowserPanel.mouseClicked(screen, event.getMouseButtonEvent());
    }

    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (handleScroll(event)) {
            event.setCanceled(true);
        }
    }

    private static boolean handleScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) {
            return false;
        }
        if (popupOpen()) {
            RecipePopup.mouseScrolled(event.getScrollDeltaY());
            return true;
        }
        return BrowserPanel.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDeltaY());
    }

    @SubscribeEvent
    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (handleKey(event)) {
            event.setCanceled(true);
        }
    }

    private static boolean handleKey(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return false;
        }
        Minecraft game = Minecraft.getInstance();
        if (SettingsPopup.isOpen()) {
            return SettingsPopup.keyPressed(event.getKeyEvent());
        }
        if (RecipePopup.isOpen()) {
            return RecipePopup.keyPressed(event.getKeyEvent(), game.mouseHandler.getScaledXPos(game.getWindow()),
                    game.mouseHandler.getScaledYPos(game.getWindow()));
        }
        if (Cheats.holding() && event.getKeyEvent().key() == InputConstants.KEY_ESCAPE) {
            Cheats.clearHeld();
            return true;
        }
        return BrowserPanel.keyPressed(screen, event.getKeyEvent());
    }

    @SubscribeEvent
    public static void onChar(ScreenEvent.CharacterTyped.Pre event) {
        if (handleChar(event)) {
            event.setCanceled(true);
        }
    }

    private static boolean handleChar(ScreenEvent.CharacterTyped.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) {
            return false;
        }
        return popupOpen() || BrowserPanel.charTyped(event.getCharacterEvent());
    }
}
