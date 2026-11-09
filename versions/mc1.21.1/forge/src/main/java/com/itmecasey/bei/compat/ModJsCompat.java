package com.itmecasey.bei.compat;

import com.itmecasey.bei.Bei;
import com.itmecasey.bei.api.CardPainter;
import com.itmecasey.bei.api.CustomRecipe;
import com.itmecasey.bei.api.RecipeCategory;
import com.itmecasey.bei.client.Groups;
import com.itmecasey.bei.index.BeiRecipe;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import com.itmecasey.bei.client.Draw;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;

/**
 * Connects ModJS's {@code RecipeViewerEvents} to BEI: scripts can hide items (by id, {@code #tag}
 * or {@code @mod}), hide recipe categories, and add information text to items; ModJS's unifier
 * also hides the items it replaces. ModJS is reached by reflection, so BEI has no compile-time
 * dependency on it and nothing here runs unless ModJS is installed.
 */
public final class ModJsCompat {
    private static final String EVENT_GROUPS = "com.itmecasey.modjs.event.EventGroups";
    private static final String VIEWER_EVENTS = "com.itmecasey.modjs.client.RecipeViewerEventJS";
    private static final String UNIFIER = "com.itmecasey.modjs.unify.Unifier";
    private static final ResourceLocation INFO_ID = ResourceLocation.fromNamespaceAndPath("bei", "info");
    private static final int INFO_WIDTH = 150;

    private static volatile List<String> hiddenItems = List.of();
    private static volatile Set<String> hiddenCategories = Set.of();
    private static volatile List<BeiRecipe> info = List.of();

    private ModJsCompat() {
    }

    /** Whether a recipe is an information page (it has no real recipe buttons). */
    public static boolean isInfo(BeiRecipe recipe) {
        return recipe.category() != null && recipe.category().id().equals(INFO_ID);
    }

    /** Item selectors scripts and the unifier want hidden, in the same form as a datapack's hidden list. */
    public static List<String> hiddenItemSpecs() {
        return hiddenItems;
    }

    /** The information pages scripts added, as recipes of the "Information" category. */
    public static List<BeiRecipe> infoRecipes() {
        return info;
    }

    /** Whether scripts hid this recipe's category (by recipe type id, or a plugin category id). */
    public static boolean hiddenCategory(BeiRecipe recipe) {
        if (JeiHidden.categoryHidden(recipe.recipeType(), recipe.category() == null ? null : recipe.category().id().toString())) {
            return true;
        }
        Set<String> hidden = hiddenCategories;
        if (hidden.isEmpty()) {
            return false;
        }
        String type = recipe.recipeType();
        if (type != null && (hidden.contains(type) || (hidden.contains("minecraft:crafting") && type.startsWith("minecraft:crafting")))) {
            return true;
        }
        return recipe.category() != null && hidden.contains(recipe.category().id().toString());
    }

    /** Reads the script events; run on the main thread when a world is joined, once ModJS has run its client scripts. */
    public static void load() {
        if (!ModList.get().isLoaded("modjs")) {
            return;
        }
        try {
            Object viewer = Class.forName(EVENT_GROUPS).getField("RECIPE_VIEWER").get(null);
            List<String> items = new ArrayList<>(readHidden(viewer));
            items.addAll(readUnified());
            hiddenItems = List.copyOf(items);
            hiddenCategories = Set.copyOf(readCategories(viewer));
            info = readInfo(viewer);
            List<Groups.Group> scripted = readGroups(viewer);
            Groups.setScriptGroups(scripted);
            Groups.refresh();
            Bei.LOGGER.info("ModJS: hiding {} item selector(s) and {} categories, {} information page(s), {} group(s)",
                    hiddenItems.size(), hiddenCategories.size(), info.size(), scripted.size());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Bei.LOGGER.warn("Could not read ModJS's recipe viewer events", e);
        }
    }

    // ---- reading the events ----------------------------------------------------------------------

    private static boolean hasHandlers(Object group, String name) throws ReflectiveOperationException {
        return (boolean) group.getClass().getMethod("hasHandlers", String.class).invoke(group, name);
    }

    private static Object post(Object group, String name, String eventClass) throws ReflectiveOperationException {
        Object event = Class.forName(VIEWER_EVENTS + "$" + eventClass).getDeclaredConstructor().newInstance();
        for (Method method : group.getClass().getMethods()) {
            if (method.getName().equals("post") && method.getParameterCount() == 2) {
                method.invoke(group, name, event);
                return event;
            }
        }
        throw new NoSuchMethodException("EventGroup.post");
    }

    @SuppressWarnings("unchecked")
    private static List<String> readHidden(Object group) throws ReflectiveOperationException {
        if (!hasHandlers(group, "hideItems")) {
            return List.of();
        }
        Object event = post(group, "hideItems", "HideItems");
        return (List<String>) event.getClass().getMethod("selectors").invoke(event);
    }

    @SuppressWarnings("unchecked")
    private static List<String> readCategories(Object group) throws ReflectiveOperationException {
        if (!hasHandlers(group, "hideCategories")) {
            return List.of();
        }
        Object event = post(group, "hideCategories", "HideCategories");
        return (List<String>) event.getClass().getMethod("ids").invoke(event);
    }

