# Changelog

## 2026.11.1 (Forge 1.21.1)

First port of the 26.1.2 release, checked in play: the browser, recipe cards, tooltips and cursor counts all look right. Recipes index from the client (1279 in a vanilla world).

- Datapack themes, groups and hidden lists are resent when the datapacks reload, not only on join.
- Recipes are read from the client's own recipe list instead of being sent by the server, so modded recipe types that implement the standard recipe interface show up and `hideCategories` works on any server.
- See `docs/PORTING.md` for the differences from the 26.1.2 build.

## 2026.11.1 (Forge 26.1.2)

First release.

- Item browser along the right screen edge: background-built index, search with `@mod`, `#tag`, `$tooltip` and `-exclude`, category icons with an All button, scrollbar, adjustable grid size.
- Recipe popup: recipes grouped by station with craftable stations first, uses lookups, back button and Backspace, a `P` button per recipe for the Master Project, and a `+` button that fills the crafting grid, furnace, blast furnace, smoker, stonecutter or smithing table (Shift fills a full stack).
- Master Blueprint tracker: recursive dependency tree, plan against your inventory with the next craft and a gather list, percent-done HUD with a corner setting, project tree screen, optional ender chest counting and a storage-source hook.
- Themes: dark and light built in, datapack themes under `data/<namespace>/bei_themes/`, opacity slider, draggable popups, BEI-drawn tooltips with the item icon, rarity border, attribute lines, durability and the mod name with F3+H.
- Datapack groups (`bei_groups`) and hidden items (`bei_hidden`), with hover tooltips that freeze while Shift is held, and inline expanding by right-click.
- Cheat mode: hold items on a remembered cursor or place them in the inventory, with no commands; server-side permission check.
- Plugin API (`@BeiPlugin`) for custom recipe categories, recipes, card slots and painters; `gradlew apiJar` builds the API alone.
- ModJS support: hide items, hide categories, add information pages, item groups, and unifier hiding, all read by reflection.
- Datapack themes, groups and hidden lists, and the recipe list, are resent when the datapacks reload, not only on join.
- Server channels are size-bounded so a hostile server cannot exhaust memory or kick players with oversized packets.
