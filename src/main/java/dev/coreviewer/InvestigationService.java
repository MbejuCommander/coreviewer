package dev.coreviewer;

import dev.coreviewer.config.*;
import dev.coreviewer.model.*;
import dev.coreviewer.storage.CsvLibrary;
import dev.coreviewer.view.EventIndex;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** CSV parsing, indexing and writes share one background worker. */
public final class InvestigationService implements AutoCloseable {
    private final CsvLibrary library;
    private final ConfigStore configStore;
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(
                    r -> {
                        var t = new Thread(r, "Coreviewer storage");
                        t.setDaemon(true);
                        return t;
                    });
    private volatile CoreTraceConfig config;

    public record Snapshot(List<CoreTraceEvent> events, EventIndex index) {}

    private volatile Snapshot snapshot = new Snapshot(List.of(), EventIndex.EMPTY);
    private volatile List<CsvLibrary.FileEntry> csvFiles = List.of();
    private volatile String status = "Ready";
    private volatile long captureGeneration;
    private boolean loaded;
    private String currentServer = "", captureName;
    private List<CoreTraceEvent> pendingCapture;
    private long pendingGeneration;
    private Runnable configurationListener = () -> {};

    public InvestigationService(Path directory, CoreTraceConfig config) {
        this.config = ConfigStore.copy(config);
        library = new CsvLibrary(directory);
        configStore = new ConfigStore(directory);
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public List<CoreTraceEvent> events() {
        return snapshot.events();
    }

    public List<CsvLibrary.FileEntry> csvFiles() {
        return csvFiles;
    }

    public void entityNames(Set<String> names) {
        library.entityNames(names);
    }

    public Path csvFolder() {
        return library.folder();
    }

    public CoreTraceConfig config() {
        return ConfigStore.copy(config);
    }

    public boolean enabled() {
        return config.enabled;
    }

    public boolean dirty() {
        return false;
    }

    public String status() {
        return status;
    }

    public void onConfigurationChanged(Runnable listener) {
        configurationListener = listener;
    }

    public void cancelCapture() {
        captureGeneration++;
    }

    private void refreshLibrary() throws Exception {
        var events = library.load(currentServer, config.simpleMode);
        snapshot = new Snapshot(events, new EventIndex(events));
        csvFiles = library.entries();
    }

    private void ensureLoaded() throws Exception {
        if (!loaded) {
            library.initialize();
            refreshLibrary();
            loaded = true;
        }
    }

    public CompletableFuture<String> initialize() {
        return execute(
                () -> {
                    ensureLoaded();
                    return "Loaded " + events().size() + " selected CSV events.";
                });
    }

    public CompletableFuture<String> beginCapture(boolean automatic, String server) {
        long token = captureGeneration;
        return execute(
                () -> {
                    ensureLoaded();
                    if (token != captureGeneration) return "Capture cancelled.";
                    pendingGeneration = token;
                    pendingCapture = new ArrayList<>();
                    currentServer = server;
                    captureName =
                            dev.coreviewer.storage.CaptureFolders.reserve(
                                    library.folder(), config.captureFolderName);
                    return "Capture started.";
                });
    }

    public CompletableFuture<String> capture(
            List<dev.coreviewer.capture.ChatLine> lines, String server, Set<String> entities) {
        long token = captureGeneration;
        return execute(
                () -> {
                    if (!config.autoCapture || token != captureGeneration)
                        return "Capture cancelled.";
                    ensureLoaded();
                    var result =
                            new dev.coreviewer.capture.CoreProtectChatParser()
                                    .parse(lines, server, entities);
                    if (token != captureGeneration) return "Capture cancelled.";
                    if (pendingCapture == null) {
                        pendingCapture = new ArrayList<>();
                        pendingGeneration = token;
                        captureName =
                                dev.coreviewer.storage.CaptureFolders.reserve(
                                        library.folder(), config.captureFolderName);
                    }
                    pendingCapture.addAll(result.events());
                    currentServer = server;
                    if (!pendingCapture.isEmpty()) {
                        if (config.simpleMode)
                            library.writeSimple(captureName, pendingCapture, server, true);
                        else library.write(captureName, pendingCapture, server);
                    }
                    refreshLibrary();
                    return "Saved "
                            + result.events().size()
                            + " events; "
                            + result.skipped()
                            + " unsupported rows.";
                });
    }

    public CompletableFuture<String> finishCapture() {
        long token = captureGeneration;
        return execute(
                () -> {
                    if (token != captureGeneration || pendingGeneration != token)
                        return "Capture cancelled.";
                    pendingCapture = null;
                    return "Capture complete. CSV saved automatically.";
                });
    }

    public CompletableFuture<String> simulate() {
        return execute(
                () -> {
                    ensureLoaded();
                    var demo = SimulatedEvents.create(System.currentTimeMillis());
                    if (config.simpleMode)
                        library.writeSimple(
                                dev.coreviewer.storage.CaptureFolders.reserve(
                                        library.folder(), config.captureFolderName),
                                demo,
                                "simulation",
                                true);
                    else library.write("demo-" + UUID.randomUUID() + ".csv", demo, "simulation");
                    refreshLibrary();
                    return "Saved 6 SIMULATED events.";
                });
    }

    public CompletableFuture<String> save() {
        return execute(
                () -> {
                    ensureLoaded();
                    return "Captures are saved automatically to the CSV library.";
                });
    }

    public CompletableFuture<String> reload() {
        return refreshCsv(currentServer);
    }

    public CompletableFuture<String> clear() {
        cancelCapture();
        return execute(
                () -> {
                    ensureLoaded();
                    pendingCapture = null;
                    library.deleteAll();
                    refreshLibrary();
                    return "All library CSV files deleted.";
                });
    }

    public CompletableFuture<String> refreshCsv(String server) {
        return execute(
                () -> {
                    currentServer = server;
                    ensureLoaded();
                    refreshLibrary();
                    return "CSV library refreshed: " + events().size() + " selected events.";
                });
    }

    public CompletableFuture<String> selectCsv(String name, boolean value, String server) {
        return execute(
                () -> {
                    ensureLoaded();
                    if (config.simpleMode) return "Disable Simple Mode first.";
                    library.select(name, value, server);
                    refreshLibrary();
                    return "CSV selection updated.";
                });
    }

    public CompletableFuture<String> selectAllCsv(boolean value, String server) {
        return execute(
                () -> {
                    ensureLoaded();
                    if (config.simpleMode) return "Disable Simple Mode first.";
                    library.selectAll(value, server);
                    refreshLibrary();
                    return "CSV selection updated.";
                });
    }

    public CompletableFuture<String> moveCsv(String name, int delta) {
        return execute(
                () -> {
                    ensureLoaded();
                    library.move(name, delta);
                    refreshLibrary();
                    return "File order updated. Events remain chronological.";
                });
    }

    public CompletableFuture<String> deleteCsv(String name) {
        cancelCapture();
        return execute(
                () -> {
                    ensureLoaded();
                    pendingCapture = null;
                    library.delete(name);
                    refreshLibrary();
                    return "CSV deleted.";
                });
    }

    public CompletableFuture<String> configure(CoreTraceConfig updated) {
        updated.validate();
        var next = ConfigStore.copy(updated);
        boolean enteringSimple = next.simpleMode && !config.simpleMode;
        if (!next.enabled || !next.autoCapture || next.simpleMode != config.simpleMode)
            cancelCapture();
        config = next;
        configurationListener.run();
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        if (enteringSimple) {
                            if (!loaded) {
                                library.initialize();
                                loaded = true;
                            }
                            var previous = library.load(currentServer, false);
                            library.writeSimple(
                                    dev.coreviewer.storage.CaptureFolders.reserve(
                                            library.folder(), next.captureFolderName),
                                    previous,
                                    currentServer,
                                    false);
                            library.enterSimple();
                        }
                        configStore.save(next);
                        if (next.enabled) {
                            ensureLoaded();
                            refreshLibrary();
                        }
                        return status = "Configuration saved.";
                    } catch (Exception ex) {
                        return status = "Storage error: " + ex.getMessage();
                    }
                },
                worker);
    }

    private CompletableFuture<String> execute(Work work) {
        if (!enabled())
            return CompletableFuture.completedFuture("COREVIEWER DISABLED. Enable it in Settings.");
        return CompletableFuture.supplyAsync(
                () -> {
                    if (!enabled()) return "COREVIEWER DISABLED.";
                    try {
                        return status = work.run();
                    } catch (Exception ex) {
                        return status = "Storage error: " + ex.getMessage();
                    }
                },
                worker);
    }

    @FunctionalInterface
    private interface Work {
        String run() throws Exception;
    }

    @Override
    public void close() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) worker.shutdownNow();
        } catch (InterruptedException ex) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
