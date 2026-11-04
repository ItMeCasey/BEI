package com.itmecasey.bei.client;

import com.itmecasey.bei.net.GiveChannel;
import com.itmecasey.bei.net.RecipeChannel;
import net.minecraft.client.Minecraft;

/**
 * Whether the server the player is on has BEI. A singleplayer world always does; on a multiplayer
 * server the answer comes from the channels the server announced when the player joined.
 */
final class ServerCheck {
    private ServerCheck() {
    }

    /** True if recipes and cheat requests can work here. */
    static boolean hasBei() {
        Minecraft game = Minecraft.getInstance();
        if (game.getSingleplayerServer() != null) {
            return true;
        }
        return game.getConnection() != null && RecipeChannel.isRemotePresent(game.getConnection().getConnection());
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
