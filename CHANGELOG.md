# Changelog

## 2026.11.16 (all ten builds)

- The recipe popup and the settings popup no longer darken the whole screen behind them. The game's own screen background is drawn exactly as vanilla draws it; BEI adds no dim or tint of its own.

## 2026.11.16 (Forge and NeoForge 26.3 only)

- Fixed the item browser's mouse buttons on Minecraft 26.3: a left click opened an item's Uses and a right click did nothing. 26.3 renumbered the mouse buttons (left 1, middle 2, right 3) and BEI still compared against 0 and 1. Left click now opens Recipes and right click opens Uses, as in JEI. The same fix covers edit mode, cheat mode, the search box's right-click clear, group expansion, drag-and-drop and clicks inside plugin recipe cards. The plugin API still numbers buttons like JEI (0 left, 1 right).
- New `MouseButtonsTest` covers left = recipes and right = uses.
- The other eight builds (26.1.2, 26.2, 1.21.1, 1.20.1) are unchanged and stay on 2026.11.15; they still number the buttons 0 and 1.
- Checked in a real Forge 26.3 pack client: left click opens Recipes and right click opens Uses.

## 2026.11.15 (all ten builds)

- Item browser: when the index is built while joining a world (before the world exists), potions and enchanted books are now added by a rebuild on the first tick that has a world. Before, they could be missing until the setting was toggled.
- JEI bridge: logs when a plugin registers a recipe manager plugin, a category decorator or a recipe button factory, and how many recipes manager plugins add.
- Development screen tour (`BEI_SCREENS`) on Forge 1.21.1 and both 1.20.1 builds; the dev check's category extension no longer depends on calling JEI interfaces directly.
- Checked in clients: screen tour on Forge 1.21.1 (dev client) and on the Forge and NeoForge 1.20.1 jars in a real Forge 47.4.28 install; AE2 on NeoForge 26.3 registering a recipe manager plugin that BEI accepts; a category extension applied on Forge 26.1.2 with the built jar (BarelyStorage's dev client).

## 2026.11.14 (all ten builds, 26.x for the packet fix)

Bugs found by running real AE2 and BarelyStorage against the bridge, plus a screen tour on every 26.x build.

- JEI bridge: `IRecipeTransferHandlerHelper.getGuiSlotIndexToIngredientMap` now answers. It used to return an empty map, so AE2's crafting terminal handler said "No items" even with the ingredients in the inventory. With it, the + button on a real AE2 crafting terminal fills the crafting grid (checked on NeoForge 1.21.1).
- 26.x: a recipe whose display a mod never registered a codec for (AE2's charger, inscriber and transform recipes on 26.3) is skipped in the recipe packet, with one log line per recipe type. Before, the packet failed to encode and the player was disconnected on joining.
- 26.x: information pages from plugins (`addItemStackInfo`) wrapped their text on the recipe thread, which asserts on 26.x and switched the whole JEI bridge off when AE2 was installed. The wrapping now runs on the render thread (all builds).
- JEI bridge: a transfer handler written as a non-public class (BarelyStorage's) is called through its public interface instead of being skipped with an access error.
- Development tour for the 26.x builds (`BEI_SCREENS`, off by default): browser, search, variants, recipe popup, uses, settings, themes, cheat mode and planner, one screenshot each.
- Checked in clients: the screen tour on Forge and NeoForge 26.1.2, 26.2 and 26.3; AE2 loading as a JEI plugin on NeoForge 26.1.2 and 26.3; BarelyStorage's `barelystorage:network` source counting a real terminal's items (cobblestone 5000, iron ingots 1280) on NeoForge 26.2.

## 2026.11.13 (all ten builds)

- JEI bridge, 26.x: recipe slots made by plugins were empty because the 26.x JEI API adds ingredients through `add(...)` (items, item-likes, fluids, game ingredients, item templates, typed values) instead of `addItemStack` and friends. Slots now take all of them, on every build.
- JEI bridge: slot builders also take `addItemStack`, `addItemLike`, `addTypedIngredient`, `addItemStacks` and `addItemLikes` directly when a JEI API version has them as plain methods.
- Vanilla category extensions look the recipe up in a single-player world's integrated server on 26.x and also by recipe id on older versions.
- Development checks (environment variable `BEI_JEI_SELFTEST`, off by default): a stand-in transfer handler and category extension run through the real adapters after joining a world and log `BEI-CHECK` lines.
- Checked in clients: the recipe popup's back and forward buttons, an information page from `addItemStackInfo`, and a + click reaching a transfer handler on NeoForge 1.21.1; a transfer handler receiving a recipe object on Forge 26.1.2 and NeoForge 26.3; a category extension applied on NeoForge 26.3.

## 2026.11.12 (all ten builds)

Zero unsupported JEI calls that BEI can answer. Plugins now get working answers where they used to get a log line.

- JEI bridge: input reaches a plugin's card. Buttons, cycle buttons, scroll boxes and grids, slotted widgets, input handlers, GUI event listeners and tooltip areas take clicks, drags, releases and the mouse wheel inside the card, and show their tooltips. `createWidgetFromDrawable`, `createScrollBoxWidget`, `createScrollGridFactory` and `createRecipeSlotDrawable` on the GUI helper work.
- JEI bridge: slot overlays, custom slot renderers, slot tooltip callbacks (extra lines for the item in a slot), focus links and the transfer button position are used on the card.
- JEI bridge: recipe manager. Plugins can build recipe layout drawables, slot drawables and ingredient suppliers; recipe manager plugins add recipes and answer focused lookups; recipes added or hidden after the index is built reach it at once. Category decorators draw over a card and change its tooltip; recipe button factories add a button to the card.
- JEI bridge: ingredient registry. Custom ingredient types keep their helper, renderer, codec and ingredients; the ingredient manager answers type lookups, typed and clickable ingredients, aliases and listeners, and listeners hear runtime additions and removals.
- JEI bridge: search. Ingredient aliases (also as translation keys) and mod aliases widen the search; extra ingredients are added to the item list.
- JEI bridge: vanilla category extensions (crafting, cooking, smithing, brewing) lay out game recipes on 1.20.1 and 1.21.1. On 26.x the client keeps no recipe objects, so they have no effect there.
- JEI bridge: transfer listeners, the transfer manager, key mappings and the config manager answer for plugins.
- 1.20.1: GUI sprite drawables look for the sprite as a plain texture (`textures/gui/sprites/NAME.png`) and stretch it.
- `bei-api`: `CardPainter` gains `paintOverlay`, `mouseClicked`, `mouseReleased`, `mouseDragged`, `mouseScrolled`, `slotTooltip` and `transferButton` (all with defaults, so existing painters keep working). Publish new API jars.
- Unit tests cover the new parts (card widgets, aliases, the ingredient registry, manager plugins, decorators, listeners, extensions, late recipes).
- JEI bridge, closing the gaps: a plugin's replacement search storage (`registerAdvancedSearch`) is filled with BEI's items and searched; the recipe layout drawable draws slots and shows slot and card tooltips; vanilla category extensions also work on 26.x in single-player worlds (the integrated server has the recipe objects; on a dedicated server they cannot); `getIngredientTypeWithSubtypesFromBase` answers for items; key mappings use BEI's real keys.
- Checked in a client on NeoForge 1.21.1 with AE2 and Farmer's Delight: a synthetic category drawn through the recipe manager's layout drawable took a button press (the callback ran) and a mouse wheel on its scroll box, showed the slot item with the plugin's tooltip callback line, and a real Farmer's Delight cooking card was drawn through the same drawable with its items; AE2's interface screen still moves the panel and reports 9 ghost targets. The other nine builds were built and passed their tests but were not started in a client for this version.

## 2026.11.11 (all ten builds)

- JEI bridge: exclusion zones. A JEI plugin's extra areas next to a machine screen (the side panels of Applied Energistics, for example) now keep the browser panel out of the way: it starts to the right of them.
- JEI bridge: ghost-ingredient drags. Drag an item from the browser onto a drop target a plugin reports (a filter or config slot) and release to drop it; the targets light up while you drag. A short click still opens recipes. It only takes over the click on screens where a plugin has targets.
- JEI bridge: the runtime's own screens answer from BEI. A plugin reading or setting the search text, asking what is under the mouse, bookmarking an item, opening the recipes of an item, or checking whether an item is hidden gets BEI's search box, bookmarks, recipe popup, edit mode and hidden items.
- JEI bridge, 26.1.2, 26.2 and 26.3: vanilla crafting recipes now reach a plugin's transfer handler as a recipe object BEI builds from the recipe the server sent.
- JEI bridge: recipe click areas. Clicking a machine's progress arrow (a plugin's `addRecipeClickArea` or a handler's clickable areas) opens BEI's recipe popup on that category, as does a plugin calling the recipes screen's `showTypes`.
- JEI bridge: more of the API. Runtime recipe additions and hides while the runtime starts, `getRecipeType` and `getRecipeCategory`, slot backgrounds, GUI sprites, the plus sign, flames, the output slot, item-like drawables and the crafting grid helper. Calls BEI needs nothing from (subtype interpreters, replacing JEI's screens) are accepted without a log line.
- Checked in a client on NeoForge 1.21.1 with AE2: on the interface screen the panel moves from the screen edge at 301 to 329 for AE2's extra area and 9 ghost drop targets are reported; on the inscriber screen a click on the progress arrow opens "Recipes at Inscriber". The other builds start and load cleanly in a client, and both 1.20.1 jars load in a real Forge 47.4.28 install. Unit tests cover the exclusion zone, the ghost targets and the runtime adapters. The `bei-api` jar is unchanged.

## 2026.11.10 (all ten builds)

JEI parity. BEI now covers what players and mod authors use JEI for.

- **Bookmarks.** Press **A** over an item (in the browser, the recipe popup or a slot) to bookmark it. A strip under the grid shows the bookmarks; click one like a grid item, press **A** over it to remove it. They are saved per profile in `config/bei_client.json`.
- **Edit mode.** A new setting. While it is on, clicking an item in the browser hides it (hidden items show dimmed so a click brings them back). What you hide is saved and works next to the datapack hidden lists.
- **Search prefixes.** `%tab` finds items by creative tab name, `^color` by color (from names and block map colors), `&id` by resource id. The existing `@`, `#`, `$` and `-` are unchanged.
- **Sort order.** A new setting: creative tab (the new default), name, or mod then name.
- **Forward in the recipe popup.** A forward button (and the Right arrow) next to back; Left arrow also goes back.
- **JEI bridge: category drawing.** A bridged JEI category now draws its own card: textures, animated progress bars, backgrounds, recipe extras and tooltips. Catalyst and display-only slots are drawn as items.
- **JEI bridge: fluids and other ingredients.** Fluid slots show the fluid's bucket named with the fluid and amount; other registered ingredient types show as named placeholders.
- **JEI bridge: recipe transfer.** A plugin's transfer handlers (typed, universal and by slot range) answer the plus button on modded machine screens.
- **JEI bridge: information pages.** `addIngredientInfo` becomes an Information station.
- **JEI bridge: runs when recipes arrive.** Plugins that read the recipe list while they register (Applied Energistics) now see it; before, their recipes were missing.
- **JEI bridge: nothing is skipped silently.** Every JEI call with no BEI equivalent is logged once (`JEI bridge: <Interface>.<method> is not supported`) and listed with its reason in `docs/PORTING.md`.
- Checked in a client on NeoForge 1.21.1 with AE2 (the inscriber card with its textures and progress bar, the in-world transformation cards) and with Farmer's Delight. Unit tests cover the new pure code, the drawables, the slot views and the transfer adapter. The other builds (NeoForge 26.1.2, 26.2, 26.3, Forge 1.21.1, 26.1.2, 26.2, 26.3) were started in a client, joined a world and loaded without errors with the new code; the Forge 1.20.1 and NeoForge 1.20.1 jars loaded in a real Forge 47.4.28 install. The `bei-api` jar is unchanged.

## 2026.11.9 (all ten builds)

- JEI bridge: runtime. Plugins now get `onRuntimeAvailable` with a small `IJeiRuntime`, so a mod's own hide calls work: `removeIngredientsAtRuntime` (item stacks) hides the items in the browser, and `hideRecipeCategory` hides a recipe category, including game recipe types such as `minecraft:campfire_cooking`. This is what ModJS's `RecipeViewerEvents.hideItems` and `hideCategories` use. Items are hidden by item id, whatever their data.
- NeoForge 1.20.1: the jar is now built with ForgeGradle against Forge 47.1 and reobfuscated to the names the game uses. The old NeoGradle build kept Mojang names, which work in a development run but would fail with `NoSuchMethodError` in a real NeoForge 1.20.1 game. The mod class now has the plain Forge 47 constructor. The jar was loaded in a real (launcher-style) Forge 47.4.28 install, joined a world and indexed items and recipes; it was not run in a real NeoForge 47.1.106 install (its installer could not be run here).
- Forge 1.20.1 was loaded the same way in a real Forge 47.4.28 install: loaded, joined a world, bridge started.
- Checked with the ModJS info tour on NeoForge 1.21.1 (`hid 17 item(s)`, `hid minecraft:campfire_cooking`). New unit tests cover the hidden lists and the runtime. The other nine builds compile and pass the hidden-list tests but were not run in a client for this.

## 2026.11.8 (all ten builds)

- JEI plugin support. Mods that ship a JEI plugin (Applied Energistics 2 and others) now show their item recipes in BEI without JEI installed: BEI embeds JEI's public API, runs the plugin's recipe registration and shows each JEI recipe category as a station. Fluid and custom ingredients, JEI recipe transfer and category drawing are not imported yet; a plugin that needs more of JEI is skipped and logged. BEI now provides the `jei` mod id, so **the real JEI mod cannot be installed next to BEI**.
- Checked in a client on NeoForge 1.21.1 with AE2 and AE2 JEI Integration (7 categories, 10 recipes bridged). Every other build except Forge 1.20.1 (its dev run task does not exist) was started in a client, joined a world and loaded the bridge without errors; none of them had a JEI-plugin mod installed, so they show 0 plugins. New unit tests cover the plain-search match on a variant's tooltip text.

## 2026.11.7 (all ten builds)

- Item variants. Potions, splash and lingering potions, tipped arrows, enchanted books and the other items the creative tabs list in several forms now get one browser entry per form. A plain search finds a variant by name. A new **Show variants** setting (on by default) turns it off.
- Plugin API: storage sources. `IBeiPlugin.registerStorageSources(IStorageRegistration)` lets a mod add a place the Master Blueprint counts items from (a backpack, a storage network) with an `IStorageSource`. The inventory and the ender chest are now sources of the same kind. A source that throws is skipped and counts nothing.
- Checked in a client on NeoForge 1.21.1: potion and enchanted-book entries with their tooltips, and the setting turning them off and on. New and not yet checked in a client: a plain search word also matches the tooltip text of a variant (so "sharpness" should find the Sharpness book). The other nine builds compile with the same code. New unit tests cover the storage totals, the variant list and the Master Blueprint tree and plan.

## 2026.11.6 (all ten builds)

- Plugin API: recipe transfer. A mod can register an `IRecipeTransferHandler` for its own screen (from `IBeiPlugin.registerTransfers`) so the plus button in BEI's recipe popup fills that screen, the way other recipe viewers' transfer handlers do. The handler decides per recipe whether the button is enabled, dimmed with a reason, or hidden, and gets the recipe's ingredients and outputs and the Shift and Control state when it is clicked. BEI sends no packets; the mod sends its own. BEI's own fill for the crafting table, furnaces, stonecutter and smithing table is unchanged and is used when no handler answers.
- Checked in a client on NeoForge 1.21.1 with a dummy handler: the enabled button, its tooltip, the dimmed button with its reason, and a click that ran the handler. Unit tests cover which handler wins and the button states.
- Plugin API: search access. `IBeiPlugin.registerSearch(ISearchAccess)` gives a mod BEI's search box: read the text, set it (the results refresh as if the player typed it) and listen for changes, so a terminal's search field can mirror BEI's both ways. Setting the same text again does nothing, so two mirroring mods do not loop.

## 2026.11.5 (never uploaded; these changes ship in 2026.11.6)

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
