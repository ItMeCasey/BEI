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

## NeoForge and 1.20.1

- **NeoForge 26.1.2 and 1.21.1** share the code of their Forge builds. What changes is the loader layer: `net.neoforged` packages, a `(IEventBus, ModContainer)` mod constructor, `ModConfigSpec`, mod-bus event registration, and networking through payload records with stream codecs (one optional registrar, `net/Channels.java`) in place of `SimpleChannel`. NeoForge handlers cancel events with `setCanceled`, and the project HUD registers with `RegisterGuiLayersEvent`.
- **1.20.1 (Forge and NeoForge)** is derived from the 1.21.1 tree. There are no data components and no `RecipeHolder` (recipes are `Recipe` objects with `getId()`), `ResourceLocation` is built with its constructor, tooltips come from `getTooltipLines(Player, TooltipFlag)`, and the HUD registers with `RegisterGuiOverlaysEvent`. Networking is Forge's classic `SimpleChannel`, kept optional.
- NeoForge 1.20.1 still uses the `net.minecraftforge` package names, so its tree differs from Forge 1.20.1 only in build files and a few calls.
- The 1.20.1 builds need Java 17 for Gradle; a newer default JDK fails.

## 26.2 and 26.3

Both start as copies of the 26.1.2 trees (`versions/mc26.2` and `versions/mc26.3`, Forge and NeoForge each). The dev-only screenshot tour (`ShotTour`) is not carried over. What changed:

| Area | 26.1.2 | 26.2 | 26.3 |
|---|---|---|---|
| Current screen | `minecraft.screen`, `setScreen` | `minecraft.gui.screen()`, `setScreenAndShow` | same as 26.2 |
| Action-bar message | `gui.setOverlayMessage` | `gui.hud.setOverlayMessage` | same as 26.2 |
| Hide GUI flag | `options.hideGui` | `gui.hud.isHidden()` | same as 26.2 |
| Dye items | `Items.RED_DYE` | `Items.DYE.pick(DyeColor.RED)` | same as 26.2 |
| Translation check | `I18n.exists` | `Language.getInstance().has` | same as 26.2 |
| Rarity color | `ChatFormatting.getColor()` | `Style.EMPTY.applyFormat(fmt).getColor()` | same as 26.2 |
| Key codes | GLFW | GLFW | SDL: `InputConstants.KEY_*` and `MOD_*`, key mappings use `InputConstants.Type.KEYBOARD` |
| Mouse button state | GLFW call | GLFW call | `mouseHandler.isLeftPressed()` |
| Is an item fuel | `level.fuelValues()` | same as 26.1.2 | the `COOKING_FUEL` item component |
| Container screen bounds | `getGuiLeft()`, `getXSize()`, `getSlotUnderMouse()` | same | Forge keeps them; NeoForge 26.3 does not, so `Ui` reads the protected fields by reflection |

## JEI bridge coverage

BEI runs the `@JeiPlugin` classes of installed mods against its own small JEI runtime (`compat/JeiBridge` and its helpers). The plugin phases run when the game's recipes arrive, in JEI's order, each phase for every plugin before the next. Every JEI call that has no BEI equivalent is answered with an empty value and logged once as `JEI bridge: <Interface>.<method> is not supported and was ignored`; nothing is dropped silently. The tables list every method of the interfaces plugins use and what BEI does with it.

How the pieces map: a JEI recipe category becomes a BEI station and card (`RecipeCategory`), a JEI recipe becomes a `CustomRecipe` whose `data()` is the JEI recipe object, the category's `draw`, `getBackground`, `createRecipeExtras` and tooltips paint the card, and a plugin's transfer handlers answer BEI's plus button. A JEI ingredient that is not an item is shown as a stack (a fluid as its named bucket, another registered type as a named placeholder) because BEI's recipe model is item based.

### IModPlugin

