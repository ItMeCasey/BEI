package com.itmecasey.bei.plugin;

import com.itmecasey.bei.api.BeiPlugin;
import com.itmecasey.bei.api.CardPainter;
import com.itmecasey.bei.api.CustomRecipe;
import com.itmecasey.bei.api.IBeiPlugin;
import com.itmecasey.bei.api.ICategoryRegistration;
import com.itmecasey.bei.api.IRecipeRegistration;
import com.itmecasey.bei.api.RecipeCategory;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * An example plugin showing the API: a "Grinding" category with two inputs, an output, a drawn bar
 * and a tooltip. It only does anything when the game runs with {@code -Dbei.examples=true}, which
 * the development client does, so it never appears for players.
 */
@BeiPlugin
public class ExamplePlugin implements IBeiPlugin {
    private static final RecipeCategory CATEGORY = RecipeCategory.builder(ResourceLocation.fromNamespaceAndPath("bei", "example_grinding"))
            .title(Component.literal("Example Grinding"))
            .station(new ItemStack(Items.GRINDSTONE))
            .size(110, 36)
            .input(8, 9)
            .input(28, 9)
            .output(86, 9)
            .painter(new CardPainter() {
                @Override
                public void paint(GuiGraphics g, CustomRecipe recipe, int mx, int my) {
                    g.fill(50, 16, 78, 20, 0xFF4F9DFF);
                }

                @Override
                public List<Component> tooltip(CustomRecipe recipe, int mx, int my) {
                    return mx >= 50 && mx < 78 && my >= 14 && my < 22 ? List.of(Component.literal("Takes 4 seconds")) : null;
                }
            })
            .build();

    private static boolean enabled() {
        return Boolean.getBoolean("bei.examples");
    }

    @Override
    public void registerCategories(ICategoryRegistration registration) {
        if (enabled()) {
            registration.register(CATEGORY);
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (!enabled()) {
            return;
        }
        registration.add(CATEGORY, CustomRecipe.builder()
                .input(new ItemStack(Items.COBBLESTONE))
                .input(new ItemStack(Items.DIAMOND))
                .output(new ItemStack(Items.GRAVEL, 4))
                .build());
    }
}
