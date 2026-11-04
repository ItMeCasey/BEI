# Master Blueprint

Pick an item as your Master Project and BEI works out everything needed to make it.

## Setting a project

- Hover an item and press `P`, or click the `P` button on a recipe card (that pins the exact recipe for the top item).
- `J` opens the full tree; the `-10 -1 +1 +10` buttons change how many to make, **Clear** drops the project.

## What it works out

- A tree of the item, its sub-assemblies and the raw materials. Each item uses one recipe, favoring plain crafting and avoiding "unpack a block" recipes; cycles such as ingot, block, ingot are cut.
- Where a slot accepts several items, one you already own is chosen.
- Your inventory (and your ender chest, if you turn that on) is laid over the tree from the top down, so a sub-assembly you already hold saves everything beneath it.
- The result is a list of crafts, deepest first. The first craft whose ingredients are all in hand is your **next step**; if none is ready, you get a gather list.

## The HUD

The project is pinned to a screen corner as `Project: <item> (34%)` with a progress bar, the next craft and its ingredients (green when you have enough), what to gather, and the next few steps. `H` shows or hides it; the corner is a setting.

## Storage

Mods that add storage can register a source with `InventoryScanner.registerSource`, so items in a backpack or storage network count as owned.
