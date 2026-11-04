package com.itmecasey.bei.index;

import com.itmecasey.bei.Bei;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/**
 * Every item in the game, built on a background thread after joining a world so opening a screen
 * never waits on it. Tooltip text (for {@code $} searches) is gathered a few items per tick on the
 * main thread, because tooltips are not safe to build elsewhere.
 */
public final class ItemIndex {
    private static volatile List<ItemEntry> entries = List.of();
    private static volatile boolean ready;
    private static final AtomicInteger generation = new AtomicInteger();
    private static int tooltipCursor;

    private ItemIndex() {
    }

    public static List<ItemEntry> entries() {
        return entries;
    }

    public static boolean ready() {
        return ready;
    }

    /** Share of items whose tooltip text has been read, 0 to 1. */
    public static float tooltipProgress() {
        List<ItemEntry> all = entries;
        return all.isEmpty() ? 1F : Math.min(1F, tooltipCursor / (float) all.size());
    }

    /** Starts a fresh background build; an older build still running is dropped. */
    public static void rebuild() {
        int mine = generation.incrementAndGet();
        ready = false;
        tooltipCursor = 0;
        Thread thread = new Thread(() -> build(mine), "BEI-index");
        thread.setDaemon(true);
        thread.start();
    }

    private static void build(int mine) {
        long start = System.nanoTime();
        List<ItemEntry> built = new ArrayList<>(BuiltInRegistries.ITEM.size());
        for (Item item : BuiltInRegistries.ITEM) {
            if (mine != generation.get()) {
                return;
            }
            if (item == Items.AIR) {
                continue;
            }
            try {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                ItemStack stack = item.getDefaultInstance();
                Set<String> tags = new HashSet<>();
                item.builtInRegistryHolder().tags().forEach(tag -> tags.add(tag.location().toString()));
                built.add(new ItemEntry(item, stack, key.toString(), key.getNamespace(),
                        stack.getHoverName().getString().toLowerCase(Locale.ROOT), tags));
            } catch (RuntimeException e) {
                Bei.LOGGER.warn("Skipping {} in the item index", item, e);
            }
        }
        if (mine == generation.get()) {
            entries = List.copyOf(built);
            ready = true;
            Bei.LOGGER.info("Indexed {} items in {} ms", built.size(), (System.nanoTime() - start) / 1_000_000);
        }
    }

    /** Reads tooltips for as many items as fit in the time budget; called every client tick. */
    public static void tickTooltips(long budgetNanos) {
        Minecraft game = Minecraft.getInstance();
        List<ItemEntry> all = entries;
        if (!ready || tooltipCursor >= all.size() || game.level == null || game.player == null) {
            return;
        }
        long end = System.nanoTime() + budgetNanos;
        while (tooltipCursor < all.size() && System.nanoTime() < end) {
            ItemEntry entry = all.get(tooltipCursor++);
            StringBuilder text = new StringBuilder();
            try {
                entry.stack.getTooltipLines(game.player, TooltipFlag.NORMAL)
                        .forEach(line -> text.append(line.getString()).append('\n'));
            } catch (RuntimeException e) {
                // A broken tooltip only means this item cannot be found by its tooltip.
            }
            entry.tooltipLower = text.toString().toLowerCase(Locale.ROOT);
        }
    }
}
