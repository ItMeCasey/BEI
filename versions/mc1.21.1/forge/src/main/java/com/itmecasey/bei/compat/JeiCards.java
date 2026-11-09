package com.itmecasey.bei.compat;

import com.itmecasey.bei.Bei;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The interactive and drawn parts a JEI category can put on its card through {@code IRecipeExtrasBuilder}: drawables,
 * text, buttons, cycle buttons, scroll boxes and grids, tooltip areas, and plugin-made widgets, input handlers and
 * GUI event listeners. Each becomes a {@link JeiBridge.Drawer} on the BEI card; BEI forwards the mouse to them.
 */
final class JeiCards {
    private JeiCards() {
    }

    // ---- small reflection helpers -----------------------------------------------------------------

    static @Nullable Object call(@Nullable Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        Method method = JeiTransfers.find(target.getClass(), name, args.length);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static int number(@Nullable Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    /** x, y, width, height of a ScreenRectangle or Rect2i-like object. */
    static int @Nullable [] rectangle(@Nullable Object rect) {
        if (rect == null) {
            return null;
        }
        String[][] names = {{"x", "y", "width", "height"}, {"getX", "getY", "getWidth", "getHeight"}, {"left", "top", "width", "height"}};
        for (String[] set : names) {
            try {
                int[] out = new int[4];
                for (int i = 0; i < 4; i++) {
                    out[i] = number(rect.getClass().getMethod(set[i]).invoke(rect));
                }
                return out;
            } catch (ReflectiveOperationException | RuntimeException e) {
                // Try the next set of names.
            }
        }
        return null;
    }

    /** A new rectangle of the given class (Rect2i or ScreenRectangle), made through its four-int constructor. */
    static @Nullable Object newRect(Class<?> type, int x, int y, int w, int h) {
        try {
            Constructor<?> constructor = type.getConstructor(int.class, int.class, int.class, int.class);
            return constructor.newInstance(x, y, w, h);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static @Nullable Object newPosition(Class<?> type, int x, int y) {
        try {
            return type.getConstructor(int.class, int.class).newInstance(x, y);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static int[] sizeOf(@Nullable Object drawable) {
        return new int[] {number(call(drawable, "getWidth")), number(call(drawable, "getHeight"))};
    }

    private static Component component(FormattedText text) {
        return text instanceof Component c ? c : Component.literal(text.getString());
    }

    /** A tooltip builder that collects lines into a list. */
    static @Nullable Object tooltipBuilder(@Nullable Class<?> type, List<Component> lines) {
        if (type == null) {
            return null;
        }
        return JeiBridge.proxy(type, (self, method, args) -> {
            for (Object arg : args) {
                if (arg instanceof FormattedText text) {
                    lines.add(component(text));
                } else if (arg instanceof Collection<?> collection) {
                    for (Object element : collection) {
                        if (element instanceof FormattedText text) {
                            lines.add(component(text));
                        }
                    }
                }
            }
            if (method.getName().equals("getLines")) {
                return List.of();
            }
            return JeiBridge.fluentOrDefault(self, method);
        });
    }

    // ---- slot drawables ----------------------------------------------------------------------------

    /** One slot of a card as JEI's {@code IRecipeSlotDrawable}: drawn, positioned and asked for its tooltip. */
    static Object slotDrawable(Class<?> type, JeiViews.SlotSpec spec, int x, int y, List<Object> tooltipCallbacks) {
        int[] at = {x, y};
        Object view = JeiViews.slot(spec);
        return JeiBridge.proxyFirst(type, (self, method, args) -> {
            switch (method.getName()) {
                case "draw":
                    if (args.length >= 1) {
                        JeiDrawables.drawSlotBackground(args[0], at[0], at[1]);
                        if (!spec.items().isEmpty()) {
                            ItemStack shown = spec.items().get((int) (System.currentTimeMillis() / 1000 % spec.items().size()));
                            JeiDrawables.item(args[0], shown, at[0] + 1, at[1] + 1);
                        }
                    }
                    return null;
                case "drawHoverOverlays":
                case "drawTooltip":
                case "clearDisplayOverrides":
                    return null;
                case "isMouseOver":
                    return args.length == 2 && ((Number) args[0]).doubleValue() >= at[0] && ((Number) args[1]).doubleValue() >= at[1]
                            && ((Number) args[0]).doubleValue() < at[0] + 18 && ((Number) args[1]).doubleValue() < at[1] + 18;
                case "setPosition":
                    at[0] = (int) args[0];
                    at[1] = (int) args[1];
                    return null;
                case "getRect":
                case "getAreaIncludingBackground":
                    return newRect(method.getReturnType(), at[0], at[1], 18, 18);
                case "getTooltip":
                    if (args.length == 0) {
                        return spec.items().isEmpty() ? List.of() : List.of(spec.items().get(0).getHoverName());
                    }
                    return null;
                case "addTooltipCallback":
                    tooltipCallbacks.add(args[0]);
                    return null;
                case "createDisplayOverrides":
                    return JeiBridge.fallback(method.getReturnType());
                default:
                    // The IRecipeSlotView part (role, ingredients, name) answers from the slot's data.
                    Method viewMethod = JeiTransfers.find(view.getClass(), method.getName(), args.length);
                    if (viewMethod != null) {
                        try {
                            return viewMethod.invoke(view, args);
                        } catch (ReflectiveOperationException | RuntimeException e) {
                            return JeiBridge.fallback(method.getReturnType());
                        }
                    }
                    return JeiBridge.DEFER;
            }
        });
    }

    /** The slots of a card as JEI's {@code IRecipeSlotDrawablesView}. */
    static Object slotsView(Class<?> viewType, Class<?> slotType, List<JeiBridge.LayoutSlot> slots) {
        List<Object> drawables = new ArrayList<>();
        List<RecipeIngredientRole> roles = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (JeiBridge.LayoutSlot slot : slots) {
            drawables.add(slotDrawable(slotType, new JeiViews.SlotSpec(slot.role(), List.copyOf(slot.items()), slot.name()), slot.x(), slot.y(),
                    new ArrayList<>()));
            roles.add(slot.role());
            names.add(slot.name());
        }
        return JeiBridge.proxy(viewType, (self, method, args) -> {
            switch (method.getName()) {
                case "getSlots":
                    if (args.length == 1) {
                        List<Object> filtered = new ArrayList<>();
                        for (int i = 0; i < drawables.size(); i++) {
                            if (roles.get(i) == args[0]) {
                                filtered.add(drawables.get(i));
                            }
                        }
                        return filtered;
                    }
                    return drawables;
                case "findSlotByName":
                    for (int i = 0; i < drawables.size(); i++) {
                        if (names.get(i) != null && names.get(i).equals(args[0])) {
                            return Optional.of(drawables.get(i));
                        }
                    }
                    return Optional.empty();
                default:
                    return JeiBridge.fallback(method.getReturnType());
            }
        });
    }

    // ---- widgets -----------------------------------------------------------------------------------

    enum Kind { DRAWABLE, TEXT, BUTTON, CYCLE, SCROLL_BOX, SCROLL_GRID, TOOLTIP_AREA, WIDGET, INPUT, LISTENER }

    /** One part on a card: where it is, how it draws, and what it does with the mouse. */
    static final class Part implements JeiBridge.Drawer {
        final Kind kind;
        int x;
        int y;
        int width;
        int height;
        // Placement inside an area: alignment of the content in a width by height box at x, y.
        boolean aligned;
        String horizontal = "LEFT";
        String vertical = "TOP";
        @Nullable Object drawable;
        @Nullable Object widget;
        @Nullable Object handler;
        final List<FormattedText> lines = new ArrayList<>();
        int color = 0xFF808080;
        boolean shadow;
        int lineSpacing = 1;
        String textHorizontal = "LEFT";
        String textVertical = "TOP";
        @Nullable Component message;
        @Nullable Object icon;
        boolean active = true;
        boolean visible = true;
        @Nullable Runnable onClick;
        List<Object> values = List.of();
        int valueIndex;
        @Nullable Consumer<Object> onCycle;
        @Nullable Function<Object, Object> messageOf;
        @Nullable Function<Object, Object> iconOf;
        List<Component> tooltipLines = List.of();
        @Nullable Object tooltipCallback;
        @Nullable Class<?> tooltipBuilderType;
        List<Object> slots = List.of();
        int columns = 1;
        int visibleRows = 1;
        String scrollbar = "DEFAULT";
        double scroll;
        long lastTick;

        Part(Kind kind) {
            this.kind = kind;
        }

        /** x, y, w, h of the part on the card. */
        int[] bounds() {
            switch (kind) {
                case DRAWABLE: {
                    int[] size = sizeOf(drawable);
                    return place(size[0], size[1]);
                }
                case TEXT:
                    return new int[] {x, y, width, height};
                case WIDGET: {
                    int[] rect = rectangle(call(widget, "getScreenRectangle"));
                    if (rect != null) {
                        return rect;
                    }
                    return new int[] {x, y, width, height};
                }
                case INPUT:
                case LISTENER: {
                    int[] rect = rectangle(call(handler, "getArea"));
                    return rect != null ? rect : new int[] {x, y, 0, 0};
                }
                case SCROLL_GRID:
                    return new int[] {x, y, columns * 18, visibleRows * 18};
                default:
                    return new int[] {x, y, width, height};
            }
        }

        private int[] place(int contentWidth, int contentHeight) {
            if (!aligned) {
                return new int[] {x, y, contentWidth, contentHeight};
            }
            int px = switch (horizontal) {
                case "CENTER" -> x + (width - contentWidth) / 2;
                case "RIGHT" -> x + width - contentWidth;
                default -> x;
            };
            int py = switch (vertical) {
                case "CENTER" -> y + (height - contentHeight) / 2;
                case "BOTTOM" -> y + height - contentHeight;
                default -> y;
            };
            return new int[] {px, py, contentWidth, contentHeight};
        }

        boolean inside(int mx, int my) {
            int[] b = bounds();
            return mx >= b[0] && my >= b[1] && mx < b[0] + b[2] && my < b[1] + b[3];
        }

        private boolean showsScrollbar(int contentHeight, int boxHeight) {
            return switch (scrollbar) {
                case "ENABLED" -> true;
                case "DISABLED" -> false;
                default -> contentHeight > boxHeight;
            };
        }

        private int contentHeight() {
            if (kind == Kind.SCROLL_BOX) {
                if (drawable != null) {
                    return sizeOf(drawable)[1];
                }
                return JeiDrawables.textHeight(lines, Math.max(1, width - 6), lineSpacing);
            }
            return ((slots.size() + Math.max(1, columns) - 1) / Math.max(1, columns)) * 18;
        }

        @Override
        public void draw(Object g, int mx, int my) throws ReflectiveOperationException {
            if (!visible) {
                return;
            }
            int[] b = bounds();
            switch (kind) {
                case DRAWABLE -> JeiBridge.drawAt(drawable, g, b[0], b[1]);
                case TEXT -> JeiDrawables.textBox(g, lines, x, y, width, height, color, shadow, lineSpacing, textHorizontal, textVertical);
                case BUTTON, CYCLE -> drawButton(g, b, mx, my);
                case SCROLL_BOX -> drawScrollBox(g, b);
                case SCROLL_GRID -> drawScrollGrid(g, b);
                case WIDGET -> JeiCards.drawRecipeWidget(widget, g, mx, my, this);
                default -> {
                }
            }
        }

        private void drawButton(Object g, int[] b, int mx, int my) {
            boolean over = active && inside(mx, my);
            JeiDrawables.fill(g, b[0], b[1], b[0] + b[2], b[1] + b[3], 0xFF000000);
            JeiDrawables.fill(g, b[0] + 1, b[1] + 1, b[0] + b[2] - 1, b[1] + b[3] - 1,
                    !active ? 0xFF3A3A3A : over ? 0xFF6F7FD0 : 0xFF5A5A5A);
            Object shownIcon = icon;
            Component shownMessage = message;
            if (kind == Kind.CYCLE && !values.isEmpty()) {
                Object value = values.get(valueIndex);
                if (iconOf != null) {
                    shownIcon = iconOf.apply(value);
                }
                if (messageOf != null && messageOf.apply(value) instanceof Component c) {
                    shownMessage = c;
                }
            }
            if (shownIcon != null) {
                int[] size = sizeOf(shownIcon);
                try {
                    JeiBridge.drawAt(shownIcon, g, b[0] + (b[2] - size[0]) / 2, b[1] + (b[3] - size[1]) / 2);
                } catch (ReflectiveOperationException e) {
                    // The icon is skipped.
                }
            } else if (shownMessage != null) {
                JeiDrawables.textBox(g, List.of(shownMessage), b[0], b[1], b[2], b[3], active ? 0xFFFFFFFF : 0xFF909090, true, 0, "CENTER", "CENTER");
            }
        }

        private void drawScrollBox(Object g, int[] b) throws ReflectiveOperationException {
            int content = contentHeight();
            boolean bar = showsScrollbar(content, b[3]);
            scroll = Math.max(0, Math.min(scroll, Math.max(0, content - b[3])));
            JeiDrawables.scissor(g, b[0], b[1], b[0] + b[2], b[1] + b[3]);
            try {
                if (drawable != null) {
                    JeiBridge.drawAt(drawable, g, b[0], b[1] - (int) scroll);
                } else {
                    JeiDrawables.textBox(g, lines, b[0], b[1] - (int) scroll, b[2] - (bar ? 6 : 0), content, color, shadow, lineSpacing, "LEFT", "TOP");
                }
            } finally {
                JeiDrawables.unscissor(g);
            }
            if (bar && content > 0) {
                int track = b[3];
                int thumb = Math.max(8, track * b[3] / Math.max(content, b[3]));
                int thumbY = b[1] + (int) ((track - thumb) * (content <= b[3] ? 0 : scroll / (content - b[3])));
                JeiDrawables.fill(g, b[0] + b[2] - 4, b[1], b[0] + b[2], b[1] + track, 0xFF202020);
                JeiDrawables.fill(g, b[0] + b[2] - 4, thumbY, b[0] + b[2], thumbY + thumb, 0xFF909090);
            }
        }

        private void drawScrollGrid(Object g, int[] b) {
            int content = contentHeight();
            scroll = Math.max(0, Math.min(scroll, Math.max(0, content - b[3])));
            int firstRow = (int) (scroll / 18);
            JeiDrawables.scissor(g, b[0], b[1], b[0] + b[2], b[1] + b[3]);
            try {
                for (int i = 0; i < slots.size(); i++) {
                    int row = i / Math.max(1, columns);
                    if (row < firstRow || row >= firstRow + visibleRows + 1) {
                        continue;
                    }
                    int sx = b[0] + (i % Math.max(1, columns)) * 18;
                    int sy = b[1] + row * 18 - (int) scroll;
                    call(slots.get(i), "setPosition", sx, sy);
                    call(slots.get(i), "draw", g);
                }
            } finally {
                JeiDrawables.unscissor(g);
            }
        }

        // ---- the mouse ----

        @Override
        public boolean click(int mx, int my, int button) {
            if (!visible || !inside(mx, my)) {
                return false;
            }
            switch (kind) {
                case BUTTON:
                    if (active && onClick != null && button == 0) {
                        onClick.run();
                        return true;
                    }
                    return false;
                case CYCLE:
                    if (active && !values.isEmpty() && button == 0) {
                        valueIndex = (valueIndex + 1) % values.size();
                        if (onCycle != null) {
                            onCycle.accept(values.get(valueIndex));
                        }
                        return true;
                    }
                    return false;
                case INPUT: {
                    Method handle = JeiBridge.ifaceMethod("mezz.jei.api.gui.inputs.IJeiInputHandler", "handleInput", 3);
                    if (handle == null) {
                        return false;
                    }
                    try {
                        return Boolean.TRUE.equals(handle.invoke(handler, (double) mx, (double) my, userInput(handle.getParameterTypes()[2], button)));
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        return false;
                    }
                }
                case LISTENER: {
                    Method clicked = JeiBridge.ifaceMethod("mezz.jei.api.gui.inputs.IJeiGuiEventListener", "mouseClicked", 3);
                    return invokeBool(clicked, handler, (double) mx, (double) my, button);
                }
                default:
                    return false;
            }
        }

        @Override
        public boolean release(int mx, int my, int button) {
            if (kind == Kind.LISTENER) {
                return invokeBool(JeiBridge.ifaceMethod("mezz.jei.api.gui.inputs.IJeiGuiEventListener", "mouseReleased", 3), handler,
                        (double) mx, (double) my, button);
            }
            return false;
        }

        @Override
        public boolean drag(int mx, int my, int button, double dx, double dy) {
            if (kind == Kind.LISTENER) {
                return invokeBool(JeiBridge.ifaceMethod("mezz.jei.api.gui.inputs.IJeiGuiEventListener", "mouseDragged", 5), handler,
                        (double) mx, (double) my, button, dx, dy);
            }
            if (kind == Kind.INPUT) {
                Method dragged = JeiBridge.ifaceMethod("mezz.jei.api.gui.inputs.IJeiInputHandler", "handleMouseDragged", 5);
                return invokeBool(dragged, handler, (double) mx, (double) my, keyFor(button), dx, dy);
            }
            return false;
        }

        @Override
        public boolean scroll(int mx, int my, double delta) {
            if (!visible || !inside(mx, my)) {
                return false;
            }
            switch (kind) {
                case SCROLL_BOX:
                case SCROLL_GRID: {
                    int[] b = bounds();
                    int content = contentHeight();
                    if (content <= b[3]) {
                        return false;
                    }
                    scroll = Math.max(0, Math.min(content - b[3], scroll - delta * 9));
                    return true;
                }
                case INPUT:
                    return invokeBool(JeiBridge.ifaceMethod("mezz.jei.api.gui.inputs.IJeiInputHandler", "handleMouseScrolled", 4), handler,
                            (double) mx, (double) my, 0.0, delta);
                case LISTENER:
                    return invokeBool(JeiBridge.ifaceMethod("mezz.jei.api.gui.inputs.IJeiGuiEventListener", "mouseScrolled", 4), handler,
                            (double) mx, (double) my, 0.0, delta);
                default:
                    return false;
            }
        }

        @Override
        public @Nullable List<Component> tooltip(int mx, int my) {
            if (!visible || !inside(mx, my)) {
                return null;
            }
            List<Component> lines = new ArrayList<>(tooltipLines);
            if (tooltipCallback != null) {
                Method on = JeiBridge.ifaceMethod("mezz.jei.api.gui.widgets.IRecipeWidgetTooltipCallback", "onTooltip", 1);
                Object builder = tooltipBuilder(tooltipBuilderType != null ? tooltipBuilderType : on == null ? null : on.getParameterTypes()[0], lines);
                if (on != null && builder != null) {
                    try {
                        on.invoke(tooltipCallback, builder);
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        // The callback adds nothing.
                    }
                }
            }
            if (kind == Kind.WIDGET) {
                Method get = JeiBridge.ifaceMethod("mezz.jei.api.gui.widgets.IRecipeWidget", "getTooltip", 3);
                if (get != null) {
                    Object builder = tooltipBuilder(get.getParameterTypes()[0], lines);
                    int[] b = bounds();
                    try {
                        get.invoke(widget, builder, (double) (mx - b[0]), (double) (my - b[1]));
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        // The widget adds nothing.
                    }
                }
            }
            return lines.isEmpty() ? null : lines;
        }
    }

    private static boolean invokeBool(@Nullable Method method, @Nullable Object target, Object... args) {
        if (method == null || target == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(method.invoke(target, args));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** The key object (InputConstants.Key) for a mouse button, or null when this game version cannot make one. */
    private static @Nullable Object keyFor(int button) {
        try {
            Class<?> typeClass = Class.forName("com.mojang.blaze3d.platform.InputConstants$Type", false, JeiCards.class.getClassLoader());
            Object mouse = typeClass.getField("MOUSE").get(null);
            return typeClass.getMethod("getOrCreate", int.class).invoke(mouse, button);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** A user input for a mouse click: the key, no modifiers, not a simulation. */
    static Object userInput(Class<?> type, int button) {
        Object key = keyFor(button);
        return JeiBridge.proxy(type, (self, method, args) -> switch (method.getName()) {
            case "getKey" -> key;
            case "getModifiers" -> 0;
            case "isSimulate" -> false;
            case "is" -> false;
            default -> JeiBridge.fallback(method.getReturnType());
        });
    }

    /** Draws a plugin's recipe widget at its own position; the widget sees mouse coordinates relative to itself. */
    static void drawRecipeWidget(@Nullable Object widget, Object g, int mx, int my, Part part) throws ReflectiveOperationException {
        if (widget == null) {
            return;
        }
        Method position = JeiBridge.ifaceMethod("mezz.jei.api.gui.widgets.IRecipeWidget", "getPosition", 0);
        Method draw = JeiBridge.ifaceMethod("mezz.jei.api.gui.widgets.IRecipeWidget", "drawWidget", 3);
        if (draw == null) {
            draw = JeiBridge.ifaceMethod("mezz.jei.api.gui.widgets.IRecipeWidget", "draw", 3);
        }
        if (position == null || draw == null) {
            return;
        }
        Object at = position.invoke(widget);
        int[] rect = rectangle(at);
        int wx = at == null ? 0 : number(at.getClass().getMethod("x").invoke(at));
        int wy = at == null ? 0 : number(at.getClass().getMethod("y").invoke(at));
        if (rect != null && at != null && wx == 0 && wy == 0) {
            wx = rect[0];
            wy = rect[1];
        }
        long now = System.currentTimeMillis();
        if (now - part.lastTick >= 50) {
            part.lastTick = now;
            Method tick = JeiBridge.ifaceMethod("mezz.jei.api.gui.widgets.IRecipeWidget", "tick", 0);
            if (tick != null) {
                tick.invoke(widget);
            }
        }
        Method drawMethod = draw;
        int dx = wx;
        int dy = wy;
        JeiDrawables.translated(g, wx, wy, () -> {
            try {
                drawMethod.invoke(widget, g, (double) (mx - dx), (double) (my - dy));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    // ---- the builders plugins get back ------------------------------------------------------------

    private static String name(@Nullable Object alignment) {
        return alignment == null ? "LEFT" : ((Enum<?>) alignment).name();
    }

    /**
     * The object a plugin chains calls on after adding a part (position, tooltip, text style, button state...). Every
     * call it makes changes the part; every other call is answered with the same object so chains keep working.
     */
    static Object chain(Class<?> type, Part part) {
        return JeiBridge.proxyFirst(type, (self, method, args) -> {
            switch (method.getName()) {
                case "setPosition":
                    if (args.length >= 2) {
                        part.x = (int) args[0];
                        part.y = (int) args[1];
                        part.aligned = args.length == 6;
                        if (args.length == 6) {
                            part.width = (int) args[2];
                            part.height = (int) args[3];
                            part.horizontal = name(args[4]);
                            part.vertical = name(args[5]);
                        }
                    }
                    return self;
                case "getWidth":
                    return part.bounds()[2];
                case "getHeight":
                    return part.bounds()[3];
                case "setTooltip":
                    if (args.length == 1) {
                        if (args[0] instanceof FormattedText text) {
                            part.tooltipLines = List.of(component(text));
                        } else if (args[0] instanceof Collection<?> collection) {
                            List<Component> lines = new ArrayList<>();
                            for (Object element : collection) {
                                if (element instanceof FormattedText text) {
                                    lines.add(component(text));
                                }
                            }
                            part.tooltipLines = lines;
                        } else if (args[0] != null && JeiBridge.ifaceMethod("mezz.jei.api.gui.widgets.IRecipeWidgetTooltipCallback", "onTooltip", 1) != null
                                && JeiTransfers.find(args[0].getClass(), "onTooltip", 1) != null) {
                            part.tooltipCallback = args[0];
                        }
                    }
                    return self;
                case "setFont":
                    return self;
                case "setColor":
                    part.color = number(args[0]) | 0xFF000000;
                    return self;
                case "setLineSpacing":
                    part.lineSpacing = number(args[0]);
                    return self;
                case "setShadow":
                    part.shadow = Boolean.TRUE.equals(args[0]);
                    return self;
                case "setTextAlignment":
                    if (args[0] instanceof Enum<?> alignment) {
                        String value = alignment.name();
                        if (value.equals("TOP") || value.equals("BOTTOM") || alignment.getClass().getSimpleName().startsWith("Vertical")) {
                            part.textVertical = value;
                        } else {
                            part.textHorizontal = value;
                        }
                    }
                    return self;
                case "alignHorizontalLeft":
                    part.textHorizontal = "LEFT";
                    return self;
                case "alignHorizontalCenter":
                    part.textHorizontal = "CENTER";
                    return self;
                case "alignHorizontalRight":
                    part.textHorizontal = "RIGHT";
                    return self;
                case "alignVerticalTop":
                    part.textVertical = "TOP";
                    return self;
                case "alignVerticalCenter":
                    part.textVertical = "CENTER";
                    return self;
                case "alignVerticalBottom":
                    part.textVertical = "BOTTOM";
                    return self;
                case "setMessage":
                    if (args[0] instanceof Component component) {
                        part.message = component;
                    } else if (args[0] instanceof Function<?, ?> function) {
                        @SuppressWarnings("unchecked")
                        Function<Object, Object> typed = (Function<Object, Object>) function;
                        part.messageOf = typed;
                    }
                    return self;
                case "setIcon":
                    if (args[0] instanceof Function<?, ?> function) {
                        @SuppressWarnings("unchecked")
                        Function<Object, Object> typed = (Function<Object, Object>) function;
                        part.iconOf = typed;
                    } else {
                        part.icon = args[0];
                    }
                    return self;
                case "setActive":
                    part.active = Boolean.TRUE.equals(args[0]);
                    return self;
                case "setVisible":
                    part.visible = Boolean.TRUE.equals(args[0]);
                    return self;
                case "getValue":
                    return part.values.isEmpty() ? null : part.values.get(part.valueIndex);
                case "setValue":
                    part.valueIndex = Math.max(0, part.values.indexOf(args[0]));
                    return self;
                case "setScrollbarVisibility":
                    part.scrollbar = args[0] instanceof Enum<?> visibility ? visibility.name() : "DEFAULT";
                    return self;
                case "getContentAreaWidth":
                    return Math.max(0, part.width - 6);
                case "getContentAreaHeight":
                    return part.height;
                case "setContents":
                    if (args[0] instanceof IDrawable) {
                        part.drawable = args[0];
                        part.lines.clear();
                    } else if (args[0] instanceof List<?> list) {
                        part.drawable = null;
                        part.lines.clear();
                        for (Object element : list) {
                            if (element instanceof FormattedText text) {
                                part.lines.add(text);
                            }
                        }
                    }
                    return self;
                case "setSlots":
                    part.slots = args[0] instanceof List<?> list ? new ArrayList<>(list) : List.of();
                    return self;
                case "getSlotUnderMouse":
                    return Optional.empty();
                case "getScreenRectangle": {
                    int[] b = part.bounds();
                    Object rect = newRect(method.getReturnType(), b[0], b[1], b[2], b[3]);
                    return rect;
                }
                default:
                    return JeiBridge.DEFER;
            }
        });
    }

    /** Parts made by IGuiHelper before a plugin puts them on a card; they join the card when it adds them. */
    private static final java.util.Map<Object, Part> STANDALONE = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** IGuiHelper's widget factories: scroll boxes, scroll grids and drawable widgets. Null when the call is not one of them. */
    static @Nullable Object guiWidget(Method method, Object[] args) {
        Class<?> returns = method.getReturnType();
        Part part;
        switch (method.getName()) {
            case "getScrollBoxScrollbarExtraWidth":
                return 6;
            case "createWidgetFromDrawable":
                part = new Part(Kind.DRAWABLE);
                part.drawable = args[0];
                part.x = number(args[1]);
                part.y = number(args[2]);
                break;
            case "createScrollBoxWidget":
                part = new Part(Kind.SCROLL_BOX);
                if (args[0] instanceof IDrawable contents) {
                    part.drawable = contents;
                    part.width = sizeOf(contents)[0] + 6;
                    part.height = number(args[1]);
                    part.x = number(args[2]);
                    part.y = number(args[3]);
                } else {
                    part.width = number(args[0]);
                    part.height = number(args[1]);
                    part.x = number(args[2]);
                    part.y = number(args[3]);
                }
                break;
            case "createScrollGridFactory": {
                int columns = Math.max(1, number(args[0]));
                int rows = Math.max(1, number(args[1]));
                int[] at = {0, 0};
                return JeiBridge.proxyFirst(returns, (self, call, callArgs) -> {
                    switch (call.getName()) {
                        case "setPosition":
                            at[0] = number(callArgs[0]);
                            at[1] = number(callArgs[1]);
                            return null;
                        case "getArea":
                            return newRect(call.getReturnType(), at[0], at[1], columns * 18, rows * 18);
                        case "createWidgetForSlots": {
                            Object grid = call(callArgs[0], "addScrollGridWidget", callArgs[2], columns, rows);
                            call(grid, "setPosition", at[0], at[1]);
                            return null;
                        }
                        default:
                            return call.isDefault() ? JeiBridge.DEFER : JeiBridge.fallback(call.getReturnType());
                    }
                });
            }
            default:
                return null;
        }
        Object widget = chain(returns, part);
        STANDALONE.put(widget, part);
        return widget;
    }

    /** The builder behind {@code createRecipeExtras}: every add call becomes a part on the card. */
    static Object extrasBuilder(Class<?> builderType, List<JeiBridge.Drawer> extras, List<JeiBridge.LayoutSlot> slots) {
        ClassLoader loader = JeiCards.class.getClassLoader();
        return JeiBridge.proxyFirst(builderType, (self, method, args) -> {
            String name = method.getName();
            Class<?> returns = method.getReturnType();
            Part part;
            switch (name) {
                case "getRecipeSlots":
                    try {
                        return slotsView(returns, Class.forName("mezz.jei.api.gui.ingredient.IRecipeSlotDrawable", false, loader), slots);
                    } catch (ClassNotFoundException e) {
                        return JeiBridge.fallback(returns);
                    }
                case "addDrawable":
                case "addDrawableWidget":
                    part = new Part(Kind.DRAWABLE);
                    part.drawable = args[0];
                    if (args.length == 3) {
                        part.x = (int) args[1];
                        part.y = (int) args[2];
                    }
                    break;
                case "addTooltipArea":
                    part = new Part(Kind.TOOLTIP_AREA);
                    part.x = (int) args[0];
                    part.y = (int) args[1];
                    part.width = (int) args[2];
                    part.height = (int) args[3];
                    break;
                case "addButton":
                    part = new Part(Kind.BUTTON);
                    part.width = (int) args[0];
                    part.height = (int) args[1];
                    part.onClick = args[2] instanceof Runnable run ? run : null;
                    break;
                case "addCycleButton":
                    part = new Part(Kind.CYCLE);
                    part.values = args[0] instanceof List<?> list ? new ArrayList<>(list) : List.of();
                    part.valueIndex = Math.max(0, part.values.indexOf(args[1]));
                    part.width = (int) args[2];
                    part.height = (int) args[3];
                    @SuppressWarnings("unchecked")
                    Consumer<Object> consumer = args[4] instanceof Consumer<?> c ? (Consumer<Object>) c : null;
                    part.onCycle = consumer;
                    break;
                case "addWidget":
                case "addSlottedWidget":
                case "addInputHandler":
                    if (args.length > 0 && STANDALONE.get(args[0]) != null) {
                        // A widget IGuiHelper made: it is already a part; add it once however many ways the plugin adds it.
                        Part made = STANDALONE.get(args[0]);
                        if (!extras.contains(made)) {
                            extras.add(made);
                        }
                        return returns == void.class ? null : JeiBridge.fallback(returns);
                    }
                    part = new Part(name.equals("addInputHandler") ? Kind.INPUT : Kind.WIDGET);
                    if (part.kind == Kind.INPUT) {
                        part.handler = args[0];
                    } else {
                        part.widget = args[0];
                    }
                    break;
                case "addGuiEventListener":
                    part = new Part(Kind.LISTENER);
                    part.handler = args[0];
                    break;
                case "addScrollBoxWidget":
                    part = new Part(Kind.SCROLL_BOX);
                    part.width = (int) args[0];
                    part.height = (int) args[1];
                    part.x = (int) args[2];
                    part.y = (int) args[3];
                    break;
                case "addScrollGridWidget":
                    part = new Part(Kind.SCROLL_GRID);
                    part.slots = args[0] instanceof List<?> list ? new ArrayList<>(list) : List.of();
                    part.columns = Math.max(1, (int) args[1]);
                    part.visibleRows = Math.max(1, (int) args[2]);
                    break;
                case "addRecipeArrow":
                case "addRecipeArrowWidget":
                    part = new Part(Kind.DRAWABLE);
                    part.drawable = JeiDrawables.arrow(true);
                    break;
                case "addAnimatedRecipeArrow":
                case "addAnimatedRecipeArrowWidget":
                    part = new Part(Kind.DRAWABLE);
                    part.drawable = JeiDrawables.animated(JeiDrawables.arrow(true), args.length > 0 && args[0] instanceof Integer t ? t : 100,
                            mezz.jei.api.gui.drawable.IDrawableAnimated.StartDirection.LEFT, false);
                    break;
                case "addRecipePlusSign":
                case "addRecipePlusSignWidget":
                    part = new Part(Kind.DRAWABLE);
                    part.drawable = JeiDrawables.plus();
                    break;
                case "addAnimatedRecipeFlame":
                case "addAnimatedRecipeFlameWidget":
                    part = new Part(Kind.DRAWABLE);
                    part.drawable = JeiDrawables.animated(JeiDrawables.flame(true), args.length > 0 && args[0] instanceof Integer t ? t : 100,
                            mezz.jei.api.gui.drawable.IDrawableAnimated.StartDirection.TOP, true);
                    break;
                case "addText":
                    part = new Part(Kind.TEXT);
                    if (args.length > 0 && args[0] instanceof FormattedText text) {
                        part.lines.add(text);
                    } else if (args.length > 0 && args[0] instanceof List<?> list) {
                        for (Object element : list) {
                            if (element instanceof FormattedText text) {
                                part.lines.add(text);
                            }
                        }
                    }
                    part.width = args.length > 1 ? number(args[args.length == 3 ? 1 : 3]) : 100;
                    part.height = args.length > 2 ? number(args[args.length == 3 ? 2 : 4]) : 0;
                    if (args.length == 5) {
                        part.x = number(args[1]);
                        part.y = number(args[2]);
                    }
                    break;
                default:
                    return JeiBridge.DEFER;
            }
            // Positioned arguments of the short forms: addRecipeArrow(x, y), addAnimatedRecipeArrow(ticks, x, y), ...
            if (name.startsWith("addRecipe") || name.startsWith("addAnimated")) {
                if (args.length >= 2 && args[args.length - 1] instanceof Integer last && args[args.length - 2] instanceof Integer before
                        && returns == void.class) {
                    part.x = before;
                    part.y = last;
                }
            }
            extras.add(part);
            return returns == void.class ? null : chain(returns, part);
        });
    }

    /** Extra part helpers used when a plugin hands over its own widgets. */
    static void log(String what, Throwable e) {
        Bei.LOGGER.debug("JEI card part {} failed", what, e);
    }

    static @Nullable Minecraft game() {
        return Minecraft.getInstance();
    }
}
