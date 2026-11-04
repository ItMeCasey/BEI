package com.itmecasey.bei.client;

import com.itmecasey.bei.Bei;
import com.itmecasey.bei.BeiConfig;
import com.itmecasey.bei.project.Plan;
import com.itmecasey.bei.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * The pinned project readout, always open: {@code Project: Jetpack (34%)} and under it the current crafting phase: what to craft next with what is in hand, or what to
 * gather first.
 */
@EventBusSubscriber(modid = Bei.MODID, value = Dist.CLIENT)
public final class ProjectHud {
    private static final Identifier LAYER = Bei.id("project");
    private static final int MARGIN = 6;
    private static final int PAD = 4;
    private static final int LINE = 10;
    
    
    

    /** One drawn line: its text and color. */
    private record Line(Component text, int color) {
    }

    private ProjectHud() {
    }

    @SubscribeEvent
    public static void addLayer(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, ProjectHud::extract);
    }

    private static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft game = Minecraft.getInstance();
        Item item = Project.item();
        if (item == null || !BeiConfig.hudVisible() || game.level == null || game.options.hideGui || game.screen != null) {
            return;
        }
        Plan plan = Project.plan();
        int percent = plan == null ? 0 : (int) Math.round(plan.fraction() * 100);
        String name = item.getDefaultInstance().getHoverName().getString();
        String count = Project.count() > 1 ? " x" + Project.count() : "";
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(Component.literal(Component.translatable("bei.hud.project").getString() + ": " + name + count + " (" + percent + "%)"), Ui.text()));
        phase(lines, plan);
        Font font = game.font;
        int width = 0;
        for (Line line : lines) {
            width = Math.max(width, font.width(line.text()));
        }
        width += 2 * PAD;
        int height = lines.size() * LINE + 2 * PAD;
        int x = switch (BeiConfig.hudCorner()) {
            case TOP_LEFT, BOTTOM_LEFT -> MARGIN;
            case TOP_RIGHT, BOTTOM_RIGHT -> graphics.guiWidth() - width - MARGIN;
        };
        int y = switch (BeiConfig.hudCorner()) {
            case TOP_LEFT, TOP_RIGHT -> MARGIN;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> graphics.guiHeight() - height - MARGIN - 24;
        };
        graphics.fill(x, y, x + width, y + height, Ui.bg());
        graphics.fill(x, y, x + 2, y + height, 0xFF4F9DFF);
        int barWidth = (int) ((width - 2) * (percent / 100.0));
        graphics.fill(x + 2, y + height - 2, x + 2 + barWidth, y + height, Ui.good());
        int ty = y + PAD;
        for (Line line : lines) {
            graphics.text(font, line.text(), x + PAD + 2, ty, line.color(), Ui.shadow());
            ty += LINE;
        }
    }

    private static void phase(List<Line> lines, Plan plan) {
        if (plan == null) {
            lines.add(new Line(Component.translatable("bei.hud.loading"), Ui.dim()));
            return;
        }
        if (plan.complete()) {
            lines.add(new Line(Component.translatable("bei.hud.complete"), Ui.good()));
            return;
        }
        Plan.Step next = plan.next();
        if (next != null) {
            lines.add(new Line(Component.translatable("bei.hud.craft", next.produces(), name(next.item())), Ui.warn()));
            for (Plan.Need need : next.needs()) {
                lines.add(new Line(Component.literal("  " + need.have() + "/" + need.need() + " " + name(need.item())),
                        need.met() ? Ui.good() : Ui.bad()));
            }
        } else {
            lines.add(new Line(Component.translatable("bei.hud.gather"), Ui.warn()));
            int shown = 0;
            for (Map.Entry<Item, Integer> missing : plan.missing.entrySet()) {
                if (shown++ == 6) {
                    lines.add(new Line(Component.literal("  ..."), Ui.dim()));
                    break;
                }
                lines.add(new Line(Component.literal("  " + missing.getValue() + "x " + name(missing.getKey())), Ui.bad()));
            }
        }
        int later = 0;
        for (Plan.Step step : plan.steps) {
            if (step != next && later++ < 3) {
                lines.add(new Line(Component.translatable("bei.hud.later", step.produces(), name(step.item())), Ui.dim()));
            }
        }
    }

    private static String name(Item item) {
        return item.getDefaultInstance().getHoverName().getString();
    }
}
