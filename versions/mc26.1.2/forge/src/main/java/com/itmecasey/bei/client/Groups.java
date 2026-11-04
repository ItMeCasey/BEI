package com.itmecasey.bei.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.bei.index.ItemEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * Item groups and hidden items, defined by datapacks ({@code bei_groups} and {@code bei_hidden}
 * folders) and sent to the client on join. A group collects items under one browser cell; a hidden
 * item is left out of the browser. Entries are an item id, {@code #tag} or {@code @modid}.
 */
public final class Groups {
    /** A group of items: shown as one highlighted cell in the browser. */
    public static final class Group {
        public final String id;
        public final String name;
        private final @Nullable String iconId;
        private final List<String> specs;
        /** Every item in the group, in datapack order; filled in by {@link #resolve}. */
        public volatile List<ItemEntry> members = List.of();
        public volatile Item icon;

        Group(String id, String name, @Nullable String iconId, List<String> specs) {
            this.id = id;
            this.name = name;
            this.iconId = iconId;
            this.specs = specs;
        }
    }

    /** The groups and hidden items worked out against the item list. */
    public record Resolved(Map<Item, Group> groupOf, Set<Item> hidden) {
    }

    /** Most entries read from one group or hidden list. */
    private static final int MAX_SPECS = 5000;

    private static String trim(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    private static volatile List<Group> groups = List.of();
    /** Groups made by ModJS scripts, in script order; they come after the datapack groups. */
    private static volatile List<Group> scriptGroups = List.of();
    private static volatile List<String> hiddenSpecs = List.of();
    private static int version;
    private static @Nullable List<ItemEntry> cacheKey;
    private static int cacheVersion = -1;
    private static @Nullable Resolved cache;
    private static final Set<String> EXPANDED = ConcurrentHashMap.newKeySet();

    private Groups() {
    }

    /** A group a script defined; {@code number} keeps its id unique and in script order. */
    public static Group scriptGroup(int number, String name, @Nullable String iconId, List<String> specs) {
        return new Group("modjs:" + number, trim(name, 48), iconId, specs.size() > MAX_SPECS ? specs.subList(0, MAX_SPECS) : specs);
    }

    /** Replaces the script groups (empty list for none). */
    public static synchronized void setScriptGroups(List<Group> newGroups) {
        scriptGroups = List.copyOf(newGroups);
        version++;
        EXPANDED.clear();
    }

    /** Makes the next search work groups and hidden items out again (a script changed what is hidden). */
    public static synchronized void refresh() {
        version++;
        BrowserPanel.invalidate();
    }

    /** Replaces the datapack definitions with the ones just received (group id to JSON, and hidden JSON texts). */
    public static synchronized void set(Map<String, String> groupJson, List<String> hiddenJson) {
        List<Group> parsed = new ArrayList<>();
        groupJson.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            try {
                JsonObject object = JsonParser.parseString(entry.getValue()).getAsJsonObject();
                List<String> specs = strings(object.get("items"));
                if (!specs.isEmpty()) {
                    parsed.add(new Group(entry.getKey(), trim(object.has("name") ? object.get("name").getAsString() : entry.getKey(), 48),
                            object.has("icon") ? object.get("icon").getAsString() : null, specs));
                }
            } catch (RuntimeException e) {
                // A group file that is not valid JSON is skipped.
            }
        });
        List<String> hidden = new ArrayList<>();
        for (String text : hiddenJson) {
            try {
                hidden.addAll(strings(JsonParser.parseString(text).getAsJsonObject().get("items")));
            } catch (RuntimeException e) {
                // Skipped like a bad group file.
            }
        }
        groups = parsed;
        hiddenSpecs = hidden;
        version++;
        EXPANDED.clear();
    }

    private static List<String> strings(@Nullable JsonElement element) {
        List<String> out = new ArrayList<>();
        if (element != null && element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                if (out.size() >= MAX_SPECS) {
                    break;
                }
                out.add(item.getAsString());
            }
        }
        return out;
    }

    /** Works out group members and hidden items against the item list; cached until either changes. */
    public static synchronized Resolved resolve(List<ItemEntry> entries) {
        if (entries == cacheKey && cacheVersion == version && cache != null) {
            return cache;
        }
        Map<String, ItemEntry> byId = new HashMap<>();
        for (ItemEntry entry : entries) {
            byId.put(entry.id, entry);
        }
        Set<Item> hidden = new HashSet<>();
        List<String> allHidden = new ArrayList<>(hiddenSpecs);
        allHidden.addAll(com.itmecasey.bei.compat.ModJsCompat.hiddenItemSpecs());
        for (String spec : allHidden) {
            for (ItemEntry entry : match(spec, entries, byId)) {
                hidden.add(entry.item);
            }
        }
        Map<Item, Group> groupOf = new HashMap<>();
        List<Group> every = new ArrayList<>(groups);
        every.addAll(scriptGroups);
        for (Group group : every) {
            Map<Item, ItemEntry> members = new LinkedHashMap<>();
            for (String spec : group.specs) {
                for (ItemEntry entry : match(spec, entries, byId)) {
                    if (!hidden.contains(entry.item)) {
                        members.putIfAbsent(entry.item, entry);
                    }
                }
            }
            if (members.isEmpty() && group.id.startsWith("modjs:")) {
                com.itmecasey.bei.Bei.LOGGER.warn("ModJS groupItems: nothing matches the group '{}'", group.name);
            }
            if (members.size() < 2) {
                group.members = List.of();
                continue;
            }
            group.members = List.copyOf(members.values());
            ItemEntry iconEntry = group.iconId == null ? null : byId.get(group.iconId.contains(":") ? group.iconId : "minecraft:" + group.iconId);
            group.icon = iconEntry != null ? iconEntry.item : group.members.get(0).item;
            for (ItemEntry entry : group.members) {
                groupOf.putIfAbsent(entry.item, group);
            }
        }
        cacheKey = entries;
        cacheVersion = version;
        cache = new Resolved(groupOf, hidden);
        return cache;
    }

    private static List<ItemEntry> match(String spec, List<ItemEntry> entries, Map<String, ItemEntry> byId) {
        List<ItemEntry> out = new ArrayList<>();
        if (spec.startsWith("#")) {
            String tag = spec.substring(1);
            String full = tag.contains(":") ? tag : "minecraft:" + tag;
            for (ItemEntry entry : entries) {
                if (entry.tags.contains(full)) {
                    out.add(entry);
                }
            }
        } else if (spec.startsWith("@")) {
            String mod = spec.substring(1);
            for (ItemEntry entry : entries) {
                if (entry.mod.equals(mod)) {
                    out.add(entry);
                }
            }
        } else {
            ItemEntry entry = byId.get(spec.contains(":") ? spec : "minecraft:" + spec);
            if (entry != null) {
                out.add(entry);
            }
        }
        return out;
    }

    // ---- expanded groups ------------------------------------------------------------------------

    public static boolean isExpanded(Group group) {
        return EXPANDED.contains(group.id);
    }

    public static void toggleExpanded(Group group) {
        if (!EXPANDED.remove(group.id)) {
            EXPANDED.add(group.id);
        }
    }

    // ---- browser cells --------------------------------------------------------------------------

    /**
     * Turns search results into browser cells: an item whose group has matches becomes the group's
     * cell, once, where the first match was; an expanded group is followed by all its members.
     */
    public static List<Cell> cells(List<ItemEntry> found, Resolved resolved) {
        List<Cell> cells = new ArrayList<>(found.size());
        Set<Group> seen = new HashSet<>();
        for (ItemEntry entry : found) {
            Group group = resolved.groupOf().get(entry.item);
            if (group == null) {
                cells.add(new Cell(entry, null, false));
            } else if (seen.add(group)) {
                cells.add(new Cell(null, group, false));
                if (EXPANDED.contains(group.id)) {
                    for (ItemEntry member : group.members) {
                        cells.add(new Cell(member, group, true));
                    }
                }
            }
        }
        return cells;
    }

    /** One browser cell: an item, a group (item is null), or a member shown inside an expanded group. */
    public record Cell(@Nullable ItemEntry item, @Nullable Group group, boolean member) {
        public boolean isGroup() {
            return item == null;
        }
    }
}
