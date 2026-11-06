package com.itmecasey.bei.client;

import com.itmecasey.bei.BeiConfig;
import com.itmecasey.bei.index.BeiRecipe;
import com.itmecasey.bei.plugin.TransferDispatch;
import com.itmecasey.bei.plugin.TransferHost;
import com.itmecasey.bei.index.RecipeIndex;
import com.itmecasey.bei.project.InventoryScanner;
import com.itmecasey.bei.project.Project;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.itmecasey.bei.client.Draw;
import com.itmecasey.bei.client.input.KeyEvent;
import com.itmecasey.bei.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A slim popup over the inventory screen listing the recipes that make an item (or use it), like
 * JEI's. Recipes are grouped by the station they are made in, one button per station, the ones
 * you can make right now first. Drag the title bar to move it, click an item inside to follow it.
 */
public final class RecipePopup {
    private static final int SLOT = 18;
    private static final int CARD_GAP = 4;
    private static final int STATION = 20;

    private record Entry(Item item, boolean uses) {
    }

    private record Hit(int x, int y, ItemStack stack) {
    }

    private record Button(int x, int y, BeiRecipe recipe, boolean handler) {
        Button(int x, int y, BeiRecipe recipe) {
            this(x, y, recipe, false);
        }
    }

    /** A dimmed plus button of a transfer handler, with why it cannot be used. */
    private record Dimmed(int x, int y, Component reason) {
    }

    private record StationButton(int x, int y, Station station) {
    }

    /** The recipes made at one station. {@code craftable} means at least one can be made from what you hold. */
    private record Station(String key, ItemStack icon, String label, List<BeiRecipe> recipes, boolean craftable) {
    }

    private static final Deque<Entry> HISTORY = new ArrayDeque<>();
    private static final List<Hit> HITS = new ArrayList<>();
    private static final List<Button> PLUS = new ArrayList<>();
    private static final List<Dimmed> DIMMED = new ArrayList<>();
    private static final List<Button> PROJECT = new ArrayList<>();
    private static final List<StationButton> STATIONS = new ArrayList<>();
    /** The area each custom card painted this frame, for the painter's tooltips. */
    private record CustomArea(int x, int y, BeiRecipe recipe) {
    }

    private static final List<CustomArea> CUSTOM = new ArrayList<>();
    private static int frameMouseX;
    private static int frameMouseY;
    private static final WindowPos POS = new WindowPos("recipe");
    private static @Nullable String selectedKey;
    private static int scroll;
    private static boolean barDragging;
    private static int contentTotal;
    private static int headerH;
    private static int bodyTop;
    private static int bodyBottom;
    private static int pw;
    private static int ph;
    private static boolean backShown;

    private RecipePopup() {
    }

    public static boolean isOpen() {
        return !HISTORY.isEmpty();
    }

    public static void open(Item item, boolean uses) {
        SettingsPopup.close();
        HISTORY.push(new Entry(item, uses));
        selectedKey = null;
        scroll = 0;
    }

    public static void close() {
        barDragging = false;
        HISTORY.clear();
        selectedKey = null;
        scroll = 0;
    }

    private static void back() {
        HISTORY.pop();
        selectedKey = null;
        scroll = 0;
    }

    // ---- grouping by station ----------------------------------------------------------------------

    private static Entry cachedFor;
    private static List<Station> cached = List.of();
    private static long cachedAt;

    private static List<BeiRecipe> recipes(Entry entry) {
        return entry.uses() ? RecipeIndex.using(entry.item()) : RecipeIndex.producing(entry.item());
    }

