package com.itmecasey.bei.compat;

import com.itmecasey.bei.Bei;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Development-only checks of the 2026.11.12 JEI features in a real client, driven by the screenshot tour. It builds a
 * synthetic category with a button, a scroll box and a slot, draws it through the recipe manager's layout drawable,
 * presses the button and scrolls the box through the drawable's input handler, and reads the same drawable for a real
 * plugin recipe. Results go to the log as {@code BEI-CHECK} lines.
 */
public final class JeiSelfCheck {
    private static final RecipeType<String> TYPE = RecipeType.create("bei", "selfcheck", String.class);
    private static final int[] PRESSED = {0};
    private static IRecipeLayoutDrawable<?> synthetic;
    private static IRecipeLayoutDrawable<?> real;

    private JeiSelfCheck() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IRecipeCategory<String> category() {
        return (IRecipeCategory<String>) JeiBridge.proxyFirst((Class) IRecipeCategory.class, (self, method, args) -> {
            switch (method.getName()) {
                case "getRecipeType":
                    return TYPE;
                case "getWidth":
                    return 150;
                case "getHeight":
                    return 90;
                case "getTitle":
                    return Component.literal("BEI self check");
                case "setRecipe":
                    ((mezz.jei.api.gui.builder.IRecipeLayoutBuilder) args[0]).addSlot(RecipeIngredientRole.INPUT, 6, 6)
                            .addItemStack(new ItemStack(Items.DIAMOND))
                            .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.literal("tooltip callback line")));
                    return null;
                case "createRecipeExtras":
                    IRecipeExtrasBuilder builder = (IRecipeExtrasBuilder) args[0];
                    builder.addButton(40, 14, () -> PRESSED[0]++).setPosition(6, 40);
                    List<FormattedText> lines = new ArrayList<>();
                    for (int i = 1; i <= 12; i++) {
                        lines.add(Component.literal("scroll box line " + i));
                    }
                    builder.addScrollBoxWidget(90, 40, 54, 6).setContents(lines);
                    return null;
                default:
                    return method.isDefault() ? JeiBridge.DEFER : JeiBridge.fallback(method.getReturnType());
            }
        });
    }

    /** Runs the checks and logs the results. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void run() {
        try {
            IJeiRuntime runtime = JeiBridge.runtime(JeiBridge.helpers());
            IRecipeManager manager = runtime.getRecipeManager();
            IRecipeCategory<String> category = category();
            synthetic = manager.createRecipeLayoutDrawable(category, "synthetic", JeiBridge.emptyFocuses()).orElseThrow();
            synthetic.setPosition(20, 20);
            var supplier = manager.getRecipeIngredients(category, "synthetic");
            Bei.LOGGER.info("BEI-CHECK synthetic card rect {}x{} inputs {} outputs {}", synthetic.getRect().getWidth(),
                    synthetic.getRect().getHeight(), supplier.getIngredients(RecipeIngredientRole.INPUT).size(),
                    supplier.getIngredients(RecipeIngredientRole.OUTPUT).size());
            // Press the button (card coordinates 6..46 by 40..54) and scroll the box through the input handler.
            var handler = synthetic.getInputHandler();
            Method press = JeiBridge.ifaceMethod("mezz.jei.api.gui.inputs.IJeiInputHandler", "handleInput", 3);
            Object input = JeiCards.userInput(press.getParameterTypes()[2], 0);
            boolean used = (boolean) press.invoke(handler, 20 + 10.0, 20 + 45.0, input);
            Bei.LOGGER.info("BEI-CHECK button press used={} callbackRuns={}", used, PRESSED[0]);
            boolean scrolled = handler.handleMouseScrolled(20 + 60.0, 20 + 20.0, 0, -1.0);
            Bei.LOGGER.info("BEI-CHECK scroll box used the wheel={}", scrolled);
            Bei.LOGGER.info("BEI-CHECK key mapping show recipe = {}", runtime.getKeyMappings().getShowRecipe().getTranslatedKeyMessage().getString());
            Bei.LOGGER.info("BEI-CHECK ingredient types {} config files {}", runtime.getIngredientManager().getRegisteredIngredientTypes().size(),
                    runtime.getConfigManager().getConfigFiles().size());
            Bei.LOGGER.info("BEI-CHECK subtype lookup {}", runtime.getIngredientManager().getIngredientTypeWithSubtypesFromBase(Items.STONE).isPresent());
            // A real plugin card (the first bridged recipe) through the same drawable.
            JeiBridge.Bridged sample = null;
            synchronized (JeiBridge.class) {
                for (JeiBridge.Bridged each : JeiBridge.allBridged()) {
                    if (each.layoutSlots().size() >= 2) {
                        sample = each;
                        break;
                    }
                }
            }
            if (sample != null) {
                IRecipeCategory realCategory = sample.category();
                real = (IRecipeLayoutDrawable<?>) ((java.util.Optional) manager.createRecipeLayoutDrawable(realCategory, sample.recipe(), JeiBridge.emptyFocuses())).orElseThrow();
                real.setPosition(20, 130);
                var ingredients = manager.getRecipeIngredients(realCategory, sample.recipe());
                Bei.LOGGER.info("BEI-CHECK real card {} rect {}x{} inputs {} outputs {}", sample.typeUid(), real.getRect().getWidth(),
                        real.getRect().getHeight(), ingredients.getIngredients(RecipeIngredientRole.INPUT).size(),
                        ingredients.getIngredients(RecipeIngredientRole.OUTPUT).size());
            } else {
                Bei.LOGGER.info("BEI-CHECK no real plugin card with two slots");
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            Bei.LOGGER.warn("BEI-CHECK failed", e);
        }
    }

    /** Draws both cards and their overlays and tooltips; the mouse position is where the tour points. */
    public static void draw(GuiGraphics g, int mx, int my) {
        draw(synthetic, g, mx, my);
        draw(real, g, mx, my);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void draw(IRecipeLayoutDrawable drawable, GuiGraphics g, int mx, int my) {
        if (drawable != null) {
            drawable.drawRecipe(g, mx, my);
            drawable.drawOverlays(g, mx, my);
        }
    }

    /** Where the first slot of the synthetic card is on screen. */
    public static int[] slotPoint() {
        return new int[] {20 + 6 + 8, 20 + 6 + 8};
    }
}
