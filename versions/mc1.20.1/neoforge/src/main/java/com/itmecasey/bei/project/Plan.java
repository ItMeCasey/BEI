package com.itmecasey.bei.project;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;

/**
 * A {@link Blueprint} checked against what the player owns. Owned items are used up from the top
 * down, so a held sub-assembly saves everything beneath it. What is left is a list of crafting
 * steps (deepest first, so the first ready one is the next thing to make) and raw materials to
 * gather.
 */
public final class Plan {
    /** One line of the full tree view. */
    public record Row(int depth, Item item, int need, int have, int crafts) {
    }

    /** One ingredient of a step: how many the step needs and how many the player has. */
    public record Need(Item item, int need, int have) {
        public boolean met() {
            return have >= need;
        }
    }

    /** Craft {@code crafts} times to make {@code item}. {@code ready} means every ingredient is in hand. */
    public record Step(Item item, int crafts, int outputPerCraft, List<Need> needs, boolean ready) {
        public int produces() {
            return crafts * outputPerCraft;
        }
    }

    public final List<Row> rows = new ArrayList<>();
    public final List<Step> steps = new ArrayList<>();
    /** Raw materials still to gather, by item. */
    public final Map<Item, Integer> missing = new LinkedHashMap<>();
    private int totalRaw;

    private Plan() {
    }

    /** Raw materials still to gather. */
    public int rawRemaining() {
        return missing.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Fraction done, 0 to 1. */
    public double fraction() {
        if (totalRaw <= 0) {
            return missing.isEmpty() && steps.isEmpty() ? 1.0 : 0.0;
        }
        return Math.max(0.0, Math.min(1.0, 1.0 - rawRemaining() / (double) totalRaw));
    }

    /** The first craft whose ingredients are all in hand, or null if raw materials must be gathered first. */
    public Step next() {
        for (Step step : steps) {
            if (step.ready()) {
                return step;
            }
        }
        return null;
    }

    public boolean complete() {
        return steps.isEmpty() && missing.isEmpty();
    }

    public static Plan compute(Blueprint blueprint, Map<Item, Integer> inventory) {
        Plan plan = new Plan();
        plan.totalRaw = blueprint.totalRaw;
        Map<Item, Integer> pool = new LinkedHashMap<>(inventory);
        plan.resolve(blueprint.root, blueprint.count, pool, 0);
        return plan;
    }

    /** Takes what the pool has, plans the rest. */
    private void resolve(Blueprint.Node node, int need, Map<Item, Integer> pool, int depth) {
        int have = Math.min(need, pool.getOrDefault(node.item, 0));
        pool.merge(node.item, -have, Integer::sum);
        int shortfall = need - have;
        int crafts = node.recipe == null || shortfall == 0 ? 0 : (shortfall + node.outputCount - 1) / node.outputCount;
        rows.add(new Row(depth, node.item, need, have, crafts));
        if (shortfall == 0) {
            return;
        }
        if (node.recipe == null) {
            missing.merge(node.item, shortfall, Integer::sum);
            return;
        }
        List<Need> needs = new ArrayList<>();
        boolean ready = true;
        for (Blueprint.Ingredient ingredient : node.ingredients) {
            int amount = ingredient.perCraft() * crafts;
            int held = Math.min(amount, pool.getOrDefault(ingredient.node().item, 0));
            needs.add(new Need(ingredient.node().item, amount, held));
            if (held < amount) {
                ready = false;
            }
            resolve(ingredient.node(), amount, pool, depth + 1);
        }
        steps.add(new Step(node.item, crafts, node.outputCount, needs, ready));
    }
}
