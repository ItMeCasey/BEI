package com.itmecasey.bei.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One-time move of the old {@code config/bei_client.json} (written by releases 2026.11.10 to 2026.11.19): the sort order goes
 * into the loader's client config ({@code bei-client.toml}), the saved lists (bookmarks, items hidden in edit mode) into
 * {@code config/bei-data.json}, and the old file is then kept as {@code bei_client.json.back}. Steps, in order: parse the
 * old file; write the lists to the data file and re-read them; write the sort order to the config and re-read it from the file;
 * write the backup (a first line saying where and why, then the exact original bytes) and re-read it; only then remove the original.
 * If any step fails the original stays where it is and no backup is left behind. Plain Java and Gson, no loader classes.
 */
public final class ClientMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger("bei");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** The one setting the old file held; every other key is player data. */
    public static final String SORT_KEY = "sort";
    /** The values the sort order may have, in the form both files use. */
    public static final List<String> SORT_VALUES = List.of("creative", "name", "mod");
    /** Player data kept in the data file (not settings): bookmarks and the items hidden in edit mode. */
    public static final List<String> DATA_KEYS = List.of("bookmarks", "hidden");

    /** The client config the setting goes to. */
    public interface Target {
        /** The sort order the config has now. */
        String current();

        /** Writes the sort order to the config file. */
        void write(String sortOrder);

        /** The sort order as the config file on disk says it (read again from the file), or null if it cannot be read. */
        String readBack();
    }

    /** What a migration did. */
    public record Result(boolean moved, String sortOrder, Map<String, Integer> copied, List<String> invalid, Path backup) {
    }

    private ClientMigration() {
    }

    private static Result left(Map<String, Integer> copied, List<String> invalid, String sort, String why) {
        LOGGER.warn("BEI left config/bei_client.json where it is: {}", why);
        return new Result(false, sort, copied, invalid, null);
    }

    /** Moves the old file's contents to their new homes; does nothing if there is no old file. */
    public static Result migrate(Path oldFile, Path dataFile, String version, String tomlName, Target target) {
        Map<String, Integer> copied = new LinkedHashMap<>();
        List<String> invalid = new ArrayList<>();
        if (!Files.isRegularFile(oldFile)) {
            return new Result(false, null, copied, invalid, null);
        }
        byte[] original;
        JsonObject old;
        try {
            original = Files.readAllBytes(oldFile);
            JsonElement parsed = JsonParser.parseString(new String(original, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return left(copied, invalid, null, "the file is not a JSON object");
            }
            old = parsed.getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            return left(copied, invalid, null, "it could not be read (" + e.getMessage() + ")");
        }

        String sort = null;
        JsonElement sortValue = old.get(SORT_KEY);
        if (sortValue != null) {
            if (sortValue.isJsonPrimitive() && sortValue.getAsJsonPrimitive().isString() && SORT_VALUES.contains(sortValue.getAsString())) {
                sort = sortValue.getAsString();
            } else {
                invalid.add("sort = " + sortValue + " is not one of " + SORT_VALUES + "; the default is used");
            }
        }
        JsonObject data = new JsonObject();
        for (String key : DATA_KEYS) {
            JsonElement list = old.get(key);
            if (list == null) {
                continue;
            }
            if (!list.isJsonArray()) {
                invalid.add(key + " is not a list; skipped");
                continue;
            }
            Set<String> ids = new LinkedHashSet<>();
            for (JsonElement element : list.getAsJsonArray()) {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    ids.add(element.getAsString());
                } else {
                    invalid.add(key + " has an entry that is not an item id: " + element);
                }
            }
            JsonArray array = new JsonArray();
            ids.forEach(array::add);
            data.add(key, array);
            copied.put(key, ids.size());
        }

        // The lists first (if the data file already exists an earlier run wrote it; it is kept as it is).
        try {
            if (!Files.exists(dataFile)) {
                Files.createDirectories(dataFile.getParent());
                Files.writeString(dataFile, GSON.toJson(data), StandardCharsets.UTF_8);
            }
            JsonObject back = JsonParser.parseString(Files.readString(dataFile, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : DATA_KEYS) {
                if (data.has(key) && !back.has(key)) {
                    return left(copied, invalid, sort, "the data file does not hold " + key + " after writing it");
                }
            }
        } catch (IOException | RuntimeException e) {
            return left(copied, invalid, sort, "the data file could not be written (" + e.getMessage() + ")");
        }

        // The setting: only while the config still has its default (a value the player already set there wins).
        if (sort != null) {
            try {
                String current = target.current();
                if ("creative".equals(current) && !sort.equals(current)) {
                    target.write(sort);
                    String onDisk = target.readBack();
                    if (!sort.equals(onDisk)) {
                        return left(copied, invalid, sort, "the config file says sortOrder = " + onDisk + " after writing " + sort);
                    }
                }
            } catch (RuntimeException e) {
                return left(copied, invalid, sort, "the config could not be written (" + e.getMessage() + ")");
            }
        }

        // The backup: a header line, then the original bytes; never over an existing backup.
        Path backup = oldFile.resolveSibling(oldFile.getFileName() + ".back");
        for (int n = 2; Files.exists(backup); n++) {
            backup = oldFile.resolveSibling(oldFile.getFileName() + ".back" + n);
        }
        try {
            byte[] header = ("# Migrated by BEI " + version + " on " + LocalDate.now() + " to config/" + tomlName
                    + " (settings) and config/" + dataFile.getFileName() + " (saved lists); this file is no longer read. Kept as a backup.\n")
                    .getBytes(StandardCharsets.UTF_8);
            byte[] all = new byte[header.length + original.length];
            System.arraycopy(header, 0, all, 0, header.length);
            System.arraycopy(original, 0, all, header.length, original.length);
            Files.write(backup, all, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            byte[] read = Files.readAllBytes(backup);
            if (!Arrays.equals(Arrays.copyOfRange(read, header.length, read.length), original) || read.length != all.length) {
                Files.deleteIfExists(backup);
                return left(copied, invalid, sort, "the backup did not hold the original content");
            }
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(backup);
            } catch (IOException ignored) {
                // nothing more to do
            }
            return left(copied, invalid, sort, "the backup could not be written (" + e.getMessage() + ")");
        }
        try {
            Files.delete(oldFile);
        } catch (IOException e) {
            return left(copied, invalid, sort, "the original could not be removed (" + e.getMessage() + ")");
        }
        LOGGER.info("BEI migrated {} -> {} (backup) and config/{}; sort order {}, copied {}", oldFile, backup, tomlName,
                sort == null ? "(default)" : sort, copied);
        for (String problem : invalid) {
            LOGGER.warn("BEI could not use part of {}: {}", oldFile.getFileName(), problem);
        }
        return new Result(true, sort, copied, invalid, backup);
    }
}
