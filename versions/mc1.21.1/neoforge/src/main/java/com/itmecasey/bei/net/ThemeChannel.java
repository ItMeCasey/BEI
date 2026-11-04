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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

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
    /** Theme ids and their JSON, in matching order. */
    private record Themes(List<String> ids, List<String> json) implements CustomPacketPayload {
        static final CustomPacketPayload.Type<Themes> TYPE = new CustomPacketPayload.Type<>(Bei.id("themes"));
        static final StreamCodec<FriendlyByteBuf, Themes> CODEC = StreamCodec.of(Themes::write, Themes::read);

        private static void write(FriendlyByteBuf buf, Themes message) {
            buf.writeVarInt(message.ids().size());
            for (int i = 0; i < message.ids().size(); i++) {
                buf.writeUtf(message.ids().get(i));
                buf.writeUtf(message.json().get(i), 1 << 20);
            }
        }

        private static Themes read(FriendlyByteBuf buf) {
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
        }

        @Override
        public CustomPacketPayload.Type<Themes> type() {
            return TYPE;
        }
    }

    private ThemeChannel() {
    }

    /** Registers the message; called from {@link Channels#register}. */
    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Themes.TYPE, Themes.CODEC, ThemeChannel::onReceive);
    }

    private static void onReceive(Themes message, IPayloadContext context) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < message.ids().size(); i++) {
            map.put(message.ids().get(i), message.json().get(i));
        }
        com.itmecasey.bei.client.Themes.setCustom(map);
        Bei.LOGGER.info("Received {} datapack theme(s)", map.size());
    }

    /** Reads every theme file the server's datapacks hold and sends them to the player. */
    public static void sendAll(ServerPlayer player) {
        if (!player.connection.hasChannel(Themes.TYPE)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        List<String> ids = new ArrayList<>();
        List<String> json = new ArrayList<>();
        int total = 0;
        Map<ResourceLocation, Resource> found = server.getResourceManager().listResources(FOLDER, id -> id.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
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
        PacketDistributor.sendToPlayer(player, new Themes(ids, json));
    }
}
