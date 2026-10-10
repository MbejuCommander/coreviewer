package dev.coreviewer.storage;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class CaptureFolders {
    public static String validate(String value) {
        String name = value.strip();
        if (name.isEmpty()) return "";
        if (name.length() > 100
                || name.equals(".")
                || name.equals("..")
                || name.endsWith(".")
                || name.chars().anyMatch(c -> c < 32 || "<>:\"/\\|?*".indexOf(c) >= 0))
            throw new IllegalArgumentException(
                    "Use a folder name without path separators or special characters (max 100"
                        + " characters).");
        if (name.split("\\.", 2)[0]
                .toUpperCase(Locale.ROOT)
                .matches("CON|PRN|AUX|NUL|CLOCK\\$|CONIN\\$|CONOUT\\$|COM[1-9¹²³]|LPT[1-9¹²³]"))
            throw new IllegalArgumentException("Reserved folder name.");
        return name;
    }

    public static String reserve(Path root, String requested) throws IOException {
        Files.createDirectories(root);
        String name = validate(requested);
        if (name.isEmpty())
            name =
                    "capture-"
                            + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                                    .format(LocalDateTime.now())
                            + "-"
                            + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 1; ; i++) {
            String candidate = name + (i == 1 ? "" : " (" + i + ")");
            try {
                Files.createDirectory(root.resolve(candidate));
                return candidate + "/events.csv";
            } catch (FileAlreadyExistsException ignored) {
            }
        }
    }
}
