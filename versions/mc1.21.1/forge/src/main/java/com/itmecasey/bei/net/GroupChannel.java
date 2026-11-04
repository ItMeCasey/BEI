package com.itmecasey.bei.net;

import com.itmecasey.bei.Bei;
import com.itmecasey.bei.client.BrowserPanel;
import com.itmecasey.bei.client.Groups;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/**
 * Sends the item groups ({@code data/<namespace>/bei_groups/<name>.json}) and the hidden item
 * lists ({@code data/<namespace>/bei_hidden/<name>.json}) that datapacks define to a joining
 * client, as plain JSON text.
 */
public final class GroupChannel {
    private static final String GROUPS = "bei_groups";
    private static final String HIDDEN = "bei_hidden";
    /** Most files of each kind a client accepts or a server sends. */
    private static final int MAX_FILES = 512;
    /** Largest single file sent, and the most sent in all, so the packet stays under the game's size cap. */
    private static final int MAX_FILE_BYTES = 64 * 1024;
    private static final int MAX_TOTAL_BYTES = 700 * 1024;
    private static SimpleChannel channel;

    /** Group ids with their JSON in matching order, and the hidden-list JSON texts. */
    private record Data(List<String> groupIds, List<String> groupJson, List<String> hiddenJson) {
    }

    private GroupChannel() {
    }

    public static void register() {
        channel = ChannelBuilder.named(Bei.id("groups"))
                .networkProtocolVersion(1)
                .optional()
                .simpleChannel()
                .messageBuilder(Data.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((data, buf) -> {
                    buf.writeVarInt(data.groupIds().size());
                    for (int i = 0; i < data.groupIds().size(); i++) {
                        buf.writeUtf(data.groupIds().get(i));
                        buf.writeUtf(data.groupJson().get(i), 1 << 20);
                    }
                    buf.writeVarInt(data.hiddenJson().size());
                    for (String json : data.hiddenJson()) {
                        buf.writeUtf(json, 1 << 20);
                    }
                })
                .decoder(buf -> {
                    int groupCount = buf.readVarInt();
                    if (groupCount < 0 || groupCount > MAX_FILES) {
                        throw new IllegalArgumentException("Too many groups: " + groupCount);
                    }
                    List<String> ids = new ArrayList<>(Math.min(groupCount, 16));
                    List<String> json = new ArrayList<>(Math.min(groupCount, 16));
                    for (int i = 0; i < groupCount; i++) {
                        ids.add(buf.readUtf(256));
                        json.add(buf.readUtf(1 << 20));
                    }
                    int hiddenCount = buf.readVarInt();
                    if (hiddenCount < 0 || hiddenCount > MAX_FILES) {
                        throw new IllegalArgumentException("Too many hidden lists: " + hiddenCount);
                    }
                    List<String> hidden = new ArrayList<>(Math.min(hiddenCount, 16));
                    for (int i = 0; i < hiddenCount; i++) {
                        hidden.add(buf.readUtf(1 << 20));
                    }
                    return new Data(ids, json, hidden);
                })
                .consumerMainThread((data, context) -> {
                    Map<String, String> groups = new LinkedHashMap<>();
                    for (int i = 0; i < data.groupIds().size(); i++) {
                        groups.put(data.groupIds().get(i), data.groupJson().get(i));
                    }
                    Groups.set(groups, data.hiddenJson());
                    BrowserPanel.invalidate();
                    context.setPacketHandled(true);
                })
                .add();
    }

    /** Reads every group and hidden file the server's datapacks hold and sends them to the player. */
    public static void sendAll(ServerPlayer player) {
        if (channel == null) {
            return;
        }
        var resources = player.level().getServer().getResourceManager();
        List<String> ids = new ArrayList<>();
        List<String> json = new ArrayList<>();
        int[] total = {0};
        for (Map.Entry<ResourceLocation, Resource> entry : resources.listResources(GROUPS, id -> id.getPath().endsWith(".json")).entrySet()) {
            String path = entry.getKey().getPath();
            String text = ids.size() < MAX_FILES ? read(entry.getValue(), total) : null;
            if (text != null) {
                ids.add(entry.getKey().getNamespace() + ":" + path.substring(GROUPS.length() + 1, path.length() - ".json".length()));
                json.add(text);
            }
        }
        List<String> hidden = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> entry : resources.listResources(HIDDEN, id -> id.getPath().endsWith(".json")).entrySet()) {
            String text = hidden.size() < MAX_FILES ? read(entry.getValue(), total) : null;
            if (text != null) {
                hidden.add(text);
            }
        }
        channel.send(new Data(ids, json, hidden), PacketDistributor.PLAYER.with(player));
    }

    private static String read(Resource resource, int[] total) {
        try (InputStream in = resource.open()) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length > MAX_FILE_BYTES || total[0] + bytes.length > MAX_TOTAL_BYTES) {
                Bei.LOGGER.warn("Skipping a BEI datapack file: too large to send");
                return null;
            }
            total[0] += bytes.length;
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            Bei.LOGGER.warn("Could not read a BEI datapack file", e);
            return null;
        }
    }
}