| Method | BEI |
|---|---|
| `getPluginUid` | read, used in logs |
| `registerItemSubtypes` | called; calls are logged and ignored (BEI lists variants from the creative tabs instead) |
| `registerFluidSubtypes`, `registerExtraIngredients`, `registerIngredientAliases`, `registerModInfo`, `registerAdvancedSearch`, `configureJei`, `onConfigManagerAvailable` | called with a logging stub; ignored (see the reasons below) |
| `registerIngredients` | supported: the ingredient helpers are kept to name non-item ingredients |
| `registerCategories` | supported |
| `registerVanillaCategoryExtensions` | called; logged and ignored: BEI has no JEI vanilla categories to extend, its own vanilla cards are used |
| `registerRecipes` | supported |
| `registerRecipeCatalysts` | supported (the first catalyst is the station icon) |
| `registerRecipeTransferHandlers` | supported (below) |
| `registerGuiHandlers`, `registerAdvanced`, `registerRuntime` | called with logging stubs; ignored (below) |
| `onRuntimeAvailable` | supported with the runtime below |
| `onRuntimeUnavailable` | not called: BEI's runtime lasts as long as the world connection and is rebuilt when recipes arrive |

### Registrations

| Interface | Supported | Logged and ignored, with the reason |
|---|---|---|
| `IRecipeCategoryRegistration` | `addRecipeCategories`, `getJeiHelpers` | none |
| `IRecipeRegistration` | `addRecipes`, `addIngredientInfo` and `addItemStackInfo` (an information page per item), `getJeiHelpers` | `getIngredientManager`, `getIngredientVisibility`, `getVanillaRecipeFactory`: answered with empty stubs; vanilla recipe factories build JEI vanilla recipe objects BEI has no cards for |
| `IRecipeCatalystRegistration` | `addRecipeCatalyst`, `addRecipeCatalysts`, `addCraftingStation` | `getIngredientManager` |
| `IModIngredientRegistration` | `register` (helper kept) | `getColorHelper`, `getSubtypeManager`: BEI's `^color` search uses its own color words; the renderer is not used because BEI draws items |
| `ISubtypeRegistration` | none | `registerSubtypeInterpreter`: BEI shows each creative-tab variant as its own entry, so it needs no interpreter |
| `IVanillaCategoryExtensionRegistration` | none | all methods: BEI draws vanilla recipes itself |
| `IRecipeTransferRegistration` | `addRecipeTransferHandler` (typed and by slot range), `addUniversalRecipeTransferHandler`, `getTransferHelper`, `getJeiHelpers` | `addRecipeTransferListener`: BEI has no transfer listeners |
| `IGuiHandlerRegistration` | none | `addGuiContainerHandler`, `addGenericGuiContainerHandler`, `addGuiScreenHandler`, `addGlobalGuiHandler`: exclusion zones; BEI's panel is a fixed strip at the right edge and moves nothing. `addRecipeClickArea`: no click areas on machine screens yet. `addGhostIngredientHandler`: BEI has no drag-to-slot |
| `IAdvancedRegistration` | none | `addRecipeManagerPlugin`, `addTypedRecipeManagerPlugin`: BEI's index is built from the recipe list, not by plugin queries. `addRecipeCategoryDecorator`, `addRecipeButtonFactory`: no card decorators or extra buttons. `getJeiFeatures`, `getRecipeManagerPluginHelper`: stubs |
| `IRuntimeRegistration` | none | all setters (`setRecipesGui`, `setIngredientListOverlay`, `setBookmarkOverlay`...): they replace JEI's own screens, which BEI does not have; BEI has its own bookmarks and recipe popup |

### The runtime (`IJeiRuntime`)

