package dev.coreviewer.model;

import java.util.*;

/** Pure operations shared by the table and its paged evidence drilldown. */
public final class StatisticsQuery {
    private static final java.time.format.DateTimeFormatter TIME =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
                    .withZone(java.time.ZoneOffset.UTC);

    public enum Metric {
        ADDED,
        REMOVED,
        TOTAL
    }

    public static String key(CoreTraceEvent event) {
        String material =
                event instanceof BlockEvent b
                        ? b.block()
                        : event instanceof ItemEvent i
                                ? i.item()
                                : event instanceof ContainerEvent c
                                        ? c.item()
                                        : event instanceof KillEvent k ? k.entity() : "session";
        return event.type().name() + "|" + material;
    }

    public static List<EventStatistics.Column> columns(
            List<EventStatistics.Column> columns, String search) {
        String needle = search.strip().toLowerCase(Locale.ROOT).replace('_', ' ');
        return columns.stream()
                .filter(
                        c ->
                                (c.label() + " " + c.tooltip())
                                        .toLowerCase(Locale.ROOT)
                                        .replace('_', ' ')
                                        .contains(needle))
                .toList();
    }

    public static List<String> players(
            EventStatistics.Table table, EventStatistics.Column column, Metric metric, int order) {
        Comparator<String> alphabetical =
                String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
        Comparator<String> comparator = alphabetical;
        if (column != null && order != 0) {
            Comparator<String> counts =
                    Comparator.comparingLong(
                            p -> {
                                var row = table.values().get(p);
                                if (!column.paired()) return column.plus(row) + column.minus(row);
                                return metric == Metric.ADDED
                                        ? column.plus(row)
                                        : metric == Metric.REMOVED
                                                ? column.minus(row)
                                                : column.plus(row) + column.minus(row);
                            });
            comparator = (order == 1 ? counts.reversed() : counts).thenComparing(alphabetical);
        }
        return table.players().stream().sorted(comparator).toList();
    }

    public static int findPlayer(List<String> players, String search) {
        if (search.isBlank()) return 0;
        for (int i = 0; i < players.size(); i++)
            if (players.get(i).equalsIgnoreCase(search.strip())) return i;
        for (int i = 0; i < players.size(); i++)
            if (players.get(i)
                    .toLowerCase(Locale.ROOT)
                    .contains(search.strip().toLowerCase(Locale.ROOT))) return i;
        return -1;
    }

    public record EvidencePage(List<CoreTraceEvent> events, int total) {}

    public static EvidencePage evidence(
            List<CoreTraceEvent> source,
            String player,
            EventStatistics.Column column,
            int page,
            int size) {
        Set<String> keys = new HashSet<>(column.positive());
        keys.addAll(column.negative());
        // Source snapshots are already chronological. Only retain the requested page.
        var selected = new ArrayList<CoreTraceEvent>();
        int total = 0;
        for (var e : source)
            if (e.context().actor().equals(player) && keys.contains(key(e))) {
                if (total >= page * size && selected.size() < size) selected.add(e);
                total++;
            }
        return new EvidencePage(List.copyOf(selected), total);
    }

    public static String time(CoreTraceEvent e) {
        if (e.context().timestamp() == 0) return "Unknown";
        return (e.context().source().contains("APPROXIMATE") ? "~" : "")
                + TIME.format(java.time.Instant.ofEpochMilli(e.context().timestamp()));
    }
}
