package dev.coreviewer;

import static org.junit.jupiter.api.Assertions.*;

import dev.coreviewer.config.*;
import dev.coreviewer.model.*;
import dev.coreviewer.storage.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

class PhaseOneTest {
    @TempDir Path folder;

    @Test
    void simulatedDataCoversAllSixActionsAndRoundTrips() throws Exception {
        var events = SimulatedEvents.create(1_800_000_000_000L);
        assertEquals(
                Set.of(EventType.values()),
                events.stream()
                        .map(CoreTraceEvent::type)
                        .collect(java.util.stream.Collectors.toSet()));
        assertTrue(
                events.stream()
                        .allMatch(
                                e ->
                                        e.context().simulated()
                                                && e.context().source().equals("SIMULATED")));
        assertTrue(events.stream().allMatch(e -> e.context().actorUuid() == null));
        assertEquals(events, EventCodec.parseJson(EventCodec.json(events)));
    }

    @Test
    void csvQuotesCommasQuotesAndNewlines() {
        assertEquals("\"A,\"\"B\"\"\nC\"", EventCodec.escape("A,\"B\"\nC"));
        var csv = EventCodec.csv(SimulatedEvents.create(1_800_000_000_000L));
        assertTrue(csv.startsWith("id,type,timestamp,server,world,dimension"));
        assertEquals(7, csv.lines().count());
        assertTrue(csv.contains("\"true\",\"SIMULATED\""));
    }

    @Test
    void storeCreatesBothFormatsAndKeepsPreviousBackup() throws Exception {
        var db = new LocalDatabase(folder);
        assertTrue(db.load().isEmpty());
        var first = SimulatedEvents.create(1_800_000_000_000L);
        db.save(first, true);
        db.save(List.of(), true);
        assertEquals(
                first, EventCodec.parseJson(Files.readString(folder.resolve("events.json.bak"))));
        assertTrue(db.load().isEmpty());
        assertEquals(1, Files.readString(folder.resolve("events.csv")).lines().count());
    }

    @Test
    void corruptJsonIsNeverSilentlyOverwritten() throws Exception {
        Files.writeString(folder.resolve("events.json"), "broken");
        var db = new LocalDatabase(folder);
        assertThrows(java.io.IOException.class, db::load);
        assertThrows(java.io.IOException.class, () -> db.save(List.of(), false));
        assertEquals("broken", Files.readString(folder.resolve("events.json")));
    }

    @Test
    void csvWithoutJsonIsPreserved() throws Exception {
        Files.writeString(folder.resolve("events.csv"), "existing evidence");
        assertThrows(java.io.IOException.class, () -> new LocalDatabase(folder).load());
        assertEquals("existing evidence", Files.readString(folder.resolve("events.csv")));
    }

    @Test
    void disabledServiceDoesNotCreateEventFilesOrAcceptDemo() throws Exception {
        var c = new CoreTraceConfig();
        c.enabled = false;
        try (var service = new InvestigationService(folder, c)) {
            assertTrue(service.initialize().join().contains("DISABLED"));
            assertTrue(service.simulate().join().contains("DISABLED"));
            assertTrue(service.events().isEmpty());
            assertFalse(Files.exists(folder.resolve("events.json")));
            assertFalse(Files.exists(folder.resolve("events.csv")));
        }
    }

    @Test
    void manualSaveSurvivesReloadAndMasterSwitch() throws Exception {
        var c = new CoreTraceConfig();
        c.autoSave = false;
        try (var service = new InvestigationService(folder, c)) {
            service.initialize().join();
            service.simulate().join();
            assertEquals(6, service.events().size());
            assertTrue(service.dirty());
            assertTrue(
                    EventCodec.parseJson(Files.readString(folder.resolve("events.json")))
                            .isEmpty());
            assertTrue(service.reload().join().contains("Unsaved"));
            service.save().join();
            assertFalse(service.dirty());
            c.enabled = false;
            service.configure(c).join();
            assertTrue(service.clear().join().contains("DISABLED"));
            assertEquals(6, service.events().size());
            c.enabled = true;
            service.configure(c).join();
            service.reload().join();
            assertEquals(6, service.events().size());
        }
    }

    @Test
    void configUsesRequestedDefaultsAndClampsInvalidChoices() throws Exception {
        var store = new ConfigStore(folder);
        var c = store.load();
        assertEquals(100, c.eventRadius);
        assertEquals(1500, c.commandDelayMs);
        assertFalse(c.autoPageAdvance);
        c.eventRadius = 12;
        c.commandDelayMs = 1;
        c.alpha = 900;
        c.timelineSpeed = 3;
        c.arrowSize = Float.NaN;
        c.validate();
        assertEquals(12, c.eventRadius);
        assertEquals(1, c.commandDelayMs);
        assertEquals(255, c.alpha);
        assertEquals(1, c.timelineSpeed);
        assertEquals(1, c.arrowSize);
        c.enabled = false;
        store.save(c);
        assertFalse(store.load().enabled);
    }

    @Test
    void malformedConfigAndUnknownSchemasArePreserved() throws Exception {
        Files.writeString(folder.resolve("config.json"), "{\"schemaVersion\":999}");
        assertThrows(java.io.IOException.class, () -> new ConfigStore(folder).load());
        assertTrue(Files.readString(folder.resolve("config.json")).contains("999"));
        assertThrows(
                java.io.IOException.class,
                () -> EventCodec.parseJson("{\"schemaVersion\":999,\"events\":[]}"));
    }

    @Test
    void invalidEventTypesAndQuantitiesAreRejected() {
        var c = SimulatedEvents.create(1_800_000_000_000L).getFirst().context();
        assertThrows(
                IllegalArgumentException.class,
                () -> new ItemEvent(c, EventType.ITEM_ADD, "minecraft:stone", 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BlockEvent(c, EventType.PLAYER_KILL, "minecraft:stone"));
        assertThrows(
                java.io.IOException.class,
                () ->
                        EventCodec.parseJson(
                                EventCodec.json(
                                        List.of(
                                                new BlockEvent(c, EventType.BLOCK_BREAK, "stone"),
                                                new BlockEvent(
                                                        c, EventType.BLOCK_BREAK, "stone")))));
    }
}
