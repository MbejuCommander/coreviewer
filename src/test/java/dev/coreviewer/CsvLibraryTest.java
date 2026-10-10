package dev.coreviewer;

import static org.junit.jupiter.api.Assertions.*;

import dev.coreviewer.config.CoreTraceConfig;
import dev.coreviewer.model.*;
import dev.coreviewer.replay.ReplayEngine;
import dev.coreviewer.storage.*;
import dev.coreviewer.view.EventIndex;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

class CsvLibraryTest {
    @TempDir Path folder;

    private CoreTraceEvent block(long time) {
        return new BlockEvent(
                new EventContext(
                        UUID.randomUUID(),
                        time,
                        "s",
                        "world",
                        null,
                        new EventContext.Position(1, 64, 2),
                        "Jose",
                        null,
                        false,
                        "test"),
                EventType.BLOCK_BREAK,
                "minecraft:stone");
    }

    @Test
    void actualCoretrace141ExportImportsSupportedTypesWithoutInventingPositions() throws Exception {
        String text =
                new String(
                        getClass()
                                .getResourceAsStream("/coretrace-1.4.1-all-event-types.csv")
                                .readAllBytes(),
                        StandardCharsets.UTF_8);
        var result = CsvEvents.read(text, "server", Set.of("minecraft:zombie"));
        assertEquals(13, result.events().size());
        assertEquals(8, result.skipped());
        assertEquals(0, result.unknownTime());
        assertEquals(2, result.events().stream().filter(e -> e instanceof SessionEvent).count());
        assertEquals(2, result.events().stream().filter(e -> e instanceof ContainerEvent).count());
        assertTrue(result.events().stream().anyMatch(e -> e.context().position() == null));
        assertEquals(
                76,
                result.events().stream()
                        .filter(e -> e instanceof ContainerEvent)
                        .mapToInt(e -> ((ContainerEvent) e).quantity())
                        .sum());
    }

    @Test
    void excelHintReorderedSubsetColumnsAndMultilineFields() throws Exception {
        String csv =
                "\uFEFFsep=,\r\n"
                    + "actor,action_id,world,x,y,z,server_timestamp,object,raw_text\r\n"
                    + "Jose,block_break,world,1,64,-2,2026-10-01 10:00:00 UTC,stone,\"line one\r\n"
                    + "line \"\"two\"\"\"\r\n";
        var result = CsvEvents.read(csv, "s");
        assertEquals(1, result.events().size());
        assertEquals(-2, result.events().getFirst().context().position().z());
        assertTrue(result.events().getFirst().context().source().contains("line \"two\""));
    }

    @Test
    void missingTimestampRemainsAvailableForStatsButNotReplay() throws Exception {
        var result =
                CsvEvents.read(
                        "action_id,actor,world,x,y,z,object\nblock_break,Jose,world,1,2,3,stone\n",
                        "s");
        assertEquals(1, result.unknownTime());
        assertEquals(1, result.events().size());
        assertTrue(
                new EventIndex(result.events()).timeline("s", "world", false).events().isEmpty());
        assertEquals(
                1,
                EventStatistics.build(result.events(), EventStatistics.Category.ALL, null)
                        .events());
    }

    @Test
    void allEventTypesRoundTripIncludingUnknownItemAmount() throws Exception {
        var original = new ArrayList<CoreTraceEvent>(SimulatedEvents.create(1800000000000L));
        var c = block(1000).context();
        original.add(new ContainerEvent(c, EventType.CONTAINER_ADD, "minecraft:diamond", 3));
        original.add(new SessionEvent(block(2000).context(), EventType.SESSION_LOGOUT));
        original.add(
                new ItemEvent(block(3000).context(), EventType.ITEM_ADD, "minecraft:stone", 0));
        assertEquals(original, CsvEvents.read(EventCodec.csv(original), "other").events());
        assertEquals(original, EventCodec.parseJson(EventCodec.json(original)));
    }

    @Test
    void selectionAndOrderPersistWhileTimelineAlwaysSortsByDate() throws Exception {
        var library = new CsvLibrary(folder);
        library.initialize();
        var late = block(5000);
        var early = block(1000);
        library.write("late.csv", List.of(late), "s");
        library.write("early.csv", List.of(early), "s");
        assertEquals(List.of(early, late), library.load("s", false));
        library.move("early.csv", -1);
        library.select("late.csv", false, "s");
        assertEquals(List.of(early), library.load("s", false));
        var reloaded = new CsvLibrary(folder);
        reloaded.initialize();
        assertEquals(List.of(early), reloaded.load("s", false));
        assertEquals("early.csv", reloaded.entries().getFirst().name());
        reloaded.selectAll(true, "s");
        assertEquals(List.of(early, late), reloaded.load("s", false));
        reloaded.selectAll(false, "s");
        assertTrue(reloaded.load("s", false).isEmpty());
    }

