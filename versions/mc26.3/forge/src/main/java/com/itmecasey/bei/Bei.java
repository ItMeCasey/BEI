package com.itmecasey.bei;

import com.itmecasey.bei.net.RecipeChannel;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/** Entry point for Barely Enough Items (Forge 26.3). */
@Mod(Bei.MODID)
public final class Bei {
    /** Mod id; must match {@code mod_id} in gradle.properties. */
    public static final String MODID = "bei";

    public static final Logger LOGGER = LogUtils.getLogger();

    public Bei(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.CLIENT, BeiConfig.SPEC);
        RecipeChannel.register();
        com.itmecasey.bei.net.ThemeChannel.register();
        com.itmecasey.bei.net.GiveChannel.register();
        com.itmecasey.bei.net.GroupChannel.register();
        LOGGER.info("Barely Enough Items loaded (Forge 26.3)");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
