package dev.coreviewer.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Copies old shared data once while preserving the original files. */
public final class LegacyDataMigration {
    private static final String[] FILES = {
        "config.json", "events.json", "events.csv",
        "config.json.bak", "events.json.bak", "events.csv.bak"
    };

    private LegacyDataMigration() {}

    public static boolean copyIfMissing(Path legacy, Path current) throws IOException {
        if (!Files.isDirectory(legacy) || Files.exists(current)) return false;
        Files.createDirectories(current);
        boolean configCopied = false;
        for (String name : FILES) {
            Path source = legacy.resolve(name);
            Path target = current.resolve(name);
            if (!Files.isRegularFile(source) || Files.exists(target)) continue;
            Files.copy(source, target);
            if (name.equals("config.json")) configCopied = true;
        }
        return configCopied;
    }
}
