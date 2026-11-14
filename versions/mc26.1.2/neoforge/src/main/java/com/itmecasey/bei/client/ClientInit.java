package com.itmecasey.bei.client;

import com.itmecasey.bei.Bei;
import com.itmecasey.bei.BeiConfig;
import com.itmecasey.bei.index.ItemIndex;
import com.itmecasey.bei.project.Project;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/** Client setup: the keys, the index build when a world is joined, and the per-tick work. */
@EventBusSubscriber(modid = Bei.MODID, value = Dist.CLIENT)
public final class ClientInit {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Bei.id("main"));
    /** Opens the full project tree. */
    public static final KeyMapping OPEN_PROJECT = key("open_project", GLFW.GLFW_KEY_J, KeyConflictContext.IN_GAME);
    /** Shows or hides the Master Project HUD. */
    public static final KeyMapping TOGGLE_HUD = key("toggle_hud", GLFW.GLFW_KEY_H, KeyConflictContext.IN_GAME);
    /** On a hovered item: show how it is made. */
    public static final KeyMapping SHOW_RECIPES = key("show_recipes", GLFW.GLFW_KEY_R, KeyConflictContext.UNIVERSAL);
    /** On a hovered item: show what it is used in. */
    public static final KeyMapping SHOW_USES = key("show_uses", GLFW.GLFW_KEY_U, KeyConflictContext.UNIVERSAL);
    /** On a hovered item: make it the Master Project (again to clear). */
    public static final KeyMapping SET_PROJECT = key("set_project", GLFW.GLFW_KEY_P, KeyConflictContext.UNIVERSAL);

    private ClientInit() {
    }

    private static KeyMapping key(String name, int code, KeyConflictContext context) {
        return new KeyMapping("key.bei." + name, context, KeyModifier.NONE, InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_PROJECT);
        event.register(TOGGLE_HUD);
        event.register(SHOW_RECIPES);
        event.register(SHOW_USES);
        event.register(SET_PROJECT);
    }

    /** The Mods screen opens the generated config screen for bei-client.toml. */
    @SubscribeEvent
    public static void registerConfigScreen(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        net.neoforged.fml.ModLoadingContext.get().getActiveContainer().registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class, net.neoforged.neoforge.client.gui.ConfigurationScreen::new);
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
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientData.migrateOnce();
        Minecraft game = Minecraft.getInstance();
        ItemIndex.tickTooltips(1_500_000L);
        Project.tick();
        if (!(game.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) {
            Cheats.clearHeld();
        }
        while (TOGGLE_HUD.consumeClick()) {
            if (game.screen == null && game.player != null) {
                BeiConfig.setHudVisible(!BeiConfig.hudVisible());
                game.gui.setOverlayMessage(Component.translatable(BeiConfig.hudVisible() ? "bei.hud.shown" : "bei.hud.hidden"), false);
            }
        }
        while (OPEN_PROJECT.consumeClick()) {
            if (game.screen == null && game.player != null) {
                game.setScreen(new ProjectScreen(null));
            }
        }
    }
}
