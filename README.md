# Barely Enough Items (BEI)

A minimalist, fast item and recipe viewer for Minecraft (Forge and NeoForge). The item browser sits against the right edge of inventory screens, recipes open in a slim popup grouped by station, and the Master Blueprint tracker breaks any item into a nested crafting plan pinned to your HUD.

**Features**

- Item browser: opens with every item (and every potion, enchanted book and other variant, with a setting to turn that off), searches in the background (`@mod`, `#tag`, `$tooltip`, `-exclude`), category icons, scrollbar, and groups and hidden items from datapacks. The index builds off the render thread, so opening a screen never waits.
- Recipe popup: recipes grouped by station (those you can make first), uses lookups, centered 3x3 crafting cards, `P` to make a recipe your project, and `+` to fill the crafting grid, furnace, stonecutter or smithing table from your inventory (Shift for a full stack).
- Master Blueprint: a dependency tree for any item that checks your inventory (and optionally ender chest and any storage a mod adds), shows the next craft or what to gather, with a percent-done HUD.
- Themes: dark, light, and datapack themes; backdrop opacity; draggable windows; rarity-colored item tooltips that include the game's graphical parts (a bundle's contents, a mod's food bar).
- Cheat mode (Ctrl+click the gear): click items to hold or place them, with no commands. Needs creative mode, operator permission, or your own singleplayer world.
- Plugin API for mods that add recipe types and recipe cards, and [ModJS](https://github.com/ItMeCasey/ModJS) script support.

## Install

1. Install Forge or NeoForge for your Minecraft version.
2. Download the matching jar from the [Releases](https://github.com/ItMeCasey/BEI/releases) page and put it in your `mods` folder.
3. Open any inventory screen: the browser is on the right edge.

| Minecraft | Loader | Java | Folder |
|---|---|---|---|
| 26.3 | Forge | 25 | `versions/mc26.3/forge` |
| 26.3 | NeoForge | 25 | `versions/mc26.3/neoforge` |
| 26.2 | Forge | 25 | `versions/mc26.2/forge` |
| 26.2 | NeoForge | 25 | `versions/mc26.2/neoforge` |
| 26.1.2 | Forge | 25 | `versions/mc26.1.2/forge` |
| 26.1.2 | NeoForge | 25 | `versions/mc26.1.2/neoforge` |
| 1.21.1 | Forge | 21 | `versions/mc1.21.1/forge` |
| 1.21.1 | NeoForge | 21 | `versions/mc1.21.1/neoforge` |
| 1.20.1 | Forge | 17 | `versions/mc1.20.1/forge` |
| 1.20.1 | NeoForge | 17 | `versions/mc1.20.1/neoforge` |

The two Forge builds for 26.1.2 and 1.21.1 have been played and checked on screen. The other eight build, load, join a server (NeoForge 1.20.1: singleplayer only) and index recipes; their screens have not been checked in play yet.

BEI works on the client alone for browsing and search. On 26.1.2, 26.2 and 26.3 the server must also have BEI for recipes to show (the game only gives a client the recipes it has unlocked); datapack themes, groups and hidden items, and cheat mode, need BEI on the server in both versions. On 1.21.1 and 1.20.1 recipes come with the game.

See [docs/PORTING.md](docs/PORTING.md) for what differs between the versions, and the [wiki](https://itmecasey.com/wiki/bei/) for guides.

## Build

From a version's folder:

```
./gradlew build      # the mod jar, in build/libs
./gradlew apiJar     # only the plugin API, for mods that add BEI plugins
./gradlew runClient  # a development client
```

## Keys

| Key | Does |
|---|---|
| `R` on an item | Recipes that make it |
| `U` on an item | Recipes that use it |
| `P` on an item | Make it your Master Project (again to clear) |
| `J` | Open the full project tree |
| `H` | Show or hide the project HUD |
| `/` | Focus the search box |

Search words: `@mod`, `#tag`, `$tooltip text`, and `-word` to leave matches out.

## Themes from datapacks

Put a JSON file at `data/<namespace>/bei_themes/<name>.json` in any datapack. The server sends it to players when they join, and it shows up in the gear popup's Theme row as `<namespace>:<name>`. (Singleplayer worlds work too; `/reload` resends them, no rejoin needed.)

```json
{
  "name": "Neon",
  "base": "bei:dark",
  "colors": {
    "bg": "#0b0014",
    "card": "#1a0830",
    "card_hover": "#2a1050",
    "field": "#07000d",
    "accent": "#ff2bd6",
    "text": "#f4e8ff",
    "dim": "#9a7fb8",
    "good": "#5dffb0",
    "bad": "#ff5d7a",
    "warn": "#ffd45d",
    "border": "#5a2a8a",
    "tooltip": "#0b0014"
  }
}
```

- `name` is what the Theme row shows (the file id if left out).
- `base` is `bei:dark` (the default) or `bei:light`; any color you leave out comes from it.
- Colors are `#RRGGBB`. Backdrop transparency still comes from the player's opacity setting.

## Groups and hidden items from datapacks

Group items under one browser cell, or hide items from the browser, with JSON files in a datapack. Like themes, the server sends them when a player joins.

`data/<namespace>/bei_groups/<name>.json`:

```json
{
  "name": "Wool",
  "icon": "minecraft:white_wool",
  "items": ["#minecraft:wool", "minecraft:string"]
}
```

`data/<namespace>/bei_hidden/<name>.json`:

```json
{ "items": ["minecraft:debug_stick", "@somemod"] }
```

- Each entry is an item id (`minecraft:stick`), an item tag (`#minecraft:logs`) or a whole mod (`@modid`).
- A group needs at least two items. An item belongs to the first group (by file id) that lists it. `icon` is optional and defaults to the first item.
- In the browser a group is one highlighted cell with its item count. Searching shows the group if any item in it matches.
- Hover a group to see every item in it. Hold Shift to freeze that tooltip, then move into it to see each item's own tooltip and click one.
- Right-click a group to expand it inline (all its items follow it, highlighted); right-click again to collapse.
- BEI ships a few example groups (wool, planks, logs, saplings) in its own `data/bei/bei_groups/`.

## Plugins: custom recipe types and cards

A mod adds its own recipe categories, recipes and card looks with a class marked `@BeiPlugin`. BEI finds it by scanning mod classes, so the mod needs no call into BEI and works fine without BEI installed. Compile against `./gradlew apiJar`, which holds only the `com.itmecasey.bei.api` package.

A plugin can also register an `IRecipeTransferHandler` from `registerTransfers`: BEI shows a plus button on the recipes of the screen that handler is for (enabled, dimmed with a reason, or hidden) and calls the handler on the client when it is clicked, with the Shift and Control state. BEI sends no packets.

A plugin can also mirror BEI's search box: `registerSearch(ISearchAccess)` hands it an object with `getText`, `setText` (refreshes the results) and listeners for text changes. And `registerStorageSources` adds a place the Master Blueprint counts items from (a backpack, a storage network): an `IStorageSource` reports what it holds through an `IItemCounter`.

```java
@BeiPlugin("mymachines")            // optional: only load when this mod is installed
public class MyPlugin implements IBeiPlugin {
    private RecipeCategory grinding;

    @Override
    public void registerCategories(ICategoryRegistration registration) {
        grinding = RecipeCategory.builder(Identifier.fromNamespaceAndPath("mymachines", "grinding"))
                .title(Component.literal("Grinding"))
                .station(new ItemStack(MyItems.GRINDER.get()))   // its icon becomes the category's button
                .size(110, 36)                                   // card area in pixels
                .input(8, 9)                                     // slot positions inside the card area
                .input(28, 9)
                .output(86, 9)
                .painter(new CardPainter() {                     // optional extras: arrows, bars, text
                    public void paint(GuiGraphicsExtractor g, CustomRecipe recipe, int mx, int my) {
                        g.fill(50, 16, 78, 20, 0xFF4F9DFF);
                    }
                    public List<Component> tooltip(CustomRecipe recipe, int mx, int my) {
                        return mx >= 50 && mx < 78 ? List.of(Component.literal("Takes 4 seconds")) : null;
                    }
                })
                .build();
        registration.register(grinding);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.add(grinding, CustomRecipe.builder()
                .input(new ItemStack(Items.COBBLESTONE))
                .input(new ItemStack(Items.DIAMOND))
                .output(new ItemStack(Items.GRAVEL, 4))
                .build());
    }
}
```

- Plugins run on the client. `registerCategories` runs once, the first time the player joins a world; build item stacks in it (not in static fields) because stacks cannot be made while the game starts. `registerRecipes` runs each time the recipe list is built, so recipes may depend on the world.
- Custom recipes show in the recipe popup under their station button, in "uses" lookups, and in the Master Project tracker like any other recipe. The card is centered, with BEI's `P` button on it.
- The n-th input slot you declare shows the recipe's n-th `input(...)`, and likewise for outputs. An `input(...)` can list several stacks; BEI cycles through them.
- `painter` draws inside the card area with (0, 0) at its top left, under the item slots.
- A plugin that fails to load or throws is logged and skipped; it cannot break BEI or other plugins.
- `ExamplePlugin` in BEI's source is a working sample; it only switches on in the development client.

## ModJS scripts

With [ModJS](https://github.com/ItMeCasey/ModJS) installed, its client scripts (`RecipeViewerEvents`) control BEI. BEI reaches ModJS by reflection, so it needs no ModJS jar to build and does nothing without it.

- `hideItems(e => e.hide('minecraft:dirt', '#minecraft:wool', '@create'))` hides items from the browser, same as a datapack hidden list.
- `groupItems(e => e.group('Gems', ['minecraft:diamond', '#c:gems'], { icon: 'minecraft:diamond' }))` makes browser groups, which follow the groups' normal rules (script groups come after datapack groups; an item belongs to the first group that lists it).
- `addInformation(e => e.add('minecraft:diamond', 'Found deep underground.'))` adds an "Information" button with the text to those items' recipe popup.
- `hideCategories(e => e.hide('minecraft:campfire_cooking'))` hides recipes by recipe type id (`minecraft:crafting` covers every crafting type). It needs the server to have BEI, since BEI learns each recipe's type from it.
- ModJS's unifier: items it replaces are hidden so only the canonical item shows.
- Scripts are read when a world is joined.

## License

MIT. See [LICENSE](LICENSE).
