package com.itmecasey.bei.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ColorNamesTest {
    @Test
    void basicColorsFromRgb() {
        assertEquals("red", ColorNames.fromRgb(0xE02020));
        assertEquals("green", ColorNames.fromRgb(0x20C040));
        assertEquals("blue", ColorNames.fromRgb(0x2040E0));
        assertEquals("yellow", ColorNames.fromRgb(0xE0E020));
        assertEquals("white", ColorNames.fromRgb(0xF8F8F8));
        assertEquals("black", ColorNames.fromRgb(0x101010));
        assertEquals("gray", ColorNames.fromRgb(0x808080));
        assertEquals("brown", ColorNames.fromRgb(0x6B4423));
    }

    @Test
    void wordsComeFromTheIdAndName() {
        String words = ColorNames.of("minecraft:light_blue_wool light blue wool", -1);
        assertTrue(words.contains("light_blue"));
        assertTrue(words.contains("lightblue"));
        assertTrue(words.contains("blue"));
    }

    @Test
    void aKnownColorIsAdded() {
        assertTrue(ColorNames.of("minecraft:stone stone", 0x7F7F7F).contains("gray"));
    }
}
