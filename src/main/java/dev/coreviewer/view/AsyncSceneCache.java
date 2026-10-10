package dev.coreviewer.view;

import dev.coreviewer.config.CoreTraceConfig;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiPredicate;

/** Captures only chunk availability on the client; scans dense histories on a bounded worker. */
public final class AsyncSceneCache {
    private record Key(
            EventIndex index,
            String server,
            String world,
            boolean demo,
            int reached,
            List<Object> filters) {}

    private record Result(Key key, double x, double y, double z, EventIndex.Selection selection) {}

    private static final EventIndex.Selection EMPTY = new EventIndex.Selection(List.of(), 0, 0);
    private volatile Result result;
    private volatile long generation;
    private ThreadPoolExecutor worker;
    private Key requested;
    private double requestedX, requestedY, requestedZ;
    private long requestedAt;
    public long queries, hits;

    public synchronized void clear() {
        generation++;
        result = null;
        requested = null;
        if (worker != null) {
            worker.shutdownNow();
            worker = null;
        }
    }

    public EventIndex.Selection select(
            EventIndex index,
            String server,
            String world,
            boolean demo,
            double x,
            double y,
            double z,
            CoreTraceConfig config,
            double cutoff,
            int reached,
            long now,
            BiPredicate<Integer, Integer> loaded) {
        var key =
                new Key(
                        index,
                        server,
                        world,
                        demo,
                        reached,
                        List.of(
                                config.enabled,
                                config.eventRadius,
                                config.maxVisibleEvents,
                                config.blockView,
                                config.killView,
                                config.itemView,
                                config.containerView,
                                config.sessionView,
                                config.playerDeaths,
                                config.mobDeaths,
                                config.windowFrom,
                                config.windowTo));
        if (!config.enabled) {
            clear();
            return EMPTY;
        }
        if (!key.equals(requested)
                || now - requestedAt >= 200_000_000L
                || distance(x, y, z, requestedX, requestedY, requestedZ) > 16) {
            requested = key;
            requestedAt = now;
            requestedX = x;
            requestedY = y;
            requestedZ = z;
            // Never capture a ClientLevel in the worker closure.
            var chunks = new HashSet<Long>();
            int radius = config.eventRadius;
            if (config.windowFrom >= 0) {
                var history = index.timeline(server, world, demo).events();
                int end = Math.min(config.windowTo, history.size());
                for (int i = Math.min(config.windowFrom, end); i < end; i++) {
                    var p = history.get(i).context().position();
                    int cx = Math.floorDiv(p.x(), 16), cz = Math.floorDiv(p.z(), 16);
                    if (loaded.test(cx, cz)) chunks.add(chunk(cx, cz));
                }
            } else {
                // Legacy reference-query path; production uses chronological windows above.
                for (int cx = (int) Math.floor((x - radius) / 16);
                        cx <= (int) Math.floor((x + radius) / 16);
                        cx++)
                    for (int cz = (int) Math.floor((z - radius) / 16);
                            cz <= (int) Math.floor((z + radius) / 16);
                            cz++) if (loaded.test(cx, cz)) chunks.add(chunk(cx, cz));
            }
            var c = config.copy();
            long token = generation;
            if (worker == null)
                worker =
                        new ThreadPoolExecutor(
                                0,
                                1,
                                1,
                                TimeUnit.SECONDS,
                                new ArrayBlockingQueue<>(1),
                                r -> {
                                    var t = new Thread(r, "Coreviewer scene selection");
                                    t.setDaemon(true);
                                    return t;
                                },
                                new ThreadPoolExecutor.DiscardOldestPolicy());
            queries++;
            worker.execute(
                    () -> {
                        try {
                            var selected =
                                    index.select(
                                            server,
                                            world,
                                            demo,
                                            x,
                                            y,
                                            z,
                                            c,
                                            cutoff,
                                            (cx, cz) -> chunks.contains(chunk(cx, cz)));
                            synchronized (this) {
                                if (token == generation && !Thread.currentThread().isInterrupted())
                                    result = new Result(key, x, y, z, selected);
                            }
                        } catch (CancellationException ignored) {
                            /* Hidden/disabled scene. */
                        }
                    });
        }
        var ready = result;
        if (ready != null
                && ready.key.equals(key)
                && distance(x, y, z, ready.x, ready.y, ready.z) <= 16) {
            hits++;
            return ready.selection;
        }
        return EMPTY;
    }

    private static long chunk(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static double distance(double x, double y, double z, double a, double b, double c) {
        return (x - a) * (x - a) + (y - b) * (y - b) + (z - c) * (z - c);
    }
}
