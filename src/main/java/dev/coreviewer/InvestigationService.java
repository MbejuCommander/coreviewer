package dev.coreviewer;

import dev.coreviewer.config.*;
import dev.coreviewer.model.*;
import dev.coreviewer.storage.LocalDatabase;
import dev.coreviewer.view.EventIndex;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * All database mutations are serialized off the Minecraft thread. No polling or scheduled tasks.
 */
public final class InvestigationService implements AutoCloseable {
    private final LocalDatabase database;
    private final ConfigStore configStore;
    private final ThreadPoolExecutor worker =
            new ThreadPoolExecutor(
                    0,
                    1,
                    1,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(),
                    r -> {
                        var t = new Thread(r, "Coreviewer local storage");
                        t.setDaemon(true);
                        return t;
                    });
    private volatile CoreTraceConfig config;

    public record Snapshot(List<CoreTraceEvent> events, EventIndex index) {}

    private volatile Snapshot snapshot = new Snapshot(List.of(), EventIndex.EMPTY);

    public Snapshot snapshot() {
        return snapshot;
    }

    private void publish(List<CoreTraceEvent> next) {
        var immutable = List.copyOf(next);
        snapshot = new Snapshot(immutable, new EventIndex(immutable));
    }

    private volatile boolean dirty, loaded;
    private volatile long captureGeneration;
    private Runnable configurationListener = () -> {};

    public void onConfigurationChanged(Runnable listener) {
        configurationListener = listener;
    }

    public void cancelCapture() {
        captureGeneration++;
    }

    private List<CoreTraceEvent> pendingCapture;
    private String pendingServer = "";
    private long pendingGeneration;

    public CompletableFuture<String> beginCapture(boolean automatic, String server) {
        long token = captureGeneration;
        return execute(
                () -> {
                    ensureLoaded();
                    pendingCapture = null;
                    if (token != captureGeneration) return "Capture cancelled.";
                    pendingGeneration = token;
                    pendingCapture =
                            automatic && config.clearPreviousCapture ? new ArrayList<>() : null;
                    pendingServer = server;
                    return "Capture started.";
                });
    }

    public CompletableFuture<String> finishCapture() {
        long token = captureGeneration;
        return execute(
                () -> {
                    if (token != captureGeneration
                            || (pendingCapture != null && pendingGeneration != token)) {
                        pendingCapture = null;
                        return "Capture cancelled.";
                    }
                    if (pendingCapture == null) return "Capture complete.";
                    var next = new ArrayList<CoreTraceEvent>();
                    int removed = 0;
                    for (var event : events()) {
                        if (!event.context().simulated()
                                && event.context().server().equals(pendingServer)
                                && event.context().source().startsWith("COREPROTECT_CHAT"))
                            removed++;
                        else next.add(event);
                    }
                    next.addAll(pendingCapture);
                    var immutable = List.copyOf(next);
                    var prepared = new Snapshot(immutable, new EventIndex(immutable));
                    boolean saved = config.autoSave;
                    if (saved) database.save(immutable, config.backup);
                    snapshot = prepared;
                    pendingCapture = null;
                    dirty = !saved;
                    return "Capture complete: replaced "
                            + removed
                            + " previous records on this server. "
                            + (dirty ? "Use Save to update JSON/CSV." : "JSON/CSV updated.");
                });
    }

    public CompletableFuture<String> capture(
            java.util.List<dev.coreviewer.capture.ChatLine> lines,
            String server,
            java.util.Set<String> entities) {
        long generation = captureGeneration;
        return execute(
                () -> {
                    if (!config.autoCapture || generation != captureGeneration)
                        return "Capture cancelled.";
                    var result =
                            new dev.coreviewer.capture.CoreProtectChatParser()
                                    .parse(lines, server, entities);
                    ensureLoaded();
                    if (!enabled() || !config.autoCapture || generation != captureGeneration)
                        return "Capture cancelled.";
                    if (pendingCapture != null) {
                        pendingCapture.addAll(result.events());
                        return "Staged " + result.events().size() + " captured records.";
                    }
                    if (!result.events().isEmpty()) {
                        var next = new ArrayList<>(events());
                        next.addAll(result.events());
                        publish(next);
                        dirty = true;
                        if (config.autoSave) persist();
                    }
                    return "Captured "
                            + result.events().size()
                            + " events; skipped "
                            + result.skipped()
                            + " incomplete/unsupported rows.";
                });
    }

