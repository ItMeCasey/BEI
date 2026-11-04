# Item Browser

The panel along the right edge of inventory screens lists every item in the game. It builds in the background after you join a world, so opening a screen never waits on it.

## Search

Type in the box at the bottom of the panel (`/` focuses it; right-click clears it).

| Word | Matches |
|---|---|
| `iron` | name or id containing the text |
| `@create` | items from that mod |
| `#c:ingots` | items with that tag (or any tag containing the text) |
| `$fire` | text in the item's tooltip (tooltips are read a few per tick, so these results narrow as it finishes) |
| `-word` | leaves out matches |

Words combine with AND.

## Categories

Turn on **Category icons** in the settings. A row of icons above the grid filters in one click: blocks, tools, armor, food, furnace fuels, logs, ingots, dyes. **All** clears the filter.

## Groups and hidden items

Datapacks and [ModJS scripts](ModJS-Scripts) can collect items into groups and hide items. A group is one highlighted cell with its item count.

- Hover a group to see every item in it.
- Hold **Shift** while hovering to freeze the tooltip; it stays until you let go of Shift, so you can move into it, see each item's own tooltip and click one.
- **Right-click** a group to expand it inline; right-click again to collapse it.
- Searching shows a group when any item in it matches.

See [Datapacks](Datapacks) for how to define them.

## Mouse

| Click | Does |
|---|---|
| Left | recipes for the item |
| Right | uses of the item |
| Wheel | scroll (Shift: a page) |
| Drag the scrollbar | scroll |
