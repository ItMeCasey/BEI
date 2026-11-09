package com.itmecasey.bei.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exclusion zones, ghost-ingredient drops and the runtime objects that stand for JEI's own screens. */
class JeiScreensTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void point() {
        com.itmecasey.bei.client.ClientData.useFile(dir.resolve("config").resolve("bei_client.json"));
        JeiHidden.clear();
    }

    /** A container screen class only the test knows; instances are made without running any constructor. */
    private static final class FakeScreen extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<net.minecraft.world.inventory.AbstractContainerMenu> {
        FakeScreen() {
            super(null, null, null);
        }

        @Override
        protected void renderBg(net.minecraft.client.gui.GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        }
    }

    private static FakeScreen screen() throws ReflectiveOperationException {
        java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (FakeScreen) ((sun.misc.Unsafe) field.get(null)).allocateInstance(FakeScreen.class);
    }

    private static IJeiRuntime runtime() {
        return JeiBridge.runtime(JeiBridge.helpers());
    }

    @Test
    @SuppressWarnings({"rawtypes"})
    void anExclusionZoneToTheRightPushesThePanel() throws Exception {
        Object handler = JeiBridge.proxyFirst(Class.forName("mezz.jei.api.gui.handlers.IGuiContainerHandler"), (self, method, args) ->
                method.getName().equals("getGuiExtraAreas") ? List.of(new Rect2i(210, 10, 30, 60), new Rect2i(0, 0, 20, 20))
                        : JeiBridge.fallback(method.getReturnType()));
        JeiBridge.putGuiHandler(false, FakeScreen.class, handler);
        assertEquals(240, JeiBridge.extraRight(screen(), 200));
        assertEquals(200, JeiBridge.extraRight(Integer.valueOf(1), 200), "another screen class is not affected");
    }

    @Test
    void aGhostHandlerGivesDropTargetsThatAcceptAndComplete() throws Exception {
        List<ItemStack> accepted = new ArrayList<>();
        boolean[] completed = {false};
        Class<?> targetType = Class.forName("mezz.jei.api.gui.handlers.IGhostIngredientHandler$Target");
        Object target = JeiBridge.proxy(targetType, (self, method, args) -> switch (method.getName()) {
            case "getArea" -> new Rect2i(50, 60, 16, 16);
            case "accept" -> {
                accepted.add((ItemStack) args[0]);
                yield null;
            }
            default -> JeiBridge.fallback(method.getReturnType());
        });
        Object handler = JeiBridge.proxy(Class.forName("mezz.jei.api.gui.handlers.IGhostIngredientHandler"), (self, method, args) ->
                switch (method.getName()) {
                    case "getTargetsTyped" -> List.of(target);
                    case "onComplete" -> {
                        completed[0] = true;
                        yield null;
                    }
                    default -> JeiBridge.fallback(method.getReturnType());
                });
        JeiBridge.putGuiHandler(true, FakeScreen.class, handler);
        List<GhostTarget> targets = JeiBridge.ghostTargets(screen(), new ItemStack(Items.STONE));
        assertEquals(1, targets.size());
        GhostTarget first = targets.get(0);
        assertTrue(first.contains(55, 65));
        assertFalse(first.contains(10, 10));
        first.accept().accept(new ItemStack(Items.DIRT));
        first.complete().run();
        assertEquals(Items.DIRT, accepted.get(0).getItem());
        assertTrue(completed[0]);
        assertTrue(JeiBridge.ghostTargets(Integer.valueOf(1), new ItemStack(Items.STONE)).isEmpty());
    }

    @Test
    void theIngredientFilterIsBeisSearchBox() {
        IJeiRuntime runtime = runtime();
        runtime.getIngredientFilter().setFilterText("redstone ^red");
        assertEquals("redstone ^red", runtime.getIngredientFilter().getFilterText());
        runtime.getIngredientFilter().setFilterText("");
    }

    @Test
    void bookmarksAndHiddenItemsGoThroughTheRuntime() {
        IJeiRuntime runtime = runtime();
        var stone = JeiViews.typed(new ItemStack(Items.STONE));
        assertFalse(runtime.getBookmarkManager().contains(stone));
        assertTrue(runtime.getBookmarkManager().add(stone));
        assertTrue(runtime.getBookmarkManager().contains(stone));
        assertTrue(runtime.getBookmarkManager().remove(stone));
        assertFalse(runtime.getBookmarkManager().contains(stone));

        assertTrue(runtime.getIngredientVisibility().isIngredientVisible(VanillaTypes.ITEM_STACK, new ItemStack(Items.STONE)),
                "hidden in edit mode: " + com.itmecasey.bei.client.ViewerFacade.hiddenInEditMode("minecraft:stone") + " jei: " + JeiHidden.itemSpecs());
        runtime.getIngredientVisibility().hideIngredients(VanillaTypes.ITEM_STACK, List.of(new ItemStack(Items.STONE)), List.of());
        assertFalse(runtime.getIngredientVisibility().isIngredientVisible(stone));
        runtime.getIngredientVisibility().unhideIngredients(VanillaTypes.ITEM_STACK, List.of(new ItemStack(Items.STONE)), List.of());
        assertTrue(runtime.getIngredientVisibility().isIngredientVisible(stone));

        assertFalse(runtime.getEditModeConfig().isIngredientHiddenUsingConfigFile(stone));
        runtime.getEditModeConfig().hideIngredientUsingConfigFile(stone,
                mezz.jei.api.runtime.IEditModeConfig.HideMode.SINGLE);
        assertTrue(runtime.getEditModeConfig().isIngredientHiddenUsingConfigFile(stone));
        runtime.getEditModeConfig().showIngredientUsingConfigFile(stone, mezz.jei.api.runtime.IEditModeConfig.HideMode.SINGLE);
        assertFalse(runtime.getEditModeConfig().isIngredientHiddenUsingConfigFile(stone));
    }

    @Test
    void aRecipeClickAreaOpensThatCategoryOnlyInsideItsRectangle() throws Exception {
        com.itmecasey.bei.client.RecipePopup.close();
        JeiBridge.putClickArea(FakeScreen.class, 80, 35, 22, 15, List.of("ae2:inscriber"));
        FakeScreen screen = screen();
        assertFalse(JeiBridge.clickAreas(screen, 10, 10, 20, 20), "outside the area");
        assertFalse(com.itmecasey.bei.client.RecipePopup.isOpen());
        assertTrue(JeiBridge.clickAreas(screen, 10, 10, 10 + 85, 10 + 40), "inside the area, relative to the screen's corner");
        assertTrue(com.itmecasey.bei.client.RecipePopup.isOpen());
        com.itmecasey.bei.client.RecipePopup.close();
    }

    @Test
    void showTypesOpensTheCategoryAndTheIndexListsItsRecipes() {
        RecipeType<Object> type = RecipeType.create("ae2", "grinder", Object.class);
        com.itmecasey.bei.index.BeiRecipe recipe = new com.itmecasey.bei.index.BeiRecipe("ae2:grinder/1", "ae2:grinder", 0,
                List.of(List.of(new ItemStack(Items.IRON_ORE))), List.of(new ItemStack(Items.IRON_INGOT)), List.of(), null, null);
        com.itmecasey.bei.index.RecipeIndex.useRecipes(List.of(recipe));
        assertEquals(1, com.itmecasey.bei.index.RecipeIndex.ofType("ae2:grinder").size());
        assertEquals(0, com.itmecasey.bei.index.RecipeIndex.ofType("ae2:other").size());
        runtime().getRecipesGui().showTypes(List.of(type));
        assertTrue(com.itmecasey.bei.client.RecipePopup.isOpen());
        com.itmecasey.bei.client.RecipePopup.close();
    }

    @Test
    void runtimeRecipeAdditionsAndLookupsWork() {
        RecipeType<Object> type = RecipeType.create("ae2", "press", Object.class);
        var manager = runtime().getRecipeManager();
        manager.addRecipes(type, List.of("a", "b"));
        assertEquals(2, manager.createRecipeLookup(type).get().count());
        manager.hideRecipes(type, List.of("a"));
        assertEquals(1, manager.createRecipeLookup(type).get().count());
        manager.unhideRecipes(type, List.of("a"));
        assertEquals(2, manager.createRecipeLookup(type).get().count());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void theRecipesScreenOpensBeisPopupForAFocus() {
        IFocus focus = JeiBridge.proxy(IFocus.class, (self, method, args) -> switch (method.getName()) {
            case "getTypedValue" -> JeiViews.typed(new ItemStack(Items.IRON_INGOT));
            case "getRole" -> RecipeIngredientRole.OUTPUT;
            default -> JeiBridge.fallback(method.getReturnType());
        });
        runtime().getRecipesGui().show(focus);
        assertTrue(com.itmecasey.bei.client.RecipePopup.isOpen());
        com.itmecasey.bei.client.RecipePopup.close();
    }
}
