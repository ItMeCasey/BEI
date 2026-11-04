package com.itmecasey.bei.client;

import com.itmecasey.bei.project.Plan;
import com.itmecasey.bei.project.Project;
import com.itmecasey.bei.client.Draw;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The whole Master Project tree: every item down to the raw materials, indented, with how many
 * are needed, how many are in hand, and how many crafts are left. Drawn with the same panels as
 * the rest of BEI, so the opacity setting applies.
 */
public final class ProjectScreen extends Screen {
    private static final int ROW = 18;
    private static final int[] DELTAS = {-10, -1, 1, 10};

    private final @Nullable Screen parent;
    private int scroll;
    private int px;
    private int py;
    private int pw;
    private int ph;

    public ProjectScreen(@Nullable Screen parent) {
        super(Component.translatable("bei.project.title"));
        this.parent = parent;
    }

    private int listTop() {
        return py + 44;
    }

    private int listBottom() {
        return py + ph - 28;
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mx, int my, float partialTick) {
        super.render(graphics, mx, my, partialTick);
        Draw g = new Draw(graphics);
        pw = Math.min(width - 16, 320);
        ph = height - 24;
        px = (width - pw) / 2;
        py = 12;
        Ui.panel(g, px, py, pw, ph);
        Plan plan = Project.plan();
        if (Project.item() == null || plan == null) {
            g.text(font, I18n.get("bei.project.none"), px + 10, py + 12, Ui.dim(), false);
            drawDone(g, mx, my);
            return;
        }
        String name = Project.item().getDefaultInstance().getHoverName().getString();
        g.text(font, name + " x" + Project.count() + "  ·  " + Math.round(plan.fraction() * 100) + "%", px + 10, py + 9, Ui.text(), Ui.shadow());
        for (int i = 0; i < DELTAS.length; i++) {
            int bx = px + 10 + i * 34;
            String label = (DELTAS[i] > 0 ? "+" : "") + DELTAS[i];
            Ui.button(g, font, bx, py + 24, 31, 14, label, Ui.in(mx, my, bx, py + 24, 31, 14), false);
        }
        String clear = I18n.get("bei.project.clear");
        int clearW = font.width(clear) + 14;
        Ui.button(g, font, px + pw - 10 - clearW, py + 24, clearW, 14, clear, Ui.in(mx, my, px + pw - 10 - clearW, py + 24, clearW, 14), false);

        int visible = Math.max(1, (listBottom() - listTop()) / ROW);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, plan.rows.size() - visible)));
        ItemStack hover = null;
        for (int i = 0; i < visible && scroll + i < plan.rows.size(); i++) {
            Plan.Row row = plan.rows.get(scroll + i);
            int y = listTop() + i * ROW;
            int x = px + 10 + row.depth() * 12;
            ItemStack stack = row.item().getDefaultInstance();
            boolean done = row.have() >= row.need();
            g.item(stack, x, y);
            g.text(font, row.need() + "x " + stack.getHoverName().getString(), x + 20, y + 5, done ? Ui.good() : Ui.text(), false);
            String right = row.have() + "/" + row.need() + (row.crafts() > 0 ? "  craft " + row.crafts() : "");
            g.text(font, right, px + pw - 10 - font.width(right), y + 5, done ? Ui.good() : Ui.bad(), false);
            if (Ui.in(mx, my, x, y, 16, 16)) {
                hover = stack;
            }
        }
        drawDone(g, mx, my);
        if (hover != null) {
            Ui.tip(g, font, hover, mx, my);
        }
    }

    private void drawDone(Draw g, int mx, int my) {
        int x = px + pw / 2 - 40;
        int y = py + ph - 22;
        Ui.button(g, font, x, y, 80, 16, I18n.get("gui.done"), Ui.in(mx, my, x, y, 80, 16), false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (Ui.in(mx, my, px + pw / 2 - 40, py + ph - 22, 80, 16)) {
            onClose();
            return true;
        }
        if (Project.item() != null) {
            for (int i = 0; i < DELTAS.length; i++) {
                if (Ui.in(mx, my, px + 10 + i * 34, py + 24, 31, 14)) {
                    Project.setCount(Project.count() + DELTAS[i]);
                    Project.refresh();
                    return true;
                }
            }
            int clearW = font.width(I18n.get("bei.project.clear")) + 14;
            if (Ui.in(mx, my, px + pw - 10 - clearW, py + 24, clearW, 14)) {
                Project.clear();
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        scroll -= (int) Math.signum(scrollY) * 2;
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
