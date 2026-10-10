package dev.coreviewer;

import static org.junit.jupiter.api.Assertions.*;

import dev.coreviewer.capture.ChatLine;
import dev.coreviewer.config.*;
import dev.coreviewer.model.*;
import dev.coreviewer.storage.*;
import dev.coreviewer.ui.FolderOpener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

class RefinementTest {
    @TempDir Path root;

    private CoreTraceEvent event(EventType type) {
        return new BlockEvent(
                new EventContext(
                        UUID.randomUUID(),
                        1800000000000L,
                        "s",
                        "world",
                        null,
                        new EventContext.Position(1, 64, 2),
                        "Jose",
                        null,
                        false,
                        "test"),
                type,
                "minecraft:stone");
    }

    @Test
    void nestedImportsWithSameFilenameRemainIndependent() throws Exception {
        var library = new CsvLibrary(root);
        library.initialize();
        library.write("Jose/day1/events.csv", List.of(event(EventType.BLOCK_BREAK)), "s");
        library.write("Jose/day2/events.csv", List.of(event(EventType.BLOCK_PLACE)), "s");
        assertEquals(2, library.load("s", false).size());
        library.select("Jose/day1/events.csv", false, "s");
        assertEquals(EventType.BLOCK_PLACE, library.load("s", false).getFirst().type());
        var reopened = new CsvLibrary(root);
        reopened.initialize();
        assertEquals(1, reopened.load("s", false).size());
        library.delete("Jose/day2/events.csv");
        assertTrue(Files.exists(library.folder().resolve("Jose/day1/events.csv")));
    }

    @Test
    void recursiveDeleteAndArchiveStayInsideLibrary() throws Exception {
        var library = new CsvLibrary(root);
        library.initialize();
        library.write("nested/deep/events.csv", List.of(event(EventType.BLOCK_BREAK)), "s");
        Files.writeString(library.folder().resolve("keep.txt"), "keep");
        Files.writeString(root.resolve("outside.csv"), "keep");
        assertThrows(java.io.IOException.class, () -> library.delete("nested/../../outside.csv"));
        library.deleteAll();
        assertFalse(Files.exists(library.folder().resolve("nested/deep/events.csv")));
        assertTrue(Files.exists(root.resolve("outside.csv")));
        library.write("nested/deep/events.csv", List.of(event(EventType.BLOCK_PLACE)), "s");
        library.enterSimple();
        try (var files = Files.walk(root.resolve("csv-archive"))) {
            assertTrue(files.anyMatch(p -> p.endsWith(Path.of("nested/deep/events.csv"))));
        }
        assertTrue(Files.exists(library.folder().resolve("keep.txt")));
    }

    @Test
    void folderNamesArePortableUniqueAndGeneratedWhenBlank() throws Exception {
        assertEquals("Investigation/events.csv", CaptureFolders.reserve(root, "Investigation"));
        assertEquals("Investigation (2)/events.csv", CaptureFolders.reserve(root, "Investigation"));
        assertNotEquals(CaptureFolders.reserve(root, ""), CaptureFolders.reserve(root, ""));
        for (String invalid : List.of("../escape", "a/b", "a\\b", "CON", "NUL.csv", "bad?", "end."))
            assertThrows(IllegalArgumentException.class, () -> CaptureFolders.validate(invalid));
    }

    @Test
    void capturesUseNamedSubfoldersWithoutOverwriting() throws Exception {
        var config = new CoreTraceConfig();
        config.captureFolderName = "Case Jose";
        try (var service = new InvestigationService(root, config)) {
            service.initialize().join();
            for (int i = 0; i < 2; i++) {
                service.beginCapture(true, "s").join();
                service.capture(
                                List.of(
                                        new ChatLine(
                                                "1/h ago - Jose broke stone.",
                                                List.of(),
                                                1800000000000L),
                                        new ChatLine(
                                                "^ (x1/y64/z2/world)", List.of(), 1800000000000L)),
                                "s",
                                Set.of())
                        .join();
                service.finishCapture().join();
            }
            assertEquals(2, service.csvFiles().size());
            assertTrue(Files.exists(service.csvFolder().resolve("Case Jose/events.csv")));
            assertTrue(Files.exists(service.csvFolder().resolve("Case Jose (2)/events.csv")));
        }
    }

    @Test
    void simpleCapturePointerSurvivesRestartAndReplacesOnlyPrevious() throws Exception {
        var library = new CsvLibrary(root);
        library.initialize();
        library.writeSimple("first/events.csv", List.of(event(EventType.BLOCK_BREAK)), "s", true);
        var reopened = new CsvLibrary(root);
        reopened.initialize();
        assertEquals(EventType.BLOCK_BREAK, reopened.load("s", true).getFirst().type());
        reopened.write("imported/events.csv", List.of(event(EventType.BLOCK_BREAK)), "s");
        reopened.writeSimple("second/events.csv", List.of(event(EventType.BLOCK_PLACE)), "s", true);
        assertFalse(Files.exists(reopened.folder().resolve("first/events.csv")));
        assertTrue(Files.exists(reopened.folder().resolve("imported/events.csv")));
        assertEquals(1, reopened.load("s", true).size());
        assertEquals(EventType.BLOCK_PLACE, reopened.load("s", true).getFirst().type());
    }

    @Test
    void smartTimelineAllowsDecimalsAndBlankDefaultsToTwo() {
        assertEquals(2, CoreTraceConfig.parseSmartTimeline(" "));
        assertEquals(1.5, CoreTraceConfig.parseSmartTimeline("1.5"));
        assertEquals(0, CoreTraceConfig.parseSmartTimeline("0"));
        for (String input : List.of("-1", "NaN", "Infinity", "abc"))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CoreTraceConfig.parseSmartTimeline(input));
        assertTrue(new CoreTraceConfig().smartTimeline);
        assertEquals(2, new CoreTraceConfig().smartTimelineSeconds);
    }

    @Test
    void compactColumnsPairCountsWithoutMixingItemsAndBlocks() {
        var events = new ArrayList<CoreTraceEvent>();
        for (int i = 0; i < 10; i++) events.add(event(EventType.BLOCK_PLACE));
        for (int i = 0; i < 5; i++) events.add(event(EventType.BLOCK_BREAK));
        events.add(
                new ItemEvent(
                        event(EventType.BLOCK_PLACE).context(),
                        EventType.ITEM_ADD,
                        "minecraft:stone",
                        7));
        var table = EventStatistics.build(events, EventStatistics.Category.ALL, null, true);
        var columns = EventStatistics.columns(table, true);
        assertEquals(2, columns.size());
        assertEquals(10, columns.getFirst().plus(table.totals()));
        assertEquals(5, columns.getFirst().minus(table.totals()));
        assertEquals(7, columns.getLast().plus(table.totals()));
        assertEquals(3, EventStatistics.columns(table, false).size());
    }

    @Test
    void folderOpenerPassesWindowsPathAsOneLiteralArgument() {
        var path = root.resolve("Case (2) with spaces");
        var cmd = FolderOpener.command("Windows 11", path);
        assertEquals(List.of("explorer.exe", path.toAbsolutePath().normalize().toString()), cmd);
    }
}
