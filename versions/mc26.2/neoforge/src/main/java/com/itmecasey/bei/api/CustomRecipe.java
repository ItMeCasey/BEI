package com.itmecasey.bei.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * One recipe of a custom {@link RecipeCategory}: what goes in, what comes out, and any data the
 * category's {@link CardPainter} wants to draw (energy cost, time, a fluid).
 */
public final class CustomRecipe {
    private final @Nullable String id;
    private final List<List<ItemStack>> inputs;
    private final List<ItemStack> outputs;
    private final @Nullable Object data;

    private CustomRecipe(Builder builder) {
        this.id = builder.id;
        this.inputs = List.copyOf(builder.inputs);
        this.outputs = List.copyOf(builder.outputs);
        this.data = builder.data;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** An id for the recipe (used for the Master Project's recipe choice), or null for a generated one. */
    public @Nullable String id() {
        return id;
    }

    /** One list of acceptable stacks per input slot, in the order of the category's input slots. */
    public List<List<ItemStack>> inputs() {
        return inputs;
    }

    /** The outputs, in the order of the category's output slots. */
    public List<ItemStack> outputs() {
        return outputs;
    }

    /** Whatever the plugin attached, for its painter to read; null if nothing. */
    public @Nullable Object data() {
        return data;
    }

    public static final class Builder {
        private @Nullable String id;
        private final List<List<ItemStack>> inputs = new ArrayList<>();
        private final List<ItemStack> outputs = new ArrayList<>();
        private @Nullable Object data;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /** Adds an input slot that accepts any one of the given stacks. */
        public Builder input(ItemStack... acceptable) {
            inputs.add(List.of(acceptable));
            return this;
        }

        /** Adds an input slot that accepts any one of the given stacks. */
        public Builder input(List<ItemStack> acceptable) {
            inputs.add(List.copyOf(acceptable));
            return this;
        }

        /** Adds a blank input slot, to keep later inputs in their positions. */
        public Builder emptyInput() {
            inputs.add(List.of());
            return this;
        }

        public Builder output(ItemStack... made) {
            outputs.addAll(Arrays.asList(made));
            return this;
        }

        public Builder data(@Nullable Object data) {
            this.data = data;
            return this;
        }

        public CustomRecipe build() {
            if (outputs.isEmpty()) {
                throw new IllegalStateException("A recipe needs at least one output");
            }
            return new CustomRecipe(this);
        }
    }
}
