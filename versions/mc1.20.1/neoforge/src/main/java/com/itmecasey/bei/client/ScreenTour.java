package com.itmecasey.bei.client;

import com.itmecasey.bei.Bei;
import com.itmecasey.bei.BeiConfig;
import com.itmecasey.bei.index.ItemEntry;
import com.itmecasey.bei.index.ItemIndex;
import com.itmecasey.bei.index.Search;
import com.itmecasey.bei.project.Project;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A development-only screen tour for the 26.x builds: joins the quick-play world, walks the item browser,
 * search, variants, recipe popup, planner, tooltips, cheat mode, themes and settings, saves a screenshot of
 * each into {@code run/screenshots} as {@code bei_scr_<name>.png}, logs {@code BEI-SCR} lines, then quits.
 * It does nothing unless the environment variable {@code BEI_SCREENS} is set.
 */
@Mod.EventBusSubscriber(modid = Bei.MODID, value = Dist.CLIENT)
final class ScreenTour {
    private static final boolean ENABLED = System.getenv("BEI_SCREENS") != null;
    private static final int SETTLE = 14;

    private record Step(int delay, Runnable action) {
    }

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static boolean planned;
    private static boolean finished;
    private static int idle;
    private static int ticks;
    private static int wait;

    private ScreenTour() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ENABLED || finished) {
            return;
        }
        Minecraft game = Minecraft.getInstance();
        if (game.player != null && game.player.isDeadOrDying()) {
            game.player.respawn();
            return;
        }
        if (game.mouseHandler.isMouseGrabbed()) {
            game.mouseHandler.releaseMouse();
        }
        if (!planned) {
            if (++ticks % 200 == 0) {
                Bei.LOGGER.info("BEI-SCR waiting: level={} player={} screen={} ready={} idle={}", game.level != null, game.player != null,
                        game.screen == null ? "none" : game.screen.getClass().getName(), ItemIndex.ready(), idle);
            }
            if (game.level == null || game.player == null || game.screen != null || !ItemIndex.ready() || ++idle < 100) {
                return;
            }
            planned = true;
            plan();
            Bei.LOGGER.info("BEI-SCR started");
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Step step = STEPS.poll();
        if (step == null) {
            finished = true;
            Bei.LOGGER.info("BEI-SCR done");
            game.stop();
            return;
        }
        try {
            step.action().run();
        } catch (RuntimeException | LinkageError e) {
            Bei.LOGGER.warn("BEI-SCR step failed", e);
        }
        Step next = STEPS.peek();
        wait = next == null ? 0 : next.delay();
    }

    private static void plan() {
        then(0, () -> {
            server("difficulty peaceful", "kill @e[type=!player]", "time set noon", "weather clear", "clear @p", "give @p minecraft:oak_planks 8", "give @p minecraft:iron_ingot 12",
                    "give @p minecraft:stick 6", "give @p minecraft:coal 8", "give @p minecraft:diamond 3",
                    "give @p minecraft:cobblestone 24", "give @p minecraft:redstone 6");
            Minecraft game = Minecraft.getInstance();
            game.options.guiScale().set(2);
            game.getWindow().setGuiScale(2);
            BeiConfig.setTheme("bei:dark");
            BeiConfig.setItemVariants(true);
            BeiConfig.setShowBrowser(true);
            BeiConfig.setWindowPos("recipe", -1, -1);
            BeiConfig.setWindowPos("settings", -1, -1);
            game.setScreen(new InventoryScreen(game.player));
        });
        shoot("browser", 90);
        then(2, () -> BrowserPanel.setSearchText("iron -nugget"));
        shoot("search", SETTLE);
        then(2, () -> BrowserPanel.setSearchText("planks"));
        then(SETTLE, () -> moveMouseToCell(0));
        shoot("group_tooltip", SETTLE);
        then(2, () -> BrowserPanel.setSearchText("netherite sword"));
        then(SETTLE, () -> moveMouseToCell(0));
        shoot("item_tooltip", SETTLE);

        // Variants: potions and enchanted books are their own entries, and a book is found by its tooltip text.
        then(2, () -> {
            moveMouse(5, 5);
            long variants = ItemIndex.entries().stream().filter(e -> e.variant).count();
            Bei.LOGGER.info("BEI-SCR variant entries in the index: {}", variants);
            BrowserPanel.setSearchText("enchanted book");
        });
        shoot("variants_books", SETTLE);
        then(2, () -> {
            BrowserPanel.setSearchText("sharpness");
            List<ItemEntry> found = Search.filter(ItemIndex.entries(), "sharpness", e -> true);
            long books = found.stream().filter(e -> e.variant).count();
            Bei.LOGGER.info("BEI-SCR search 'sharpness': {} entries, {} variants", found.size(), books);
        });
        shoot("variants_sharpness", SETTLE);
        then(2, () -> BrowserPanel.setSearchText("potion"));
        shoot("variants_potions", SETTLE);
        then(2, () -> {
            BeiConfig.setItemVariants(false);
            ItemIndex.rebuild();
            BrowserPanel.invalidate();
            BrowserPanel.setSearchText("enchanted book");
        });
        shoot("variants_off", 60);
        then(2, () -> {
            BeiConfig.setItemVariants(true);
            ItemIndex.rebuild();
            BrowserPanel.invalidate();
            BrowserPanel.setSearchText("");
        });

        then(60, () -> RecipePopup.open(Items.CRAFTING_TABLE, false));
        shoot("recipe_popup", SETTLE);
        then(2, () -> {
            RecipePopup.close();
            RecipePopup.open(Items.IRON_INGOT, false);
        });
        shoot("recipe_stations", SETTLE);
        then(2, () -> {
            RecipePopup.close();
            RecipePopup.open(Items.STICK, true);
        });
        shoot("uses_popup", SETTLE);
        then(2, () -> {
            RecipePopup.close();
            SettingsPopup.toggle();
        });
        shoot("settings", SETTLE);
        then(2, () -> {
            SettingsPopup.close();
            BeiConfig.setTheme("bei:light");
            RecipePopup.open(Items.STONE_PICKAXE, false);
        });
        shoot("light_theme", SETTLE);
        then(2, () -> {
            RecipePopup.close();
            BeiConfig.setTheme("bei:dark");
            Cheats.toggle();
            Cheats.give(Items.DIAMOND, false);
            moveMouse(300, 150);
        });
        shoot("cheat_mode", SETTLE);
        then(2, () -> {
            Cheats.toggle();
            server("clear @p", "give @p minecraft:oak_planks 2", "give @p minecraft:iron_ingot 3");
            Project.set(Items.IRON_PICKAXE, 1);
            Minecraft.getInstance().setScreen(new ProjectScreen(null));
        });
        then(30, Project::refresh);
        shoot("planner", SETTLE);
        then(2, () -> Minecraft.getInstance().setScreen(null));
        then(90, Project::refresh);
        shoot("planner_hud", SETTLE);
    }

    private static void moveMouseToCell(int index) {
        moveMouse(Minecraft.getInstance().getWindow().getGuiScaledWidth() - 95, 33);
    }

    /** Puts the mouse at a scaled-GUI position by writing the position the game last read from the window. */
    private static void moveMouse(double guiX, double guiY) {
        Minecraft game = Minecraft.getInstance();
        double x = guiX * game.getWindow().getScreenWidth() / game.getWindow().getGuiScaledWidth();
        double y = guiY * game.getWindow().getScreenHeight() / game.getWindow().getGuiScaledHeight();
        try {
            java.util.List<Field> doubles = new java.util.ArrayList<>();
            for (Field f : game.mouseHandler.getClass().getDeclaredFields()) {
                if (f.getType() == double.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    doubles.add(f);
                }
            }
            Field fx = doubles.get(0);
            Field fy = doubles.get(1);
            fx.setAccessible(true);
            fy.setAccessible(true);
            fx.setDouble(game.mouseHandler, x);
            fy.setDouble(game.mouseHandler, y);
        } catch (ReflectiveOperationException e) {
            Bei.LOGGER.warn("BEI-SCR could not move the mouse", e);
        }
    }

    private static void shoot(String name, int delay) {
        then(delay, () -> {
            Minecraft game = Minecraft.getInstance();
            game.gui.getChat().clearMessages(false);
            game.getToasts().clear();
        });
        then(2, () -> {
            Minecraft game = Minecraft.getInstance();
            Screenshot.grab(game.gameDirectory, "bei_scr_" + name + ".png", game.getMainRenderTarget(),
                    message -> Bei.LOGGER.info("BEI-SCR saved {}", name));
        });
    }

    private static void then(int delay, Runnable action) {
        STEPS.add(new Step(delay, action));
    }

    private static void server(String... commands) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                for (String command : commands) {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
                }
            });
        }
    }
}
