package com.itmecasey.bei.client;

import com.itmecasey.bei.index.BeiRecipe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Moves a recipe's ingredients from the player's inventory into the open station with ordinary
 * slot clicks, so the server sees exactly what a player doing it by hand would send. Covers the
 * crafting table and the inventory's 2x2 grid, furnace, blast furnace and smoker, stonecutter
 * (which also picks the recipe) and smithing table.
 */
final class AutoFill {
    private AutoFill() {
    }

    /** An ingredient slot: any of {@code options} goes into {@code slot}. */
    private record Target(List<ItemStack> options, Slot slot) {
    }

    /** One ingredient move; {@code all} puts the whole stack in, otherwise a single item. */
    private record Move(Slot from, Slot to, boolean all) {
    }

    /** Which slots to fill and which to empty first, for the open station. */
    private record Layout(List<Target> targets, List<Slot> clear, boolean all) {
    }

    static @Nullable AbstractContainerMenu menu() {
        return Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen ? screen.getMenu() : null;
    }

    /** Whether the open screen is a station this recipe is made in and the player holds every ingredient. */
    static boolean canFill(BeiRecipe recipe) {
        AbstractContainerMenu menu = menu();
        Player player = Minecraft.getInstance().player;
        if (menu == null || player == null) {
            return false;
        }
        Layout layout = layout(recipe, menu);
        return layout != null && plan(layout, menu, player, true) != null;
    }

