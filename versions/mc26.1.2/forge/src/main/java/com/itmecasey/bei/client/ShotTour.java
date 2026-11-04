package com.itmecasey.bei.client;

import com.itmecasey.bei.Bei;
import com.itmecasey.bei.BeiConfig;
import com.itmecasey.bei.index.ItemIndex;
import com.itmecasey.bei.project.Project;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A development-only screenshot tour for the wiki: it opens a fresh survival superflat world, gives
 * the player a few items, walks BEI's screens into the states worth showing and saves a screenshot
 * of each into {@code run/screenshots} as {@code bei_<name>.png}, then quits. It does nothing unless
 * the environment variable {@code BEI_SHOTS} is set. Progress goes to the log as {@code BEI-SHOTS} lines.
 */
@Mod.EventBusSubscriber(modid = Bei.MODID, value = Dist.CLIENT)
final class ShotTour {
    private static final boolean ENABLED = System.getenv("BEI_SHOTS") != null;
    private static final String WORLD = "bei_shots";
    private static final int SETTLE = 12;

    private enum Stage { WAITING_FOR_MENU, LOADING_WORLD, TOURING, DONE }

    private record Step(int delay, Runnable action) {
    }

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static Stage stage = Stage.WAITING_FOR_MENU;
    private static int idle;
    private static int wait;

