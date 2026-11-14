package com.itmecasey.bei.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.bei.Bei;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * BEI's saved client data that is not a setting: bookmarks, items hidden in edit mode and the sort order, in
 * {@code config/bei-data.json} of the game folder (so it is kept per profile). Plain JSON, no loader classes: the loaders
 * have no store for client data, only for settings, and the settings (the sort order included) live in {@code bei-client.toml}.
 * The first run moves the old {@code config/bei_client.json} here and into the config, see {@link ClientMigration}.
 */
public final class ClientData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static JsonObject root;
    private static Path testFile;

    private ClientData() {
    }

    /** Points the data at another file (unit tests); null goes back to the game folder. */
    public static synchronized void useFile(Path file) {
        testFile = file;
        root = null;
    }

    private static Path file() {
        if (testFile != null) {
            return testFile;
        }
        return Path.of(String.valueOf(Minecraft.getInstance().gameDirectory)).resolve("config").resolve("bei-data.json");
    }

    /** Forgets what was read, so the next call reads the data file again (after the import wrote it). */
    public static synchronized void reload() {
        root = null;
    }

    private static boolean migrated;

    /**
     * Runs the one-time move of the old {@code bei_client.json} once the client config is loaded; call every tick until it has run
     * (a file that could not be moved is tried again on the next launch).
     */
    public static synchronized void migrateOnce() {
        if (migrated || !com.itmecasey.bei.BeiConfig.isLoaded() || testFile != null) {
            return;
        }
        migrated = true;
        Path config = Path.of(String.valueOf(Minecraft.getInstance().gameDirectory)).resolve("config");
        String tomlName = "bei-client.toml";
        String version = net.neoforged.fml.ModList.get().getModContainerById(Bei.MODID).map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        ClientMigration.migrate(config.resolve("bei_client.json"), config.resolve("bei-data.json"), version, tomlName, new ClientMigration.Target() {
            @Override
            public String current() {
                return com.itmecasey.bei.BeiConfig.sortOrder();
            }

            @Override
            public void write(String sortOrder) {
                com.itmecasey.bei.BeiConfig.setSortOrder(sortOrder);
            }

            @Override
            public String readBack() {
                try {
                    var m = java.util.regex.Pattern.compile("(?m)^\\s*sortOrder\\s*=\\s*\"([^\"]*)\"").matcher(Files.readString(config.resolve(tomlName), StandardCharsets.UTF_8));
                    return m.find() ? m.group(1) : null;
                } catch (IOException | RuntimeException e) {
                    return null;
                }
            }
        });
        root = null;
    }

    private static JsonObject data() {
        if (root == null) {
            root = new JsonObject();
            try {
                Path file = file();
                if (Files.exists(file)) {
                    JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                    if (parsed.isJsonObject()) {
                        root = parsed.getAsJsonObject();
                    }
                }
            } catch (IOException | RuntimeException e) {
                Bei.LOGGER.warn("Could not read BEI's saved client data; starting empty", e);
            }
        }
        return root;
    }

    public static synchronized List<String> list(String key) {
        List<String> out = new ArrayList<>();
        JsonElement value = data().get(key);
        if (value != null && value.isJsonArray()) {
            for (JsonElement element : value.getAsJsonArray()) {
                if (element.isJsonPrimitive()) {
                    out.add(element.getAsString());
                }
            }
        }
        return out;
    }

    public static synchronized void setList(String key, List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        data().add(key, array);
        save();
    }

    public static synchronized String string(String key, String fallback) {
        JsonElement value = data().get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    public static synchronized void setString(String key, String value) {
        data().addProperty(key, value);
        save();
    }

    private static void save() {
        try {
            Path file = file();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(data()), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            Bei.LOGGER.warn("Could not save BEI's client data", e);
        }
    }
}
