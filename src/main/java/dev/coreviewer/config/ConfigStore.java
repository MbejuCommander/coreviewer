package dev.coreviewer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.coreviewer.storage.AtomicFiles;

import java.io.IOException;
import java.nio.file.*;

public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;

    public ConfigStore(Path directory) {
        file = directory.resolve("config.json");
    }

    public CoreTraceConfig load() throws IOException {
        if (!Files.exists(file)) {
            var c = new CoreTraceConfig();
            save(c);
            return c;
        }
        try {
            var json =
                    com.google.gson.JsonParser.parseString(Files.readString(file))
                            .getAsJsonObject();
            var c = GSON.fromJson(json, CoreTraceConfig.class);
            if (!json.has("smartTimeline")) {
                if (c.commandDelayMs == 3000) c.commandDelayMs = 1500;
                if (c.maxVisibleEvents == 500) c.maxVisibleEvents = 10;
                if ("key.keyboard.space".equals(c.playPauseKey))
                    c.playPauseKey = "key.keyboard.right.shift";
                if ("key.keyboard.right".equals(c.forwardKey)) c.forwardKey = "key.keyboard.comma";
                if ("key.keyboard.left".equals(c.backwardKey))
                    c.backwardKey = "key.keyboard.period";
            }
            if (c == null) throw new IllegalArgumentException("Empty configuration");
            c.validate();
            return c;
        } catch (RuntimeException ex) {
            throw new IOException("Invalid config.json; original file preserved", ex);
        }
    }

    public void save(CoreTraceConfig config) throws IOException {
        config.validate();
        if (Files.exists(file))
            Files.copy(
                    file,
                    file.resolveSibling("config.json.bak"),
                    StandardCopyOption.REPLACE_EXISTING);
        AtomicFiles.write(file, GSON.toJson(config) + "\n");
    }

    public static CoreTraceConfig copy(CoreTraceConfig config) {
        return config.copy();
    }
}
