package com.itmecasey.bei.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.bei.index.BeiRecipe;
import com.itmecasey.bei.index.ItemEntry;
import com.itmecasey.bei.index.Search;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.advanced.IRecipeManagerPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.category.extensions.IRecipeCategoryDecorator;
import mezz.jei.api.recipe.transfer.IRecipeTransferListener;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IIngredientAliasRegistration;
import mezz.jei.api.registration.IModInfoRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiKeyMappings;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Aliases, mod names, extra items, the ingredient registry, manager plugins, decorators, listeners and extensions. */
class JeiRegistryTest {
    private static final RecipeType<String> TYPE = RecipeType.create("test", "cat", String.class);

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void clean() {
        JeiRegistry.clear();
    }

    private static ItemEntry entry(net.minecraft.world.item.Item item, String name) {
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
        return new ItemEntry(item, new ItemStack(item), id, id.substring(0, id.indexOf(':')), name, Set.of());
    }

    @Test
    void itemAliasesAreFoundBySearch() {
        IJeiHelpers helpers = JeiBridge.helpers();
        IIngredientAliasRegistration aliases = (IIngredientAliasRegistration) JeiRegistry.searchRegistration(IIngredientAliasRegistration.class, helpers);
        aliases.addAlias(Items.STONE, "bedrock-lite");
        aliases.addAliases(Items.DIRT, List.of("mud", "soil"));
        aliases.addAlias(VanillaTypes.ITEM_STACK, new ItemStack(Items.GLASS), "see through");
        assertTrue(Search.parse("bedrock-lite").test(entry(Items.STONE, "stone")));
        assertFalse(Search.parse("bedrock-lite").test(entry(Items.DIRT, "dirt")));
        assertTrue(Search.parse("soil").test(entry(Items.DIRT, "dirt")));
        assertTrue(Search.parse("see through").test(entry(Items.GLASS, "glass")) || Search.parse("see").test(entry(Items.GLASS, "glass")));
        assertEquals(List.of("mud", "soil").size(), JeiAliases.listOfItem("minecraft:dirt").size());
    }

    @Test
    void modAliasesWidenTheModSearch() {
        IModInfoRegistration info = (IModInfoRegistration) JeiRegistry.searchRegistration(IModInfoRegistration.class, JeiBridge.helpers());
        info.addModAliases("minecraft", "vanilla", "base game");
        assertTrue(Search.parse("@vanilla").test(entry(Items.STONE, "stone")));
        assertFalse(Search.parse("@vanilla").test(new ItemEntry(Items.STONE, new ItemStack(Items.STONE), "other:stone", "other", "stone", Set.of())));
    }

    @Test
    void extraItemsAreCollectedForTheIndex() {
        IExtraIngredientRegistration extra = (IExtraIngredientRegistration) JeiRegistry.searchRegistration(IExtraIngredientRegistration.class, JeiBridge.helpers());
        extra.addExtraItemStacks(List.of(new ItemStack(Items.STONE, 3)));
        extra.addExtraIngredients(VanillaTypes.ITEM_STACK, List.of(new ItemStack(Items.DIRT)));
        assertEquals(2, JeiAliases.extraStacks().size());
    }

