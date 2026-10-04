package dev.coreviewer;

import static org.junit.jupiter.api.Assertions.*;

import dev.coreviewer.capture.*;
import dev.coreviewer.config.*;
import dev.coreviewer.model.*;
import dev.coreviewer.replay.ReplayEngine;
import dev.coreviewer.storage.LegacyDataMigration;
import dev.coreviewer.view.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

class CustomizationTest {
    @TempDir Path folder;

    @Test
    void migrationCopiesPriorDataWithoutSharingOrOverwritingIt() throws Exception {
        Path legacy = Files.createDirectory(folder.resolve("coretrace"));
        Path current = folder.resolve("coreviewer");
        Files.writeString(legacy.resolve("config.json"), "old config");
        Files.writeString(legacy.resolve("events.json"), "old events");
        Files.writeString(legacy.resolve("events.csv"), "old csv");
        assertTrue(LegacyDataMigration.copyIfMissing(legacy, current));
        assertEquals("old config", Files.readString(current.resolve("config.json")));
        assertEquals("old events", Files.readString(current.resolve("events.json")));
        Files.writeString(current.resolve("events.json"), "new events");
        Files.writeString(legacy.resolve("events.json"), "later legacy events");
        assertFalse(LegacyDataMigration.copyIfMissing(legacy, current));
        assertEquals("new events", Files.readString(current.resolve("events.json")));
        assertEquals("later legacy events", Files.readString(legacy.resolve("events.json")));
        Files.delete(current.resolve("events.csv"));
        assertFalse(LegacyDataMigration.copyIfMissing(legacy, current));
        assertFalse(Files.exists(current.resolve("events.csv")));
    }

    private BlockEvent event(int i, long time) {
        return new BlockEvent(
                new EventContext(
                        UUID.randomUUID(),
                        time,
                        "s",
                        "w",
                        null,
                        new EventContext.Position(i, 64, 0),
                        "A",
                        null,
                        false,
                        "COREPROTECT_CHAT"),
                EventType.BLOCK_BREAK,
                "minecraft:stone");
    }

    @Test
    void arbitraryNumbersAndFactoryDefaults() {
        var c = new CoreTraceConfig();
        assertEquals(1500, c.commandDelayMs);
        assertEquals(10, c.maxVisibleEvents);
        assertEquals(20, c.replayVisibleEvents);
        c.eventRadius = 17;
        c.commandDelayMs = 2345;
        c.maxVisibleEvents = 3;
        c.replayVisibleEvents = 21;
        c.validate();
        assertEquals(17, c.eventRadius);
        assertEquals(2345, c.commandDelayMs);
        assertEquals(3, c.maxVisibleEvents);
        assertEquals(21, c.replayVisibleEvents);
        assertTrue(
                c.resetCaptureOnComplete
                        && c.resetCaptureMessage
                        && c.clearPreviousCapture
                        && c.clearCaptureMessage
                        && c.smartTimeline);
        assertFalse(c.captureSounds);
    }

    @Test
    void migratesOnlyLegacyFactoryValues() throws Exception {
        Files.writeString(
                folder.resolve("config.json"),
                "{\"schemaVersion\":1,\"commandDelayMs\":3000,\"maxVisibleEvents\":500,\"playPauseKey\":\"key.keyboard.space\",\"forwardKey\":\"key.keyboard.right\",\"backwardKey\":\"key.keyboard.left\"}");
        var store = new ConfigStore(folder);
        var c = store.load();
        assertEquals(1500, c.commandDelayMs);
        assertEquals(10, c.maxVisibleEvents);
        assertEquals("key.keyboard.right.shift", c.playPauseKey);
        c.commandDelayMs = 3000;
        c.maxVisibleEvents = 500;
        store.save(c);
        var loaded = store.load();
        assertEquals(3000, loaded.commandDelayMs);
        assertEquals(500, loaded.maxVisibleEvents);
    }

