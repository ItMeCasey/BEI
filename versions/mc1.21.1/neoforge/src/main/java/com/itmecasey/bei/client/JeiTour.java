package com.itmecasey.bei.client;

import com.itmecasey.bei.Bei;
import com.itmecasey.bei.index.ItemIndex;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A development-only tour that checks the JEI bridge and the browser features in a real client: it waits in the world
 * the game was started into, opens BEI's screens on items made by a JEI-plugin mod, and saves a screenshot of each
 * as {@code run/screenshots/bei_jei_<name>.png}. It does nothing unless the environment variable
 * {@code BEI_JEI_TOUR} is set, and it quits the game at the end. Progress goes to the log as {@code BEI-TOUR} lines.
 */
@EventBusSubscriber(modid = Bei.MODID, value = Dist.CLIENT)
final class JeiTour {
    private static final boolean ENABLED = System.getenv("BEI_JEI_TOUR") != null;
    private static final String ITEMS = System.getenv("BEI_JEI_TOUR_ITEMS");
    private static final String BLOCK = System.getenv("BEI_JEI_TOUR_BLOCK");
    private static final boolean CHECK = System.getenv("BEI_JEI_TOUR_CHECK") != null;
    private static final boolean UI = System.getenv("BEI_JEI_TOUR_UI") != null;
    private static final boolean AE2 = System.getenv("BEI_JEI_TOUR_AE2") != null;

    private record Step(int delay, Runnable action) {
    }

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static boolean planned;
    private static boolean finished;
    private static int idle;
    private static int wait;

