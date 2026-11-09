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
        then(2, () -> ClientData.setString("sort", "name"));
        shoot("sorted_by_name", 25);
        then(2, () -> ClientData.setString("sort", "creative"));
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
