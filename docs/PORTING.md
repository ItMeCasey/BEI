# Porting notes: 26.1.2 to 1.21.1

Both builds live under `versions/` and share one design; the 1.21.1 tree started as a copy of the 26.1.2 sources. What differs:

| Area | 26.1.2 | 1.21.1 |
|---|---|---|
| Drawing | `GuiGraphicsExtractor` | `GuiGraphics`, wrapped by `client/Draw` so the screens keep the same calls (`text`, `item`, `fill`, `outline`, `pose`, `nextStratum`) |
| Input events | `MouseButtonEvent`, `KeyEvent`, `CharacterEvent` from the game | Raw values from Forge's screen events, wrapped into small records in `client/input` |
| Cancelling an event | the listener returns `true` | `event.setCanceled(true)` |
| Identifiers | `Identifier` | `ResourceLocation` |
| Recipes | The client keeps only unlocked recipes, so the server sends every recipe's display (`RecipeChannel`) | The client already has every recipe; `RecipeIndex` reads each recipe's ingredients and result when `RecipesUpdatedEvent` fires. No recipe channel. Modded recipe types are covered as long as they implement `getIngredients()` and `getResultItem()` |
| Recipe type ids | Sent by the server with each recipe | Read straight from the recipe, so `hideCategories` works on any server |
| Crafting menus | `AbstractCraftingMenu` | `CraftingMenu` / `InventoryMenu` as `RecipeBookMenu`; grid slots follow the result slot |
| Slot clicks | `handleContainerInput` with `ContainerInput` | `handleInventoryMouseClick` with `ClickType` |
| Smithing recipes | standard display | ingredients read from the recipe's own fields by reflection |
| Plugin API | `CardPainter` draws with `GuiGraphicsExtractor` | `CardPainter` draws with `GuiGraphics` |
| Permissions | `permissions().hasPermission(...)` | `hasPermissions(2)` |

Everything else (index, search, groups, themes, blueprint, project, cheat mode, ModJS compat) is the same code.
