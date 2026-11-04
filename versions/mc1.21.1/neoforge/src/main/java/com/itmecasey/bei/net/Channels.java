package com.itmecasey.bei.net;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers BEI's three messages (themes, groups, give). They are optional, so a client or server
 * without BEI still connects; the server just does not send them, and the client's cheat mode stays
 * off on a server that lacks the give channel.
 */
public final class Channels {
    /** Bump when a message changes shape. */
    private static final String VERSION = "1";

    private Channels() {
    }

    /** NeoForge wants payloads registered while mods load, so the mod constructor hands this to the mod bus. */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION).optional();
        ThemeChannel.register(registrar);
        GroupChannel.register(registrar);
        GiveChannel.register(registrar);
    }
}
