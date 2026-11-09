package com.itmecasey.bei.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.bei.api.TransferState;
import com.itmecasey.bei.index.BeiRecipe;
import com.itmecasey.bei.plugin.TransferHost;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Fluids, drawables, slot views and transfer handlers of the JEI bridge. */
class JeiFeaturesTest {
    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aFluidBecomesANamedBucket() {
        ItemStack stack = JeiBridge.item(new FluidStack(Fluids.WATER, 1000));
        assertEquals(Items.WATER_BUCKET, stack.getItem());
        assertTrue(stack.getHoverName().getString().contains("1000 mB"));
    }

    @Test
    void textureDrawablesKnowTheirSizeAndPadding() {
        IGuiHelper gui = JeiBridge.helpers().getGuiHelper();
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("test", "textures/gui/x.png");
        IDrawableStatic plain = gui.createDrawable(texture, 0, 0, 16, 18);
        assertEquals(16, plain.getWidth());
        assertEquals(18, plain.getHeight());
        IDrawableStatic padded = gui.drawableBuilder(texture, 0, 0, 16, 16).setTextureSize(32, 32).addPadding(1, 2, 3, 4).build();
        assertEquals(23, padded.getWidth());
        assertEquals(19, padded.getHeight());
        IDrawableAnimated animated = gui.createAnimatedDrawable(plain, 40, IDrawableAnimated.StartDirection.TOP, false);
        assertEquals(18, animated.getHeight());
        assertEquals(18, gui.getSlotDrawable().getWidth());
        assertEquals(24, gui.getRecipeArrow().getWidth());
        assertEquals(10, gui.createBlankDrawable(10, 12).getWidth());
        assertEquals(13, gui.getRecipePlusSign().getWidth());
        assertEquals(14, gui.getRecipeFlameFilled().getHeight());
        assertEquals(26, gui.getOutputSlot().getWidth());
        assertEquals(14, gui.createAnimatedRecipeFlame(200).getWidth());
        assertEquals(16, gui.createDrawableItemLike(Items.STONE).getWidth());
        assertEquals(30, gui.createDrawableSprite(ResourceLocation.fromNamespaceAndPath("test", "gui/sprite"), 30, 20).getWidth());
    }

    @Test
    void slotViewsFilterByRoleAndName() {
        var view = JeiViews.slots(List.of(
                new JeiViews.SlotSpec(RecipeIngredientRole.INPUT, List.of(new ItemStack(Items.STONE)), "in"),
                new JeiViews.SlotSpec(RecipeIngredientRole.OUTPUT, List.of(new ItemStack(Items.DIRT)), null)));
        assertEquals(2, view.getSlotViews().size());
        assertEquals(1, view.getSlotViews(RecipeIngredientRole.INPUT).size());
        assertTrue(view.findSlotByName("in").isPresent());
        assertEquals(Items.DIRT, view.getSlotViews(RecipeIngredientRole.OUTPUT).get(0).getDisplayedItemStack().orElseThrow().getItem());
        assertEquals(Items.STONE, view.getSlotViews().get(0).getAllIngredients().findFirst().orElseThrow()
                .getItemStack().orElseThrow().getItem());
        assertEquals(VanillaTypes.ITEM_STACK, view.getSlotViews().get(0).getAllIngredients().findFirst().orElseThrow().getType());
    }

    @Test
    void transferErrorsKeepTheirTypeAndMessage() {
        var helper = JeiViews.helper();
        JeiViews.Failure user = JeiViews.failure(helper.createUserErrorWithTooltip(Component.literal("Missing iron")));
        assertEquals(IRecipeTransferError.Type.USER_FACING, user.type());
        assertEquals("Missing iron", user.message().getString());
        assertEquals(IRecipeTransferError.Type.INTERNAL, JeiViews.failure(helper.createInternalError()).type());
    }

    /** A menu class only the test knows. */
    private static final class FakeMenu extends AbstractContainerMenu {
        FakeMenu() {
            super(null, 0);
        }

        @Override
        public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(net.minecraft.world.entity.player.Player player) {
            return true;
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void aJeiTransferHandlerDrivesThePlusButton() {
        JeiTransfers.allowNoPlayer = true;
        RecipeType<Object> type = RecipeType.create("ae2", "inscriber", Object.class);
        Object[] answer = {null};
        boolean[] ran = {false};
        IRecipeTransferHandler handler = JeiBridge.proxy(IRecipeTransferHandler.class, (self, method, args) -> switch (method.getName()) {
            case "getContainerClass" -> FakeMenu.class;
            case "getRecipeType" -> type;
            case "transferRecipe" -> {
                ran[0] = ran[0] || (boolean) args[5];
                yield answer[0];
            }
            default -> JeiBridge.fallback(method.getReturnType());
        });
        JeiTransfers.clear();
        JeiTransfers.add(handler, "ae2:inscriber");
        JeiTransfers.install();
        JeiBridge.putBridged("ae2:inscriber/1", new JeiBridge.Bridged("ae2:inscriber", null, "the-recipe",
                List.of(new JeiViews.SlotSpec(RecipeIngredientRole.INPUT, List.of(new ItemStack(Items.IRON_INGOT)), null)), List.of()));
        BeiRecipe recipe = new BeiRecipe("ae2:inscriber/1", "ae2:inscriber", 0, List.of(List.of(new ItemStack(Items.IRON_INGOT))),
                List.of(new ItemStack(Items.IRON_NUGGET)), List.of(), null, null);
        FakeMenu menu = new FakeMenu();

        assertEquals(TransferState.Kind.ENABLED, kind(menu, recipe));
        assertTrue(TransferHost.transfer(menu, recipe, false, false));
        assertTrue(ran[0], "the handler ran with doTransfer");

        answer[0] = JeiViews.userError(Component.literal("Missing iron"));
        JeiTransfers.clearCache();
        assertEquals(TransferState.Kind.DISABLED, kind(menu, recipe));
        assertEquals("Missing iron", TransferHost.view(menu, recipe, false, false, false).reason().getString());

        BeiRecipe other = new BeiRecipe("x/2", "other:type", 0, List.of(), List.of(new ItemStack(Items.DIRT)), List.of(), null, null);
        assertEquals(TransferState.Kind.HIDDEN, kind(menu, other));
        JeiTransfers.allowNoPlayer = false;
    }

    private static TransferState.Kind kind(AbstractContainerMenu menu, BeiRecipe recipe) {
        TransferHost.View view = TransferHost.view(menu, recipe, false, false, false);
        return switch (view.mode()) {
            case HANDLER -> TransferState.Kind.ENABLED;
            case DISABLED -> TransferState.Kind.DISABLED;
            default -> TransferState.Kind.HIDDEN;
        };
    }
}
