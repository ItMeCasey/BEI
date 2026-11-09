package com.itmecasey.bei.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The runtime a JEI plugin gets in onRuntimeAvailable: hiding items and categories reaches BEI's hiding. */
class JeiRuntimeTest {
    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void reset() {
        JeiHidden.clear();
    }

    private static IJeiRuntime runtime() {
        return JeiBridge.runtime(JeiBridge.helpers());
    }

    @Test
    void removingItemStacksHidesTheirItems() {
        runtime().getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK,
                List.of(new ItemStack(Items.STONE), new ItemStack(Items.DIAMOND), ItemStack.EMPTY));
        assertEquals(List.of("minecraft:diamond", "minecraft:stone"), JeiHidden.itemSpecs().stream().sorted().toList());
    }

    @Test
    void addingThemBackShowsThemAgain() {
        IJeiRuntime runtime = runtime();
        runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, List.of(new ItemStack(Items.STONE)));
        runtime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, List.of(new ItemStack(Items.STONE)));
        assertTrue(JeiHidden.itemSpecs().isEmpty());
    }

    @Test
    void hidingACategoryReachesTheHiddenList() {
        RecipeType<Object> type = RecipeType.create("ae2", "inscriber", Object.class);
        IJeiRuntime runtime = runtime();
        runtime.getRecipeManager().hideRecipeCategory(type);
        assertTrue(JeiHidden.categoryHidden(null, "ae2:inscriber"));
        runtime.getRecipeManager().unhideRecipeCategory(type);
        assertFalse(JeiHidden.categoryHidden(null, "ae2:inscriber"));
    }

    @Test
    void theCategoryLookupOffersGameRecipeTypesAndHidingOneWorks() {
        IJeiRuntime runtime = runtime();
        var category = runtime.getRecipeManager().createRecipeCategoryLookup().includeHidden().get()
                .filter(c -> c.getRecipeType().getUid().toString().equals("minecraft:campfire_cooking"))
                .findFirst().orElseThrow();
        runtime.getRecipeManager().hideRecipeCategory(category.getRecipeType());
        assertTrue(JeiHidden.categoryHidden("minecraft:campfire_cooking", null));
        assertFalse(JeiHidden.categoryHidden("minecraft:smelting", null));
    }

    @Test
    void unsupportedCallsAnswerWithoutThrowing() {
        IJeiRuntime runtime = runtime();
        runtime.getIngredientManager().getAllIngredients(VanillaTypes.ITEM_STACK);
        runtime.getIngredientFilter();
        runtime.getBookmarkOverlay();
    }
}
