package dev.coreviewer;

import static org.junit.jupiter.api.Assertions.*;

import dev.coreviewer.config.CoreTraceConfig;
import dev.coreviewer.model.*;
import dev.coreviewer.view.StaticScene;

import org.junit.jupiter.api.Test;

import java.util.*;

class PhaseThreeTest {
    BlockEvent block(
            long time,
            String actor,
            int x,
            String server,
            String world,
            boolean simulated,
            String source) {
        return new BlockEvent(
                new EventContext(
                        UUID.randomUUID(),
                        time,
                        server,
                        world,
                        null,
                        new EventContext.Position(x, 64, 0),
                        actor,
                        null,
                        simulated,
                        source),
                EventType.BLOCK_BREAK,
                "minecraft:stone");
    }

    @Test
    void selectionIsolatesWorldServerAndSimulation() {
        var c = new CoreTraceConfig();
        var correct = block(1000, "A", 0, "s", "world", false, "COREPROTECT_CHAT");
        var all =
                List.<CoreTraceEvent>of(
                        correct,
                        block(2000, "A", 1, "s2", "world", false, "X"),
                        block(3000, "A", 2, "s", "nether", false, "X"),
                        block(4000, "A", 3, "s", "world", true, "SIMULATED"));
        assertEquals(List.of(correct), StaticScene.select(all, "s", "world", 0, 64, 0, c, false));
        c.enabled = false;
        assertTrue(StaticScene.select(all, "s", "world", 0, 64, 0, c, false).isEmpty());
    }

    @Test
    void radiusCapAndChronologicalOrder() {
        var c = new CoreTraceConfig();
        c.eventRadius = 50;
        c.maxVisibleEvents = 2;
        var a = block(3000, "A", 0, "s", "w", false, "X");
        var b = block(1000, "A", 1, "s", "w", false, "X");
        var list =
                List.<CoreTraceEvent>of(
                        a,
                        b,
                        block(2000, "A", 4, "s", "w", false, "X"),
                        block(0, "A", 100, "s", "w", false, "X"));
        assertEquals(List.of(b, a), StaticScene.select(list, "s", "w", 0, 64, 0, c, false));
        c.blockView = false;
        assertTrue(StaticScene.select(list, "s", "w", 0, 64, 0, c, false).isEmpty());
    }

    @Test
    void arrowsRespectActorTiesApproximationAndDirection() {
        var a = block(1000, "A", 0, "s", "w", false, "X");
        var b = block(2000, "B", 1, "s", "w", false, "X");
        var c = block(3000, "A", 2, "s", "w", false, "X");
        var links = StaticScene.links(List.of(a, b, c), true);
        assertEquals(1, links.size());
        assertEquals(a, links.getFirst().from());
        assertEquals(c, links.getFirst().to());
        assertEquals(2, StaticScene.links(List.of(a, b, c), false).size());
        assertTrue(
                StaticScene.links(List.of(a, block(1000, "A", 1, "s", "w", false, "X")), true)
                        .isEmpty());
        assertTrue(
                StaticScene.links(
                                List.of(
                                        a,
                                        block(
                                                2000,
                                                "A",
                                                1,
                                                "s",
                                                "w",
                                                false,
                                                "COREPROTECT_CHAT_APPROXIMATE")),
                                true)
                        .isEmpty());
        assertTrue(
                StaticScene.links(List.of(a, block(2000, "A", 0, "s", "w", false, "X")), true)
                        .isEmpty());
    }

    @Test
    void missingCoordinatesAndDeathToggles() {
        var c = new CoreTraceConfig();
        var context = block(1, "A", 0, "s", "w", false, "X").context();
        var player =
                new KillEvent(
                        context, EventType.PLAYER_KILL, "Victim", null, "minecraft:player", null);
        var mob = new KillEvent(context, EventType.MOB_KILL, null, null, "minecraft:zombie", null);
        c.playerDeaths = false;
        assertFalse(StaticScene.visible(player, c));
        assertTrue(StaticScene.visible(mob, c));
        c.killView = false;
        assertFalse(StaticScene.visible(mob, c));
        var unknown =
                new EventContext(UUID.randomUUID(), 1, "s", "w", null, null, "A", null, false, "X");
        assertTrue(
                StaticScene.select(
                                List.of(
                                        new BlockEvent(
                                                unknown, EventType.BLOCK_BREAK, "minecraft:stone")),
                                "s",
                                "w",
                                0,
                                0,
                                0,
                                c,
                                false)
                        .isEmpty());
    }
}
