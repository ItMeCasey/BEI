package com.itmecasey.bei.net;

import com.itmecasey.bei.Bei;
import com.itmecasey.bei.index.RecipeIndex;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Sends every recipe's display to a joining client in small batches (the vanilla client only keeps
 * the recipes a player has unlocked). The channel is optional: a server without BEI sends nothing,
 * and the viewer then has no recipes to show.
 */
public final class RecipeChannel {
    private static final int BATCH = 150;
    /** Largest block a client accepts (the game caps a whole packet at about this). */
    private static final int MAX_BLOCK = 1 << 20;
    /** Most recipes a client accepts in one batch; the real batch size is far smaller. */
    private static final int MAX_PER_BATCH = 4096;

    /** One slice of the recipe list. */
    private record Batch(boolean first, boolean last, List<RecipeIndex.Raw> recipes, byte[] data) implements CustomPacketPayload {
        static final CustomPacketPayload.Type<Batch> TYPE = new CustomPacketPayload.Type<>(Bei.id("recipes"));
        static final StreamCodec<RegistryFriendlyByteBuf, Batch> CODEC = StreamCodec.of(RecipeChannel::write, RecipeChannel::read);

        @Override
        public CustomPacketPayload.Type<Batch> type() {
            return TYPE;
        }

        Batch(boolean first, boolean last, List<RecipeIndex.Raw> recipes) {
            this(first, last, recipes, null);
        }
    }

    private RecipeChannel() {
    }

    /** Registers the message with the mod's (optional) payload registrar. */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Batch.TYPE, Batch.CODEC, (batch, context) -> {
            try {
                RecipeIndex.receive(batch.first(), decode(batch.data()), batch.last());
            } catch (RuntimeException e) {
                Bei.LOGGER.warn("Could not read a recipe batch from the server", e);
            }
        });
    }

    /** The recipes go over as a byte block, decoded later on the main thread when the client's registries exist. */
    private static void write(RegistryFriendlyByteBuf buf, Batch batch) {
        RegistryFriendlyByteBuf wrapped = new RegistryFriendlyByteBuf(Unpooled.buffer(), buf.registryAccess());
        wrapped.writeVarInt(batch.recipes().size());
        for (RecipeIndex.Raw raw : batch.recipes()) {
            wrapped.writeUtf(raw.id());
            wrapped.writeUtf(raw.typeId());
            RecipeDisplay.STREAM_CODEC.encode(wrapped, raw.display());
        }
        byte[] bytes = new byte[wrapped.readableBytes()];
        wrapped.readBytes(bytes);
        buf.writeBoolean(batch.first());
        buf.writeBoolean(batch.last());
        buf.writeByteArray(bytes);
    }

    private static Batch read(RegistryFriendlyByteBuf buf) {
        boolean first = buf.readBoolean();
        boolean last = buf.readBoolean();
        return new Batch(first, last, List.of(), buf.readByteArray(MAX_BLOCK));
    }

    private static List<RecipeIndex.Raw> decode(byte[] data) {
        RegistryFriendlyByteBuf wrapped = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), ClientAccess.registries());
        int size = wrapped.readVarInt();
        if (size < 0 || size > MAX_PER_BATCH) {
            throw new IllegalArgumentException("Too many recipes in one batch: " + size);
        }
        List<RecipeIndex.Raw> recipes = new ArrayList<>(Math.min(size, 256));
        for (int i = 0; i < size; i++) {
            String id = wrapped.readUtf(256);
            String typeId = wrapped.readUtf(256);
            recipes.add(new RecipeIndex.Raw(id, RecipeDisplay.STREAM_CODEC.decode(wrapped), typeId));
        }
        return recipes;
    }

    /** Kept apart so a dedicated server never loads the client class. */
    private static final class ClientAccess {
        static RegistryAccess registries() {
            return net.minecraft.client.Minecraft.getInstance().getConnection().registryAccess();
        }
    }

    /** Whether the server on the other end of this connection has BEI. */
    public static boolean isRemotePresent(net.minecraft.network.Connection connection) {
        return Channels.isRemotePresent(connection, Batch.TYPE.id());
    }

    /** Sends all recipes to the player in batches. */
    public static void sendAll(ServerPlayer player) {
        if (!player.connection.hasChannel(Batch.TYPE)) {
            return;
        }
        List<RecipeIndex.Raw> all = new ArrayList<>();
        for (RecipeHolder<?> holder : player.level().getServer().getRecipeManager().getRecipes()) {
            String id = holder.id().identifier().toString();
            String typeId = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()));
            for (RecipeDisplay display : holder.value().display()) {
                all.add(new RecipeIndex.Raw(id, display, typeId));
            }
        }
        if (all.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new Batch(true, true, List.of()));
            return;
        }
        for (int from = 0; from < all.size(); from += BATCH) {
            int to = Math.min(all.size(), from + BATCH);
            PacketDistributor.sendToPlayer(player, new Batch(from == 0, to == all.size(), new ArrayList<>(all.subList(from, to))));
        }
    }
}
