# Recipes and Auto-Fill

Click an item in the browser (or press `R` / `U` on a hovered item) to open the recipe popup.

## The popup

- One **button per station** (crafting table, furnace, stonecutter, ...). Stations where you can make something right now come first and carry a green dot; inside a station, the recipes you can make come first.
- Recipes cycle through the alternatives of tag ingredients once a second.
- Click an ingredient or result to follow it; `<` or **Backspace** goes back, **Esc** closes.
- Drag the title bar to move the window; it remembers where you left it.
- Crafting recipes always show a full 3x3 grid, centered in the card.

## Buttons on each recipe

- **`P`**: make this exact recipe your [Master Project](Master-Blueprint). Click again to clear.
- **`+`** (only when you hold every ingredient and the right station is open): moves the ingredients into the station with ordinary slot clicks. **Shift** puts in as many sets as your inventory covers, up to a stack.

`+` works for the crafting table and the inventory's 2x2 grid, furnace, blast furnace, smoker, stonecutter (it also picks the cut) and smithing table. Furnace recipes only show `+` in the furnace that makes them.

## Modded recipes

- **1.21.1:** any recipe type that follows the standard recipe interface shows up with its ingredients and result.
- **26.1.2:** recipes that provide a vanilla recipe display show fully; other types list what they make.

Mods can add their own recipe types and cards with the [Plugin API](Plugin-API).
