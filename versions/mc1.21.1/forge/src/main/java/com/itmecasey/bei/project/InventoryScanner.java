package com.itmecasey.bei.project;

import com.itmecasey.bei.Bei;
import com.itmecasey.bei.BeiConfig;
import com.itmecasey.bei.api.IItemCounter;
import com.itmecasey.bei.api.IStorageSource;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * Totals what the player owns for the Master Blueprint and the recipe popup. The sources are the inventory, the ender
 * chest if the setting is on, and whatever plugins add with {@link #register} (a backpack, a networked storage
 * system); see {@link IStorageSource}.
 */
public final class InventoryScanner {
    private static final StorageTotals.Registry<IStorageSource> SOURCES = new StorageTotals.Registry<>(IStorageSource::id);

    static {
        SOURCES.register(new IStorageSource() {
            @Override
            public String id() {
                return "bei:inventory";
            }

            @Override
            public void count(Player player, IItemCounter counter) {
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    counter.add(player.getInventory().getItem(i));
                }
            }
        });
        SOURCES.register(new IStorageSource() {
            @Override
            public String id() {
                return "bei:ender_chest";
            }

            @Override
            public void count(Player player, IItemCounter counter) {
                if (!BeiConfig.countEnderChest()) {
                    return;
                }
                for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++) {
                    counter.add(player.getEnderChestInventory().getItem(i));
                }
            }
        });
    }

    private InventoryScanner() {
    }

    /** Adds a place to count items from; a source with the same id replaces the earlier one. */
    public static void register(IStorageSource source) {
        SOURCES.register(source);
    }

    public static Map<Item, Integer> snapshot(Player player) {
        return StorageTotals.collect(SOURCES.snapshot(), (source, totals) -> source.count(player, totals::add),
                e -> Bei.LOGGER.warn("A BEI storage source failed; its items are not counted", e));
    }
}
