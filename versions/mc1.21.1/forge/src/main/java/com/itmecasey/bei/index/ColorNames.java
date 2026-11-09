package com.itmecasey.bei.index;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** The color words an item can be found by with {@code ^red}: from its id and name, and from a color it is known by. */
public final class ColorNames {
    private static final String[] WORDS = {"white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow", "lime", "green",
            "cyan", "light_blue", "blue", "purple", "magenta", "pink"};

    private ColorNames() {
    }

    /** Space-separated color words for an item id/name and an optional 0xRRGGBB color (-1 for none). */
    public static String of(String text, int rgb) {
        Set<String> found = new LinkedHashSet<>();
        String lower = text.toLowerCase(Locale.ROOT).replace(' ', '_');
        for (String word : WORDS) {
            if (lower.contains(word)) {
                found.add(word.replace("_", ""));
                found.add(word);
            }
        }
        if (rgb >= 0) {
            found.add(fromRgb(rgb));
        }
        return String.join(" ", found);
    }

    /** The nearest basic color word for 0xRRGGBB. */
    public static String fromRgb(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255F;
        float g = ((rgb >> 8) & 0xFF) / 255F;
        float b = (rgb & 0xFF) / 255F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float saturation = max == 0 ? 0 : delta / max;
        if (saturation < 0.18F) {
            return max > 0.85F ? "white" : max > 0.2F ? "gray" : "black";
        }
        float hue;
        if (max == r) {
            hue = 60 * (((g - b) / delta) % 6);
        } else if (max == g) {
            hue = 60 * ((b - r) / delta + 2);
        } else {
            hue = 60 * ((r - g) / delta + 4);
        }
        if (hue < 0) {
            hue += 360;
        }
        if (hue < 15 || hue >= 345) {
            return max < 0.45F ? "brown" : "red";
        }
        if (hue < 40) {
            return max < 0.7F ? "brown" : "orange";
        }
        if (hue < 70) {
            return "yellow";
        }
        if (hue < 100) {
            return "lime";
        }
        if (hue < 165) {
            return "green";
        }
        if (hue < 200) {
            return "cyan";
        }
        if (hue < 260) {
            return "blue";
        }
        if (hue < 300) {
            return "purple";
        }
        return hue < 330 ? "magenta" : "pink";
    }
}
