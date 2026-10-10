package dev.coreviewer;

import static org.junit.jupiter.api.Assertions.*;

import dev.coreviewer.capture.*;
import dev.coreviewer.config.*;
import dev.coreviewer.model.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

class PhaseTwoTest {
    @TempDir Path folder;
    static final long NOW = 1800000000000L;

    ChatLine line(String s) {
        return new ChatLine(s, List.of(), NOW);
    }

    List<ChatLine> pair(String action) {
        return List.of(
                line("0.02/m ago - Alice " + action + "."),
                line("^ (x-12/y64/z30/world) (a:block)"));
    }

    @Test
    void allSixTypesAndExactHoverTimestamp() {
        var lines = new ArrayList<ChatLine>();
        for (String action :
                List.of(
                        "broke stone",
                        "placed dirt",
                        "killed Steve",
                        "killed zombie",
                        "added x4 diamond",
                        "removed x2 dirt")) lines.addAll(pair(action));
        lines.set(
                0, new ChatLine(lines.getFirst().text(), List.of("2026-09-26 03:00:00 UTC"), NOW));
        var result =
                new CoreProtectChatParser().parse(lines, "test:25565", Set.of("minecraft:zombie"));
        assertEquals(6, result.events().size());
        assertEquals(
                Set.of(
                        EventType.BLOCK_BREAK,
                        EventType.BLOCK_PLACE,
                        EventType.PLAYER_KILL,
                        EventType.MOB_KILL,
                        EventType.CONTAINER_ADD,
                        EventType.CONTAINER_REMOVE),
                new HashSet<>(result.events().stream().map(CoreTraceEvent::type).toList()));
        var first = result.events().getFirst().context();
        assertEquals(Instant.parse("2026-09-26T03:00:00Z").toEpochMilli(), first.timestamp());
        assertEquals(-12, first.position().x());
        assertNull(first.actorUuid());
        assertNull(first.dimension());
        assertFalse(first.simulated());
        assertTrue(result.events().get(1).context().source().contains("APPROXIMATE"));
        assertNull(((KillEvent) result.events().get(2)).cause());
    }

    @Test
    void unsupportedRowsDoNotStealCoordinatesAndMissingDataStaysUnknown() {
        var result =
                new CoreProtectChatParser()
                        .parse(
                                List.of(
                                        line("1,50/h ago + Alice added x3 diamond."),
                                        line("1/m ago - Bob clicked chest."),
                                        line("^ (x1/y2/z3/world)")),
                                "server",
                                Set.of());
        assertEquals(
                0,
                result.events()
                        .size()); // An interrupted row is rejected, never attached to another
        // action.
        var item =
                new CoreProtectChatParser()
                        .parse(
                                List.of(line("1/m ago + Alice added x3 diamond.")),
                                "server",
                                Set.of())
                        .events()
                        .getFirst();
        assertNull(item.context().position());
        assertEquals("", item.context().world());
    }

    @Test
    void itemPickupDropAndBadQuantity() {
        var parser = new CoreProtectChatParser();
        assertEquals(
                EventType.ITEM_ADD,
                parser.parse(pair("picked up x2 stone"), "s", Set.of()).events().getFirst().type());
        assertEquals(
                EventType.ITEM_REMOVE,
                parser.parse(pair("dropped x2 stone"), "s", Set.of()).events().getFirst().type());
        assertEquals(1, parser.parse(pair("added x0 stone"), "s", Set.of()).skipped());
    }

    @Test
    void delayOneRequestPerPageAndLastPageStop() {
        var config = new CoreTraceConfig();
        config.commandDelayMs = 3000;
        config.autoPageAdvance = true;
        var sent = new ArrayList<String>();
        var batches = new ArrayList<List<ChatLine>>();
        var engine = new CaptureEngine(() -> config, sent::add, (l, s) -> batches.add(l));
        engine.command("co lookup r:100 t:1h", "s", NOW);
        pair("broke stone").forEach(engine::receive);
        engine.receive(line("Page 1/3"));
        engine.tick(NOW + 2999);
        assertTrue(sent.isEmpty());
        engine.tick(NOW + 3000);
        assertEquals(List.of("co l 2"), sent);
        engine.tick(NOW + 5000);
        assertEquals(1, sent.size());
        engine.receive(new ChatLine("Page 2/3", List.of(), NOW + 5000));
        engine.tick(NOW + 7999);
        assertEquals(1, sent.size());
        engine.tick(NOW + 8000);
        assertEquals("co l 3", sent.getLast());
        engine.receive(new ChatLine("Page 3/3", List.of(), NOW + 8100));
        assertFalse(engine.active());
        assertEquals(1, batches.size());
    }

    @Test
    void passiveModeMasterSwitchTimeoutAndRepeatedPage() {
        var c = new CoreTraceConfig();
        c.commandDelayMs = 3000;
        var sent = new ArrayList<String>();
        var e = new CaptureEngine(() -> c, sent::add, (l, s) -> {});
        e.command("co near", "s", NOW);
        e.receive(line("Page 1/4"));
        e.tick(NOW + 4000);
        assertTrue(sent.isEmpty());
        c.autoPageAdvance = true;
        e.command("co l", "s", NOW);
        e.receive(line("Page 1/4"));
        c.autoPageAdvance = false;
        e.configurationChanged();
        c.autoPageAdvance = true;
        e.tick(NOW + 4000);
        assertTrue(sent.isEmpty());
        e.command("co l", "s", NOW);
        e.receive(line("Page 1/4"));
        c.enabled = false;
        e.configurationChanged();
        e.tick(NOW + 4000);
        assertTrue(sent.isEmpty());
        c.enabled = true;
        e.command("co l", "s", NOW);
        e.receive(line("Page 1/4"));
        e.receive(line("Page 1/4"));
        assertFalse(e.active());
        e.command("co l", "s", NOW);
        e.tick(NOW + 30001);
        assertFalse(e.active());
        assertTrue(sent.isEmpty());
    }

    @Test
    void onlyReadOnlyQueriesAndResponsesInsideSession() {
        var c = new CoreTraceConfig();
        c.commandDelayMs = 3000;
        c.autoPageAdvance = true;
        var sent = new ArrayList<String>();
        var batches = new ArrayList<List<ChatLine>>();
        var e = new CaptureEngine(() -> c, sent::add, (l, s) -> batches.add(l));
        pair("broke stone").forEach(e::receive);
        assertTrue(batches.isEmpty());
        assertTrue(e.start("co rollback r:100", "s", NOW).startsWith("Only"));
        e.start("co lookup r:100 t:1h", "s", NOW);
        e.tick(NOW);
        assertEquals(List.of("co lookup r:100 t:1h"), sent);
        e.receive(line("CoreProtect - You do not have permission to do that."));
        assertFalse(e.active());
    }

    @Test
    void capturePersistsAndDisabledCaptureRejects() throws Exception {
        var c = new CoreTraceConfig();
        c.commandDelayMs = 3000;
        try (var service = new InvestigationService(folder, c)) {
            service.capture(pair("broke stone"), "test-server", Set.of()).join();
            assertEquals(1, service.events().size());
            service.reload().join();
            assertEquals(1, service.events().size());
            c.autoCapture = false;
            service.configure(c).join();
            service.capture(pair("broke dirt"), "test-server", Set.of()).join();
            assertEquals(1, service.events().size());
        }
    }
}
