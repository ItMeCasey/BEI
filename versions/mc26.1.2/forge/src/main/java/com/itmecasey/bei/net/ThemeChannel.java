package com.itmecasey.bei.net;

import com.itmecasey.bei.Bei;
import com.itmecasey.bei.client.Themes;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/**
 * Sends the themes that datapacks define ({@code data/<namespace>/bei_themes/<name>.json}) to a
 * joining client, as plain JSON text. The theme id is {@code namespace:name}.
 */
public final class ThemeChannel {
    private static final String FOLDER = "bei_themes";
    /** Most theme files a client accepts or a server sends. */
    private static final int MAX_FILES = 256;
    /** Largest single theme file sent, and the most sent in all, so the packet stays under the game's size cap. */
    private static final int MAX_FILE_BYTES = 32 * 1024;
    private static final int MAX_TOTAL_BYTES = 600 * 1024;
    private static SimpleChannel channel;

    /** Theme ids and their JSON, in matching order. */
    private record Themes(List<String> ids, List<String> json) {
    }

    private ThemeChannel() {
    }

    public static void register() {
        channel = ChannelBuilder.named(Bei.id("themes"))
                .networkProtocolVersion(1)
                .optional()
                .simpleChannel()
                .messageBuilder(Themes.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buf) -> {
                    buf.writeVarInt(message.ids().size());
                    for (int i = 0; i < message.ids().size(); i++) {
                        buf.writeUtf(message.ids().get(i));
                        buf.writeUtf(message.json().get(i), 1 << 20);
                    }
                })
                .decoder(buf -> {
                    int size = buf.readVarInt();
                    if (size < 0 || size > MAX_FILES) {
                        throw new IllegalArgumentException("Too many themes: " + size);
                    }
                    List<String> ids = new ArrayList<>(Math.min(size, 16));
                    List<String> json = new ArrayList<>(Math.min(size, 16));
                    for (int i = 0; i < size; i++) {
                        ids.add(buf.readUtf(256));
                        json.add(buf.readUtf(1 << 20));
                    }
                    return new Themes(ids, json);
                })
                .consumerMainThread((message, context) -> {
                    Map<String, String> map = new LinkedHashMap<>();
                    for (int i = 0; i < message.ids().size(); i++) {
                        map.put(message.ids().get(i), message.json().get(i));
                    }
                    com.itmecasey.bei.client.Themes.setCustom(map);
                    Bei.LOGGER.info("Received {} datapack theme(s)", map.size());
                    context.setPacketHandled(true);
                })
                .add();
    }

    /** Reads every theme file the server's datapacks hold and sends them to the player. */
    public static void sendAll(ServerPlayer player) {
        if (channel == null) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        List<String> ids = new ArrayList<>();
        List<String> json = new ArrayList<>();
        int total = 0;
        Map<Identifier, Resource> found = server.getResourceManager().listResources(FOLDER, id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
            String path = entry.getKey().getPath();
            String name = path.substring(FOLDER.length() + 1, path.length() - ".json".length());
            if (ids.size() >= MAX_FILES) {
                break;
            }
            try (InputStream in = entry.getValue().open()) {
                byte[] bytes = in.readAllBytes();
                if (bytes.length > MAX_FILE_BYTES || total + bytes.length > MAX_TOTAL_BYTES) {
                    Bei.LOGGER.warn("Skipping theme {}: too large to send", entry.getKey());
                    continue;
                }
                total += bytes.length;
                json.add(new String(bytes, StandardCharsets.UTF_8));
                ids.add(entry.getKey().getNamespace() + ":" + name);
            } catch (IOException | RuntimeException e) {
                Bei.LOGGER.warn("Could not read theme {}", entry.getKey(), e);
            }
        }
        channel.send(new Themes(ids, json), PacketDistributor.PLAYER.with(player));
    }
}
