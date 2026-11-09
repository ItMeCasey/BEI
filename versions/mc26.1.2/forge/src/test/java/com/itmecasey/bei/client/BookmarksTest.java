package com.itmecasey.bei.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The saved lists behind bookmarks and edit mode: order, toggling and surviving a restart. */
class BookmarksTest {
    @TempDir
    Path dir;

    @BeforeEach
    void point() {
        ClientData.useFile(dir.resolve("config").resolve("bei_client.json"));
    }

    @Test
    void toggleAddsThenRemoves() {
        IdList list = new IdList("test");
        assertTrue(list.toggle("minecraft:stone"));
        assertTrue(list.contains("minecraft:stone"));
        assertFalse(list.toggle("minecraft:stone"));
        assertTrue(list.isEmpty());
    }

    @Test
    void orderIsKeptAndSurvivesAReload() throws IOException {
        IdList list = new IdList("marks");
        list.toggle("minecraft:b");
        list.toggle("minecraft:a");
        list.toggle("minecraft:c");
        assertTrue(Files.exists(dir.resolve("config").resolve("bei_client.json")));
        ClientData.useFile(dir.resolve("config").resolve("bei_client.json"));
        IdList again = new IdList("marks");
        assertEquals(List.of("minecraft:b", "minecraft:a", "minecraft:c"), again.all());
    }

    @Test
    void listsAndSettingsDoNotClash() {
        IdList marks = new IdList("bookmarks");
        IdList hidden = new IdList("hidden");
        marks.toggle("minecraft:stone");
        hidden.toggle("minecraft:dirt");
        ClientData.setString("sort", "name");
        assertEquals(List.of("minecraft:stone"), marks.all());
        assertEquals(List.of("minecraft:dirt"), hidden.all());
        assertEquals("name", ClientData.string("sort", "creative"));
        assertEquals("fallback", ClientData.string("missing", "fallback"));
    }

    @Test
    void aBrokenFileStartsEmpty() throws IOException {
        Path file = dir.resolve("config").resolve("bei_client.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{ not json");
        ClientData.useFile(file);
        assertTrue(new IdList("x").isEmpty());
    }
}
