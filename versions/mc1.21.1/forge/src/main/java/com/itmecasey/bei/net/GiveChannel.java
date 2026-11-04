package com.itmecasey.bei.net;

import com.itmecasey.bei.Bei;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/**
 * Cheat mode's request to the server: put this item in my inventory, or in this slot of the open
 * screen. Nothing is ever spawned in the world. The server only agrees for players in creative
 * mode, with game master permission, or alone in their own singleplayer world, so cheat mode needs
 * no commands but cannot be used by someone who is not allowed to cheat.
 */
public final class GiveChannel {
    private static SimpleChannel channel;

    /** Client to server: {@code amount} of the item, into a slot of the open menu ({@code slot} -1 means anywhere in the inventory). */
    private record Give(String item, int amount, int containerId, int slot) {
    }

    private GiveChannel() {
    }

    public static void register() {
        channel = ChannelBuilder.named(Bei.id("give"))
                .networkProtocolVersion(2)
                .optional()
                .simpleChannel()
                .messageBuilder(Give.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder((give, buf) -> {
                    buf.writeUtf(give.item());
                    buf.writeVarInt(give.amount());
                    buf.writeVarInt(give.containerId());
                    buf.writeVarInt(give.slot() + 1);
                })
                .decoder(buf -> new Give(buf.readUtf(256), buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1))
                .consumerMainThread((give, context) -> {
                    handle(context.getSender(), give);
                    context.setPacketHandled(true);
                })
                .add();
    }

    /** Whether the server on the other end of this connection has BEI's give channel. */
    public static boolean isRemotePresent(net.minecraft.network.Connection connection) {
        return channel != null && channel.isRemotePresent(connection);
    }

    /** Asks for items in the inventory (called by the client). */
    public static void toInventory(Item item, int amount) {
        send(item, amount, 0, -1);
    }

    /** Asks for items in one slot of the open menu (called by the client). */
    public static void toSlot(Item item, int amount, int containerId, int slot) {
        send(item, amount, containerId, slot);
    }

    private static void send(Item item, int amount, int containerId, int slot) {
        if (channel != null) {
            channel.send(new Give(BuiltInRegistries.ITEM.getKey(item).toString(), amount, containerId, slot), PacketDistributor.SERVER.noArg());
        }
    }

    /** Creative players, game masters, and the owner of a private singleplayer world (cheat mode is their own switch). */
    private static boolean allowed(ServerPlayer player) {
        var server = player.level().getServer();
        return player.isCreative() || player.hasPermissions(2)
                || (server.isSingleplayer() && !server.isPublished());
    }

    private static void handle(ServerPlayer player, Give give) {
        if (player == null || !allowed(player)) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(give.item());
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null || item == Items.AIR) {
            return;
        }
        int max = item.getDefaultInstance().getMaxStackSize();
        int amount = Math.max(1, Math.min(give.amount(), max));
        AbstractContainerMenu menu = player.containerMenu;
        ItemStack stack = item.getDefaultInstance();
        stack.setCount(amount);
        if (give.slot() < 0) {
            if (!player.getInventory().add(stack) || !stack.isEmpty()) {
                player.displayClientMessage(Component.translatable("bei.cheat.full"), true);
            }
        } else {
            if (menu.containerId != give.containerId() || give.slot() >= menu.slots.size()) {
                return;
            }
            Slot slot = menu.getSlot(give.slot());
            if (!slot.mayPlace(stack)) {
                return;
            }
            int limit = Math.min(max, slot.getMaxStackSize(stack));
            ItemStack existing = slot.getItem();
            if (existing.isEmpty()) {
                stack.setCount(Math.min(amount, limit));
                slot.set(stack);
            } else if (existing.is(item)) {
                ItemStack grown = existing.copy();
                grown.setCount(Math.min(limit, existing.getCount() + amount));
                slot.set(grown);
            } else {
                return;
            }
        }
        menu.broadcastChanges();
    }
}
