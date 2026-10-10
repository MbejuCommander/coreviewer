package dev.coreviewer.view;

import dev.coreviewer.config.CoreTraceConfig;
import dev.coreviewer.model.*;

import java.util.*;

/** Pure selection/chronology logic; world binding is explicitly chosen by the moderator. */
public final class StaticScene {
    public record Link(CoreTraceEvent from, CoreTraceEvent to) {}

    public static List<CoreTraceEvent> select(
            List<CoreTraceEvent> source,
            String server,
            String world,
            double x,
            double y,
            double z,
            CoreTraceConfig c,
            boolean demo) {
        if (!c.enabled) return List.of();
        return source.stream()
                .filter(e -> e.context().simulated() == demo)
                .filter(
                        e ->
                                e.context().server().equals(server)
                                        && e.context().world().equals(world))
                .filter(e -> e.context().position() != null)
                .filter(e -> visible(e, c))
                .filter(e -> distance(e, x, y, z) <= (double) c.eventRadius * c.eventRadius)
                .sorted(Comparator.comparingDouble(e -> distance(e, x, y, z)))
                .limit(c.maxVisibleEvents)
                .sorted(Comparator.comparingLong(e -> e.context().timestamp()))
                .toList();
    }

    public static boolean visible(CoreTraceEvent e, CoreTraceConfig c) {
        return switch (e.type()) {
            case BLOCK_BREAK, BLOCK_PLACE -> c.blockView;
            case PLAYER_KILL -> c.killView && c.playerDeaths;
            case MOB_KILL -> c.killView && c.mobDeaths;
            case ITEM_ADD, ITEM_REMOVE -> c.itemView;
            case CONTAINER_ADD, CONTAINER_REMOVE -> c.containerView;
            case SESSION_LOGIN, SESSION_LOGOUT -> c.sessionView;
        };
    }

    private static double distance(CoreTraceEvent e, double x, double y, double z) {
        var p = e.context().position();
        double dx = p.x() + .5 - x, dy = p.y() + .5 - y, dz = p.z() + .5 - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public static List<Link> links(List<CoreTraceEvent> ordered, boolean samePlayer) {
        var result = new ArrayList<Link>();
        var previous = new HashMap<String, CoreTraceEvent>();
        for (var e : ordered) {
            if (e.context().position() == null) continue;
            String group = samePlayer ? e.context().actor() : "*";
            var before = previous.put(group, e);
            if (before == null || before.context().timestamp() >= e.context().timestamp()) continue;
            if (before.context().source().contains("APPROXIMATE")
                    || e.context().source().contains("APPROXIMATE")) continue;
            if (!before.context().position().equals(e.context().position()))
                result.add(new Link(before, e));
        }
        return List.copyOf(result);
    }
}
