package com.itmecasey.bei.project;

import com.itmecasey.bei.index.BeiRecipe;
import com.itmecasey.bei.index.RecipeIndex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The dependency tree of an item: each node is one item with the recipe chosen for it (none for a
 * raw material) and the ingredients one craft takes. The tree knows nothing about the player's
 * inventory; {@link Plan} lays an inventory over it.
 */
public final class Blueprint {
    private static final int MAX_DEPTH = 16;

    /** An ingredient: the node it is, and how many one craft of the parent uses. */
    public record Ingredient(Node node, int perCraft) {
    }

    /** One item in the tree. {@code recipe} is null for a raw material. */
    public static final class Node {
        public final Item item;
        public BeiRecipe recipe;
        public int outputCount = 1;
        public final List<Ingredient> ingredients = new ArrayList<>();

        Node(Item item) {
            this.item = item;
        }
    }

    public final Node root;
    public final int count;
    /** Raw materials the whole project needs from nothing; the base for the percent done. */
    public final int totalRaw;

    private Blueprint(Node root, int count) {
        this.root = root;
        this.count = count;
        this.totalRaw = Plan.compute(this, Map.of()).rawRemaining();
    }

    /**
     * Builds the tree for {@code count} of an item. Where a slot accepts several items, one the
     * player already owns wins, which keeps the plan near what they can do now.
     */
    public static Blueprint build(Item item, int count, Set<Item> owned, @Nullable String preferredRecipe) {
        Map<Item, Node> done = new HashMap<>();
        Node root = node(item, new ArrayList<>(), done, owned, 0, preferredRecipe);
        return new Blueprint(root, count);
    }

    private static Node node(Item item, List<Item> path, Map<Item, Node> done, Set<Item> owned, int depth, @Nullable String preferred) {
        Node memo = done.get(item);
        if (memo != null) {
            return memo;
        }
        Node node = new Node(item);
        if (depth < MAX_DEPTH) {
            path.add(item);
            BeiRecipe best = null;
            List<Map<Item, Integer>> bestSlots = null;
            int bestScore = Integer.MAX_VALUE;
            for (BeiRecipe recipe : RecipeIndex.producing(item)) {
                List<Map<Item, Integer>> slots = pick(recipe, path, owned);
                if (slots == null) {
                    continue;
                }
                int score = depth == 0 && preferred != null && recipe.id().equals(preferred) ? -1000 : score(recipe, item, slots);
                if (score < bestScore) {
                    best = recipe;
                    bestSlots = slots;
                    bestScore = score;
                }
            }
            if (best != null) {
                node.recipe = best;
                node.outputCount = outputCount(best, item);
                Map<Item, Integer> merged = new LinkedHashMap<>();
                for (Map<Item, Integer> slot : bestSlots) {
                    slot.forEach((k, v) -> merged.merge(k, v, Integer::sum));
                }
                merged.forEach((ingredient, n) ->
                        node.ingredients.add(new Ingredient(node(ingredient, path, done, owned, depth + 1, null), n)));
            }
            path.remove(path.size() - 1);
        }
        // Shared so a diamond-shaped tree is not built twice. A node built under an open ancestor may
        // have been cut short by the cycle check; that only ever makes it a raw material, never wrong.
        done.put(item, node);
        return node;
    }

    /** One item per filled slot, or null if some slot can only be filled by an item already on the path. */
    private static List<Map<Item, Integer>> pick(BeiRecipe recipe, List<Item> path, Set<Item> owned) {
        List<Map<Item, Integer>> slots = new ArrayList<>();
        for (List<ItemStack> alternatives : recipe.inputs()) {
            if (alternatives.isEmpty()) {
                continue;
            }
            Item chosen = null;
            for (ItemStack alternative : alternatives) {
                Item candidate = alternative.getItem();
                if (path.contains(candidate)) {
                    continue;
                }
                if (owned.contains(candidate)) {
                    chosen = candidate;
                    break;
                }
                if (chosen == null) {
                    chosen = candidate;
                }
            }
            if (chosen == null) {
                return null;
            }
            slots.add(Map.of(chosen, 1));
        }
        return slots.isEmpty() ? null : slots;
    }

    private static int outputCount(BeiRecipe recipe, Item item) {
        for (ItemStack stack : recipe.outputs()) {
            if (stack.getItem() == item) {
                return Math.max(1, stack.getCount());
            }
        }
        return 1;
    }

    /** Lower is better: few ingredients, plain crafting, and not an "unpack a block" recipe. */
    private static int score(BeiRecipe recipe, Item item, List<Map<Item, Integer>> slots) {
        int score = slots.size();
        if (!recipe.isCrafting()) {
            score += 4;
        }
        long distinct = slots.stream().flatMap(s -> s.keySet().stream()).distinct().count();
        if (distinct == 1 && slots.size() == 1 && outputCount(recipe, item) >= 4) {
            score += 50;
        }
        return score;
    }
}
