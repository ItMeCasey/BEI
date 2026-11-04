package com.itmecasey.bei.net;

import com.itmecasey.bei.Bei;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;

/** Gives a joining player the recipe list. */
@Mod.EventBusSubscriber(modid = Bei.MODID)
public final class ServerEvents {
    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RecipeChannel.sendAll(player);
            ThemeChannel.sendAll(player);
            GroupChannel.sendAll(player);
        }
    }
}
