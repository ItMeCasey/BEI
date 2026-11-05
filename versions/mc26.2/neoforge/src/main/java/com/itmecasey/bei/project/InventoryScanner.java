package com.itmecasey.bei.project;

import com.itmecasey.bei.BeiConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Totals what the player owns. The inventory is always counted, the ender chest if the setting is
 * on, and other mods can add storage (a backpack, a networked storage system) with
 * {@link #registerSource}.
 */
public final class InventoryScanner {
    private static final List<Function<Player, ? extends Iterable<ItemStack>>> SOURCES = new CopyOnWriteArrayList<>();

    private InventoryScanner() {
    }

    /** Adds a place to count items from; the function is called with the local player. */
    public static void registerSource(Function<Player, ? extends Iterable<ItemStack>> source) {
        SOURCES.add(source);
    }

    public static Map<Item, Integer> snapshot(Player player) {
        Map<Item, Integer> totals = new HashMap<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            add(totals, player.getInventory().getItem(i));
        }
        if (BeiConfig.countEnderChest()) {
            for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++) {
                add(totals, player.getEnderChestInventory().getItem(i));
            }
        }
        for (Function<Player, ? extends Iterable<ItemStack>> source : SOURCES) {
            try {
                for (ItemStack stack : source.apply(player)) {
                    add(totals, stack);
                }
            } catch (RuntimeException e) {
                // A broken storage source only means its items are not counted.
            }
        }
        return totals;
    }

    private static void add(Map<Item, Integer> totals, ItemStack stack) {
        if (!stack.isEmpty()) {
            totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
    }
}
