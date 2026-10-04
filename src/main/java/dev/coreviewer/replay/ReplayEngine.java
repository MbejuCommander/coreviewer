package dev.coreviewer.replay;

import dev.coreviewer.config.CoreTraceConfig;
import dev.coreviewer.model.*;

import java.util.*;

/** A deterministic action timeline, not a recording or a terrain rollback. */
public final class ReplayEngine {
    private List<CoreTraceEvent> events = List.of();
    private double cursor, start, end;
    private boolean playing, approximate;

    public void load(List<CoreTraceEvent> source) {
        events =
                source.stream()
                        .filter(e -> e.context().position() != null)
                        .sorted(Comparator.comparingLong(e -> e.context().timestamp()))
                        .toList();
        approximate = events.stream().anyMatch(e -> e.context().source().contains("APPROXIMATE"));
        resetBounds();
    }

    /** Input is the immutable, sorted timeline prepared by EventIndex on the storage worker. */
    public void loadPrepared(dev.coreviewer.view.EventIndex.Timeline timeline) {
        events = timeline.events();
        approximate = timeline.approximate();
        resetBounds();
    }

    private void resetBounds() {
        start = events.isEmpty() ? 0 : Math.max(0, events.getFirst().context().timestamp() - 1000d);
        end = events.isEmpty() ? 0 : events.getLast().context().timestamp() + 4000d;
        cursor = start;
        playing = false;
    }

    public List<CoreTraceEvent> events() {
        return events;
    }

    public boolean approximate() {
        return approximate;
    }

    public boolean loaded() {
        return !events.isEmpty();
    }

    public boolean playing() {
        return playing;
    }

    public double cursor() {
        return cursor;
    }

    public double start() {
        return start;
    }

    public double end() {
        return end;
    }

    public double fraction() {
        return end == start ? 0 : (cursor - start) / (end - start);
    }

    public void pause() {
        playing = false;
    }

    public void play() {
        if (!loaded()) return;
        if (cursor >= end) cursor = start;
        playing = true;
    }

    public void toggle() {
        if (playing) pause();
        else play();
    }

    public void advance(double elapsedMillis, CoreTraceConfig config) {
        if (!config.enabled) {
            pause();
            return;
        }
        if (!playing || !Double.isFinite(elapsedMillis) || elapsedMillis <= 0) return;
        double target = cursor + elapsedMillis * config.timelineSpeed;
        if (config.smartTimeline) {
            int next = visibleCount();
            double tail = 1000 / Math.min(config.blockBreakSpeed, config.blockPlaceSpeed);
            while (next < events.size()) {
                double nextTime = events.get(next).context().timestamp();
                double previous = next == 0 ? start : events.get(next - 1).context().timestamp();
                double skipAt = Math.max(cursor, previous + tail);
                if (nextTime - previous > 10000 && target >= skipAt && nextTime > skipAt)
                    target += nextTime - skipAt;
                if (nextTime > target) break;
                next = bound(nextTime, true);
            }
        }
        cursor = Math.min(end, target);
        if (cursor >= end) pause();
    }

    public void seek(double timestamp) {
        if (!Double.isFinite(timestamp)) return;
        cursor = Math.clamp(timestamp, start, end);
        pause();
    }

    public void seekFraction(double fraction) {
        seek(start + (end - start) * fraction);
    }

    private int bound(double time, boolean inclusive) {
        int low = 0, high = events.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            long t = events.get(mid).context().timestamp();
            if (t < time || (inclusive && t == time)) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    public int visibleCount() {
        return bound(cursor, true);
    }

    public void next() {
        int at = visibleCount();
        seek(at < events.size() ? events.get(at).context().timestamp() : end);
    }

    public void previous() {
        int at = bound(cursor, false) - 1;
        seek(at < 0 ? start : events.get(at).context().timestamp());
    }

    /** A constant-space slice; no materialization of all past events while rendering. */
    public List<CoreTraceEvent> visible() {
        return events.subList(0, visibleCount());
    }

    public double progress(CoreTraceEvent event, CoreTraceConfig c) {
        double speed =
                event.type() == EventType.BLOCK_BREAK ? c.blockBreakSpeed : c.blockPlaceSpeed;
        return Math.clamp((cursor - event.context().timestamp()) * speed / 1000d, 0, 1);
    }
}