    @Test
    void staticPagesAndReplayWindowAreChronologicalAndDoNotDuplicateTies() {
        var all = new ArrayList<CoreTraceEvent>();
        for (int i = 0; i < 100; i++) all.add(event(i, 1000 + i / 2));
        var index = new EventIndex(all);
        var c = new CoreTraceConfig();
        c.eventRadius = 1000;
        c.windowFrom = 10;
        c.windowTo = 20;
        assertEquals(
                all.subList(10, 20),
                index.select("s", "w", false, 0, 64, 0, c, Double.POSITIVE_INFINITY, (x, z) -> true)
                        .events());
        c.windowFrom = 1;
        c.windowTo = 21;
        var selected =
                index.select("s", "w", false, 0, 64, 0, c, Double.POSITIVE_INFINITY, (x, z) -> true)
                        .events();
        assertEquals(20, selected.size());
        assertFalse(selected.contains(all.getFirst()));
        assertTrue(selected.contains(all.get(20)));
    }

    @Test
    void smartTimelineSkipsHoursButAllowsAnimationAndCanBeDisabled() {
        var e = new ReplayEngine();
        var c = new CoreTraceConfig();
        e.load(List.of(event(0, 1000), event(1, 10_801_000)));
        e.seek(1000);
        e.play();
        e.advance(500, c);
        assertEquals(1500, e.cursor());
        e.advance(501, c);
        assertTrue(e.cursor() >= 10_801_000);
        c.smartTimeline = false;
        e.seek(1000);
        e.play();
        e.advance(1001, c);
        assertEquals(2001, e.cursor());
        c.smartTimeline = true;
        c.blockBreakSpeed = .25;
        e.seek(1000);
        e.play();
        e.advance(3999, c);
        assertEquals(4999, e.cursor());
        e.advance(2, c);
        assertTrue(e.cursor() >= 10_801_000);
    }

    @Test
    void completionFiresOnceOnlyOnSuccessfulFinalPage() {
        var c = new CoreTraceConfig();
        c.autoPageAdvance = true;
        var signals = new ArrayList<String>();
        var e = new CaptureEngine(() -> c, x -> {}, (l, s) -> signals.add("flush"));
        e.lifecycle(a -> signals.add("start:" + a), a -> signals.add("done:" + a));
        e.command("co l", "s", 0);
        new PhaseTwoTest().pair("broke stone").forEach(e::receive);
        e.receive(new ChatLine("Page 1/1", List.of(), 100));
        e.receive(new ChatLine("Page 1/1", List.of(), 100));
        assertEquals(List.of("start:true", "flush", "done:true"), signals);
        signals.clear();
        e.command("co l", "s", 0);
        e.receive(new ChatLine("No results", List.of(), 100));
        assertEquals(List.of("start:true"), signals);
    }

    @Test
    void replacementCommitsAfterLastPagePreservesOtherServersAndCancelledData() throws Exception {
        var c = new CoreTraceConfig();
        var pair = new PhaseTwoTest();
        try (var service = new InvestigationService(folder, c)) {
            service.initialize().join();
            service.capture(pair.pair("broke stone"), "s", Set.of()).join();
            service.capture(pair.pair("placed dirt"), "other", Set.of()).join();
            var original = service.events();
            service.beginCapture(true, "s").join();
            service.capture(pair.pair("placed diamond_block"), "s", Set.of()).join();
            assertEquals(original, service.events());
            service.cancelCapture();
            service.beginCapture(true, "s").join();
            assertEquals(original, service.events());
            service.capture(pair.pair("placed gold_block"), "s", Set.of()).join();
            String message = service.finishCapture().join();
            assertTrue(message.contains("replaced 1"));
            assertEquals(2, service.events().size());
            assertTrue(
                    service.events().stream().anyMatch(e -> e.context().server().equals("other")));
            assertEquals("minecraft:gold_block", ((BlockEvent) service.events().getLast()).block());
            assertEquals(
                    2,
                    dev.coreviewer.storage.EventCodec.parseJson(read(folder.resolve("events.json")))
                            .size());
        }
    }

    private String read(Path p) {
        try {
            return Files.readString(p);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
