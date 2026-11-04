package com.itmecasey.bei.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

/**
 * A color scheme for every BEI surface. Colors are 0xRRGGBB; the alpha of backdrops comes from the
 * opacity setting. Datapacks add themes as JSON under {@code data/<namespace>/bei_themes/}.
 */
public record Theme(String id, String name, int bg, int card, int cardHover, int field, int accent, int text, int dim,
                    int good, int bad, int warn, int border, int tooltip) {

    /** The key names a theme file may set; any it leaves out come from its base theme. */
    static final String[] KEYS = {"bg", "card", "card_hover", "field", "accent", "text", "dim", "good", "bad", "warn", "border", "tooltip"};

    public static final Theme DARK = new Theme("bei:dark", "Dark", 0x141821, 0x1E2430, 0x2A3342, 0x0E1117, 0x4F9DFF,
            0xE6EAF2, 0x8A93A6, 0x6FD58A, 0xE5767D, 0xFFC66D, 0x4A5468, 0x101218);

    public static final Theme LIGHT = new Theme("bei:light", "Light", 0xF2F4F8, 0xE1E6EF, 0xD2DAE7, 0xFFFFFF, 0x2F6FDB,
            0x1B2230, 0x5B6578, 0x1E8E3E, 0xC5313B, 0xB36B00, 0xAEB8C8, 0xFAFBFD);

    /**
     * Reads a theme file: {@code {"name": "Neon", "base": "bei:dark", "colors": {"accent": "#ff00ff"}}}.
     * Returns null if the text is not a JSON object. Bad color values are ignored.
     */
    static @Nullable Theme parse(String id, String json, Theme fallbackBase) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject object = root.getAsJsonObject();
            Theme base = object.has("base") && object.get("base").getAsString().equals(LIGHT.id) ? LIGHT : fallbackBase;
            String name = object.has("name") ? trim(object.get("name").getAsString(), 40) : trim(id, 40);
            JsonObject colors = object.has("colors") && object.get("colors").isJsonObject() ? object.getAsJsonObject("colors") : new JsonObject();
            int[] values = {base.bg, base.card, base.cardHover, base.field, base.accent, base.text, base.dim, base.good,
                    base.bad, base.warn, base.border, base.tooltip};
            for (int i = 0; i < KEYS.length; i++) {
                if (colors.has(KEYS[i])) {
                    values[i] = color(colors.get(KEYS[i]).getAsString(), values[i]);
                }
            }
            return new Theme(id, name, values[0], values[1], values[2], values[3], values[4], values[5], values[6],
                    values[7], values[8], values[9], values[10], values[11]);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String trim(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    private static int color(String text, int fallback) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        try {
            return (int) (Long.parseLong(hex, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
