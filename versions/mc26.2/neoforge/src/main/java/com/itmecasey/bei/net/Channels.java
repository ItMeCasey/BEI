package com.itmecasey.bei.net;

import net.minecraft.network.Connection;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers BEI's four messages. Every one is optional, so a client or server without BEI still
 * connects: the server only sends to players whose connection announced the channel, and the
 * client only sends cheat requests to a server that did.
 */
public final class Channels {
    /** Bump when the messages change shape. */
    private static final String VERSION = "3";

    private Channels() {
    }

    /**
     * NeoForge wants payloads registered while mods load, so the mod constructor hands this to the
     * mod bus.
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION).optional();
        RecipeChannel.register(registrar);
        ThemeChannel.register(registrar);
        GiveChannel.register(registrar);
        GroupChannel.register(registrar);
    }

    /** Whether the other end of this connection announced the payload with this id. */
    static boolean isRemotePresent(Connection connection, Identifier payloadId) {
        return NetworkRegistry.hasChannel(connection, null, payloadId);
    }
}