    private JeiTour() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) {
            return;
        }
        Minecraft game = Minecraft.getInstance();
        if (game.mouseHandler.isMouseGrabbed()) {
            game.mouseHandler.releaseMouse();
        }
        if (!planned) {
            if (game.level == null || game.player == null || game.screen != null || !ItemIndex.ready() || ++idle < 100) {
                return;
            }
            planned = true;
            plan();
            Bei.LOGGER.info("BEI-TOUR started");
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Step step = STEPS.poll();
        if (step == null) {
            finished = true;
            Bei.LOGGER.info("BEI-TOUR done");
            game.stop();
            return;
        }
        try {
            step.action().run();
        } catch (RuntimeException e) {
            Bei.LOGGER.warn("BEI-TOUR step failed", e);
        }
        Step next = STEPS.peek();
        wait = next == null ? 0 : next.delay();
    }

    private static void plan() {
        then(0, () -> {
            Minecraft game = Minecraft.getInstance();
            game.options.guiScale().set(2);
            game.getWindow().setGuiScale(2);
            Bookmarks.toggle("minecraft:diamond");
            Bookmarks.toggle("minecraft:iron_ingot");
            Bookmarks.toggle("minecraft:redstone");
            game.setScreen(new InventoryScreen(game.player));
        });
        shoot("browser", 20);
        then(2, () -> BrowserPanel.setSearchText("%redstone ^red"));
        shoot("prefix_search", 25);
        then(2, () -> BrowserPanel.setSearchText(""));
        then(2, () -> com.itmecasey.bei.BeiConfig.setSortOrder("name"));
        shoot("sorted_by_name", 25);
        then(2, () -> com.itmecasey.bei.BeiConfig.setSortOrder("creative"));
        then(2, EditMode::toggle);
        then(2, () -> BrowserPanel.setSearchText("diamond"));
        then(10, () -> EditMode.flip("minecraft:diamond_block"));
        shoot("edit_mode", 25);
        then(2, () -> EditMode.flip("minecraft:diamond_block"));
        then(2, () -> BrowserPanel.setSearchText(""));
        then(2, EditMode::toggle);
        if (ITEMS != null) {
            for (String id : ITEMS.split(",")) {
                String name = id.trim();
                if (name.isEmpty()) {
                    continue;
                }
                then(2, () -> open(name, false));
                shoot("recipes_" + name.replace(':', '_'), 25);
                then(2, () -> open(name, true));
                shoot("uses_" + name.replace(':', '_'), 25);
            }
        }
        if (UI) {
            then(2, () -> open("minecraft:stone", false));
            then(8, () -> open("minecraft:diamond", false));
            then(8, () -> Bei.LOGGER.info("BEI-UI history {} forward {}", popupSize("HISTORY"), popupSize("FORWARD")));
            then(2, () -> clickPopup(true));
            then(8, () -> Bei.LOGGER.info("BEI-UI after back click: history {} forward {}", popupSize("HISTORY"), popupSize("FORWARD")));
            shoot("ui_after_back", 8);
            then(2, () -> clickPopup(false));
            then(8, () -> Bei.LOGGER.info("BEI-UI after forward click: history {} forward {}", popupSize("HISTORY"), popupSize("FORWARD")));
            shoot("ui_after_forward", 8);
            then(2, com.itmecasey.bei.compat.JeiSelfCheck::addInfoPage);
            then(10, () -> open("minecraft:emerald", false));
            then(10, () -> clickStation(true));
            shoot("ui_info_page", 25);
            then(2, RecipePopup::close);
            then(2, com.itmecasey.bei.compat.JeiSelfCheck::registerCraftingHandler);
            then(2, () -> placeBlock("minecraft:crafting_table"));
            then(20, JeiTour::useBlock);
            then(60, () -> open("minecraft:stick", false));
            then(30, JeiTour::clickPlus);
            then(10, () -> Bei.LOGGER.info("BEI-UI transfer handler calls after the + click: {}", com.itmecasey.bei.compat.JeiSelfCheck.craftingHandlerCalls()));
            shoot("ui_crafting_plus", 10);
            then(2, () -> Minecraft.getInstance().setScreen(null));
        }
        if (CHECK) {
            then(2, com.itmecasey.bei.compat.JeiSelfCheck::run);
            then(2, () -> Minecraft.getInstance().setScreen(new CheckScreen()));
            shoot("selfcheck_cards", 20);
            then(2, () -> Minecraft.getInstance().setScreen(null));
        }
        if (AE2) {
            // Builds a real AE2 crafting terminal: a cable on stone, the terminal part on the cable, then opens it.
            then(2, () -> serverCommands("clear @p", "give @p ae2:fluix_glass_cable", "give @p ae2:crafting_terminal", "give @p minecraft:oak_planks 8", "give @p ae2:pattern_encoding_terminal"));
            then(10, () -> placeBlock("minecraft:stone"));
            then(20, () -> useAt(0, blockPos, net.minecraft.core.Direction.UP, 0.5, 1.0, 0.5));
            then(10, () -> {
                net.minecraft.core.BlockPos cell = blockPos.above().east();
                serverCommands("setblock " + cell.getX() + " " + cell.getY() + " " + cell.getZ() + " ae2:creative_energy_cell");
            });
            then(10, () -> useAt(1, blockPos.above(), net.minecraft.core.Direction.UP, 0.5, 1.0, 0.5));
            if (System.getenv("BEI_JEI_TOUR_AE2").equals("pattern")) {
                // a pattern encoding terminal on the north side of the same cable: its + button needs no items
                then(10, () -> useAt(3, blockPos.above(), net.minecraft.core.Direction.NORTH, 0.5, 0.5, 0.0));
                then(10, () -> useAt(8, blockPos.above(), net.minecraft.core.Direction.NORTH, 0.5, 0.5, 0.03));
            } else {
                then(10, () -> useAt(8, blockPos.above(), net.minecraft.core.Direction.UP, 0.5, 0.97, 0.5));
            }
            then(40, () -> {
                var screen = Minecraft.getInstance().screen;
                Bei.LOGGER.info("BEI-AE2 screen after opening the terminal: {}", screen == null ? "none" : screen.getClass().getName());
            });
            shoot("ae2_terminal", 5);
            then(30, () -> open("minecraft:stick", false));
            then(30, JeiTour::logDimmed);
            then(2, JeiTour::clickPlus);
            then(20, () -> {
                var menu = Minecraft.getInstance().player.containerMenu;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < menu.slots.size(); i++) {
                    var stack = menu.slots.get(i).getItem();
                    if (!stack.isEmpty() && menu.slots.get(i).container != Minecraft.getInstance().player.getInventory()) {
                        sb.append(i).append('=').append(stack.getItem()).append('x').append(stack.getCount()).append(' ');
                    }
                }
                Bei.LOGGER.info("BEI-AE2 menu {} non-player slots with items after the + click: {}", menu.getClass().getSimpleName(), sb);
            });
            shoot("ae2_terminal_plus", 10);
            then(2, () -> Minecraft.getInstance().setScreen(null));
        }
        if (BLOCK != null) {
            then(2, () -> placeBlock(BLOCK));
            then(20, JeiTour::useBlock);
            then(60, JeiTour::probeScreen);
            shoot("machine_screen", 5);
            then(2, () -> Minecraft.getInstance().setScreen(null));
        }
        then(2, () -> Bookmarks.toggle("minecraft:diamond"));
        then(0, () -> Bookmarks.toggle("minecraft:iron_ingot"));
        then(0, () -> Bookmarks.toggle("minecraft:redstone"));
        then(5, RecipePopup::close);
    }

    /** A bare screen that draws the self-check cards with the mouse held over the synthetic card's slot. */
    private static final class CheckScreen extends net.minecraft.client.gui.screens.Screen {
        CheckScreen() {
            super(net.minecraft.network.chat.Component.literal("BEI check"));
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int[] point = com.itmecasey.bei.compat.JeiSelfCheck.slotPoint();
            com.itmecasey.bei.compat.JeiSelfCheck.draw(graphics, point[0], point[1]);
        }
    }

    private static int popupSize(String field) {
        try {
            var f = RecipePopup.class.getDeclaredField(field);
            f.setAccessible(true);
            return ((java.util.Collection<?>) f.get(null)).size();
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    /** Clicks the popup's back or forward button through its own click handler (no OS cursor). */
    private static void clickPopup(boolean back) {
        try {
            var posField = RecipePopup.class.getDeclaredField("POS");
            posField.setAccessible(true);
            Object pos = posField.get(null);
            var xm = pos.getClass().getDeclaredMethod("x");
            xm.setAccessible(true);
            var ym = pos.getClass().getDeclaredMethod("y");
            ym.setAccessible(true);
            int px = (int) xm.invoke(pos);
            int py = (int) ym.invoke(pos);
            int x = px + 6 + (!back && popupSize("HISTORY") > 1 ? 20 : 0) + 8;
            int y = py + 26 + 8;
            boolean used = RecipePopup.mouseClicked(new com.itmecasey.bei.client.input.MouseButtonEvent(x, y, 0, 0));
            Bei.LOGGER.info("BEI-UI clicked the {} button at {},{} used={}", back ? "back" : "forward", x, y, used);
        } catch (ReflectiveOperationException e) {
            Bei.LOGGER.warn("BEI-UI popup click failed", e);
        }
    }

    /** Clicks the last (or first) station tab of the open popup through its own click handler. */
    private static void clickStation(boolean last) {
        try {
            var f = RecipePopup.class.getDeclaredField("STATIONS");
            f.setAccessible(true);
            java.util.List<?> stations = (java.util.List<?>) f.get(null);
            if (stations.isEmpty()) {
                return;
            }
            Object button = stations.get(last ? stations.size() - 1 : 0);
            var mx = button.getClass().getDeclaredMethod("x");
            mx.setAccessible(true);
            var my = button.getClass().getDeclaredMethod("y");
            my.setAccessible(true);
            var st = button.getClass().getDeclaredMethod("station");
            st.setAccessible(true);
            Object station = st.invoke(button);
            var title = station.getClass().getDeclaredMethod("key");
            title.setAccessible(true);
            int x = (int) mx.invoke(button) + 8;
            int y = (int) my.invoke(button) + 8;
            RecipePopup.mouseClicked(new com.itmecasey.bei.client.input.MouseButtonEvent(x, y, 0, 0));
            Bei.LOGGER.info("BEI-UI clicked station tab {} of {} ({})", last ? stations.size() : 1, stations.size(), title.invoke(station));
        } catch (ReflectiveOperationException e) {
            Bei.LOGGER.warn("BEI-UI station click failed", e);
        }
    }

    /** Clicks the first + button of the open popup through its own click handler. */
    private static void clickPlus() {
        try {
            var f = RecipePopup.class.getDeclaredField("PLUS");
            f.setAccessible(true);
            java.util.List<?> plus = (java.util.List<?>) f.get(null);
            Bei.LOGGER.info("BEI-UI + buttons shown: {}", plus.size());
            if (plus.isEmpty()) {
                return;
            }
            Object button = plus.get(0);
            var accessor = button.getClass().getDeclaredMethod("x");
            accessor.setAccessible(true);
            int x = (int) accessor.invoke(button);
            var accessorY = button.getClass().getDeclaredMethod("y");
            accessorY.setAccessible(true);
            int y = (int) accessorY.invoke(button);
            var handlerAccessor = button.getClass().getDeclaredMethod("handler");
            handlerAccessor.setAccessible(true);
            Bei.LOGGER.info("BEI-UI first + button at {},{} handler={}", x, y, handlerAccessor.invoke(button));
            RecipePopup.mouseClicked(new com.itmecasey.bei.client.input.MouseButtonEvent(x + 6, y + 6, 0, 0));
        } catch (ReflectiveOperationException e) {
            Bei.LOGGER.warn("BEI-UI plus click failed", e);
        }
    }

    private static net.minecraft.core.BlockPos blockPos;

    /** Puts a block next to the player. */
    private static void placeBlock(String id) {
        Minecraft game = Minecraft.getInstance();
        blockPos = game.player.blockPosition().east(2);
        net.minecraft.core.BlockPos pos = blockPos;
        net.minecraft.server.MinecraftServer server = game.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + id));
        }
    }

    /** Uses the block, so its screen opens when the server answers. */
    /** Logs the reason the popup gives for a dimmed + button (a handler that said the recipe cannot be moved). */
    private static void logDimmed() {
        try {
            var f = RecipePopup.class.getDeclaredField("DIMMED");
            f.setAccessible(true);
            java.util.List<?> dimmed = (java.util.List<?>) f.get(null);
            for (Object d : dimmed) {
                var reason = d.getClass().getDeclaredMethod("reason");
                reason.setAccessible(true);
                Bei.LOGGER.info("BEI-AE2 dimmed + button reason: {}", reason.invoke(d));
            }
            Bei.LOGGER.info("BEI-AE2 dimmed + buttons: {}", dimmed.size());
        } catch (ReflectiveOperationException e) {
            Bei.LOGGER.warn("BEI-AE2 could not read the dimmed buttons", e);
        }
    }

    private static void serverCommands(String... commands) {
        net.minecraft.server.MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                for (String command : commands) {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
                }
            });
        }
    }

    /** Right-clicks a block face with a hotbar slot selected, at a position inside the block (fractions of the block). */
    private static void useAt(int slot, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction face, double fx, double fy, double fz) {
        Minecraft game = Minecraft.getInstance();
        game.player.getInventory().selected = slot;
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                new net.minecraft.world.phys.Vec3(pos.getX() + fx, pos.getY() + fy, pos.getZ() + fz), face, pos, false);
        var result = game.gameMode.useItemOn(game.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        Bei.LOGGER.info("BEI-AE2 used slot {} ({}) on {} {} -> {}", slot, game.player.getInventory().getItem(slot).getItem(), pos, face, result);
    }

    private static void useBlock() {
        Minecraft game = Minecraft.getInstance();
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(blockPos), net.minecraft.core.Direction.UP, blockPos, false);
        game.gameMode.useItemOn(game.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        Bei.LOGGER.info("BEI-TOUR used the block at {}", blockPos);
    }

    /** Logs what the JEI bridge reports for the open machine screen: the panel's start and the ghost drop targets. */
    private static void probeScreen() {
        Minecraft game = Minecraft.getInstance();
        var screen = game.screen;
        Bei.LOGGER.info("BEI-TOUR screen is {}", screen == null ? "none" : screen.getClass().getName());
        if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container) {
            int guiRight = container.getGuiLeft() + container.getXSize();
            Bei.LOGGER.info("BEI-TOUR guiRight {} extraRight {}", guiRight, com.itmecasey.bei.compat.JeiHooks.extraRight(container, guiRight));
            var targets = com.itmecasey.bei.compat.JeiHooks.ghostTargets(container, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
            Bei.LOGGER.info("BEI-TOUR ghost targets {}", targets.size());
            int left = container.getGuiLeft();
            int top = container.getGuiTop();
            outer:
            for (int dy = 0; dy < container.getYSize(); dy += 3) {
                for (int dx = 0; dx < container.getXSize(); dx += 3) {
                    if (com.itmecasey.bei.compat.JeiHooks.clickAreas(container, left, top, left + dx, top + dy)) {
                        Bei.LOGGER.info("BEI-TOUR click area hit at {},{} (popup open: {})", dx, dy, RecipePopup.isOpen());
                        break outer;
                    }
                }
            }
            for (var target : targets) {
                Bei.LOGGER.info("BEI-TOUR   target at {},{} size {}x{}", target.x(), target.y(), target.width(), target.height());
            }
        }
    }

    private static void open(String id, boolean uses) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        Bei.LOGGER.info("BEI-TOUR opening {} ({})", id, uses ? "uses" : "recipes");
        RecipePopup.open(item, uses);
    }

    private static void shoot(String name, int delay) {
        then(delay, () -> {
            Minecraft game = Minecraft.getInstance();
            game.gui.getChat().clearMessages(false);
            game.getToasts().clear();
        });
        then(2, () -> {
            Minecraft game = Minecraft.getInstance();
            Screenshot.grab(game.gameDirectory, "bei_jei_" + name + ".png", game.getMainRenderTarget(),
                    message -> Bei.LOGGER.info("BEI-TOUR saved {}", name));
        });
    }

    private static void then(int delay, Runnable action) {
        STEPS.add(new Step(delay, action));
    }
}
