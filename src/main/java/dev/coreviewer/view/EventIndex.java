package dev.coreviewer.view;

import dev.coreviewer.config.CoreTraceConfig;
import dev.coreviewer.model.*;

import java.util.*;
import java.util.function.BiPredicate;

/** Immutable, worker-built index. No Minecraft objects or forced chunk loads. */
public final class EventIndex {
    private record Scope(String server, String world, boolean demo) {}

    private record Chunk(int x, int z) {}

    private record Entry(CoreTraceEvent event, int ordinal) {}

    public record Timeline(List<CoreTraceEvent> events, boolean approximate) {}

    public record Selection(List<CoreTraceEvent> events, int examined, int chunks) {}

    private final Map<Scope, Map<Chunk, List<Entry>>> buckets;
    private final Map<Scope, Timeline> timelines;
    public static final EventIndex EMPTY = new EventIndex(List.of());

    public EventIndex(List<CoreTraceEvent> events) {
        var working = new HashMap<Scope, Map<Chunk, List<Entry>>>();
        var histories = new HashMap<Scope, List<CoreTraceEvent>>();
        for (int i = 0; i < events.size(); i++) {
            var e = events.get(i);
            var c = e.context();
            var p = c.position();
            if (p == null || c.world().isBlank()) continue;
            var scope = new Scope(c.server(), c.world(), c.simulated());
            histories.computeIfAbsent(scope, ignored -> new ArrayList<>()).add(e);
            working.computeIfAbsent(scope, ignored -> new HashMap<>())
                    .computeIfAbsent(
                            new Chunk(Math.floorDiv(p.x(), 16), Math.floorDiv(p.z(), 16)),
                            ignored -> new ArrayList<>())
                    .add(new Entry(e, i));
        }
        var frozen = new HashMap<Scope, Map<Chunk, List<Entry>>>();
        working.forEach(
                (scope, chunks) -> {
                    var copy = new HashMap<Chunk, List<Entry>>();
                    chunks.forEach(
                            (chunk, list) -> {
                                list.sort(
                                        Comparator.comparingLong(
                                                e -> e.event.context().timestamp()));
                                copy.put(chunk, List.copyOf(list));
                            });
                    frozen.put(scope, Map.copyOf(copy));
                });
        buckets = Map.copyOf(frozen);
        var ordered = new HashMap<Scope, Timeline>();
        histories.forEach(
                (scope, list) -> {
                    list.sort(Comparator.comparingLong(e -> e.context().timestamp()));
                    ordered.put(
                            scope,
                            new Timeline(
                                    List.copyOf(list),
                                    list.stream()
                                            .anyMatch(
                                                    e ->
                                                            e.context()
                                                                    .source()
                                                                    .contains("APPROXIMATE"))));
                });
        timelines = Map.copyOf(ordered);
    }

    public Timeline timeline(String server, String world, boolean demo) {
        return timelines.getOrDefault(
                new Scope(server, world, demo), new Timeline(List.of(), false));
    }

    public List<String> worlds(String server) {
        return timelines.keySet().stream()
                .filter(s -> s.server.equals(server) && !s.demo)
                .map(Scope::world)
                .sorted()
                .toList();
    }

    public Selection select(
            String server,
            String world,
            boolean demo,
            double x,
            double y,
            double z,
            CoreTraceConfig config,
            double cutoff,
            BiPredicate<Integer, Integer> loaded) {
        if (!config.enabled) return new Selection(List.of(), 0, 0);
        if (config.windowFrom >= 0) {
            var history = timeline(server, world, demo).events();
            int from = Math.min(config.windowFrom, history.size()),
                    to = Math.min(config.windowTo, history.size());
            var selected = new ArrayList<CoreTraceEvent>();
            int examined = 0;
            for (int i = from; i < to; i++) {
                if (Thread.currentThread().isInterrupted())
                    throw new java.util.concurrent.CancellationException();
                var event = history.get(i);
                var pos = event.context().position();
                examined++;
                if (event.context().timestamp() <= cutoff
                        && StaticScene.visible(event, config)
                        && distance(event, x, y, z)
                                <= (double) config.eventRadius * config.eventRadius
                        && loaded.test(Math.floorDiv(pos.x(), 16), Math.floorDiv(pos.z(), 16)))
                    selected.add(event);
            }
            return new Selection(List.copyOf(selected), examined, 0);
        }
        var scope = buckets.get(new Scope(server, world, demo));
        if (scope == null) return new Selection(List.of(), 0, 0);
        Comparator<Entry> nearest =
                Comparator.comparingDouble((Entry e) -> distance(e.event, x, y, z))
                        .thenComparingInt(Entry::ordinal);
        var heap = new PriorityQueue<Entry>(nearest.reversed());
        int radius = config.eventRadius, examined = 0, chunks = 0;
        int minX = (int) Math.floor((x - radius) / 16), maxX = (int) Math.floor((x + radius) / 16);
        int minZ = (int) Math.floor((z - radius) / 16), maxZ = (int) Math.floor((z + radius) / 16);
        for (int cx = minX; cx <= maxX; cx++)
            for (int cz = minZ; cz <= maxZ; cz++) {
                var list = scope.get(new Chunk(cx, cz));
                if (list == null || !loaded.test(cx, cz)) continue;
                chunks++;
                for (var entry : list) {
                    if (entry.event.context().timestamp() > cutoff) break;
                    if ((examined & 1023) == 0 && Thread.currentThread().isInterrupted())
                        throw new java.util.concurrent.CancellationException();
                    examined++;
                    if (!StaticScene.visible(entry.event, config)
                            || distance(entry.event, x, y, z) > (double) radius * radius) continue;
                    if (heap.size() < config.maxVisibleEvents) heap.add(entry);
                    else if (nearest.compare(entry, heap.peek()) < 0) {
                        heap.poll();
                        heap.add(entry);
                    }
                }
            }
        // Preserve the old stable ordering for equal timestamps: distance, then input order.
        var result = new ArrayList<>(heap);
        result.sort(
                Comparator.comparingLong((Entry e) -> e.event.context().timestamp())
                        .thenComparing(nearest));
        return new Selection(result.stream().map(Entry::event).toList(), examined, chunks);
    }

    private static double distance(CoreTraceEvent e, double x, double y, double z) {
        var p = e.context().position();
        double dx = p.x() + .5 - x, dy = p.y() + .5 - y, dz = p.z() + .5 - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
