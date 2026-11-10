package com.itmecasey.bei.index;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.CreativeModeTab;
import com.itmecasey.bei.BeiConfig;
import com.itmecasey.bei.Bei;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    /** Creative tab names and positions per item, read on the main thread before the background build. */
    private static volatile Map<Item, String> tabNames = Map.of();
    private static volatile Map<Item, Integer> tabOrder = Map.of();

    private ItemIndex() {
    }

    public static List<ItemEntry> entries() {
        return entries;
    }

    private static volatile boolean variantsPending;

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
        collectTabs();
        List<ItemStack> variants = collectVariants();
        Thread thread = new Thread(() -> build(mine, variants), "BEI-index");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * The variants of items (potions, enchanted books, goat horns...) the creative tabs list, as their own stacks.
     * Needs the game's main thread and a world; empty when the setting is off or anything goes wrong.
     */
    private static List<ItemStack> collectVariants() {
        Minecraft game = Minecraft.getInstance();
        if (!BeiConfig.itemVariants()) {
            return List.of();
        }
        if (game.level == null || game.player == null) {
            // Joining: the world is not there yet, so the variants are added by a rebuild on the first tick that has one.
            variantsPending = true;
            return List.of();
        }
        variantsPending = false;
        try {
            CreativeModeTabs.tryRebuildTabContents(game.player.connection.enabledFeatures(), false, game.level.registryAccess());
            List<ItemStack> all = new ArrayList<>();
            for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                if (tab.getType() == CreativeModeTab.Type.CATEGORY) {
                    all.addAll(tab.getDisplayItems());
                }
            }
            return VariantFilter.select(all, stack -> !stack.isEmpty() && !ItemStack.isSameItemSameTags(stack, stack.getItem().getDefaultInstance()),
                    stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()) + "|" + stack.getTag()).stream().map(ItemStack::copy).toList();
        } catch (RuntimeException e) {
            Bei.LOGGER.warn("Could not list item variants from the creative tabs", e);
            return List.of();
        }
    }

    /** Which creative tabs list each item and where, for {@code %tab} searches and the creative tab sort. */
    private static void collectTabs() {
        Minecraft game = Minecraft.getInstance();
        if (game.level == null || game.player == null) {
            return;
        }
        try {
            CreativeModeTabs.tryRebuildTabContents(game.player.connection.enabledFeatures(), false, game.level.registryAccess());
            Map<Item, String> names = new HashMap<>();
            Map<Item, Integer> order = new HashMap<>();
            int position = 0;
            for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                if (tab.getType() != CreativeModeTab.Type.CATEGORY) {
                    continue;
                }
                String tabName = tab.getDisplayName().getString().toLowerCase(Locale.ROOT);
                for (ItemStack stack : tab.getDisplayItems()) {
                    Item item = stack.getItem();
                    names.merge(item, tabName, (a, b) -> a.contains(b) ? a : a + " " + b);
                    order.putIfAbsent(item, position++);
                }
            }
            tabNames = names;
            tabOrder = order;
        } catch (RuntimeException e) {
            Bei.LOGGER.warn("Could not read the creative tabs for tab searches", e);
        }
    }

    private static void tag(ItemEntry entry) {
        entry.tabsLower = tabNames.getOrDefault(entry.item, "");
        entry.tabOrder = tabOrder.getOrDefault(entry.item, Integer.MAX_VALUE);
        int rgb = -1;
        if (entry.item instanceof net.minecraft.world.item.BlockItem block) {
            rgb = block.getBlock().defaultMapColor().col;
        }
        entry.colorsLower = ColorNames.of(entry.id + " " + entry.nameLower, rgb);
    }

    private static ItemEntry variantEntry(ItemStack variant) {
        try {
            Item item = variant.getItem();
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            Set<String> tags = new HashSet<>();
            item.builtInRegistryHolder().tags().forEach(tag -> tags.add(tag.location().toString()));
            ItemEntry made = new ItemEntry(item, variant, key.toString(), key.getNamespace(),
                    variant.getHoverName().getString().toLowerCase(Locale.ROOT), tags, true);
            tag(made);
            return made;
        } catch (RuntimeException e) {
            Bei.LOGGER.warn("Skipping a variant of {} in the item index", variant.getItem(), e);
            return null;
        }
    }

    /** Lists stacks a JEI plugin asked to add (extra ingredients) once the index is already built. */
    public static synchronized void addExtras(List<ItemStack> stacks) {
        if (!ready || stacks.isEmpty()) {
            return;
        }
        List<ItemEntry> merged = new ArrayList<>(entries);
        Set<String> have = new HashSet<>();
        for (ItemEntry entry : merged) {
            if (entry.variant) {
                have.add(entry.id + "|" + entry.nameLower);
            }
        }
        for (ItemStack stack : stacks) {
            ItemEntry made = variantEntry(stack);
            if (made != null && have.add(made.id + "|" + made.nameLower)) {
                merged.add(made);
            }
        }
        entries = List.copyOf(merged);
    }

    private static void build(int mine, List<ItemStack> variants) {
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
                ItemEntry made = new ItemEntry(item, stack, key.toString(), key.getNamespace(),
                        stack.getHoverName().getString().toLowerCase(Locale.ROOT), tags);
                tag(made);
                built.add(made);
            } catch (RuntimeException e) {
                Bei.LOGGER.warn("Skipping {} in the item index", item, e);
            }
        }
        List<ItemStack> all = new ArrayList<>(variants);
        all.addAll(com.itmecasey.bei.compat.JeiAliases.extraStacks());
        for (ItemStack variant : all) {
            if (mine != generation.get()) {
                return;
            }
            ItemEntry made = variantEntry(variant);
            if (made != null) {
                built.add(made);
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
        if (variantsPending && ready && game.level != null && game.player != null) {
            rebuild();
            return;
        }
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
