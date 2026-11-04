package com.itmecasey.bei.client;

import java.util.function.Predicate;
import com.itmecasey.bei.index.ItemEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** The clickable icons of the tag tray; each one is a ready-made filter. */
public enum Category {
    BLOCKS(Items.GRASS_BLOCK, e -> e.item instanceof BlockItem),
    TOOLS(Items.IRON_PICKAXE, e -> hasTag(e, "pickaxes", "axes", "shovels", "hoes", "swords", "tools")),
    ARMOR(Items.IRON_CHESTPLATE, e -> e.stack.has(DataComponents.EQUIPPABLE) && !(e.item instanceof BlockItem)),
    FOOD(Items.APPLE, e -> e.stack.has(DataComponents.FOOD)),
    FUEL(Items.COAL, Category::isFuel),
    LOGS(Items.OAK_LOG, e -> hasTag(e, "logs")),
    INGOTS(Items.IRON_INGOT, e -> hasTag(e, "ingots")),
    DYES(Items.RED_DYE, e -> hasTag(e, "dyes"));

    public final Item icon;
    public final Predicate<ItemEntry> filter;

    Category(Item icon, Predicate<ItemEntry> filter) {
        this.icon = icon;
        this.filter = filter;
    }

    public String translationKey() {
        return "bei.category." + name().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean hasTag(ItemEntry entry, String... names) {
        for (String tag : entry.tags) {
            String path = tag.substring(tag.indexOf(':') + 1);
            for (String name : names) {
                if (path.equals(name) || path.endsWith("/" + name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isFuel(ItemEntry entry) {
        var level = Minecraft.getInstance().level;
        return level != null && level.fuelValues().isFuel(entry.stack);
    }
}
