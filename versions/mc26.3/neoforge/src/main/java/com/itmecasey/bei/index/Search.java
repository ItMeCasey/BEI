package com.itmecasey.bei.index;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * The search language: {@code @mod} matches the mod id, {@code #tag} matches item tags (a full id
 * like {@code #c:ingots} or just a piece of one), {@code $text} matches tooltip text, anything else
 * matches the name or id. Words are ANDed; a leading {@code -} on a word negates it.
 */
public final class Search {
    private Search() {
    }

    public static Predicate<ItemEntry> parse(String query) {
        Predicate<ItemEntry> all = entry -> true;
        for (String raw : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (raw.isEmpty()) {
                continue;
            }
            boolean negate = raw.length() > 1 && raw.startsWith("-");
            String word = negate ? raw.substring(1) : raw;
            Predicate<ItemEntry> one = word(word);
            all = all.and(negate ? one.negate() : one);
        }
        return all;
    }

    private static Predicate<ItemEntry> word(String word) {
        if (word.startsWith("@")) {
            String mod = word.substring(1);
            return entry -> entry.mod.contains(mod);
        }
        if (word.startsWith("#")) {
            String tag = word.substring(1);
            return entry -> entry.tags.stream().anyMatch(t -> t.contains(tag));
        }
        if (word.startsWith("$")) {
            String text = word.substring(1);
            // An item whose tooltip is not read yet is kept, so results shrink as the pass finishes.
            return entry -> entry.tooltipLower == null || entry.tooltipLower.contains(text);
        }
        // A variant (an enchanted book) is also found by what its tooltip says, once that is read.
        return entry -> entry.nameLower.contains(word) || entry.id.contains(word)
                || entry.variant && entry.tooltipLower != null && entry.tooltipLower.contains(word);
    }

    public static List<ItemEntry> filter(List<ItemEntry> source, String query, Predicate<ItemEntry> category) {
        Predicate<ItemEntry> predicate = parse(query);
        if (category != null) {
            predicate = predicate.and(category);
        }
        List<ItemEntry> out = new ArrayList<>();
        for (ItemEntry entry : source) {
            if (predicate.test(entry)) {
                out.add(entry);
            }
        }
        return out;
    }
}
