package com.itmecasey.bei.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.bei.index.BeiRecipe;
import com.itmecasey.bei.index.RecipeIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The Master Blueprint's tree and the plan laid over an inventory, on small made-up recipe sets. */
class PlanTest {
    private static final Item LOG = Items.OAK_LOG;
    private static final Item PLANKS = Items.OAK_PLANKS;
    private static final Item BIRCH_PLANKS = Items.BIRCH_PLANKS;
    private static final Item STICK = Items.STICK;
    private static final Item CHEST = Items.CHEST;
    private static final Item IRON = Items.IRON_INGOT;

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @SafeVarargs
    private static BeiRecipe recipe(String id, Item out, int outCount, List<Item>... slots) {
        List<List<ItemStack>> inputs = new ArrayList<>();
        for (List<Item> slot : slots) {
            inputs.add(slot.stream().map(Item::getDefaultInstance).toList());
        }
        return new BeiRecipe(id, "ShapelessCrafting", 0, inputs, List.of(new ItemStack(out, outCount)), List.of());
    }

    @SafeVarargs
    private static List<Item>[] each(Item... items) {
        @SuppressWarnings("unchecked")
        List<Item>[] slots = new List[items.length];
        for (int i = 0; i < items.length; i++) {
            slots[i] = List.of(items[i]);
        }
        return slots;
    }

    private static void recipes(BeiRecipe... all) {
        RecipeIndex.useRecipes(List.of(all));
    }

    @Test
    void anItemWithNoRecipeIsARawMaterial() {
        recipes();
        Plan plan = Plan.compute(Blueprint.build(IRON, 3, Set.of(), null), Map.of());
        assertNull(plan.next());
        assertEquals(Map.of(IRON, 3), plan.missing);
        assertEquals(1, plan.rows.size());
        assertEquals(0.0, plan.fraction());
    }

    @Test
    void aCraftIsCountedInWholeOutputBatches() {
        recipes(recipe("sticks", STICK, 4, each(PLANKS, PLANKS)));
        Plan plan = Plan.compute(Blueprint.build(STICK, 5, Set.of(), null), Map.of());
        assertEquals(1, plan.steps.size());
        Plan.Step step = plan.steps.get(0);
        assertEquals(2, step.crafts());
        assertEquals(8, step.produces());
        assertEquals(Map.of(PLANKS, 4), plan.missing);
    }

    @Test
    void whatYouHoldIsUsedBeforeAnythingIsCrafted() {
        recipes(recipe("sticks", STICK, 4, each(PLANKS, PLANKS)));
        Plan plan = Plan.compute(Blueprint.build(STICK, 4, Set.of(), null), Map.of(STICK, 4));
        assertTrue(plan.complete());
        assertEquals(1.0, plan.fraction());
    }

    @Test
    void aHeldSubAssemblySavesEverythingBeneathIt() {
        // chest = 8 planks, 4 planks = 1 log. Holding 8 planks means no logs are needed.
        List<Item>[] eight = each(PLANKS, PLANKS, PLANKS, PLANKS, PLANKS, PLANKS, PLANKS, PLANKS);
        recipes(recipe("chest", CHEST, 1, eight), recipe("planks", PLANKS, 4, each(LOG)));
        Plan have = Plan.compute(Blueprint.build(CHEST, 1, Set.of(), null), Map.of(PLANKS, 8));
        assertTrue(have.missing.isEmpty());
        assertEquals(1, have.steps.size());
        assertTrue(have.next() != null && have.next().item() == CHEST);

        Plan none = Plan.compute(Blueprint.build(CHEST, 1, Set.of(), null), Map.of());
        assertEquals(Map.of(LOG, 2), none.missing);
        assertEquals(2, none.steps.size());
        assertEquals(PLANKS, none.steps.get(0).item());
    }

    @Test
    void theNextCraftIsTheFirstOneWithEverythingInHand() {
        List<Item>[] eight = each(PLANKS, PLANKS, PLANKS, PLANKS, PLANKS, PLANKS, PLANKS, PLANKS);
        recipes(recipe("chest", CHEST, 1, eight), recipe("planks", PLANKS, 4, each(LOG)));
        Plan plan = Plan.compute(Blueprint.build(CHEST, 1, Set.of(), null), Map.of(LOG, 2));
        assertEquals(PLANKS, plan.next().item());
        assertFalse(plan.steps.get(plan.steps.size() - 1).ready());
    }

    @Test
    void theFractionDoneRisesAsRawMaterialsArrive() {
        recipes(recipe("sticks", STICK, 4, each(PLANKS, PLANKS)));
        Blueprint blueprint = Blueprint.build(STICK, 4, Set.of(), null);
        assertEquals(0.0, Plan.compute(blueprint, Map.of()).fraction());
        assertEquals(0.5, Plan.compute(blueprint, Map.of(PLANKS, 1)).fraction());
        assertEquals(1.0, Plan.compute(blueprint, Map.of(PLANKS, 2)).fraction());
    }

    @Test
    void aRecipeCycleDoesNotLoopForever() {
        // a needs b and b needs a: both end up as raw materials somewhere in the tree.
        recipes(recipe("a", STICK, 1, each(PLANKS)), recipe("b", PLANKS, 1, each(STICK)));
        Plan plan = Plan.compute(Blueprint.build(STICK, 1, Set.of(), null), Map.of());
        assertFalse(plan.rows.isEmpty());
        assertFalse(plan.missing.isEmpty());
    }

    @Test
    void aSlotThatAcceptsSeveralItemsTakesOneYouOwn() {
        recipes(new BeiRecipe("chest", "ShapelessCrafting", 0, List.of(List.of(PLANKS.getDefaultInstance(), BIRCH_PLANKS.getDefaultInstance())),
                List.of(new ItemStack(CHEST)), List.of()));
        Blueprint blueprint = Blueprint.build(CHEST, 1, Set.of(BIRCH_PLANKS), null);
        assertEquals(BIRCH_PLANKS, blueprint.root.ingredients.get(0).node().item);
        Blueprint plain = Blueprint.build(CHEST, 1, Set.of(), null);
        assertEquals(PLANKS, plain.root.ingredients.get(0).node().item);
    }

    @Test
    void theRecipeWithFewerIngredientsWins() {
        recipes(recipe("long", CHEST, 1, each(PLANKS, PLANKS, PLANKS)), recipe("short", CHEST, 1, each(LOG)));
        Blueprint blueprint = Blueprint.build(CHEST, 1, Set.of(), null);
        assertEquals("short", blueprint.root.recipe.id());
    }

    @Test
    void aPreferredRecipeBeatsTheBestScore() {
        recipes(recipe("long", CHEST, 1, each(PLANKS, PLANKS, PLANKS)), recipe("short", CHEST, 1, each(LOG)));
        Blueprint blueprint = Blueprint.build(CHEST, 1, Set.of(), "long");
        assertEquals("long", blueprint.root.recipe.id());
    }

    @Test
    void anUnpackBlockRecipeIsLastResort() {
        // 1 block -> 9 items would loop through the block; the plain 3-item recipe is better even if longer.
        recipes(recipe("unpack", STICK, 9, each(CHEST)), recipe("make", STICK, 1, each(PLANKS, PLANKS, PLANKS)));
        Blueprint blueprint = Blueprint.build(STICK, 1, Set.of(), null);
        assertEquals("make", blueprint.root.recipe.id());
    }
}
