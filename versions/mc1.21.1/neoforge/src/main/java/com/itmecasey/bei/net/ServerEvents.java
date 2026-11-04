package com.itmecasey.bei.net;

import com.itmecasey.bei.Bei;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/** Sends data-driven content to players. */
@EventBusSubscriber(modid = Bei.MODID)
public final class ServerEvents {
    private ServerEvents() {
    }

    /**
     * Runs when a player joins and again when the datapacks reload, so changed themes, groups and
     * hidden lists (and, on 26.1.2, recipes) reach players without a rejoin.
     */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        for (ServerPlayer player : event.getRelevantPlayers().toList()) {
            ThemeChannel.sendAll(player);
            GroupChannel.sendAll(player);
        }
    }
}
