package com.itmecasey.bei.net;

import com.itmecasey.bei.Bei;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.fml.common.Mod;

/** Sends data-driven content to players. */
@Mod.EventBusSubscriber(modid = Bei.MODID)
public final class ServerEvents {
    private ServerEvents() {
    }

    /**
     * Runs when a player joins and again when the datapacks reload, so changed themes, groups and
     * hidden lists (and, on 26.2, recipes) reach players without a rejoin.
     */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        for (ServerPlayer player : event.getPlayers()) {
            RecipeChannel.sendAll(player);
            ThemeChannel.sendAll(player);
            GroupChannel.sendAll(player);
        }
    }
}
