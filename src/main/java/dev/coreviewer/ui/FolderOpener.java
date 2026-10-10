package dev.coreviewer.ui;

import dev.coreviewer.CoreTraceClient;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class FolderOpener {
    public static List<String> command(String os, Path path) {
        String folder = path.toAbsolutePath().normalize().toString();
        String system = os.toLowerCase(Locale.ROOT);
        return List.of(
                system.startsWith("windows")
                        ? "explorer.exe"
                        : system.contains("mac") ? "open" : "xdg-open",
                folder);
    }

    public static void open(Path path) {
        CompletableFuture.runAsync(
                () -> {
                    try {
                        Files.createDirectories(path);
                        new ProcessBuilder(command(System.getProperty("os.name"), path)).start();
                    } catch (Exception ex) {
                        CoreTraceClient.tell(
                                "Could not open folder: " + path + " (" + ex.getMessage() + ")");
                    }
                });
    }
}