    private ShotTour() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        if (!ENABLED || stage == Stage.DONE) {
            return;
        }
        Minecraft game = Minecraft.getInstance();
        if (game.mouseHandler.isMouseGrabbed()) {
            game.mouseHandler.releaseMouse();
        }
        switch (stage) {
            case WAITING_FOR_MENU -> waitForMenu(game);
            case LOADING_WORLD -> waitForWorld(game);
            case TOURING -> runSteps();
            default -> {
            }
        }
    }

    private static void waitForMenu(Minecraft game) {
        if (game.getOverlay() != null || game.level != null || game.screen == null || ++idle < 20) {
            return;
        }
        game.options.pauseOnLostFocus = false;
        LevelStorageSource source = game.getLevelSource();
        if (source.levelExists(WORLD)) {
            try (LevelStorageSource.LevelStorageAccess access = source.createAccess(WORLD)) {
                access.deleteLevel();
            } catch (IOException e) {
                Bei.LOGGER.warn("BEI-SHOTS could not delete the old world", e);
            }
        }
        LevelSettings settings = new LevelSettings(WORLD, GameType.SURVIVAL,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        stage = Stage.LOADING_WORLD;
        idle = 0;
        Bei.LOGGER.info("BEI-SHOTS creating world");
        game.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0L, false, false),
                WorldPresets::createFlatWorldDimensions, new TitleScreen());
    }

    private static void waitForWorld(Minecraft game) {
        if (game.level == null || game.player == null || game.screen != null || game.getSingleplayerServer() == null
                || ++idle < 60 || !ItemIndex.ready()) {
            return;
        }
        Bei.LOGGER.info("BEI-SHOTS in the world");
        planTour();
        stage = Stage.TOURING;
    }

    private static void runSteps() {
        if (wait > 0) {
            wait--;
            return;
        }
        Step step = STEPS.poll();
        if (step == null) {
            return;
        }
        step.action().run();
        Step next = STEPS.peek();
        wait = next == null ? 0 : next.delay();
    }

    // ---- the tour ---------------------------------------------------------------------------------

    private static void planTour() {
        then(0, () -> {
            server("time set noon", "weather clear", "clear @p", "give @p minecraft:oak_planks 8", "give @p minecraft:iron_ingot 12",
                    "give @p minecraft:stick 6", "give @p minecraft:coal 8", "give @p minecraft:diamond 3",
                    "give @p minecraft:cobblestone 24", "give @p minecraft:redstone 6");
            Minecraft game = Minecraft.getInstance();
            game.options.guiScale().set(3);
            game.getWindow().setGuiScale(3);
            BeiConfig.setTheme("bei:dark");
            BeiConfig.setCheatTarget(BeiConfig.CheatTarget.CURSOR);
            BeiConfig.setWindowPos("recipe", -1, -1);
            BeiConfig.setWindowPos("settings", -1, -1);
            BeiConfig.setTagTray(true);
            BeiConfig.setShowBrowser(true);
        });
        then(90, ShotTour::openInventory);
        shoot("browser", SETTLE);

        then(2, () -> BrowserPanel.search("iron -nugget"));
        shoot("search", SETTLE);

        then(2, () -> BrowserPanel.search("planks"));
        then(SETTLE, () -> moveMouseToCell(0));
        shoot("group_tooltip", SETTLE);
        then(2, () -> {
            var resolved = Groups.resolve(ItemIndex.entries());
            var group = resolved.groupOf().get(Items.OAK_PLANKS);
            if (group != null) {
                Groups.toggleExpanded(group);
                BrowserPanel.invalidate();
            }
            moveMouse(5, 5);
        });
        shoot("group_expanded", SETTLE);
        then(2, () -> {
            BrowserPanel.search("");
            var group = Groups.resolve(ItemIndex.entries()).groupOf().get(Items.OAK_PLANKS);
            if (group != null && Groups.isExpanded(group)) {
                Groups.toggleExpanded(group);
            }
            BrowserPanel.invalidate();
            BrowserPanel.search("netherite sword");
        });
        then(SETTLE, () -> moveMouseToCell(0));
        shoot("item_tooltip", SETTLE);

        then(2, () -> {
            BrowserPanel.search("");
            moveMouse(5, 5);
            RecipePopup.open(Items.CRAFTING_TABLE, false);
        });
        shoot("recipe_popup", SETTLE);
        then(2, () -> {
            RecipePopup.close();
            RecipePopup.open(Items.IRON_INGOT, false);
        });
        shoot("recipe_stations", SETTLE);
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
            Cheats.give(Items.DIAMOND, false);
            Cheats.give(Items.DIAMOND, false);
            moveMouse(300, 150);
        });
        shoot("cheat_mode", SETTLE);
        then(2, () -> {
            Cheats.toggle();
            // A smaller inventory, so the plan has something left to do.
            server("clear @p", "give @p minecraft:oak_planks 2", "give @p minecraft:iron_ingot 3");
            Project.set(Items.IRON_PICKAXE, 1);
            Minecraft.getInstance().setScreen(new ProjectScreen(null));
        });
        then(30, Project::refresh);
        shoot("project_tree", SETTLE);
        then(2, () -> Minecraft.getInstance().setScreen(null));
        then(90, Project::refresh);
        shoot("project_hud", SETTLE);

        then(20, () -> {
            Bei.LOGGER.info("BEI-SHOTS DONE");
            stage = Stage.DONE;
            Minecraft.getInstance().stop();
        });
    }

    private static void openInventory() {
        Minecraft game = Minecraft.getInstance();
        game.setScreen(new InventoryScreen(game.player));
    }

    private static void moveMouseToCell(int index) {
        int[] at = BrowserPanel.cellCenter(index);
        if (at != null) {
            moveMouse(at[0], at[1]);
        }
    }

    /** Puts the mouse at a scaled-GUI position by writing the position the game last read from the window. */
    private static void moveMouse(double guiX, double guiY) {
        Minecraft game = Minecraft.getInstance();
        double x = guiX * game.getWindow().getScreenWidth() / game.getWindow().getGuiScaledWidth();
        double y = guiY * game.getWindow().getScreenHeight() / game.getWindow().getGuiScaledHeight();
        try {
            Field fx = game.mouseHandler.getClass().getDeclaredField("xpos");
            Field fy = game.mouseHandler.getClass().getDeclaredField("ypos");
            fx.setAccessible(true);
            fy.setAccessible(true);
            fx.setDouble(game.mouseHandler, x);
            fy.setDouble(game.mouseHandler, y);
        } catch (ReflectiveOperationException e) {
            Bei.LOGGER.warn("BEI-SHOTS could not move the mouse", e);
        }
    }

    private static void shoot(String name, int delay) {
        then(delay, () -> {
            Minecraft game = Minecraft.getInstance();
            game.gui.getChat().clearMessages(false);
            game.getToastManager().clear();
        });
        then(2, () -> {
            Minecraft game = Minecraft.getInstance();
            Screenshot.grab(game.gameDirectory, "bei_" + name + ".png", game.getMainRenderTarget(), 1,
                    message -> Bei.LOGGER.info("BEI-SHOTS saved {}", name));
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
