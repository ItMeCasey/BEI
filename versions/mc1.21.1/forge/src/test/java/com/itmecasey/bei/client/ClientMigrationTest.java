package com.itmecasey.bei.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The move of the old config/bei_client.json. The fixtures are files in the form the releases wrote
 * ({@code released-default.json} was copied from a client run of a release build; the others are the same format with other contents).
 */
class ClientMigrationTest {
    /** A config that keeps its value in a toml-like file next to the test files. */
    static final class FakeConfig implements ClientMigration.Target {
        String value = "creative";
        boolean breakWrite;
        int writes;

        @Override
        public String current() {
            return value;
        }

        @Override
        public void write(String sortOrder) {
            writes++;
            if (!breakWrite) {
                value = sortOrder;
            }
        }

        @Override
        public String readBack() {
            return value;
        }
    }

    @TempDir
    Path dir;
    Path old;
    Path data;
    FakeConfig config;

    @BeforeEach
    void paths() throws IOException {
        Path folder = dir.resolve("config");
        Files.createDirectories(folder);
        old = folder.resolve("bei_client.json");
        data = folder.resolve("bei-data.json");
        config = new FakeConfig();
    }

    private void fixture(String name) throws IOException {
        try (InputStream in = ClientMigrationTest.class.getResourceAsStream("/bei-fixtures/" + name)) {
            Files.write(old, in.readAllBytes());
        }
    }

    private ClientMigration.Result run() {
        return ClientMigration.migrate(old, data, "2026.11.20", "bei-client.toml", config);
    }

    @Test
    void successMovesTheSettingAndTheListsAndKeepsAnExactBackup() throws IOException {
        fixture("released-used.json");
        byte[] original = Files.readAllBytes(old);
        var result = run();
        assertTrue(result.moved());
        assertEquals("mod", config.value);
        assertFalse(Files.exists(old));
        Path back = dir.resolve("config").resolve("bei_client.json.back");
        assertEquals(back, result.backup());
        byte[] all = Files.readAllBytes(back);
        String text = new String(all, StandardCharsets.UTF_8);
        String header = text.substring(0, text.indexOf('\n') + 1);
        assertTrue(header.startsWith("# Migrated by BEI 2026.11.20 on "));
        assertTrue(header.contains("bei-client.toml") && header.contains("no longer read"));
        assertArrayEquals(original, Arrays.copyOfRange(all, header.getBytes(StandardCharsets.UTF_8).length, all.length));
    }

    @Test
    void theSavedListsSurviveInTheirOwnFileInOrder() throws IOException {
        fixture("released-used.json");
        run();
        ClientData.useFile(data);
        assertEquals(List.of("minecraft:diamond", "minecraft:stone", "create:cogwheel"), new IdList("bookmarks").all());
        assertEquals(List.of("minecraft:dirt", "minecraft:cobblestone"), new IdList("hidden").all());
    }

    @Test
    void aReleasedDefaultFileMigratesToTheDefaults() throws IOException {
        fixture("released-default.json");
        var result = run();
        assertTrue(result.moved());
        assertEquals("creative", config.value);
        assertEquals(0, config.writes);
        assertTrue(result.invalid().isEmpty());
    }

    @Test
    void aFailedConfigWriteKeepsTheOriginalAndLeavesNoBackup() throws IOException {
        fixture("released-used.json");
        byte[] original = Files.readAllBytes(old);
        config.breakWrite = true;
        var result = run();
        assertFalse(result.moved());
        assertArrayEquals(original, Files.readAllBytes(old));
        assertFalse(Files.exists(dir.resolve("config").resolve("bei_client.json.back")));
    }

    @Test
    void aCorruptFileStaysAndCreatesNoBackup() throws IOException {
        fixture("corrupt.json");
        byte[] original = Files.readAllBytes(old);
        var result = run();
        assertFalse(result.moved());
        assertNull(result.backup());
        assertArrayEquals(original, Files.readAllBytes(old));
        assertFalse(Files.exists(dir.resolve("config").resolve("bei_client.json.back")));
        assertEquals("creative", config.value);
    }

    @Test
    void anEmptyFileCountsAsCorruptAndStays() throws IOException {
        fixture("empty.json");
        assertFalse(run().moved());
        assertTrue(Files.exists(old));
    }

    @Test
    void anEmptyObjectMovesWithDefaults() throws IOException {
        fixture("empty-object.json");
        var result = run();
        assertTrue(result.moved());
        assertNull(result.sortOrder());
        assertTrue(result.invalid().isEmpty());
    }

    @Test
    void anExistingBackupIsNeverOverwritten() throws IOException {
        fixture("released-used.json");
        Path back = dir.resolve("config").resolve("bei_client.json.back");
        Files.writeString(back, "somebody's earlier backup");
        var result = run();
        assertTrue(result.moved());
        assertEquals("somebody's earlier backup", Files.readString(back));
        assertEquals(dir.resolve("config").resolve("bei_client.json.back2"), result.backup());
        fixture("released-used.json");
        assertEquals(dir.resolve("config").resolve("bei_client.json.back3"), run().backup());
        assertEquals("somebody's earlier backup", Files.readString(back));
    }

    @Test
    void aSecondLaunchDoesNothing() throws IOException {
        fixture("released-used.json");
        assertTrue(run().moved());
        Files.writeString(data, "{\"bookmarks\":[\"minecraft:gold_ingot\"]}");
        config.value = "name";
        var again = run();
        assertFalse(again.moved());
        assertEquals("{\"bookmarks\":[\"minecraft:gold_ingot\"]}", Files.readString(data));
        assertEquals("name", config.value);
    }

    @Test
    void aSettingThePlayerAlreadyChangedInTheConfigWins() throws IOException {
        fixture("released-used.json");
        config.value = "name";
        var result = run();
        assertTrue(result.moved());
        assertEquals("name", config.value);
    }

    @Test
    void aPartialFileTakesWhatIsThere() throws IOException {
        fixture("partial-lists-only.json");
        var lists = run();
        assertNull(lists.sortOrder());
        assertEquals(1, lists.copied().get("bookmarks"));
        assertTrue(lists.moved());
        fixture("partial-sort-only.json");
        Files.delete(data);
        var sort = run();
        assertEquals("name", sort.sortOrder());
        assertEquals("name", config.value);
        assertTrue(sort.copied().isEmpty());
    }

    @Test
    void invalidValuesFallBackAndUnknownKeysAreIgnored() throws IOException {
        fixture("invalid-values.json");
        var result = run();
        assertTrue(result.moved());
        assertNull(result.sortOrder());
        assertEquals("creative", config.value);
        assertEquals(1, result.copied().get("bookmarks"));
        assertFalse(result.copied().containsKey("hidden"));
        assertTrue(result.invalid().size() >= 3);
        ClientData.useFile(data);
        assertEquals(List.of("minecraft:stone"), new IdList("bookmarks").all());
        assertFalse(Files.readString(data).contains("somethingElse"));
    }

    @Test
    void noOldFileMeansNothingToDo() {
        var result = run();
        assertFalse(result.moved());
        assertFalse(Files.exists(data));
    }
}