    private volatile String status = "Ready — Phase 5 (indexed investigation)";

    public InvestigationService(Path directory, CoreTraceConfig config) {
        this.config = ConfigStore.copy(config);
        database = new LocalDatabase(directory);
        configStore = new ConfigStore(directory);
    }

    public CoreTraceConfig config() {
        return ConfigStore.copy(config);
    }

    public List<CoreTraceEvent> events() {
        return snapshot.events();
    }

    public boolean dirty() {
        return dirty;
    }

    public String status() {
        return status;
    }

    public boolean enabled() {
        return config.enabled;
    }

    public CompletableFuture<String> initialize() {
        return execute(
                () -> {
                    ensureLoaded();
                    return "Loaded " + events().size() + " local events.";
                });
    }

    private void ensureLoaded() throws Exception {
        if (!loaded) {
            publish(database.load());
            loaded = true;
        }
    }

    public CompletableFuture<String> simulate() {
        return execute(
                () -> {
                    ensureLoaded();
                    var next = new ArrayList<>(events());
                    next.addAll(SimulatedEvents.create(System.currentTimeMillis()));
                    publish(next);
                    dirty = true;
                    if (config.autoSave) persist();
                    return "Added 6 SIMULATED events. "
                            + (dirty ? "Not saved yet." : "Saved CSV and JSON.");
                });
    }

    public CompletableFuture<String> save() {
        return execute(
                () -> {
                    ensureLoaded();
                    persist();
                    return "Saved " + events().size() + " events to CSV and JSON.";
                });
    }

    private void persist() throws Exception {
        database.save(events(), config.backup);
        dirty = false;
    }

    public CompletableFuture<String> reload() {
        return execute(
                () -> {
                    if (dirty) return "Unsaved changes: use Save before Reload.";
                    var next = database.load();
                    publish(next);
                    loaded = true;
                    return "Reloaded " + events().size() + " events.";
                });
    }

    public CompletableFuture<String> clear() {
        cancelCapture();
        return execute(
                () -> {
                    ensureLoaded();
                    pendingCapture = null;
                    publish(List.of());
                    dirty = true;
                    if (config.autoSave) persist();
                    return dirty
                            ? "Cleared memory; use Save to update files."
                            : "Cleared local history. Previous files backed up if Backup is ON.";
                });
    }

    public CompletableFuture<String> configure(CoreTraceConfig updated) {
        updated.validate();
        config = ConfigStore.copy(updated);
        if (!config.enabled || !config.autoCapture) cancelCapture();
        configurationListener.run();
        return CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                configStore.save(config);
                                return status =
                                        config.enabled
                                                ? "Configuration saved."
                                                : "COREVIEWER DISABLED — no investigation work is"
                                                        + " running.";
                            } catch (Exception ex) {
                                return status = "Configuration save failed: " + ex.getMessage();
                            }
                        },
                        worker)
                .thenCompose(
                        message ->
                                config.enabled && !loaded
                                        ? initialize()
                                        : CompletableFuture.completedFuture(message));
    }

    private CompletableFuture<String> execute(Work action) {
        if (!enabled())
            return CompletableFuture.completedFuture(
                    status = "COREVIEWER DISABLED. Enable it in Settings.");
        return CompletableFuture.supplyAsync(
                () -> {
                    if (!enabled()) return status = "COREVIEWER DISABLED. Operation cancelled.";
                    try {
                        return status = action.run();
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