    @Test
    void externalImportsStartUnselectedAndBindToSelectedServer() throws Exception {
        var library = new CsvLibrary(folder);
        library.initialize();
        Files.writeString(
                library.folder().resolve("import.csv"),
                "action_id,actor,object,server_timestamp\n"
                    + "block_break,Jose,stone,2026-10-01 10:00:00 UTC\n");
        assertTrue(library.load("", false).isEmpty());
        library.select("import.csv", true, "s");
        assertEquals("s", library.load("s", false).getFirst().context().server());
    }

    @Test
    void deleteDoesNotEscapeLibraryAndDeleteAllIncludesNewImports() throws Exception {
        var library = new CsvLibrary(folder);
        library.initialize();
        Files.writeString(folder.resolve("outside.csv"), "preserve");
        assertThrows(java.io.IOException.class, () -> library.delete("../outside.csv"));
        Files.writeString(library.folder().resolve("new.csv"), "test");
        library.deleteAll();
        assertFalse(Files.exists(library.folder().resolve("new.csv")));
        assertTrue(Files.exists(folder.resolve("outside.csv")));
    }

    @Test
    void itemTotalsUseQuantitiesAndKeepBlocksAndActionsSeparate() {
        var c = block(1000).context();
        var events =
                List.<CoreTraceEvent>of(
                        block(1000),
                        new ItemEvent(c, EventType.ITEM_ADD, "minecraft:diamond", 5),
                        new ContainerEvent(c, EventType.CONTAINER_REMOVE, "minecraft:diamond", 12),
                        new ItemEvent(c, EventType.ITEM_REMOVE, "minecraft:stone", 0));
        var all = EventStatistics.build(events, EventStatistics.Category.ALL, null);
        assertEquals(4, all.events());
        assertEquals(1, all.unknownAmounts());
        assertEquals(5L, all.totals().get("ITEM_ADD"));
        assertEquals(12L, all.totals().get("CONTAINER_REMOVE"));
        assertEquals(1L, all.totals().get("BLOCK_BREAK"));
    }

    @Test
    void configurableSmartGapRetainsOriginalTimestamps() {
        var engine = new ReplayEngine();
        var c = new CoreTraceConfig();
        c.smartTimelineSeconds = 7;
        engine.load(List.of(block(1000), block(3600000)));
        engine.seek(1000);
        engine.play();
        engine.advance(6999, c);
        assertEquals(7999, engine.cursor());
        engine.advance(1, c);
        assertEquals(3600000, engine.cursor());
        assertEquals(3600000, engine.events().getLast().context().timestamp());
    }

    @Test
    void changedImportedFileInvalidatesCacheAndUnknownTimeSurvivesExport() throws Exception {
        var library = new CsvLibrary(folder);
        library.initialize();
        Path file = library.folder().resolve("import.csv");
        Files.writeString(
                file,
                "action_id,actor,object,server_timestamp\n"
                    + "block_break,Jose,stone,2026-10-01 10:00:00 UTC\n");
        library.load("server-at-discovery", false);
        library.select("import.csv", true, "actual-server");
        assertEquals(
                "actual-server",
                library.load("actual-server", false).getFirst().context().server());
        Files.writeString(
                file,
                "action_id,actor,object,server_timestamp,source\n"
                    + "block_place,Ana,diamond_block,,external\n");
        var events = library.load("actual-server", false);
        assertEquals(EventType.BLOCK_PLACE, events.getFirst().type());
        assertTrue(events.getFirst().context().source().contains("UNKNOWN_TIMESTAMP"));
        var reimport = CsvEvents.read(EventCodec.csv(events), "actual-server").events();
        assertEquals(events, reimport);
        assertTrue(
                new EventIndex(reimport)
                        .timeline("actual-server", "world", false)
                        .events()
                        .isEmpty());
    }

    @Test
    void chatSessionsAndContainerHintsRetainTheirEventType() {
        var parser = new dev.coreviewer.capture.CoreProtectChatParser();
        var lines = new ArrayList<dev.coreviewer.capture.ChatLine>();
        for (String text :
                List.of(
                        "0.05/h ago + Jose logged in.",
                        "^ (x1/y64/z2/world)",
                        "0.05/h ago - Jose logged out.",
                        "^ (x1/y64/z2/world)",
                        "0.05/h ago + Jose added x12 diamond.",
                        "^ (x1/y64/z2/world) (a:container)",
                        "0.05/h ago + Jose added x3 diamond.",
                        "^ (x1/y64/z2/world) (a:item)",
                        "0.05/h ago + Jose added x3 diamond.",
                        "^ (x1/y64/z2/world) (a:inventory)"))
            lines.add(new dev.coreviewer.capture.ChatLine(text, List.of(), 1800000000000L));
        var result = parser.parse(lines, "s", Set.of());
        assertEquals(
                List.of(
                        EventType.SESSION_LOGIN,
                        EventType.SESSION_LOGOUT,
                        EventType.CONTAINER_ADD,
                        EventType.ITEM_ADD),
                result.events().stream().map(CoreTraceEvent::type).toList());
        assertEquals(1, result.skipped());
    }
}
