package com.itmecasey.bei.client;

import com.itmecasey.bei.Bei;
import com.itmecasey.bei.BeiConfig;
import com.itmecasey.bei.index.ItemIndex;
import com.itmecasey.bei.project.Project;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client setup: the keys, the index build when a world is joined, and the per-tick work. */
@Mod.EventBusSubscriber(modid = Bei.MODID, value = Dist.CLIENT)
public final class ClientInit {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Bei.id("main"));
    /** Opens the full project tree. */
    public static final KeyMapping OPEN_PROJECT = key("open_project", InputConstants.KEY_J, KeyConflictContext.IN_GAME);
    /** Shows or hides the Master Project HUD. */
    public static final KeyMapping TOGGLE_HUD = key("toggle_hud", InputConstants.KEY_H, KeyConflictContext.IN_GAME);
    /** On a hovered item: show how it is made. */
    public static final KeyMapping SHOW_RECIPES = key("show_recipes", InputConstants.KEY_R, KeyConflictContext.UNIVERSAL);
    /** On a hovered item: show what it is used in. */
    public static final KeyMapping SHOW_USES = key("show_uses", InputConstants.KEY_U, KeyConflictContext.UNIVERSAL);
    /** On a hovered item: make it the Master Project (again to clear). */
    public static final KeyMapping SET_PROJECT = key("set_project", InputConstants.KEY_P, KeyConflictContext.UNIVERSAL);

    private ClientInit() {
    }

    private static KeyMapping key(String name, int code, KeyConflictContext context) {
        return new KeyMapping("key.bei." + name, context, KeyModifier.NONE, InputConstants.Type.KEYBOARD, code, CATEGORY, 0);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PROJECT);
        event.register(TOGGLE_HUD);
        event.register(SHOW_RECIPES);
        event.register(SHOW_USES);
        event.register(SET_PROJECT);
    }

    @SubscribeEvent
    public static void onJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        com.itmecasey.bei.plugin.PluginHost.loadOnce();
        com.itmecasey.bei.compat.ModJsCompat.load();
        ItemIndex.rebuild();
        com.itmecasey.bei.index.RecipeIndex.buildLocal();
        Project.load();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft game = Minecraft.getInstance();
        ItemIndex.tickTooltips(1_500_000L);
        Project.tick();
        if (!(game.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) {
            Cheats.clearHeld();
        }
        while (TOGGLE_HUD.consumeClick()) {
            if (game.gui.screen() == null && game.player != null) {
                BeiConfig.setHudVisible(!BeiConfig.hudVisible());
                game.gui.hud.setOverlayMessage(Component.translatable(BeiConfig.hudVisible() ? "bei.hud.shown" : "bei.hud.hidden"), false);
            }
        }
        while (OPEN_PROJECT.consumeClick()) {
            if (game.gui.screen() == null && game.player != null) {
                game.setScreenAndShow(new ProjectScreen(null));
            }
        }
    }
}
