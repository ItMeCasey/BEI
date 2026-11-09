package com.itmecasey.bei.index;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Recipes a plugin adds or hides after BEI built its index reach the lookups at once. */
class LateRecipesTest {
    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static BeiRecipe recipe(String id, net.minecraft.world.item.Item in, net.minecraft.world.item.Item out) {
        return new BeiRecipe(id, "test", 0, List.of(List.of(new ItemStack(in))), List.of(new ItemStack(out)), List.of());
    }

    @Test
    void addedRecipesAreFoundAndRemovedOnesAreGone() {
        RecipeIndex.useRecipes(List.of(recipe("t:one", Items.COAL, Items.DIAMOND)));
        assertEquals(1, RecipeIndex.producing(Items.DIAMOND).size());
        RecipeIndex.addRecipes(List.of(recipe("t:two", Items.STICK, Items.DIAMOND), recipe("t:three", Items.STONE, Items.DIRT)));
        assertEquals(2, RecipeIndex.producing(Items.DIAMOND).size());
        assertEquals(1, RecipeIndex.using(Items.STONE).size());
        assertEquals(3, RecipeIndex.count());
        RecipeIndex.removeRecipes(Set.of("t:one", "t:three"));
        assertEquals(1, RecipeIndex.producing(Items.DIAMOND).size());
        assertEquals("t:two", RecipeIndex.producing(Items.DIAMOND).get(0).id());
        assertEquals(0, RecipeIndex.using(Items.STONE).size());
        assertEquals(1, RecipeIndex.count());
    }
}
