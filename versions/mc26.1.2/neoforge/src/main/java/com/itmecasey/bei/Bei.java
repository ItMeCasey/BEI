package com.itmecasey.bei;

import com.itmecasey.bei.net.Channels;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/** Entry point for Barely Enough Items (NeoForge 26.1.2). */
@Mod(Bei.MODID)
public final class Bei {
    /** Mod id; must match {@code mod_id} in gradle.properties. */
    public static final String MODID = "bei";

    public static final Logger LOGGER = LogUtils.getLogger();

    public Bei(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, BeiConfig.SPEC);
        modBus.addListener(Channels::register);
        LOGGER.info("Barely Enough Items loaded (NeoForge 26.1.2)");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
