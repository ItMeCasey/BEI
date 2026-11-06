package com.itmecasey.bei.client;

import com.itmecasey.bei.BeiConfig;
import com.itmecasey.bei.client.Groups.Cell;
import com.itmecasey.bei.index.ItemEntry;
import com.itmecasey.bei.index.ItemIndex;
import com.itmecasey.bei.index.RecipeIndex;
import com.itmecasey.bei.index.Search;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The item browser: a panel flush against the right edge of the screen with the category strip on
 * its left, the item grid in the middle and the search bar and gear along the bottom. Searching
 * runs on a background thread; the grid draws only the cells in view.
 */
public final class BrowserPanel {
    private static final int CELL = 18;
    private static final int TRAY_ROW = 24;
    private static final int EDGE = 4;
    private static final int MAX_COLS = 24;

    private static final ExecutorService SEARCHER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "BEI-search");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicInteger SEARCH_GENERATION = new AtomicInteger();

    private static String query = "";
    private static volatile List<Cell> results = List.of();
    private static volatile int matchCount;
    private static volatile boolean dirty = true;
    private static @Nullable Category category;
    private static int scroll;
    private static boolean barDragging;
    private static int lastTotalRows;
    private static EditBox box;

    private static boolean shown;
    private static int panelX;
    private static int panelY;
    private static int panelW;
    private static int panelH;
    private static int trayX;
    private static int trayY;
    private static int gridX;
    private static int gridY;
    private static int cols;
    private static int rows;
    private static int searchY;
    private static int searchW;

    private BrowserPanel() {
    }

    // ---- layout ---------------------------------------------------------------------------------

    /** Works out where everything goes; called when the screen is set up or resized. */
    private static final com.itmecasey.bei.plugin.SearchListeners SEARCH_LISTENERS = new com.itmecasey.bei.plugin.SearchListeners();

    /** The listeners of the search text, for {@link SearchAccess}. */
    static com.itmecasey.bei.plugin.SearchListeners searchListeners() {
        return SEARCH_LISTENERS;
    }

    /** The text in the search box. */
    static String searchText() {
        return query;
    }

    /** Sets the search text from a plugin and refreshes the results; nothing happens if it is already that text. */
    static void setSearchText(String text) {
        String value = text == null ? "" : text;
        if (value.equals(query)) {
            return;
        }
        if (box != null) {
            box.setValue(value);
        } else {
            query = value;
            scroll = 0;
            dirty = true;
            SEARCH_LISTENERS.fire(value);
        }
    }

    public static void layout(AbstractContainerScreen<?> screen) {
        Font font = Minecraft.getInstance().font;
        compute(screen);
        box = new EditBox(font, panelX + 14, searchY + 5, searchW - 16, 8, Component.translatable("bei.search"));
        box.setBordered(false);
        box.setMaxLength(100);
        box.setHint(Component.translatable("bei.search.hint"));
        box.setValue(query);
        box.setResponder(text -> {
            boolean changed = !text.equals(query);
            query = text;
            scroll = 0;
            dirty = true;
            if (changed) {
                SEARCH_LISTENERS.fire(text);
            }
        });
        dirty = true;
    }

    private static int trayRowFor() {
        return BeiConfig.tagTray() ? TRAY_ROW : 0;
    }

    /** Recomputes the geometry; run every frame so a settings change shows at once. */
    private static void compute(AbstractContainerScreen<?> screen) {
        int guiRight = screen.getGuiLeft() + screen.getXSize();
        int trayRow = trayRowFor();
        int room = screen.width - guiRight - 6 - EDGE;
        int fitCols = Math.min(MAX_COLS, (room - 16) / CELL);
        int fitRows = Math.max(1, (screen.height - 2 * EDGE - 20 - trayRowFor() - 6 - 24 - 4) / CELL);
        cols = BeiConfig.customGrid() ? Math.min(BeiConfig.ingredientColumns(), fitCols) : fitCols;
        rows = BeiConfig.customGrid() ? Math.min(BeiConfig.ingredientRows(), fitRows) : fitRows;
        shown = BeiConfig.showBrowser() && cols >= 1;
        panelW = cols * CELL + 16;
        panelX = screen.width - EDGE - panelW;
        panelY = EDGE;
        trayX = panelX + 8;
        trayY = panelY + 20;
        gridX = panelX + 8;
        gridY = panelY + 20 + trayRow;
        searchY = gridY + rows * CELL + 6;
        panelH = searchY + 24 - panelY;
        searchW = panelW - 12 - 22;
    }

    // ---- searching ------------------------------------------------------------------------------

    private static void refreshResults() {
        int mine = SEARCH_GENERATION.incrementAndGet();
        String text = query;
        Category chosen = category;
        List<ItemEntry> source = ItemIndex.entries();
        SEARCHER.execute(() -> {
            Groups.Resolved resolved = Groups.resolve(source);
            List<ItemEntry> pool = source;
            if (!resolved.hidden().isEmpty()) {
                pool = new java.util.ArrayList<>(source.size());
                for (ItemEntry entry : source) {
                    if (!resolved.hidden().contains(entry.item)) {
                        pool.add(entry);
                    }
                }
            }
            List<ItemEntry> found = Search.filter(pool, text, chosen == null ? null : chosen.filter);
            List<Cell> cells = Groups.cells(found, resolved);
            if (mine == SEARCH_GENERATION.get()) {
                matchCount = found.size();
                results = cells;
            }
        });
    }

    /** Sets the search text, as if typed (used by the screenshot tour). */
    public static void search(String text) {
        query = text;
        scroll = 0;
        if (box != null) {
            box.setValue(text);
        }
        dirty = true;
    }

    /** The screen position of the center of a result cell, or null if it is not on screen (used by the screenshot tour). */
    public static int[] cellCenter(int index) {
        if (!shown || cols < 1) {
            return null;
        }
        int row = index / cols - scroll;
        if (row < 0 || row >= rows) {
            return null;
        }
        return new int[] {gridX + (index % cols) * CELL + CELL / 2, gridY + row * CELL + CELL / 2};
    }

    /** Runs the search again (the groups or hidden items changed). */
    public static void invalidate() {
        dirty = true;
    }

    // ---- drawing --------------------------------------------------------------------------------

    public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        compute(screen);
        box.setX(panelX + 14);
        box.setY(searchY + 5);
        box.setWidth(searchW - 16);
        if (!shown) {
            return;
        }
        if (dirty || (ItemIndex.ready() && results.isEmpty() && query.isEmpty() && category == null)) {
            dirty = false;
            refreshResults();
        }
        if (BeiConfig.browserBackground()) {
            Ui.panel(graphics, panelX, panelY, panelW, panelH);
        }

        List<Cell> list = results;
        String status = ItemIndex.ready() ? matchCount + " " + I18n.get("bei.items") : I18n.get("bei.loading");
        if (ItemIndex.ready() && query.contains("$") && ItemIndex.tooltipProgress() < 1F) {
            status += "  ·  " + I18n.get("bei.reading_tooltips") + " " + (int) (ItemIndex.tooltipProgress() * 100) + "%";
        }
        if (ItemIndex.ready() && RecipeIndex.count() == 0) {
            status += "  ·  " + I18n.get(ServerCheck.hasBei() ? "bei.no_recipes" : "bei.no_server_recipes");
        }
        graphics.text(font, status, panelX + 8, panelY + 8, Ui.dim(), false);

        Component trayTip = null;
        if (BeiConfig.tagTray()) {
            Category[] all = Category.values();
            for (int i = 0; i <= all.length && i < Math.max(cols, 1); i++) {
                int x = trayX + i * CELL;
                Category entry = i == 0 ? null : all[i - 1];
                boolean active = entry == category;
                boolean hover = Ui.in(mouseX, mouseY, x, trayY, 18, 18);
                if (active || hover) {
                    Ui.round(graphics, x - 1, trayY - 1, 20, 20, active ? Ui.accentSoft() : Ui.cardHover());
                }
                if (entry == null) {
                    String label = I18n.get("bei.category.all");
                    graphics.text(font, label, x + (18 - font.width(label)) / 2, trayY + 5, Ui.text(), false);
                } else {
                    graphics.item(entry.icon.getDefaultInstance(), x, trayY);
                }
                if (hover) {
                    trayTip = Component.translatable(entry == null ? "bei.category.all.tip" : entry.translationKey());
                }
            }
            graphics.fill(panelX + 6, trayY + 20, panelX + panelW - 6, trayY + 21, Ui.dim());
        }

        int totalRows = (list.size() + cols - 1) / cols;
        lastTotalRows = totalRows;
        if (barDragging) {
            if (Ui.leftDown() && totalRows > rows) {
                int barHeight = rows * CELL;
                int thumb = Math.max(10, barHeight * rows / totalRows);
                double fraction = (mouseY - gridY - thumb / 2.0) / (barHeight - thumb);
                scroll = (int) Math.round(fraction * (totalRows - rows));
            } else {
                barDragging = false;
            }
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, totalRows - rows)));
        ItemStack hover = null;
        Groups.Group hoverGroup = null;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int index = (scroll + r) * cols + c;
                if (index >= list.size()) {
                    break;
                }
                int x = gridX + c * CELL;
                int y = gridY + r * CELL;
                Cell cell = list.get(index);
                ItemStack stack = cell.isGroup() ? cell.group().icon.getDefaultInstance() : cell.item().stack;
                if (cell.isGroup()) {
                    boolean open = Groups.isExpanded(cell.group());
                    Ui.round(graphics, x, y, CELL, CELL, open ? Ui.accentSoft() : Ui.accentFaint());
                    graphics.outline(x, y, CELL, CELL, Ui.accent());
                } else if (cell.member()) {
                    Ui.round(graphics, x, y, CELL, CELL, Ui.accentFaint());
                }
                boolean over = Ui.in(mouseX, mouseY, x, y, CELL, CELL);
                if (over) {
                    Ui.round(graphics, x, y, CELL, CELL, Ui.accentSoft());
                    if (cell.isGroup()) {
                        hoverGroup = cell.group();
                    } else {
                        hover = stack;
                    }
                }
                graphics.item(stack, x + 1, y + 1);
                if (cell.isGroup()) {
                    String count = Integer.toString(cell.group().members.size());
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(x + 17 - font.width(count) * 0.6F, y + 17 - 8 * 0.6F);
                    graphics.pose().scale(0.6F, 0.6F);
                    graphics.text(font, count, 0, 0, Ui.text(), Ui.shadow());
                    graphics.pose().popMatrix();
                }
            }
        }
        if (totalRows > rows) {
            int barHeight = rows * CELL;
            int thumb = Math.max(10, barHeight * rows / totalRows);
            int thumbY = gridY + (barHeight - thumb) * scroll / (totalRows - rows);
            Ui.round(graphics, panelX + panelW - 7, gridY, 4, barHeight, Ui.field());
            boolean overBar = Ui.in(mouseX, mouseY, panelX + panelW - 11, gridY, 11, barHeight);
            Ui.round(graphics, panelX + panelW - 7, thumbY, 4, thumb, barDragging || overBar ? Ui.accent() : Ui.dim());
        }

        // Search bar and gear along the bottom.
        Ui.round(graphics, panelX + 6, searchY, searchW, 18, box.isFocused() ? Ui.accent() : Ui.field());
        Ui.round(graphics, panelX + 7, searchY + 1, searchW - 2, 16, Ui.field());
        box.extractRenderState(graphics, mouseX, mouseY, 0F);
        int gearX = panelX + panelW - 6 - 18;
        boolean overGear = Ui.in(mouseX, mouseY, gearX, searchY, 18, 18);
        int gearColor = Cheats.enabled() ? (overGear ? 0xFFE53935 : 0xFFC62828)
                : SettingsPopup.isOpen() ? Ui.accent() : overGear ? Ui.cardHover() : Ui.card();
        Ui.round(graphics, gearX, searchY, 18, 18, gearColor);
        Ui.gear(graphics, gearX + 4, searchY + 4, Ui.text());

        if (GroupTip.render(graphics, font, hoverGroup, mouseX, mouseY)) {
            // The group tooltip is drawn.
        } else if (hover != null) {
            Ui.tip(graphics, font, hover, mouseX, mouseY);
        } else if (trayTip != null) {
            Ui.tip(graphics, font, trayTip, mouseX, mouseY);
        } else if (overGear) {
            Ui.tip(graphics, font, List.of(Component.translatable("bei.settings.title"), Component.translatable("bei.tip.cheat")), mouseX, mouseY);
        } else if (Ui.in(mouseX, mouseY, panelX + 6, searchY, searchW, 18)) {
            Ui.tip(graphics, font, List.of(Component.translatable("bei.tip.search_mod"),
                    Component.translatable("bei.tip.search_tag"), Component.translatable("bei.tip.search_tooltip"),
                    Component.translatable("bei.tip.search_not")), mouseX, mouseY);
        } else if (Ui.in(mouseX, mouseY, panelX + 6, panelY + 4, panelW - 12, 14)) {
            Ui.tip(graphics, font, Component.translatable("bei.tip.status"), mouseX, mouseY);
        }
    }

    // ---- hover ----------------------------------------------------------------------------------

    /** The item under the mouse, in the grid or in a container slot. */
    public static @Nullable ItemStack hovered(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        if (shown) {
            List<Cell> list = results;
            double dx = mouseX - gridX;
            double dy = mouseY - gridY;
            if (dx >= 0 && dy >= 0 && dx < cols * CELL && dy < rows * CELL) {
                int index = (scroll + (int) (dy / CELL)) * cols + (int) (dx / CELL);
                if (index < list.size() && !list.get(index).isGroup()) {
                    return list.get(index).item().stack;
                }
            }
        }
        Slot slot = screen.getSlotUnderMouse();
        return slot != null && slot.hasItem() ? slot.getItem() : null;
    }

    private static boolean inPanel(double x, double y) {
        return shown && Ui.in(x, y, panelX, panelY, panelW, panelH);
    }

    // ---- input ----------------------------------------------------------------------------------

    public static boolean mouseClicked(AbstractContainerScreen<?> screen, MouseButtonEvent event) {
        double x = event.x();
        double y = event.y();
        if (GroupTip.isFrozen() && GroupTip.insideFrozen(x, y)) {
            ItemEntry picked = GroupTip.itemAt(x, y);
            if (picked != null) {
                itemClicked(picked.stack, event);
            }
            return true;
        }
        if (!inPanel(x, y)) {
            if (shown) {
                box.setFocused(false);
            }
            return false;
        }
        if (Ui.in(x, y, panelX + panelW - 6 - 18, searchY, 18, 18)) {
            box.setFocused(false);
            if ((event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
                Cheats.toggle();
            } else {
                SettingsPopup.toggle();
            }
            return true;
        }
        if (lastTotalRows > rows && Ui.in(x, y, panelX + panelW - 11, gridY, 11, rows * CELL)) {
            box.setFocused(false);
            barDragging = true;
            return true;
        }
        boolean inBox = Ui.in(x, y, panelX + 6, searchY, searchW, 18);
        box.setFocused(inBox);
        if (inBox) {
            if (event.button() == 1) {
                box.setValue("");
            } else {
                box.mouseClicked(event, false);
            }
            return true;
        }
        if (BeiConfig.tagTray() && y >= trayY && y < trayY + 18 && x >= trayX
                && x < trayX + Math.min(Category.values().length + 1, Math.max(cols, 1)) * CELL) {
            int index = (int) ((x - trayX) / CELL);
            category = index == 0 ? null : Category.values()[index - 1];
            scroll = 0;
            dirty = true;
            return true;
        }
        if (Ui.in(x, y, gridX, gridY, cols * CELL, rows * CELL)) {
            List<Cell> list = results;
            int index = (scroll + (int) ((y - gridY) / CELL)) * cols + (int) ((x - gridX) / CELL);
            if (index >= 0 && index < list.size()) {
                Cell cell = list.get(index);
                if (cell.isGroup()) {
                    if (event.button() == 1) {
                        Groups.toggleExpanded(cell.group());
                        dirty = true;
                    }
                } else {
                    itemClicked(cell.item().stack, event);
                }
            }
        }
        return true;
    }

    /** A click on an item: cheat mode gives it, otherwise recipes (left) or uses (right). */
    private static void itemClicked(ItemStack stack, MouseButtonEvent event) {
        if (Cheats.enabled() && event.button() == 0) {
            Cheats.give(stack.getItem(), Minecraft.getInstance().hasShiftDown());
        } else if (event.button() == 0 || event.button() == 1) {
            RecipePopup.open(stack.getItem(), event.button() == 1);
        }
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double deltaY) {
        if (!inPanel(mouseX, mouseY)) {
            return false;
        }
        scroll -= (int) Math.signum(deltaY) * (Minecraft.getInstance().hasShiftDown() ? rows : 2);
        return true;
    }

    public static boolean keyPressed(AbstractContainerScreen<?> screen, KeyEvent event) {
        if (shown && box.isFocused()) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                box.setFocused(false);
                return true;
            }
            box.keyPressed(event);
            return true;
        }
        if (shown && event.key() == GLFW.GLFW_KEY_SLASH) {
            box.setFocused(true);
            return true;
        }
        Minecraft game = Minecraft.getInstance();
        ItemStack stack = hovered(screen, game.mouseHandler.getScaledXPos(game.getWindow()),
                game.mouseHandler.getScaledYPos(game.getWindow()));
        if (stack == null) {
            return false;
        }
        if (ClientInit.SHOW_RECIPES.matches(event)) {
            RecipePopup.open(stack.getItem(), false);
            return true;
        }
        if (ClientInit.SHOW_USES.matches(event)) {
            RecipePopup.open(stack.getItem(), true);
            return true;
        }
        if (ClientInit.SET_PROJECT.matches(event)) {
            RecipePopup.toggleProject(stack.getItem());
            return true;
        }
        return false;
    }

    public static boolean charTyped(CharacterEvent event) {
        if (shown && box.isFocused()) {
            box.charTyped(event);
            return true;
        }
        return false;
    }
}
