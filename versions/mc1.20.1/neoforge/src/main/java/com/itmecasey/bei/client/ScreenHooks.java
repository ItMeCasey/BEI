package com.itmecasey.bei.client;

import com.itmecasey.bei.Bei;
import com.itmecasey.bei.client.input.CharacterEvent;
import com.itmecasey.bei.client.input.KeyEvent;
import com.itmecasey.bei.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Wires the browser panel and its popups into every container screen (inventory, chests, crafting
 * tables...). While a popup is open it takes all input. The 1.20.1 screen events report raw values,
 * so they are wrapped into the same small event objects the 26.1.2 build receives.
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
            Draw draw = new Draw(event.getGuiGraphics());
            try {
                int mx = popupOpen() ? -1000 : event.getMouseX();
                int my = popupOpen() ? -1000 : event.getMouseY();
                BrowserPanel.render(screen, draw, mx, my);
                RecipePopup.render(draw, screen.width, screen.height, event.getMouseX(), event.getMouseY());
                SettingsPopup.render(draw, screen.width, screen.height, event.getMouseX(), event.getMouseY());
                Cheats.renderHeld(draw, event.getMouseX(), event.getMouseY());
            } finally {
                draw.finish();
            }
        }
    }

    @SubscribeEvent
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        MouseButtonEvent info = new MouseButtonEvent(event.getMouseX(), event.getMouseY(), event.getButton(), Ui.modifiers());
        boolean handled;
        if (SettingsPopup.isOpen()) {
            handled = SettingsPopup.mouseClicked(info);
        } else if (RecipePopup.isOpen()) {
            handled = RecipePopup.mouseClicked(info);
        } else if (Cheats.holding()) {
            handled = true;
            if (!BrowserPanel.mouseClicked(screen, info)) {
                var slot = screen.getSlotUnderMouse();
                if (slot != null) {
                    Cheats.place(screen.getMenu().containerId, slot, event.getButton() == 1);
                } else {
                    Cheats.clearHeld();
                }
            }
        } else {
            handled = BrowserPanel.mouseClicked(screen, info);
        }
        if (handled) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) {
            return;
        }
        if (popupOpen()) {
            RecipePopup.mouseScrolled(event.getScrollDelta());
            event.setCanceled(true);
        } else if (BrowserPanel.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDelta())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        KeyEvent info = new KeyEvent(event.getKeyCode(), event.getScanCode(), event.getModifiers());
        boolean handled;
        if (SettingsPopup.isOpen()) {
            handled = SettingsPopup.keyPressed(info);
        } else if (RecipePopup.isOpen()) {
            handled = RecipePopup.keyPressed(info, Ui.mouseX(), Ui.mouseY());
        } else if (Cheats.holding() && info.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            Cheats.clearHeld();
            handled = true;
        } else {
            handled = BrowserPanel.keyPressed(screen, info);
        }
        if (handled) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onChar(ScreenEvent.CharacterTyped.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) {
            return;
        }
        if (popupOpen() || BrowserPanel.charTyped(new CharacterEvent(event.getCodePoint()))) {
            event.setCanceled(true);
        }
    }
}