| Interface | Supported | Logged and ignored |
|---|---|---|
| `IJeiRuntime` | `getRecipeManager`, `getIngredientManager`, `getJeiHelpers` | `getRecipesGui`, `getIngredientFilter`, `getIngredientListOverlay`, `getBookmarkOverlay`, `getBookmarkManager`, `getKeyMappings`, `getScreenHelper`, `getRecipeTransferManager`, `getEditModeConfig`, `getConfigManager`, `getIngredientVisibility`: BEI's browser, bookmarks, edit mode and transfer are its own, so a plugin that drives JEI's screens has nothing to drive |
| `IRecipeManager` | `hideRecipeCategory`, `unhideRecipeCategory`, `createRecipeCategoryLookup` (bridged categories plus a stand-in per game recipe type), `createRecipeLookup`, `createRecipeCatalystLookup` | `addRecipes`, `hideRecipes`, `unhideRecipes` (runtime recipe changes are not applied to the index), `createRecipeLayoutDrawable*`, `createRecipeSlotDrawable`, `getRecipeCategory`, `getRecipeIngredients`, `getRecipeType`, `getRecipeButtonControllerFactories`: JEI's own recipe screen |
| `IIngredientManager` | `removeIngredientsAtRuntime` and `addIngredientsAtRuntime` (item ingredients are hidden or shown by item id), `getAllIngredients` and `getAllItemStacks` for items, `getIngredientType` | the rest (typed ingredient creation, codecs, aliases, listeners, `refreshIngredientsAtRuntime`): BEI keeps no typed ingredient registry beyond the item list |

### IGuiHelper and the drawables

Supported and drawn for real: `createDrawable`, `drawableBuilder` (`setTextureSize`, `addPadding`, `build`, `buildAnimated`), `createAnimatedDrawable`, `createTickTimer`, `createBlankDrawable`, `createDrawableIngredient`, `createDrawableItemStack`, `getSlotDrawable`, `getRecipeArrow`, `getRecipeArrowFilled`, `getRecipeArrowEmpty`, `createAnimatedRecipeArrow`. The slot and arrow are drawn by BEI, not from JEI's textures (BEI does not ship JEI's assets). Logged and ignored: `createDrawableSprite`, `createScalableDrawableSprite`, `createWidgetFromDrawable`, `createScrollBoxWidget`, `createScrollGridFactory`, `createCraftingGridHelper`, `createRecipeSlotDrawable`, `getOutputSlot`, `getRecipeFlame*`, `getRecipePlusSign`, `createAnimatedRecipeFlame`, `createDrawableItemLike`, `getScrollBoxScrollbarExtraWidth`: they draw JEI's own sprites or widgets.

### IRecipeLayoutBuilder, slots and extras

`addSlot`, `addInputSlot`, `addOutputSlot`, `addInvisibleIngredients`, `setShapeless`, and on a slot `setPosition`, `setSlotName`, `addIngredient(s)`, `addItemStack(s)`, `addFluidStack`, `addTypedIngredient`. Catalyst and render-only slots are drawn on the card as items (they are not consumed or made). Slot backgrounds and overlays, custom renderers, tooltips callbacks, focus links and the transfer button position are logged and ignored (the card draws BEI's own slot box and item tooltips).

`IRecipeExtrasBuilder` (JEI 19 and later): `addDrawable`, `addWidget`, `addRecipeArrow`, `addAnimatedRecipeArrow`, `addText`. Logged and ignored: buttons, cycle buttons, scroll boxes and grids, slotted widgets, input handlers, tooltip areas, flames and plus signs (they need JEI's widget system and mouse events, which a BEI card does not forward).

### Recipe transfer

Typed handlers (`IRecipeTransferHandler`), universal handlers and slot-range handlers (`addRecipeTransferHandler(menuClass, menuType, recipeType, ...)`) become one BEI handler per menu class. The handler is asked with `doTransfer` false to decide the plus button (enabled, dimmed with the handler's message, or hidden) and with `doTransfer` true on a click. Slot-range handlers move items with ordinary slot clicks (BEI sends no packets). Vanilla crafting recipes are handed to handlers as the game's recipe object on 1.20.1 and 1.21.1; on 26.x the client keeps no recipe objects, so a handler is only asked about recipes a JEI plugin itself registered.
