package com.itmecasey.bei.api;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A kind of recipe and the look of its card: a title, the station it is made in (shown as the
 * category's button in the recipe popup), the size of the card area, where the item slots sit, and
 * an optional {@link CardPainter} for everything else.
 *
 * <pre>{@code
 * RecipeCategory grinder = RecipeCategory.builder(Identifier.fromNamespaceAndPath("mymod", "grinding"))
 *         .title(Component.literal("Grinding"))
 *         .station(new ItemStack(MyItems.GRINDER.get()))
 *         .size(110, 36)
 *         .input(8, 9)
 *         .output(86, 9)
 *         .painter((g, recipe, mx, my) -> g.text(font, "->", 50, 14, 0xFFFFFFFF, false))
 *         .build();
 * }</pre>
 */
public final class RecipeCategory {
    private final Identifier id;
    private final Component title;
    private final ItemStack station;
    private final int width;
    private final int height;
    private final List<SlotSpec> slots;
    private final @Nullable CardPainter painter;

    private RecipeCategory(Builder builder) {
        this.id = builder.id;
        this.title = builder.title;
        this.station = builder.station;
        this.width = builder.width;
        this.height = builder.height;
        this.slots = List.copyOf(builder.slots);
        this.painter = builder.painter;
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public Identifier id() {
        return id;
    }

    /** The name shown on the card and in the station button's tooltip. */
    public Component title() {
        return title;
    }

    /** The block or item this is made in; its icon is the category's button. */
    public ItemStack station() {
        return station;
    }

    /** Width of the card area in pixels. */
    public int width() {
        return width;
    }

    /** Height of the card area in pixels. */
    public int height() {
        return height;
    }

    /** The slots, in order: the n-th input slot shows the recipe's n-th input, the n-th output slot its n-th output. */
    public List<SlotSpec> slots() {
        return slots;
    }

    public @Nullable CardPainter painter() {
        return painter;
    }

    public static final class Builder {
        private final Identifier id;
        private Component title;
        private ItemStack station = ItemStack.EMPTY;
        private int width = 100;
        private int height = 36;
        private final List<SlotSpec> slots = new ArrayList<>();
        private @Nullable CardPainter painter;

        private Builder(Identifier id) {
            this.id = id;
            this.title = Component.literal(id.toString());
        }

        public Builder title(Component title) {
            this.title = title;
            return this;
        }

        public Builder station(ItemStack station) {
            this.station = station;
            return this;
        }

        /** Size of the card area; the card is this plus a title line and some padding. */
        public Builder size(int width, int height) {
            this.width = Math.max(18, width);
            this.height = Math.max(18, height);
            return this;
        }

        /** Adds an input slot at this spot (pixels from the card area's top left). */
        public Builder input(int x, int y) {
            slots.add(new SlotSpec(x, y, false));
            return this;
        }

        /** Adds an output slot at this spot. */
        public Builder output(int x, int y) {
            slots.add(new SlotSpec(x, y, true));
            return this;
        }

        public Builder painter(@Nullable CardPainter painter) {
            this.painter = painter;
            return this;
        }

        public RecipeCategory build() {
            if (station.isEmpty()) {
                throw new IllegalStateException("A category needs a station item");
            }
            return new RecipeCategory(this);
        }
    }
}
