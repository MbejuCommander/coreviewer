package dev.coreviewer;

import static org.junit.jupiter.api.Assertions.*;

import dev.coreviewer.config.*;
import dev.coreviewer.model.*;
import dev.coreviewer.replay.ReplayEngine;
import dev.coreviewer.view.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

class PhaseFiveTest {
    @TempDir Path directory;

    private CoreTraceEvent block(
            int x, int y, int z, long time, String server, String world, boolean demo) {
        return new BlockEvent(
                new EventContext(
                        UUID.randomUUID(),
                        time,
                        server,
                        world,
                        null,
                        new EventContext.Position(x, y, z),
                        "A",
                        null,
                        demo,
                        "COREPROTECT_CHAT"),
                EventType.BLOCK_BREAK,
                "minecraft:stone");
    }

    @Test
    void indexedSelectionMatchesReferenceWithNegativeChunksTiesAndFilters() {
        var random = new Random(42);
        var events = new ArrayList<CoreTraceEvent>();
        for (int i = 0; i < 5000; i++)
            events.add(
                    block(
                            random.nextInt(400) - 200,
                            random.nextInt(80),
                            random.nextInt(400) - 200,
                            i % 17,
                            "s",
                            i % 5 == 0 ? "other" : "w",
                            i % 13 == 0));
        var c = new CoreTraceConfig();
        c.maxVisibleEvents = 75;
        c.eventRadius = 100;
        var index = new EventIndex(events);
        for (int x = -40; x <= 40; x += 20) {
            assertEquals(
                    StaticScene.select(events, "s", "w", x, 40, -16, c, false),
                    index.select(
                                    "s",
                                    "w",
                                    false,
                                    x,
                                    40,
                                    -16,
                                    c,
                                    Double.POSITIVE_INFINITY,
                                    (a, b) -> true)
                            .events());
        }
    }

    @Test
    void sparseHistoryOnlyExaminesLocalLoadedChunks() {
        var events = new ArrayList<CoreTraceEvent>();
        for (int i = 0; i < 100000; i++)
            events.add(block(10000 + i * 16, 64, 0, i, "s", "w", false));
        var near = block(-1, 64, -1, 100, "s", "w", false);
        events.add(near);
        var index = new EventIndex(events);
        var c = new CoreTraceConfig();
        c.eventRadius = 50;
        var result =
                index.select(
                        "s", "w", false, 0, 64, 0, c, Double.POSITIVE_INFINITY, (a, b) -> true);
        assertEquals(List.of(near), result.events());
        assertEquals(1, result.examined());
        assertEquals(1, result.chunks());
        assertTrue(
                index.select(
                                "s",
                                "w",
                                false,
                                0,
                                64,
                                0,
                                c,
                                Double.POSITIVE_INFINITY,
                                (a, b) -> false)
                        .events()
                        .isEmpty());
        System.out.println(
                "Sparse index: 100001 records; examined "
                        + result.examined()
                        + " event in "
                        + result.chunks()
                        + " chunk.");
    }

    @Test
    void chunkGatePrecedesLimitAndReplayCutoff() {
        var a = block(0, 64, 0, 2000, "s", "w", false);
        var b = block(17, 64, 0, 1000, "s", "w", false);
        var c = new CoreTraceConfig();
        c.maxVisibleEvents = 1;
        var index = new EventIndex(List.of(a, b));
        assertEquals(
                List.of(b),
                index.select("s", "w", false, 0, 64, 0, c, 3000, (x, z) -> x == 1).events());
        assertEquals(
                List.of(b),
                index.select("s", "w", false, 0, 64, 0, c, 1500, (x, z) -> true).events());
        assertTrue(
                index.select("different", "w", false, 0, 64, 0, c, 3000, (x, z) -> true)
                        .events()
                        .isEmpty());
    }

    @Test
    void asyncSelectionStaysOffClientAndInvalidatesScopeAndSettings() throws Exception {
        var index = new EventIndex(List.of(block(0, 64, 0, 2000, "s", "w", false)));
        var cache = new AsyncSceneCache();
        var c = new CoreTraceConfig();
        var owner = Thread.currentThread();
        java.util.function.BiPredicate<Integer, Integer> loaded =
                (x, z) -> {
                    assertSame(owner, Thread.currentThread(), "Chunk access escaped to worker");
                    return true;
                };
        try {
            var ready = cache.select(index, "s", "w", false, 0, 64, 0, c, 3000, 1, 0, loaded);
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (ready.events().isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(5);
                ready = cache.select(index, "s", "w", false, 0, 64, 0, c, 3000, 1, 100, loaded);
            }
            assertEquals(1, ready.events().size());
            assertEquals(1, cache.queries);
            assertTrue(cache.hits > 0);
            assertTrue(
                    cache.select(index, "s", "other", false, 0, 64, 0, c, 3000, 1, 200, loaded)
                            .events()
                            .isEmpty());
            c.enabled = false;
            assertTrue(
                    cache.select(index, "s", "w", false, 0, 64, 0, c, 3000, 1, 300, loaded)
                            .events()
                            .isEmpty());
            c.enabled = true;
            assertTrue(
                    cache.select(index, "s", "w", false, 0, 64, 0, c, 1000, 0, 400, loaded)
                            .events()
                            .isEmpty());
        } finally {
            cache.clear();
        }
    }

    @Test
    void replayBinaryBoundsMatchLinearReferenceAtTiesAndRewinds() {
        var all = new ArrayList<CoreTraceEvent>();
        for (int i = 0; i < 1000; i++) all.add(block(i, 64, 0, 1000 + i / 4, "s", "w", false));
        var index = new EventIndex(all);
        var engine = new ReplayEngine();
        engine.loadPrepared(index.timeline("s", "w", false));
        var random = new Random(8);
        for (int n = 0; n < 200; n++) {
            double cursor = 900 + random.nextDouble() * 500;
            engine.seek(cursor);
            var expected = all.stream().filter(e -> e.context().timestamp() <= cursor).toList();
            assertEquals(expected, engine.visible());
            assertEquals(expected.size(), engine.visibleCount());
        }
    }

    @Test
    void publishedIndexAndDataStayAtomicAndOldSnapshotSurvivesClear() {
        var c = new CoreTraceConfig();
        c.autoSave = false;
        try (var service = new InvestigationService(directory, c)) {
            service.initialize().join();
            service.simulate().join();
            var old = service.snapshot();
            assertEquals(6, old.events().size());
            var event = old.events().getFirst();
            var ctx = event.context();
            assertFalse(old.index().timeline(ctx.server(), ctx.world(), true).events().isEmpty());
            service.clear().join();
            assertTrue(service.snapshot().events().isEmpty());
            assertTrue(
                    service.snapshot()
                            .index()
                            .timeline(ctx.server(), ctx.world(), true)
                            .events()
                            .isEmpty());
            assertEquals(6, old.events().size());
        }
    }

    @Test
    void modelCacheIsBoundedAndConfigCopyIsIndependent() {
        var cache = new BoundedCache<Integer, String>(2);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.get(1);
        cache.put(3, "c");
        assertEquals(Set.of(1, 3), cache.keySet());
        cache.clear();
        assertTrue(cache.isEmpty());
        var c = new CoreTraceConfig();
        var copy = ConfigStore.copy(c);
        copy.enabled = false;
        copy.eventRadius = 500;
        assertTrue(c.enabled);
        assertEquals(100, c.eventRadius);
    }
}
