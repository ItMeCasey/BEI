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
| `registerFluidSubtypes`, `configureJei` | called with a logging stub; ignored (see the reasons below) |
| `registerExtraIngredients` | supported: extra item stacks are added to the item list |
| `registerIngredientAliases` | supported: aliases are matched by the search (an alias is also tried as a translation key) |
| `registerModInfo` | supported: mod aliases widen `@mod` searches |
| `registerAdvancedSearch` | called; a replacement search storage is kept but not used, BEI's search is its own |
| `onConfigManagerAvailable` | called with a config manager that lists no JEI config files (BEI's settings are its own) |
| `registerIngredients` | supported: custom ingredient types, their helpers, renderers, codecs and ingredients are kept (below) |
| `registerCategories` | supported |
| `registerVanillaCategoryExtensions` | supported for crafting, cooking, smithing and brewing extensions on 1.20.1 and 1.21.1 (the game recipe is laid out by the plugin's extension). On 26.x the client keeps no recipe objects, so the extension is accepted and has no effect |
| `registerRecipes` | supported |
| `registerRecipeCatalysts` | supported (the first catalyst is the station icon) |
| `registerRecipeTransferHandlers` | supported (below) |
| `registerGuiHandlers`, `registerAdvanced`, `registerRuntime` | supported (below) |
| `onRuntimeAvailable` | supported with the runtime below |
| `onRuntimeUnavailable` | not called: BEI's runtime lasts as long as the world connection and is rebuilt when recipes arrive |

### Registrations

| Interface | Supported | Logged and ignored, with the reason |
|---|---|---|
| `IRecipeCategoryRegistration` | `addRecipeCategories`, `getJeiHelpers` | none |
| `IRecipeRegistration` | `addRecipes`, `addIngredientInfo` and `addItemStackInfo` (an information page per item), `getJeiHelpers`, `getIngredientManager` and `getIngredientVisibility` (accepted; the runtime's own objects answer) | `getVanillaRecipeFactory`: answered with an empty stub; it builds JEI vanilla recipe objects BEI has no cards for |
| `IRecipeCatalystRegistration` | `addRecipeCatalyst`, `addRecipeCatalysts`, `addCraftingStation` | `getIngredientManager` |
| `IModIngredientRegistration` | `register` (4 and 5 argument forms: the type, ingredients, helper, renderer and codec are kept for the ingredient manager) | `getColorHelper`, `getSubtypeManager`: BEI's `^color` search uses its own color words |
| `IIngredientAliasRegistration` | every `addAlias` and `addAliases` form | none |
| `IModInfoRegistration` | `addModAliases` | none |
| `IExtraIngredientRegistration` | `addExtraItemStacks`, `addExtraIngredients` (item stacks) | none |
| `IAdvancedSearchRegistration` | `replaceSearchStorage` (kept, unused), `getJeiHelpers` | `getDefaultSearchStorageBuilderFactory`: accepted; BEI's search is its own |
| `ISubtypeRegistration` | `registerSubtypeInterpreter` is accepted and ignored without a log line: BEI shows each creative-tab variant as its own entry | none |
| `IVanillaCategoryExtensionRegistration` | `getCraftingCategory`, `getSmeltingCategory`, `getBlastingCategory`, `getSmokingCategory`, `getCampfireCookingCategory`, `getSmithingCategory`, `getBrewingCategory` with `addExtension`, and `addCookingCategoryExtension` (applied on 1.20.1 and 1.21.1) | none |
| `IRecipeTransferRegistration` | `addRecipeTransferHandler` (typed and by slot range), `addUniversalRecipeTransferHandler`, `addRecipeTransferListener`, `getTransferHelper`, `getJeiHelpers` | none |
| `IGuiHandlerRegistration` | `addGuiContainerHandler`, `addGenericGuiContainerHandler`, `addGlobalGuiHandler` (extra areas to keep clear: BEI's panel starts to the right of any area that sticks out past the screen's right edge), `addGhostIngredientHandler` (drag an item from the browser onto a drop target a plugin reports), `addRecipeClickArea` and the handlers' clickable areas (clicking a machine's progress arrow opens that category's recipes in BEI's popup), `getJeiHelpers` | `addGuiScreenHandler` (accepted and ignored: BEI's panel only attaches to container screens, whose bounds it reads itself) |
| `IAdvancedRegistration` | `addRecipeManagerPlugin` and `addTypedRecipeManagerPlugin` (their recipes join the category when the plugins have registered, and answer focused lookups), `addRecipeCategoryDecorator` (draws over the card and changes its tooltip), `addRecipeButtonFactory` (a small button at the card's right edge), `getRecipeManagerPluginHelper`, `getJeiHelpers` | `getJeiFeatures`: accepted |
| `IRuntimeRegistration` | `getRecipeManager`, `getIngredientManager`, `getScreenHelper`, `getRecipeTransferManager`, `getEditModeConfig`, `getIngredientVisibility`, `getJeiHelpers`; `setRecipesGui`, `setIngredientListOverlay`, `setBookmarkOverlay`, `setBookmarkManager`, `setIngredientFilter` are accepted and ignored: BEI's own screens answer the runtime calls instead | `getSearchStorageFactory`, `getSearchStorageBuilderFactory`: accepted; BEI's search is its own |

### The runtime (`IJeiRuntime`)

| Interface | Supported | Logged and ignored |
|---|---|---|
| `IJeiRuntime` | `getRecipeManager`, `getIngredientManager`, `getJeiHelpers`, `getRecipeTransferManager` (handlers by menu and category, the listeners), `getKeyMappings` (show recipe R, show uses U, bookmark A, as BEI's keys), `getConfigManager` (no JEI config files), and answers from BEI's own screens: `getIngredientFilter` (the search box: `getFilterText`, `setFilterText`, `getFilteredItemStacks`), `getIngredientListOverlay` (`isListDisplayed`, `hasKeyboardFocus`, the item under the mouse, `getVisibleIngredients`), `getBookmarkOverlay` and `getBookmarkManager` (BEI's bookmarks), `getRecipesGui` (`show`, `showTypes` and `showRecipes` open BEI's recipe popup), `getEditModeConfig` (BEI's edit mode), `getIngredientVisibility` (hidden items), `getScreenHelper` (exclusion areas and ghost handlers) | parts of the adapters with no BEI screen behind them (the screen helper's gui properties) answer with empty values |
| `IRecipeManager` | `hideRecipeCategory`, `unhideRecipeCategory`, `createRecipeCategoryLookup`, `createRecipeLookup` (with `limitFocus` and `includeHidden`; manager plugins answer focused lookups), `createRecipeCatalystLookup`, `createRecipeLayoutDrawable` and `createRecipeLayoutDrawableOrShowError` (draws the card where the plugin wants it, with its slots, mouse and input handler), `createRecipeSlotDrawable`, `getRecipeIngredients` (ingredients by role), `getRecipeButtonControllerFactories`, `addRecipes`, `hideRecipes`, `unhideRecipes` (also after the index is built: the recipes are bridged and added to or removed from the index at once), `getRecipeCategory`, `getRecipeType` | the layout drawable's `drawOverlays` draws slot overlays but not tooltips |
| `IIngredientManager` | `getAllIngredients`, `getAllTypedIngredients`, `getAllItemStacks`, `getRegisteredIngredientTypes`, `getIngredientTypeForUid`, `getIngredientType`, `getIngredientTypeChecked`, `getIngredientHelper`, `getIngredientRenderer`, `getIngredientCodec` (for items and for every type a plugin registered), `createTypedIngredient`, `normalizeTypedIngredient`, `getIngredientByUid`, `getTypedIngredientByUid`, `getIngredientAliases`, `registerIngredientListener` (fired when custom ingredients are added, removed or refreshed), `getClickableIngredientFactory`, `createClickableIngredient`, `addIngredientsAtRuntime` and `removeIngredientsAtRuntime` (items are hidden or shown by item id, custom types change their list), `refreshIngredientsAtRuntime` | `getIngredientTypeWithSubtypesFromBase`: answers empty |

### IGuiHelper and the drawables

Supported and drawn for real: `createDrawable`, `drawableBuilder` (`setTextureSize`, `addPadding`, `build`, `buildAnimated`), `createAnimatedDrawable`, `createTickTimer`, `createBlankDrawable`, `createDrawableIngredient`, `createDrawableItemStack`, `getSlotDrawable`, `getRecipeArrow`, `getRecipeArrowFilled`, `getRecipeArrowEmpty`, `createAnimatedRecipeArrow`. The slot and arrow are drawn by BEI, not from JEI's textures (BEI does not ship JEI's assets). Also supported: `createDrawableItemLike`, `createDrawableSprite` and `createScalableDrawableSprite` (GUI sprites stretched; on 1.20.1, which has no GUI sprite atlas, the sprite is looked up as the texture `textures/gui/sprites/NAME.png` and stretched, and stays empty when there is none), `getOutputSlot`, `getRecipePlusSign`, `getRecipeFlameFilled`, `getRecipeFlameEmpty`, `createAnimatedRecipeFlame`, `createCraftingGridHelper`, `createRecipeSlotDrawable`, `createWidgetFromDrawable`, `createScrollBoxWidget`, `createScrollGridFactory` and `getScrollBoxScrollbarExtraWidth`.

### IRecipeLayoutBuilder, slots and extras

`addSlot`, `addInputSlot`, `addOutputSlot`, `addSlotToWidget`, `addInvisibleIngredients`, `setShapeless`, `createFocusLink` (BEI cycles every slot on one clock, so linked slots stay in step), `moveRecipeTransferButton` (the plus button moves to the plugin's spot), and on a slot `setPosition`, `setSlotName`, `addIngredient(s)`, `addItemStack(s)`, `addFluidStack`, `addTypedIngredient`, `setBackground`, `setOverlay`, `setCustomRenderer`, `addRichTooltipCallback` and `addTooltipCallback` (extra tooltip lines for the item in that slot).

`IRecipeExtrasBuilder` (JEI 19 and later): every method is supported. Drawables, widgets, text, recipe arrows, plus signs and flames are drawn; buttons, cycle buttons, scroll boxes, scroll grids, slotted widgets, input handlers, GUI event listeners and tooltip areas take the mouse: clicks, drags, releases and the wheel inside a card reach them, and their tooltips show.

### Recipe transfer

Typed handlers (`IRecipeTransferHandler`), universal handlers and slot-range handlers (`addRecipeTransferHandler(menuClass, menuType, recipeType, ...)`) become one BEI handler per menu class. Transfer listeners (`addRecipeTransferListener`) hear `beforeRecipeTransfer` and `afterRecipeTransfer` with a context and the result of every click. The handler is asked with `doTransfer` false to decide the plus button (enabled, dimmed with the handler's message, or hidden) and with `doTransfer` true on a click. Slot-range handlers move items with ordinary slot clicks (BEI sends no packets). Vanilla crafting recipes are handed to handlers as the game's recipe object on 1.20.1 and 1.21.1. On 26.x the client keeps no recipe objects, so BEI builds a `RecipeHolder` of a shaped or shapeless recipe from the recipe it received from the server (the ingredients, shape and result); this needs BEI on the server, which is also what supplies BEI's recipe list there.