    /** The stations for this item, those with something you can make first; redone about once a second. */
    private static List<Station> stations(Entry entry) {
        long now = System.currentTimeMillis();
        if (entry.equals(cachedFor) && now - cachedAt < 1000) {
            return cached;
        }
        Minecraft game = Minecraft.getInstance();
        Map<Item, Integer> owned = game.player == null ? Map.of() : InventoryScanner.snapshot(game.player);
        Map<String, List<BeiRecipe>> buildable = new LinkedHashMap<>();
        Map<String, List<BeiRecipe>> rest = new LinkedHashMap<>();
        Map<String, ItemStack> icons = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        for (BeiRecipe recipe : recipes(entry)) {
            String key = stationKey(recipe);
            if (!icons.containsKey(key)) {
                ItemStack icon = recipe.stations().isEmpty() ? new ItemStack(Items.BOOK) : recipe.stations().get(0);
                icons.put(key, icon);
                labels.put(key, recipe.category() != null || recipe.stations().isEmpty() ? labelOf(recipe) : icon.getHoverName().getString());
            }
            (canBuild(recipe, owned) ? buildable : rest).computeIfAbsent(key, k -> new ArrayList<>()).add(recipe);
        }
        List<Station> out = new ArrayList<>();
        for (String key : icons.keySet()) {
            List<BeiRecipe> mine = new ArrayList<>(buildable.getOrDefault(key, List.of()));
            boolean craftable = !mine.isEmpty();
            mine.addAll(rest.getOrDefault(key, List.of()));
            out.add(new Station(key, icons.get(key), labels.get(key), mine, craftable));
        }
        out.sort(Comparator.comparing((Station s) -> !s.craftable()).thenComparing((Station s) -> -s.recipes().size())
                .thenComparing(Station::label));
        cachedFor = entry;
        cached = out;
        cachedAt = now;
        return out;
    }

    private static String stationKey(BeiRecipe recipe) {
        if (recipe.category() != null) {
            return "cat:" + recipe.category().id();
        }
        return recipe.stations().isEmpty() ? "type:" + recipe.type()
                : BuiltInRegistries.ITEM.getKey(recipe.stations().get(0).getItem()).toString();
    }

    private static String labelOf(BeiRecipe recipe) {
        if (recipe.category() != null) {
            return recipe.category().title().getString();
        }
        String key = "bei.recipe_type." + recipe.type().toLowerCase(Locale.ROOT);
        return I18n.exists(key) ? I18n.get(key) : recipe.type();
    }

    private static @Nullable Station selected(List<Station> stations) {
        for (Station station : stations) {
            if (station.key().equals(selectedKey)) {
                return station;
            }
        }
        return stations.isEmpty() ? null : stations.get(0);
    }

