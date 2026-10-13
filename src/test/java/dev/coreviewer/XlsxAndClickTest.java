package dev.coreviewer;

import static org.junit.jupiter.api.Assertions.*;

import dev.coreviewer.config.CoreTraceConfig;
import dev.coreviewer.model.BlockEvent;
import dev.coreviewer.model.EventStatistics;
import dev.coreviewer.model.EventType;
import dev.coreviewer.model.StatisticsQuery;
import dev.coreviewer.view.StaticScene;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

class XlsxAndClickTest {
    @TempDir Path root;

    private static void sheet(
            XSSFWorkbook book, String name, String action, String object, int second) {
        Sheet sheet = book.createSheet(name);
        String[] headers = {
            "event_type", "action_id", "server_timestamp", "world", "x", "y", "z", "actor", "object"
        };
        var header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
        String[] values = {
            action.equals("interaction") ? "click" : "block",
            action,
            Instant.parse("2026-10-11T00:00:00Z").plusSeconds(second).toString(),
            "world",
            second == 20 ? "5" : "4",
            "64",
            "8",
            "Jose",
            object
        };
        var row = sheet.createRow(1);
        for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
    }

    @Test
    void workbookSheetsBecomeChronologicalBlockAndClickEvents() throws Exception {
        Path file = root.resolve("Investigation.xlsx");
        try (var book = new XSSFWorkbook()) {
            sheet(book, "Late click", "interaction", "ender_chest", 20);
            sheet(book, "Early break", "block_break", "stone", 10);
            book.createSheet("Notes").createRow(0).createCell(0).setCellValue("unrelated");
            try (var output = java.nio.file.Files.newOutputStream(file)) {
                book.write(output);
            }
        }
        try (var service = new InvestigationService(root.resolve("data"), new CoreTraceConfig())) {
            assertTrue(service.importXlsx(file, "server").get().startsWith("Imported 2"));
            var events = service.events();
            assertEquals(
                    List.of(EventType.BLOCK_BREAK, EventType.BLOCK_CLICK),
                    events.stream().map(e -> e.type()).toList());
            assertEquals("minecraft:ender_chest", ((BlockEvent) events.get(1)).block());
            assertEquals(2, service.csvFiles().size());
            assertTrue(service.csvFiles().stream().allMatch(f -> f.selected()));
            assertEquals(1, StaticScene.links(events, true).size());
            var clicks =
                    EventStatistics.build(events, EventStatistics.Category.CLICKS, "Jose", true);
            assertEquals(1, clicks.events());
            var column = EventStatistics.columns(clicks, true).getFirst();
            assertEquals("minecraft:ender_chest", column.icon());
            assertEquals(1, StatisticsQuery.evidence(events, "Jose", column, 0, 20).total());
            var config = service.config();
            config.clickView = false;
            assertFalse(StaticScene.visible(events.get(1), config));
            assertTrue(StaticScene.visible(events.get(0), config));
        }
    }
}
