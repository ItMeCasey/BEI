package com.itmecasey.bei.client;

import com.itmecasey.bei.BeiConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The themes available: the built-in dark and light, plus any the server's datapacks sent. */
public final class Themes {
    private static volatile Map<String, Theme> custom = Map.of();

    private Themes() {
    }

    /** Replaces the datapack themes with the ones just received (id to JSON text). */
    public static void setCustom(Map<String, String> json) {
        Map<String, Theme> parsed = new LinkedHashMap<>();
        json.forEach((id, text) -> {
            Theme theme = Theme.parse(id, text, Theme.DARK);
            if (theme != null) {
                parsed.put(id, theme);
            }
        });
        custom = parsed;
    }

    public static List<String> ids() {
        List<String> ids = new ArrayList<>(List.of(Theme.DARK.id(), Theme.LIGHT.id()));
        ids.addAll(custom.keySet());
        return ids;
    }

    public static Theme get(String id) {
        if (id.equals(Theme.LIGHT.id())) {
            return Theme.LIGHT;
        }
        Theme found = custom.get(id);
        return found != null ? found : Theme.DARK;
    }

    public static Theme current() {
        return get(BeiConfig.theme());
    }

    /** The theme after the current one, wrapping around. */
    public static String next() {
        List<String> ids = ids();
        int at = ids.indexOf(BeiConfig.theme());
        return ids.get((at + 1) % ids.size());
    }
}