    /** Whether the owned items cover every ingredient slot, each item counted once. */
    private static boolean canBuild(BeiRecipe recipe, Map<Item, Integer> owned) {
        Map<Item, Integer> left = new HashMap<>(owned);
        boolean any = false;
        for (List<ItemStack> options : recipe.inputs()) {
            if (options.isEmpty()) {
                continue;
            }
            any = true;
            boolean found = false;
            for (ItemStack option : options) {
                if (left.getOrDefault(option.getItem(), 0) > 0) {
                    left.merge(option.getItem(), -1, Integer::sum);
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return any;
    }

    // ---- card geometry ----------------------------------------------------------------------------

    private static int gridWidth(BeiRecipe recipe) {
        int count = Math.max(1, recipe.inputs().size());
        if (recipe.isCrafting()) {
            return 3;
        }
        if (BeiConfig.customGrid()) {
            int columns = BeiConfig.ingredientColumns();
            int rows = BeiConfig.ingredientRows();
            if (columns * rows < count) {
                columns = (count + rows - 1) / rows;
            }
            return Math.max(1, Math.min(columns, count));
        }
        int w = recipe.width() > 0 ? recipe.width() : count;
        return Math.max(1, w);
    }

    private static int gridRows(BeiRecipe recipe) {
        if (recipe.isCrafting()) {
            return 3;
        }
        return Math.max(1, (recipe.inputs().size() + gridWidth(recipe) - 1) / gridWidth(recipe));
    }

    private static int cardHeight(BeiRecipe recipe) {
        if (recipe.category() != null) {
            return 15 + recipe.category().height() + 4;
        }
        return 15 + gridRows(recipe) * SLOT + 4;
    }

    /** Width of the grid, arrow and outputs together, as drawn. */
    private static int contentWidth(BeiRecipe recipe) {
        return gridWidth(recipe) * SLOT + 20 + Math.min(recipe.outputs().size(), 4) * SLOT;
    }

    private static int cardWidth(BeiRecipe recipe) {
        if (recipe.category() != null) {
            return recipe.category().width() + 2 * 12;
        }
        return contentWidth(recipe) + 2 * 24;
    }

    // ---- drawing --------------------------------------------------------------------------------

    public static void render(Draw g, int screenW, int screenH, int mx, int my) {
        Entry entry = HISTORY.peek();
        if (entry == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        List<Station> stations = stations(entry);
        Station chosen = selected(stations);
        List<BeiRecipe> list = chosen == null ? List.of() : chosen.recipes();
        int widest = 200;
        for (BeiRecipe recipe : recipes(entry)) {
            widest = Math.max(widest, cardWidth(recipe) + 12);
        }
        int total = 0;
        for (BeiRecipe recipe : list) {
            total += cardHeight(recipe) + CARD_GAP;
        }
        pw = Math.min(screenW - 16, widest);
        backShown = HISTORY.size() > 1;
        int perRow = Math.max(1, (pw - 12 - (backShown ? 20 : 0)) / (STATION + 2));
        int stationRows = stations.isEmpty() ? 0 : (stations.size() + perRow - 1) / perRow;
        headerH = 26 + stationRows * (STATION + 2) + 4;
        ph = Math.min(Math.min(screenH - 16, 300), Math.max(70, headerH + total + 8));
        POS.update(mx, my, screenW, screenH, pw, ph);
        int px = POS.x();
        int py = POS.y();

        g.nextStratum();
        g.fill(0, 0, screenW, screenH, 0x70000000);
        Ui.panel(g, px, py, pw, ph);
        String tip = null;
        List<Component> tipLines = null;
        if (Ui.in(mx, my, px, py, pw - 24, 22)) {
            tip = "bei.tip.drag";
        }

        ItemStack icon = entry.item().getDefaultInstance();
        g.item(icon, px + 5, py + 4);
        String name = (entry.uses() ? I18n.get("bei.popup.uses") + ": " : "") + icon.getHoverName().getString();
        int nameMax = pw - 24 - 26;
        while (font.width(name) > nameMax && name.length() > 3) {
            name = name.substring(0, name.length() - 2);
        }
        g.text(font, name, px + 25, py + 8, Ui.text(), Ui.shadow());
        boolean overClose = Ui.in(mx, my, px + pw - 18, py + 5, 12, 12);
        Ui.round(g, px + pw - 18, py + 5, 12, 12, overClose ? Ui.bad() : Ui.card());
        g.text(font, "×", px + pw - 15, py + 7, Ui.text(), false);
        if (overClose) {
            tip = "bei.tip.close";
        }

        // Back button and one button per station.
        STATIONS.clear();
        int rowY = py + 26;
        int firstX = px + 6;
        if (backShown) {
            boolean over = Ui.in(mx, my, firstX, rowY, 16, STATION);
            Ui.button(g, font, firstX, rowY, 16, STATION, "‹", over, false);
            if (over) {
                tip = "bei.tip.back";
            }
            firstX += 20;
        }
        for (int i = 0; i < stations.size(); i++) {
            Station station = stations.get(i);
            int x = firstX + (i % perRow) * (STATION + 2);
            int y = rowY + (i / perRow) * (STATION + 2);
            boolean active = station == chosen;
            boolean over = Ui.in(mx, my, x, y, STATION, STATION);
            Ui.round(g, x, y, STATION, STATION, active ? Ui.accentSoft() : over ? Ui.cardHover() : Ui.card());
            if (active) {
                g.outline(x, y, STATION, STATION, Ui.accent());
            }
            g.item(station.icon(), x + 2, y + 2);
            if (station.craftable()) {
                Ui.round(g, x + STATION - 5, y + 1, 4, 4, Ui.good());
            }
            STATIONS.add(new StationButton(x, y, station));
            if (over) {
                tipLines = new ArrayList<>();
                tipLines.add(Component.literal(station.label()));
                tipLines.add(Component.translatable("bei.station.count", station.recipes().size()));
                if (station.craftable()) {
                    tipLines.add(Component.translatable("bei.station.craftable").withStyle(net.minecraft.ChatFormatting.GREEN));
                }
            }
        }

        bodyTop = py + headerH;
        bodyBottom = py + ph - 6;
        HITS.clear();
        PLUS.clear();
        DIMMED.clear();
        PROJECT.clear();
        CUSTOM.clear();
        frameMouseX = mx;
        frameMouseY = my;
        if (list.isEmpty()) {
            String none = I18n.get(RecipeIndex.count() == 0 ? (ServerCheck.hasBei() ? "bei.recipes.loading" : "bei.recipes.no_server") : "bei.recipes.none");
            g.text(font, none, px + 8, bodyTop + 4, Ui.dim(), false);
        } else {
            contentTotal = total;
            int viewport = bodyBottom - bodyTop;
            if (barDragging) {
                if (Ui.leftDown() && total > viewport) {
                    int thumb = Math.max(10, viewport * viewport / total);
                    scroll = (int) ((my - bodyTop - thumb / 2.0) / (viewport - thumb) * (total - viewport));
                } else {
                    barDragging = false;
                }
            }
            scroll = Math.max(0, Math.min(scroll, Math.max(0, total - viewport)));
            g.enableScissor(px, bodyTop, px + pw, bodyBottom);
            long second = System.currentTimeMillis() / 1000;
            int y = bodyTop - scroll;
            for (BeiRecipe recipe : list) {
                int h = cardHeight(recipe);
                if (y + h >= bodyTop && y <= bodyBottom) {
                    drawCard(g, font, recipe, px + 6, y, pw - 12 - (total > viewport ? 8 : 0), h, second);
                }
                y += h + CARD_GAP;
            }
            g.disableScissor();
            if (total > viewport) {
                int thumb = Math.max(10, viewport * viewport / total);
                int thumbY = bodyTop + (viewport - thumb) * scroll / (total - viewport);
                Ui.round(g, px + pw - 6, bodyTop, 4, viewport, Ui.field());
                boolean overBar = Ui.in(mx, my, px + pw - 10, bodyTop, 10, viewport);
                Ui.round(g, px + pw - 6, thumbY, 4, thumb, barDragging || overBar ? Ui.accent() : Ui.dim());
            }
        }
        if (Ui.in(mx, my, px, bodyTop, pw, bodyBottom - bodyTop)) {
            for (CustomArea area : CUSTOM) {
                var painter = area.recipe().category().painter();
                if (painter != null && tipLines == null) {
                    try {
                        tipLines = painter.tooltip(area.recipe().custom(), mx - area.x(), my - area.y());
                    } catch (RuntimeException e) {
                        // A broken painter only loses its tooltip.
                    }
                }
            }
            for (Button plus : PLUS) {
                if (Ui.in(mx, my, plus.x(), plus.y(), 12, 12)) {
                    tip = plus.handler() ? "bei.tip.transfer" : "bei.tip.autofill";
                }
            }
            for (Dimmed dimmed : DIMMED) {
                if (Ui.in(mx, my, dimmed.x(), dimmed.y(), 12, 12) && tipLines == null) {
                    tipLines = List.of(Component.translatable("bei.tip.transfer_unavailable"), dimmed.reason());
                }
            }
            for (Button project : PROJECT) {
                if (Ui.in(mx, my, project.x(), project.y(), 12, 12)) {
                    tip = isProjectRecipe(project.recipe()) ? "bei.tip.project_clear" : "bei.tip.project_recipe";
                }
            }
            for (Hit hit : HITS) {
                if (Ui.in(mx, my, hit.x(), hit.y(), 16, 16)) {
                    Ui.tip(g, font, hit.stack(), mx, my);
                    return;
                }
            }
        }
        if (tipLines != null) {
            Ui.tip(g, font, tipLines, mx, my);
        } else if (tip != null) {
            Ui.tip(g, font, Component.translatable(tip), mx, my);
        }
    }

    private static boolean isProjectRecipe(BeiRecipe recipe) {
        return !recipe.outputs().isEmpty() && Project.item() == recipe.outputs().get(0).getItem()
                && recipe.id().equals(Project.recipeId());
    }

    private static void drawCard(Draw g, Font font, BeiRecipe recipe, int x, int y, int w, int h, long second) {
        if (BeiConfig.cardBackground()) {
            Ui.round(g, x, y, w, h, Ui.card());
        }
        g.text(font, labelOf(recipe), x + 5, y + 4, Ui.dim(), false);
        if (recipe.category() != null) {
            drawCustom(g, font, recipe, x, y, w, second);
            cardButtons(g, font, recipe, x, y, w, h);
            return;
        }
        int gw = gridWidth(recipe);
        int rows = gridRows(recipe);
        int gy = y + 15;
        // The grid, arrow and outputs are centered in the card as one piece.
        int startX = x + (w - contentWidth(recipe)) / 2;
        for (int cell = 0; cell < gw * rows; cell++) {
            slotBox(g, startX + (cell % gw) * SLOT, gy + (cell / gw) * SLOT);
        }
        for (int slot = 0; slot < recipe.inputs().size(); slot++) {
            List<ItemStack> options = recipe.inputs().get(slot);
            int cell = recipe.isCrafting() && recipe.width() > 0 ? (slot / recipe.width()) * 3 + slot % recipe.width() : slot;
            if (!options.isEmpty() && cell < gw * rows) {
                item(g, font, options.get((int) (second % options.size())), startX + (cell % gw) * SLOT, gy + (cell / gw) * SLOT);
            }
        }
        int outY = gy + (rows - 1) * SLOT / 2;
        int arrowX = startX + gw * SLOT + 6;
        g.text(font, "→", arrowX, outY + 5, Ui.accent(), false);
        int outX = arrowX + 14;
        int shown = 0;
        for (ItemStack output : recipe.outputs()) {
            if (shown++ == 4) {
                break;
            }
            slotBox(g, outX, outY);
            item(g, font, output, outX, outY);
            outX += SLOT;
        }
        cardButtons(g, font, recipe, x, y, w, h);
    }

    /** A card whose layout and extras come from a plugin's category. */
    private static void drawCustom(Draw g, Font font, BeiRecipe recipe, int x, int y, int w, long second) {
        var category = recipe.category();
        int ox = x + (w - category.width()) / 2;
        int oy = y + 15;
        var painter = category.painter();
        if (painter != null) {
            g.pose().pushMatrix();
            g.pose().translate(ox, oy);
            try {
                painter.paint(g.graphics(), recipe.custom(), frameMouseX - ox, frameMouseY - oy);
            } catch (RuntimeException e) {
                // A broken painter only loses its extras.
            }
            g.pose().popMatrix();
        }
        CUSTOM.add(new CustomArea(ox, oy, recipe));
        int inputIndex = 0;
        int outputIndex = 0;
        for (var slot : category.slots()) {
            int sx = ox + slot.x();
            int sy = oy + slot.y();
            slotBox(g, sx, sy);
            if (slot.output()) {
                if (outputIndex < recipe.outputs().size()) {
                    item(g, font, recipe.outputs().get(outputIndex), sx, sy);
                }
                outputIndex++;
            } else {
                if (inputIndex < recipe.inputs().size() && !recipe.inputs().get(inputIndex).isEmpty()) {
                    List<ItemStack> options = recipe.inputs().get(inputIndex);
                    item(g, font, options.get((int) (second % options.size())), sx, sy);
                }
                inputIndex++;
            }
        }
    }

    /** P (Master Project) over + (auto-fill), bottom right of a card. */
    private static void cardButtons(Draw g, Font font, BeiRecipe recipe, int x, int y, int w, int h) {
        if (com.itmecasey.bei.compat.ModJsCompat.isInfo(recipe)) {
            return;
        }
        int bx = x + w - 15;
        int plusY = y + h - 15;
        int projectY = plusY - 14;
        boolean active = isProjectRecipe(recipe);
        Ui.round(g, bx, projectY, 12, 12, active ? Ui.accent() : Ui.cardHover());
        g.text(font, "P", bx + 3, projectY + 2, active ? Ui.onAccent() : Ui.text(), false);
        PROJECT.add(new Button(bx, projectY, recipe));
        var menu = AutoFill.menu();
        boolean builtIn = AutoFill.canFill(recipe);
        TransferHost.View view = menu == null
                ? new TransferHost.View(builtIn ? TransferDispatch.Mode.BUILT_IN : TransferDispatch.Mode.HIDDEN, null)
                : TransferHost.view(menu, recipe, builtIn, net.minecraft.client.gui.screens.Screen.hasShiftDown(), net.minecraft.client.gui.screens.Screen.hasControlDown());
        switch (view.mode()) {
            case HANDLER, BUILT_IN -> {
                PLUS.add(new Button(bx, plusY, recipe, view.mode() == TransferDispatch.Mode.HANDLER));
                Ui.round(g, bx, plusY, 12, 12, Ui.accent());
                g.text(font, "+", bx + 3, plusY + 2, Ui.onAccent(), false);
            }
            case DISABLED -> {
                DIMMED.add(new Dimmed(bx, plusY, view.reason() != null ? view.reason() : Component.translatable("bei.tip.transfer_unavailable")));
                Ui.round(g, bx, plusY, 12, 12, Ui.cardHover());
                g.text(font, "+", bx + 3, plusY + 2, Ui.dim(), false);
            }
            default -> {
            }
        }
    }

    private static void slotBox(Draw g, int x, int y) {
        if (BeiConfig.slotBackground()) {
            Ui.round(g, x - 1, y - 1, 18, 18, Ui.field());
        }
        g.outline(x - 1, y - 1, 18, 18, Ui.border());
    }

    private static void item(Draw g, Font font, ItemStack stack, int x, int y) {
        g.item(stack, x, y);
        g.itemDecorations(font, stack, x, y);
        HITS.add(new Hit(x, y, stack));
    }

    // ---- input ----------------------------------------------------------------------------------

    private static @Nullable ItemStack hovered(double mx, double my) {
        if (!Ui.in(mx, my, POS.x(), bodyTop, pw, bodyBottom - bodyTop)) {
            return null;
        }
        for (Hit hit : HITS) {
            if (Ui.in(mx, my, hit.x(), hit.y(), 16, 16)) {
                return hit.stack();
            }
        }
        return null;
    }

    /** Handles a click; the popup swallows every click while it is open. */
    public static boolean mouseClicked(MouseButtonEvent event) {
        double mx = event.x();
        double my = event.y();
        if (HISTORY.isEmpty()) {
            return false;
        }
        int px = POS.x();
        int py = POS.y();
        if (!Ui.in(mx, my, px, py, pw, ph) || Ui.in(mx, my, px + pw - 18, py + 5, 12, 12)) {
            close();
            return true;
        }
        if (backShown && Ui.in(mx, my, px + 6, py + 26, 16, STATION)) {
            back();
            return true;
        }
        for (StationButton button : STATIONS) {
            if (Ui.in(mx, my, button.x(), button.y(), STATION, STATION)) {
                selectedKey = button.station().key();
                scroll = 0;
                return true;
            }
        }
        if (Ui.in(mx, my, px, py, pw - 24, 22)) {
            POS.startDrag(mx, my);
            return true;
        }
        if (contentTotal > bodyBottom - bodyTop && Ui.in(mx, my, px + pw - 10, bodyTop, 10, bodyBottom - bodyTop)) {
            barDragging = true;
            return true;
        }
        if (Ui.in(mx, my, px, bodyTop, pw, bodyBottom - bodyTop)) {
            for (Button plus : PLUS) {
                if (Ui.in(mx, my, plus.x(), plus.y(), 12, 12)) {
                    if (clickPlus(plus, net.minecraft.client.gui.screens.Screen.hasShiftDown(), net.minecraft.client.gui.screens.Screen.hasControlDown())) {
                        close();
                    }
                    return true;
                }
            }
            for (Button project : PROJECT) {
                if (Ui.in(mx, my, project.x(), project.y(), 12, 12)) {
                    toggleRecipeProject(project.recipe());
                    return true;
                }
            }
        }
        ItemStack stack = hovered(mx, my);
        if (stack != null && (event.button() == 0 || event.button() == 1)) {
            open(stack.getItem(), event.button() == 1);
        }
        return true;
    }

    /** Clicks a plus button: a plugin's transfer handler when it answers for the open menu, otherwise BEI's own fill. */
    private static boolean clickPlus(Button plus, boolean shift, boolean control) {
        var menu = AutoFill.menu();
        if (menu != null && plus.handler()) {
            return TransferHost.transfer(menu, plus.recipe(), shift, control);
        }
        return AutoFill.fill(plus.recipe(), shift);
    }

    /** The P button: make this recipe the Master Project (again to clear). */
    private static void toggleRecipeProject(BeiRecipe recipe) {
        if (recipe.outputs().isEmpty()) {
            return;
        }
        Item product = recipe.outputs().get(0).getItem();
        if (isProjectRecipe(recipe)) {
            Project.clear();
        } else {
            Project.setRecipe(product, recipe.id());
            Project.refresh();
        }
        Minecraft game = Minecraft.getInstance();
        if (game.player != null) {
            game.gui.setOverlayMessage(Project.item() == null
                    ? Component.translatable("bei.project.cleared")
                    : Component.translatable("bei.project.set", product.getDefaultInstance().getHoverName()), false);
        }
    }

    public static boolean mouseScrolled(double deltaY) {
        if (!isOpen()) {
            return false;
        }
        scroll -= (int) Math.signum(deltaY) * 20;
        return true;
    }

    public static boolean keyPressed(KeyEvent event, double mx, double my) {
        if (!isOpen()) {
            return false;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (HISTORY.size() > 1) {
                back();
            } else {
                close();
            }
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (HISTORY.size() > 1) {
                back();
            }
            return true;
        }
        ItemStack stack = hovered(mx, my);
        if (stack != null) {
            if (ClientInit.SHOW_RECIPES.matches(event.key(), event.scancode())) {
                open(stack.getItem(), false);
            } else if (ClientInit.SHOW_USES.matches(event.key(), event.scancode())) {
                open(stack.getItem(), true);
            } else if (ClientInit.SET_PROJECT.matches(event.key(), event.scancode())) {
                toggleProject(stack.getItem());
            }
        }
        return true;
    }

    static void toggleProject(Item item) {
        Project.toggle(item);
        Project.refresh();
        Minecraft game = Minecraft.getInstance();
        if (game.player != null) {
            game.gui.setOverlayMessage(Project.item() == null
                    ? Component.translatable("bei.project.cleared")
                    : Component.translatable("bei.project.set", item.getDefaultInstance().getHoverName()), false);
        }
    }
}
