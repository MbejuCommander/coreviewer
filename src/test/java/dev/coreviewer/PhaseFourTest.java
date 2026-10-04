package dev.coreviewer;

import static org.junit.jupiter.api.Assertions.*;

import dev.coreviewer.config.CoreTraceConfig;
import dev.coreviewer.model.*;
import dev.coreviewer.replay.ReplayEngine;

import org.junit.jupiter.api.Test;

import java.util.*;

class PhaseFourTest {
    private CoreTraceEvent event(long t, EventType type) {
        var ctx =
                new EventContext(
                        UUID.randomUUID(),
                        t,
                        "server",
                        "world",
                        null,
                        new EventContext.Position(1, 64, 1),
                        "Actor",
                        null,
                        false,
                        "COREPROTECT_CHAT");
        return new BlockEvent(ctx, type, "minecraft:stone");
    }

    @Test
    void snapshotSortsAndDoesNotFollowNewCapture() {
        var source = new ArrayList<CoreTraceEvent>();
        source.add(event(4000, EventType.BLOCK_BREAK));
        source.add(event(2000, EventType.BLOCK_PLACE));
        var e = new ReplayEngine();
        e.load(source);
        source.clear();
        assertEquals(2, e.events().size());
        assertTrue(e.visible().isEmpty());
        e.next();
        assertEquals(2000, e.cursor());
        assertEquals(EventType.BLOCK_PLACE, e.visible().getFirst().type());
    }

    @Test
    void stepsGroupTiesAndRewindRemovesFutureActions() {
        var e = new ReplayEngine();
        e.load(
                List.of(
                        event(2000, EventType.BLOCK_BREAK),
                        event(2000, EventType.BLOCK_PLACE),
                        event(8000, EventType.BLOCK_BREAK)));
        e.next();
        assertEquals(2, e.visible().size());
        assertEquals(2000, e.cursor());
        e.next();
        assertEquals(3, e.visible().size());
        e.previous();
        assertEquals(2, e.visible().size());
        assertFalse(e.playing());
        e.previous();
        assertTrue(e.visible().isEmpty());
    }

    @Test
    void speedPauseEndAndRestart() {
        var e = new ReplayEngine();
        var c = new CoreTraceConfig();
        e.load(List.of(event(2000, EventType.BLOCK_BREAK)));
        c.timelineSpeed = 2;
        e.play();
        e.advance(250, c);
        assertEquals(1500, e.cursor());
        e.pause();
        e.advance(1000, c);
        assertEquals(1500, e.cursor());
        e.play();
        e.advance(10000, c);
        assertEquals(e.end(), e.cursor());
        assertFalse(e.playing());
        e.play();
        assertEquals(e.start(), e.cursor());
        assertTrue(e.playing());
        c.enabled = false;
        e.advance(100, c);
        assertFalse(e.playing());
        assertEquals(e.start(), e.cursor());
    }

    @Test
    void independentAnimationSpeedsAndDeterministicSeek() {
        var a = event(2000, EventType.BLOCK_BREAK);
        var b = event(2000, EventType.BLOCK_PLACE);
        var e = new ReplayEngine();
        var c = new CoreTraceConfig();
        e.load(List.of(a, b));
        c.blockBreakSpeed = .5;
        c.blockPlaceSpeed = 2;
        e.seek(2250);
        assertEquals(.125, e.progress(a, c));
        assertEquals(.5, e.progress(b, c));
        e.seekFraction(1);
        assertEquals(1, e.progress(a, c));
        e.seek(2250);
        assertEquals(.125, e.progress(a, c));
    }

    @Test
    void emptyInvalidAndClampedSeeks() {
        var e = new ReplayEngine();
        e.load(List.of());
        e.play();
        e.next();
        e.previous();
        assertFalse(e.playing());
        assertEquals(0, e.fraction());
        e.load(List.of(event(2000, EventType.BLOCK_PLACE)));
        e.seek(Double.NaN);
        assertEquals(e.start(), e.cursor());
        e.seek(-100);
        assertEquals(e.start(), e.cursor());
        e.seek(Double.MAX_VALUE);
        assertEquals(e.end(), e.cursor());
    }
}