    /** Fills the station; returns whether it did. {@code max} puts in as many sets as the inventory allows, up to a full stack. */
    static boolean fill(BeiRecipe recipe, boolean max) {
        AbstractContainerMenu menu = menu();
        Minecraft game = Minecraft.getInstance();
        Player player = game.player;
        if (menu == null || player == null || game.gameMode == null) {
            return false;
        }
        Layout layout = layout(recipe, menu);
        if (layout == null) {
            return false;
        }
        // Empty the slots first; the clicks apply to the client's menu at once, so the plan sees the result.
        for (Slot slot : layout.clear()) {
            if (slot.hasItem()) {
                game.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, ClickType.QUICK_MOVE, player);
            }
        }
        List<Move> moves = plan(layout, menu, player, false);
        if (moves == null) {
            return false;
        }
        int sets = max && !layout.all() ? setsFor(moves) : 1;
        for (Move move : moves) {
            game.gameMode.handleInventoryMouseClick(menu.containerId, move.from().index, 0, ClickType.PICKUP, player);
            if (move.all()) {
                game.gameMode.handleInventoryMouseClick(menu.containerId, move.to().index, 0, ClickType.PICKUP, player);
            } else {
                for (int i = 0; i < sets; i++) {
                    game.gameMode.handleInventoryMouseClick(menu.containerId, move.to().index, 1, ClickType.PICKUP, player);
                }
            }
            game.gameMode.handleInventoryMouseClick(menu.containerId, move.from().index, 0, ClickType.PICKUP, player);
        }
        if (menu instanceof StonecutterMenu cutter) {
            selectCut(cutter, recipe);
        }
        return true;
    }

    // ---- which station, which slots ---------------------------------------------------------------

    private static @Nullable Layout layout(BeiRecipe recipe, AbstractContainerMenu menu) {
        if (recipe.type().startsWith("bei:slots:")) {
            // A plain slot range (see SlotFill): the recipe's inputs go into the slots from the given index on.
            int start = Integer.parseInt(recipe.type().substring("bei:slots:".length()));
            List<Target> targets = new ArrayList<>();
            List<Slot> clear = new ArrayList<>();
            for (int i = 0; i < recipe.inputs().size(); i++) {
                if (start + i >= menu.slots.size()) {
                    return null;
                }
                Slot slot = menu.slots.get(start + i);
                clear.add(slot);
                if (!recipe.inputs().get(i).isEmpty()) {
                    targets.add(new Target(recipe.inputs().get(i), slot));
                }
            }
            return new Layout(targets, clear, false);
        }
        switch (recipe.type()) {
            case "ShapedCrafting", "ShapelessCrafting" -> {
                if (!(menu instanceof CraftingMenu) && !(menu instanceof InventoryMenu)) {
                    return null;
                }
                RecipeBookMenu<?, ?> crafting = (RecipeBookMenu<?, ?>) menu;
                int gridW = crafting.getGridWidth();
                int gridH = crafting.getGridHeight();
                // The grid's slots follow the result slot.
                List<Slot> grid = new ArrayList<>(menu.slots.subList(1, 1 + gridW * gridH));
                int count = recipe.inputs().size();
                int width = recipe.width();
                if (width > 0 ? width > gridW || (count + width - 1) / width > gridH : count > grid.size()) {
                    return null;
                }
                List<Target> targets = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    int cell = width > 0 ? (i / width) * gridW + i % width : i;
                    if (cell >= grid.size()) {
                        return null;
                    }
                    targets.add(new Target(recipe.inputs().get(i), grid.get(cell)));
                }
                return new Layout(targets, grid, false);
            }
            case "Furnace" -> {
                if (!(menu instanceof AbstractFurnaceMenu) || !madeIn(recipe, stationOf(menu)) || recipe.inputs().isEmpty()) {
                    return null;
                }
                Slot input = menu.slots.get(0);
                return new Layout(List.of(new Target(recipe.inputs().get(0), input)), List.of(input), true);
            }
            case "Stonecutter" -> {
                if (!(menu instanceof StonecutterMenu) || recipe.inputs().isEmpty()) {
                    return null;
                }
                Slot input = menu.slots.get(0);
                return new Layout(List.of(new Target(recipe.inputs().get(0), input)), List.of(input), true);
            }
            case "Smithing" -> {
                if (!(menu instanceof SmithingMenu) || recipe.inputs().size() < 3) {
                    return null;
                }
                List<Slot> slots = List.of(menu.slots.get(0), menu.slots.get(1), menu.slots.get(2));
                List<Target> targets = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    targets.add(new Target(recipe.inputs().get(i), slots.get(i)));
                }
                return new Layout(targets, slots, false);
            }
            default -> {
                return null;
            }
        }
    }

    private static @Nullable Item stationOf(AbstractContainerMenu menu) {
        if (menu instanceof FurnaceMenu) {
            return Items.FURNACE;
        }
        if (menu instanceof BlastFurnaceMenu) {
            return Items.BLAST_FURNACE;
        }
        if (menu instanceof SmokerMenu) {
            return Items.SMOKER;
        }
        return null;
    }

    /** Smelting, blasting and smoking all share one recipe shape, so the station tells them apart. */
    private static boolean madeIn(BeiRecipe recipe, @Nullable Item station) {
        if (station == null) {
            return false;
        }
        for (ItemStack made : recipe.stations()) {
            if (made.getItem() == station) {
                return true;
            }
        }
        return false;
    }

    // ---- matching ingredients to inventory slots --------------------------------------------------

    /**
     * Matches each target to an inventory slot that has the item, counting stacks down as they are
     * used. Null if something is missing. {@code countCleared} also counts what already sits in the
     * slots about to be emptied (for the button, which is judged before the clearing).
     */
    private static @Nullable List<Move> plan(Layout layout, AbstractContainerMenu menu, Player player, boolean countCleared) {
        Map<Slot, Integer> left = new HashMap<>();
        for (Slot slot : menu.slots) {
            boolean inventory = slot.container == player.getInventory();
            if ((inventory || (countCleared && layout.clear().contains(slot))) && slot.hasItem()) {
                left.put(slot, slot.getItem().getCount());
            }
        }
        List<Move> moves = new ArrayList<>();
        for (Target target : layout.targets()) {
            if (target.options().isEmpty()) {
                continue;
            }
            Slot source = null;
            for (Map.Entry<Slot, Integer> entry : left.entrySet()) {
                if (entry.getValue() > 0 && accepts(target.options(), entry.getKey().getItem().getItem())) {
                    source = entry.getKey();
                    break;
                }
            }
            if (source == null) {
                return null;
            }
            left.merge(source, layout.all() ? -left.get(source) : -1, Integer::sum);
            moves.add(new Move(source, target.slot(), layout.all()));
        }
        return moves.isEmpty() ? null : moves;
    }

    /** How many copies of the whole recipe the chosen stacks cover, up to one full stack. */
    private static int setsFor(List<Move> moves) {
        Map<Slot, Integer> uses = new HashMap<>();
        for (Move move : moves) {
            uses.merge(move.from(), 1, Integer::sum);
        }
        int sets = 64;
        for (Map.Entry<Slot, Integer> entry : uses.entrySet()) {
            ItemStack stack = entry.getKey().getItem();
            sets = Math.min(sets, Math.min(stack.getMaxStackSize(), stack.getCount() / entry.getValue()));
        }
        return Math.max(1, sets);
    }

    private static boolean accepts(List<ItemStack> options, Item item) {
        for (ItemStack option : options) {
            if (option.getItem() == item) {
                return true;
            }
        }
        return false;
    }

    /** With the input in place, picks the stonecutter's entry that makes what the recipe makes. */
    private static void selectCut(StonecutterMenu menu, BeiRecipe recipe) {
        Minecraft game = Minecraft.getInstance();
        if (game.level == null || game.gameMode == null) {
            return;
        }
        var recipes = menu.getRecipes();
        for (int i = 0; i < recipes.size(); i++) {
            ItemStack shown = recipes.get(i).value().getResultItem(game.level.registryAccess());
            for (ItemStack wanted : recipe.outputs()) {
                if (shown.getItem() == wanted.getItem()) {
                    game.gameMode.handleInventoryButtonClick(menu.containerId, i);
                    return;
                }
            }
        }
    }
}
