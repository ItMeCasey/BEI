# Changelog

## 2026.11.6 (all ten builds)

- Plugin API: search access. `IBeiPlugin.registerSearch(ISearchAccess)` gives a mod BEI's search box: read the text, set it (the results refresh as if the player typed it) and listen for changes, so a terminal's search field can mirror BEI's both ways. Setting the same text again does nothing, so two mirroring mods do not loop.

## 2026.11.5 (all ten builds)

- Plugin API: recipe transfer. A mod can register an `IRecipeTransferHandler` for its own screen (from `IBeiPlugin.registerTransfers`) so the plus button in BEI's recipe popup fills that screen, the way other recipe viewers' transfer handlers do. The handler decides per recipe whether the button is enabled, dimmed with a reason, or hidden, and gets the recipe's ingredients and outputs and the Shift and Control state when it is clicked. BEI sends no packets; the mod sends its own. BEI's own fill for the crafting table, furnaces, stonecutter and smithing table is unchanged and is used when no handler answers.
- Checked in a client on NeoForge 1.21.1 with a dummy handler: the enabled button, its tooltip, the dimmed button with its reason, and a click that ran the handler. Unit tests cover which handler wins and the button states.

## 2026.11.4 (all ten builds)

- Item tooltips no longer repeat text that a mod's graphic replaces. When a mod removes lines while the game gathers a tooltip (OrangePeel removes its "Hunger +N Saturation +N" line because its shank icons show the same thing), BEI now leaves those lines out and draws the graphic once. Lines the mod keeps still show.
- Checked in the real item browser, recipe popup and project planner on all ten builds (hovering entries with the game's own mouse handling, OrangePeel 2026.11.1 loaded on every build that has a jar for it): the duplicate "Hunger / Saturation" line is gone and the shank rows draw once, not clipped. Minecraft 1.20.1 has no OrangePeel build, so there it was checked with vanilla tooltips only. A unit test covers the list handling.

## 2026.11.3 (never uploaded; these changes ship in 2026.11.4)

- Item tooltips now show the graphical parts the game draws in its own tooltips, not only text: a bundle's contents preview, and any component a mod gives an item (such as a food bar) through its tooltip image or the loader's tooltip component events. This works in the item browser, recipe views and the project planner. The image sits under the item's name, above the other lines. Text-only tooltips look exactly as before, and lines other mods add to the tooltip still show.
- Checked with a bundle in a client on every build except Forge 1.20.1, whose Gradle run tasks do not start here (NeoForge 1.20.1 has the same code and was checked). Components that replace the text lines of a tooltip are not applied; only non-text components are drawn.

## 2026.11.2 (Forge 26.2, NeoForge 26.2, Forge 26.3, NeoForge 26.3)

New builds for Minecraft 26.2 and 26.3. Each builds, loads, joins a dev server and indexes its recipes (about 1570 on 26.2 and 1750 on 26.3 in a vanilla world); none has been checked on screen yet. The older builds are unchanged and keep 2026.11.1.

- 26.3 reads input through SDL instead of GLFW, tells fuel by the `COOKING_FUEL` item component, and the NeoForge build reads the container screen's bounds by reflection. See `docs/PORTING.md`.
- The dev-only screenshot tour is not part of these builds.

## 2026.11.1 (NeoForge 26.1.2, NeoForge 1.21.1, Forge 1.20.1, NeoForge 1.20.1)

Ports of the release below. Each builds, loads, joins a server and indexes recipes (NeoForge 1.20.1 was only tried in singleplayer, because its dev server crashes in the development environment); none has been checked on screen yet.

- The NeoForge builds use NeoForge's payload networking, mod-bus events and config spec; the 1.20.1 builds use Forge's classic channel and read recipes from the client's recipe list.
- On 1.20.1 recipes come with the game, as on 1.21.1.

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
