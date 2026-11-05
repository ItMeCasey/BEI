package com.itmecasey.bei.net;

import com.itmecasey.bei.Bei;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Sends data-driven content to players. */
@EventBusSubscriber(modid = Bei.MODID)
public final class ServerEvents {
    private ServerEvents() {
    }

    /**
     * Runs when a player joins and again when the datapacks reload, so changed themes, groups and
     * hidden lists (and, on 26.2, recipes) reach players without a rejoin.
     */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(player -> {
            RecipeChannel.sendAll(player);
            ThemeChannel.sendAll(player);
            GroupChannel.sendAll(player);
        });
    }
}