    /** The item groups scripts defined, in script order. */
    private static List<Groups.Group> readGroups(Object group) throws ReflectiveOperationException {
        if (!hasHandlers(group, "groupItems")) {
            return List.of();
        }
        Object event = post(group, "groupItems", "GroupItems");
        List<Groups.Group> out = new ArrayList<>();
        int number = 0;
        for (Object entry : (List<?>) event.getClass().getMethod("groups").invoke(event)) {
            String name = String.valueOf(entry.getClass().getMethod("name").invoke(entry));
            Object icon = entry.getClass().getMethod("icon").invoke(entry);
            List<String> selectors = new ArrayList<>();
            for (Object selector : (List<?>) entry.getClass().getMethod("selectors").invoke(entry)) {
                selectors.add(selector.toString());
            }
            out.add(Groups.scriptGroup(number++, name, icon == null ? null : icon.toString(), selectors));
        }
        return out;
    }

    /** Items ModJS's unifier replaced with a canonical one: hidden so only the canonical item shows. */
    private static List<String> readUnified() throws ReflectiveOperationException {
        Object unifier = Class.forName(UNIFIER).getMethod("current").invoke(null);
        List<String> out = new ArrayList<>();
        if (!(boolean) unifier.getClass().getMethod("active").invoke(unifier)) {
            return out;
        }
        for (Object group : (List<?>) unifier.getClass().getMethod("groups").invoke(unifier)) {
            for (Object member : (List<?>) group.getClass().getMethod("members").invoke(group)) {
                out.add(member.toString());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<BeiRecipe> readInfo(Object group) throws ReflectiveOperationException {
        if (!hasHandlers(group, "addInformation")) {
            return List.of();
        }
        Object event = post(group, "addInformation", "AddInformation");
        Map<String, List<String>> pages = (Map<String, List<String>>) event.getClass().getMethod("info").invoke(event);
        List<BeiRecipe> out = new ArrayList<>();
        int number = 0;
        for (Map.Entry<String, List<String>> page : pages.entrySet()) {
            List<Item> items = resolve(page.getKey());
            if (items.isEmpty()) {
                Bei.LOGGER.warn("ModJS addInformation: nothing matches '{}'", page.getKey());
                continue;
            }
            RecipeCategory category = infoCategory(page.getValue());
            for (Item item : items) {
                ItemStack shown = new ItemStack(item);
                CustomRecipe custom = CustomRecipe.builder().output(shown).build();
                out.add(new BeiRecipe("modjs/info/" + number++, INFO_ID.toString(), 0, List.of(), List.of(shown),
                        List.of(category.station()), category, custom));
            }
        }
        return out;
    }

    /** Information pages for items from a JEI plugin's addIngredientInfo, in the same category ModJS's pages use. */
    public static List<BeiRecipe> infoPages(String idPrefix, List<ItemStack> items, List<String> paragraphs) {
        RecipeCategory category = infoCategory(paragraphs);
        List<BeiRecipe> out = new ArrayList<>();
        int number = 0;
        for (ItemStack item : items) {
            ItemStack shown = item.copy();
            CustomRecipe custom = CustomRecipe.builder().output(shown).build();
            out.add(new BeiRecipe(idPrefix + "/" + number++, INFO_ID.toString(), 0, List.of(), List.of(shown),
                    List.of(category.station()), category, custom));
        }
        return out;
    }

    /** A card made of the page's paragraphs, wrapped to the card width; its height fits the text. */
    private static RecipeCategory infoCategory(List<String> paragraphs) {
        var font = Minecraft.getInstance().font;
        List<List<FormattedCharSequence>> wrapped = new ArrayList<>();
        int height = 0;
        for (String paragraph : paragraphs) {
            List<FormattedCharSequence> lines = font.split(Component.literal(paragraph), INFO_WIDTH - 4);
            wrapped.add(lines);
            height += lines.size() * 10 + 4;
        }
        int total = Math.max(18, height - 4);
        CardPainter painter = (graphics, recipe, mx, my) -> paintInfo(new Draw(graphics), wrapped);
        return RecipeCategory.builder(INFO_ID)
                .title(Component.literal("Information"))
                .station(new ItemStack(Items.BOOK))
                .size(INFO_WIDTH, total)
                .painter(painter)
                .build();
    }

    private static void paintInfo(Draw graphics, List<List<FormattedCharSequence>> paragraphs) {
        var font = Minecraft.getInstance().font;
        int y = 0;
        for (List<FormattedCharSequence> lines : paragraphs) {
            for (FormattedCharSequence line : lines) {
                graphics.text(font, line, 2, y, com.itmecasey.bei.client.Ui.text(), com.itmecasey.bei.client.Ui.shadow());
                y += 10;
            }
            y += 4;
        }
    }

    // ---- selectors --------------------------------------------------------------------------------

    /** Items matching an id, {@code #tag} or {@code @mod}. */
    private static List<Item> resolve(String selector) {
        List<Item> out = new ArrayList<>();
        if (selector.startsWith("#")) {
            String tag = selector.substring(1).contains(":") ? selector.substring(1) : "minecraft:" + selector.substring(1);
            for (Item item : BuiltInRegistries.ITEM) {
                Set<String> tags = new HashSet<>();
                item.builtInRegistryHolder().tags().forEach(key -> tags.add(key.location().toString()));
                if (tags.contains(tag)) {
                    out.add(item);
                }
            }
        } else if (selector.startsWith("@")) {
            String mod = selector.substring(1);
            for (Item item : BuiltInRegistries.ITEM) {
                if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(mod)) {
                    out.add(item);
                }
            }
        } else {
            ResourceLocation id = ResourceLocation.tryParse(selector.contains(":") ? selector : "minecraft:" + selector);
            if (id != null) {
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(out::add);
            }
        }
        return out;
    }
}
