package com.itmecasey.bei.client;

import com.itmecasey.bei.net.GiveChannel;
import net.minecraft.client.Minecraft;

/**
 * Whether the server the player is on has BEI. A singleplayer world always does; on a multiplayer
 * server the answer comes from the channels the server announced when the player joined.
 */
final class ServerCheck {
    private ServerCheck() {
    }

    /** Recipes come with the game in 1.20.1, so they never depend on the server having BEI. */
    static boolean hasBei() {
        return true;
    }

    /** True if the server has BEI's give channel (cheat mode). */
    static boolean canGive() {
        Minecraft game = Minecraft.getInstance();
        if (game.getSingleplayerServer() != null) {
            return true;
        }
        return game.getConnection() != null && GiveChannel.isRemotePresent(game.getConnection().getConnection());
    }
}