    /** A made-up ingredient type for the registry tests. */
    private static final IIngredientType<String> WORD = new IIngredientType<>() {
        @Override
        public String getUid() {
            return "test:word";
        }

        @Override
        public Class<? extends String> getIngredientClass() {
            return String.class;
        }
    };

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void customIngredientTypesAreListedAndHeard() {
        IIngredientHelper<String> helper = (IIngredientHelper<String>) JeiBridge.proxy((Class) IIngredientHelper.class, (self, method, args) ->
                method.getName().equals("getIngredientType") ? WORD : method.getName().equals("getUniqueId") ? args[0] : JeiBridge.fallback(method.getReturnType()));
        JeiRegistry.registerType(new Object[] {WORD, new ArrayList<>(List.of("alpha", "beta")), helper, null});
        IIngredientManager manager = JeiRegistry.ingredientManager();
        assertEquals(List.of("alpha", "beta"), List.copyOf(manager.getAllIngredients(WORD)));
        assertEquals(2, manager.getAllTypedIngredients(WORD).size());
        assertEquals(WORD, manager.getIngredientTypeForUid("test:word").orElseThrow());
        assertEquals(WORD, manager.getIngredientType("alpha"));
        assertTrue(manager.getRegisteredIngredientTypes().contains(WORD));
        assertEquals(helper, manager.getIngredientHelper(WORD));
        ITypedIngredient<String> typed = manager.createTypedIngredient(WORD, "gamma").orElseThrow();
        assertEquals("gamma", typed.getIngredient());
        assertEquals("beta", manager.getIngredientByUid(WORD, "beta").orElseThrow());
        List<String> heard = new ArrayList<>();
        manager.registerIngredientListener(JeiBridge.proxy(IIngredientManager.IIngredientListener.class, (self, method, args) -> {
            heard.add(method.getName());
            return null;
        }));
        manager.addIngredientsAtRuntime(WORD, List.of("delta"));
        manager.removeIngredientsAtRuntime(WORD, List.of("alpha"));
        assertEquals(List.of("onIngredientsAdded", "onIngredientsRemoved"), heard);
        assertEquals(List.of("beta", "delta"), List.copyOf(manager.getAllIngredients(WORD)));
        assertEquals(VanillaTypes.ITEM_STACK, manager.getIngredientType(new ItemStack(Items.STONE)));
        assertNotNull(manager.getIngredientHelper(VanillaTypes.ITEM_STACK));
        assertEquals("minecraft:stone", manager.getIngredientHelper(VanillaTypes.ITEM_STACK).getUniqueId(new ItemStack(Items.STONE),
                mezz.jei.api.ingredients.subtypes.UidContext.Ingredient));
        assertTrue(manager.getIngredientAliases(JeiViews.typed(new ItemStack(Items.STONE))).isEmpty());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static IRecipeCategory<String> category() {
        return (IRecipeCategory<String>) JeiBridge.proxy((Class) IRecipeCategory.class, (self, method, args) ->
                method.getName().equals("getRecipeType") ? TYPE : JeiBridge.fallback(method.getReturnType()));
    }

    @Test
    void managerPluginsAddRecipes() {
        IAdvancedRegistration advanced = JeiRegistry.advanced(JeiBridge.helpers());
        advanced.addRecipeManagerPlugin(new IRecipeManagerPlugin() {
            @Override
            public <V> List<RecipeType<?>> getRecipeTypes(IFocus<V> focus) {
                return List.of(TYPE);
            }

            @SuppressWarnings("unchecked")
            @Override
            public <T, V> List<T> getRecipes(IRecipeCategory<T> recipeCategory, IFocus<V> focus) {
                return (List<T>) List.of("focused");
            }

            @SuppressWarnings("unchecked")
            @Override
            public <T> List<T> getRecipes(IRecipeCategory<T> recipeCategory) {
                return (List<T>) List.of("one", "two");
            }
        });
        List<Object> found = JeiRegistry.pluginRecipes(TYPE, category(), new ArrayList<>(List.of("one")));
        assertEquals(List.of("two"), found);
    }

    @Test
    void decoratorsChangeTooltipsAndDraw() {
        List<String> drawn = new ArrayList<>();
        JeiRegistry.advanced(JeiBridge.helpers()).addRecipeCategoryDecorator(TYPE, new IRecipeCategoryDecorator<String>() {
            @Override
            public void draw(String recipe, IRecipeCategory<String> recipeCategory, mezz.jei.api.gui.ingredient.IRecipeSlotsView view,
                    net.minecraft.client.gui.GuiGraphics graphics, double mouseX, double mouseY) {
                drawn.add(recipe);
            }

            @Override
            public List<Component> decorateExistingTooltips(List<Component> tooltips, String recipe, IRecipeCategory<String> recipeCategory,
                    mezz.jei.api.gui.ingredient.IRecipeSlotsView view, double mouseX, double mouseY) {
                List<Component> changed = new ArrayList<>(tooltips);
                changed.add(Component.literal("decorated " + recipe));
                return changed;
            }
        });
        JeiRegistry.decoratePaint(category(), "r1", JeiViews.slots(List.of()), null, 0, 0);
        assertEquals(List.of("r1"), drawn);
        List<Component> lines = JeiRegistry.decorateTooltip(category(), "r1", JeiViews.slots(List.of()), 0, 0, new ArrayList<>());
        assertEquals(1, lines.size());
        assertEquals("decorated r1", lines.get(0).getString());
    }

    @Test
    void transferListenersAreListedByTheTransferManager() {
        IRecipeTransferListener listener = new IRecipeTransferListener() {
        };
        JeiTransfers.addListener(listener);
        IRecipeTransferManager manager = (IRecipeTransferManager) JeiTransfers.manager(IRecipeTransferManager.class);
        assertEquals(List.of(listener), manager.getRecipeTransferListeners());
        JeiTransfers.clear();
        assertTrue(manager.getRecipeTransferListeners().isEmpty());
    }

    @Test
    void keyMappingsMatchBeisKeys() {
        IJeiKeyMappings keys = (IJeiKeyMappings) JeiRegistry.keyMappings(IJeiKeyMappings.class);
        assertFalse(keys.getShowRecipe().isUnbound());
        assertEquals("R", keys.getShowRecipe().getTranslatedKeyMessage().getString());
        assertEquals("U", keys.getShowUses().getTranslatedKeyMessage().getString());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void recipeLayoutDrawablesAndIngredientSuppliersWork() {
        IRecipeCategory<String> category = (IRecipeCategory<String>) JeiBridge.proxyFirst((Class) IRecipeCategory.class, (self, method, args) -> {
            switch (method.getName()) {
                case "getRecipeType":
                    return TYPE;
                case "getWidth":
                    return 100;
                case "getHeight":
                    return 40;
                case "setRecipe":
                    var slot = ((mezz.jei.api.gui.builder.IRecipeLayoutBuilder) args[0]).addSlot(RecipeIngredientRole.INPUT, 4, 5);
                    slot.addItemStack(new ItemStack(Items.STONE));
                    return null;
                default:
                    return JeiBridge.fallback(method.getReturnType());
            }
        });
        var manager = JeiBridge.runtime(JeiBridge.helpers()).getRecipeManager();
        var supplier = manager.getRecipeIngredients(category, "recipe");
        assertEquals(1, supplier.getIngredients(RecipeIngredientRole.INPUT).size());
        assertEquals(0, supplier.getIngredients(RecipeIngredientRole.OUTPUT).size());
        var drawable = manager.createRecipeLayoutDrawable(category, "recipe", JeiBridge.emptyFocuses()).orElseThrow();
        drawable.setPosition(10, 20);
        assertEquals(10, drawable.getRect().getX());
        assertEquals(100, drawable.getRect().getWidth());
        assertTrue(drawable.isMouseOver(12, 22));
        assertFalse(drawable.isMouseOver(0, 0));
        assertEquals("recipe", drawable.getRecipe());
        assertEquals(Items.STONE, drawable.getItemStackUnderMouse(10 + 5, 20 + 6).orElseThrow().getItem());
        var slotDrawable = manager.createRecipeSlotDrawable(RecipeIngredientRole.INPUT,
                List.of(Optional.of(JeiViews.typed(new ItemStack(Items.DIRT)))), Set.of(), 0);
        assertEquals(Items.DIRT, slotDrawable.getDisplayedItemStack().orElseThrow().getItem());
    }

    @Test
    void categoryExtensionsLayOutGameRecipes() {
        Object extension = JeiBridge.proxyFirst(mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension.class, (self, method, args) -> {
            if (method.getName().equals("setRecipe") && method.getParameterCount() == 4) {
                var builder = (mezz.jei.api.gui.builder.IRecipeLayoutBuilder) args[1];
                builder.addSlot(RecipeIngredientRole.INPUT, 0, 0).addItemStack(new ItemStack(Items.COAL));
                builder.addSlot(RecipeIngredientRole.OUTPUT, 40, 0).addItemStack(new ItemStack(Items.DIAMOND));
                return null;
            }
            return method.isDefault() ? JeiBridge.DEFER : JeiBridge.fallback(method.getReturnType());
        });
        JeiBridge.putExtension("crafting", String.class, extension);
        BeiRecipe original = new BeiRecipe("test:r", "ShapelessCrafting", 0, List.of(), List.of(new ItemStack(Items.STONE)), List.of());
        BeiRecipe changed = JeiBridge.extended("a game recipe", original);
        assertNotNull(changed);
        assertEquals(Items.DIAMOND, changed.outputs().get(0).getItem());
        assertEquals(Items.COAL, changed.inputs().get(0).get(0).getItem());
        assertEquals(null, JeiBridge.extended(42, original));
    }

    @SuppressWarnings("unchecked")
    @Test
    void guiHelperWidgetsJoinTheCardOnce() {
        mezz.jei.api.helpers.IGuiHelper gui = JeiBridge.helpers().getGuiHelper();
        List<JeiBridge.Drawer> extras = new ArrayList<>();
        mezz.jei.api.gui.widgets.IRecipeExtrasBuilder builder = (mezz.jei.api.gui.widgets.IRecipeExtrasBuilder)
                JeiCards.extrasBuilder(mezz.jei.api.gui.widgets.IRecipeExtrasBuilder.class, extras, new ArrayList<>());
        var widget = gui.createWidgetFromDrawable(gui.createBlankDrawable(30, 20), 5, 6);
        builder.addWidget(widget);
        builder.addWidget(widget);
        assertEquals(1, extras.size());
        var box = gui.createScrollBoxWidget(gui.createBlankDrawable(40, 100), 30, 2, 3);
        builder.addWidget(box);
        builder.addInputHandler(box);
        assertEquals(2, extras.size());
        assertEquals(6, gui.getScrollBoxScrollbarExtraWidth());
        var grid = (mezz.jei.api.gui.widgets.IScrollGridWidgetFactory<Object>) gui.createScrollGridFactory(3, 2);
        grid.setPosition(4, 5);
        assertEquals(54, grid.getArea().width());
        grid.createWidgetForSlots(builder, "recipe", List.of());
        assertEquals(3, extras.size());
        var slot = gui.createRecipeSlotDrawable(RecipeIngredientRole.INPUT,
                acceptor -> acceptor.addItemStack(new ItemStack(Items.STONE)), Set.of(), 0);
        assertEquals(Items.STONE, slot.getDisplayedItemStack().orElseThrow().getItem());
    }
}
