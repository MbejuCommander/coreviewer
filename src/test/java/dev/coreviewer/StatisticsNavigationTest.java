package dev.coreviewer;

import static org.junit.jupiter.api.Assertions.*;

import dev.coreviewer.model.*;
import dev.coreviewer.storage.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

class StatisticsNavigationTest {
    @TempDir Path root;

    private CoreTraceEvent event(String actor, EventType type, int x) {
        return new BlockEvent(
                new EventContext(
                        UUID.randomUUID(),
                        1800000000000L + x,
                        "s",
                        "world",
                        null,
                        new EventContext.Position(x, 64, 2),
                        actor,
                        null,
                        false,
                        "CSV"),
                type,
                "minecraft:diamond_ore");
    }

    @Test
    void pairedColumnRanksPositiveAndNegativeIndependentlyAndResetsAlphabetically() {
        var source = new ArrayList<CoreTraceEvent>();
        for (int i = 0; i < 10; i++) source.add(event("Andrea", EventType.BLOCK_BREAK, i));
        for (int i = 0; i < 40; i++) source.add(event("Julio", EventType.BLOCK_BREAK, i));
        for (int i = 0; i < 50; i++) source.add(event("Andrea", EventType.BLOCK_PLACE, i));
        var table = EventStatistics.build(source, EventStatistics.Category.ALL, null, true);
        var col = EventStatistics.columns(table, true).getFirst();
        assertEquals(
                List.of("Julio", "Andrea"),
                StatisticsQuery.players(table, col, StatisticsQuery.Metric.REMOVED, 1));
        assertEquals(
                List.of("Andrea", "Julio"),
                StatisticsQuery.players(table, col, StatisticsQuery.Metric.REMOVED, 2));
        assertEquals(
                "Andrea",
                StatisticsQuery.players(table, col, StatisticsQuery.Metric.ADDED, 1).getFirst());
        assertEquals(
                List.of("Andrea", "Julio"),
                StatisticsQuery.players(table, col, StatisticsQuery.Metric.REMOVED, 0));
        var broken =
                EventStatistics.columns(table, false).stream()
                        .filter(c -> !c.negative().isEmpty())
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                "Julio",
                StatisticsQuery.players(table, broken, StatisticsQuery.Metric.ADDED, 1).getFirst());
        assertEquals(1, StatisticsQuery.findPlayer(table.players(), "jul"));
        assertEquals(-1, StatisticsQuery.findPlayer(table.players(), "absent"));
        assertEquals(1, StatisticsQuery.columns(List.of(col), "DIAMOND").size());
        assertTrue(StatisticsQuery.columns(List.of(col), "iron").isEmpty());
    }

    @Test
    void drilldownRetainsOnlyRequestedPageWithExactCoordinatesAndActors() {
        var source = new ArrayList<CoreTraceEvent>();
        for (int i = 0; i < 100; i++) source.add(event("Jose", EventType.BLOCK_BREAK, i));
        source.add(event("Other", EventType.BLOCK_BREAK, 100));
        var table = EventStatistics.build(source, EventStatistics.Category.BLOCKS, "Jose", true);
        var result =
                StatisticsQuery.evidence(
                        source, "Jose", EventStatistics.columns(table, true).getFirst(), 3, 10);
        assertEquals(100, result.total());
        assertEquals(10, result.events().size());
        assertEquals(30, result.events().getFirst().context().position().x());
        assertEquals(39, result.events().getLast().context().position().x());
        assertTrue(StatisticsQuery.time(result.events().getFirst()).endsWith("UTC"));
    }

    @Test
    void deletingLastCsvPrunesEmptyAncestorsButKeepsRootAndOtherFiles() throws Exception {
        var library = new CsvLibrary(root);
        library.initialize();
        library.write("Case/day/a.csv", List.of(event("Jose", EventType.BLOCK_BREAK, 0)), "s");
        library.write("Case/day/b.csv", List.of(event("Jose", EventType.BLOCK_BREAK, 1)), "s");
        library.delete("Case/day/a.csv");
        assertTrue(Files.isDirectory(library.folder().resolve("Case/day")));
        library.delete("Case/day/b.csv");
        assertFalse(Files.exists(library.folder().resolve("Case")));
        assertTrue(Files.isDirectory(library.folder()));
        library.write("Keep/a.csv", List.of(event("Jose", EventType.BLOCK_BREAK, 0)), "s");
        Files.writeString(library.folder().resolve("Keep/notes.txt"), "keep");
        library.deleteAll();
        assertTrue(Files.exists(library.folder().resolve("Keep/notes.txt")));
    }
}
