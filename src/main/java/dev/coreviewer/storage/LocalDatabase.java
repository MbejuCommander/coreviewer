package dev.coreviewer.storage;

import dev.coreviewer.model.CoreTraceEvent;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/** JSON is authoritative; CSV is an export regenerated from the same snapshot. */
public final class LocalDatabase {
    private final Path directory;

    public LocalDatabase(Path directory) {
        this.directory = directory;
    }

    public List<CoreTraceEvent> load() throws IOException {
        Path json = directory.resolve("events.json");
        if (!Files.exists(json)) {
            if (Files.exists(directory.resolve("events.csv")))
                throw new IOException(
                        "events.json is missing; existing CSV preserved. Restore the JSON backup"
                                + " before saving.");
            save(List.of(), false);
            return List.of();
        }
        List<CoreTraceEvent> loaded = EventCodec.parseJson(Files.readString(json));
        AtomicFiles.write(directory.resolve("events.csv"), EventCodec.csv(loaded));
        return loaded;
    }

    public void save(List<CoreTraceEvent> snapshot, boolean backup) throws IOException {
        Files.createDirectories(directory);
        Path jsonFile = directory.resolve("events.json");
        if (Files.exists(jsonFile)) EventCodec.parseJson(Files.readString(jsonFile));
        if (backup)
            for (String name : List.of("events.json", "events.csv")) {
                Path source = directory.resolve(name);
                if (Files.exists(source))
                    Files.copy(
                            source,
                            directory.resolve(name + ".bak"),
                            StandardCopyOption.REPLACE_EXISTING);
            }
        String json = EventCodec.json(snapshot), csv = EventCodec.csv(snapshot);
        AtomicFiles.write(directory.resolve("events.csv"), csv);
        AtomicFiles.write(jsonFile, json);
    }
}
