package dev.coreviewer.model;

import java.util.*;

public final class EventStatistics {
    public enum Category {
        ALL,
        BLOCKS,
        ITEMS,
        CONTAINERS,
        KILLS,
        SESSIONS
    }

    public record Table(
            List<String> columns,
            List<String> players,
            Map<String, Map<String, Long>> values,
            Map<String, Long> totals,
            long events,
            long unknownAmounts) {}

    public static Category category(CoreTraceEvent event) {
        if (event instanceof BlockEvent) return Category.BLOCKS;
        if (event instanceof ItemEvent) return Category.ITEMS;
        if (event instanceof ContainerEvent) return Category.CONTAINERS;
        if (event instanceof KillEvent) return Category.KILLS;
        return Category.SESSIONS;
    }

    public static Table build(List<CoreTraceEvent> events, Category selected, String player) {
        return build(events, selected, player, false);
    }

    public static Table build(
            List<CoreTraceEvent> events, Category selected, String player, boolean materials) {
        var values = new TreeMap<String, Map<String, Long>>();
        var totals = new TreeMap<String, Long>();
        long count = 0, unknown = 0;
        for (var event : events) {
            if (selected != Category.ALL && category(event) != selected) continue;
            if (player != null && !event.context().actor().equals(player)) continue;
            String material =
                    event instanceof BlockEvent b
                            ? b.block()
                            : event instanceof ItemEvent i
                                    ? i.item()
                                    : event instanceof ContainerEvent c
                                            ? c.item()
                                            : event instanceof KillEvent k ? k.entity() : "session";
            String column =
                    selected == Category.ALL && !materials
                            ? event.type().name()
                            : event.type().name() + "|" + material;
            long amount =
                    event instanceof ItemEvent i
                            ? i.quantity()
                            : event instanceof ContainerEvent c ? c.quantity() : 1;
            if (amount == 0) unknown++;
            values.computeIfAbsent(event.context().actor(), a -> new TreeMap<>())
                    .merge(column, amount, Long::sum);
            totals.merge(column, amount, Long::sum);
            count++;
        }
        var frozen = new TreeMap<String, Map<String, Long>>();
        values.forEach((k, v) -> frozen.put(k, Map.copyOf(v)));
        return new Table(
                List.copyOf(totals.keySet()),
                List.copyOf(values.keySet()),
                Map.copyOf(frozen),
                Map.copyOf(totals),
                count,
                unknown);
    }

    public record Column(
            String label,
            String icon,
            List<String> positive,
            List<String> negative,
            boolean paired,
            boolean neutral,
            String tooltip) {
        public long plus(Map<String, Long> row) {
            return positive.stream().mapToLong(k -> row.getOrDefault(k, 0L)).sum();
        }

        public long minus(Map<String, Long> row) {
            return negative.stream().mapToLong(k -> row.getOrDefault(k, 0L)).sum();
        }
    }

    public static List<Column> columns(Table table, boolean compact) {
        var groups = new LinkedHashMap<String, List<String>>();
        for (String key : table.columns()) {
            String[] parts = key.split("\\|", 2);
            String type = parts[0], material = parts.length > 1 ? parts[1] : "";
            String family =
                    type.startsWith("BLOCK")
                            ? "BLOCKS"
                            : type.startsWith("ITEM")
                                    ? "ITEMS"
                                    : type.startsWith("CONTAINER")
                                            ? "CONTAINERS"
                                            : type.startsWith("SESSION") ? "SESSIONS" : "KILLS";
            groups.computeIfAbsent(compact ? family + "|" + material : key, k -> new ArrayList<>())
                    .add(key);
        }
        var result = new ArrayList<Column>();
        for (var group : groups.entrySet()) {
            String first = group.getValue().getFirst();
            String type = first.split("\\|", 2)[0];
            String material = first.contains("|") ? first.substring(first.indexOf('|') + 1) : "";
            boolean neutral = type.endsWith("KILL"), session = type.startsWith("SESSION");
            var plus = new ArrayList<String>();
            var minus = new ArrayList<String>();
            for (String key : group.getValue())
                if (key.startsWith("BLOCK_BREAK")
                        || key.startsWith("ITEM_REMOVE")
                        || key.startsWith("CONTAINER_REMOVE")
                        || key.startsWith("SESSION_LOGOUT")) minus.add(key);
                else plus.add(key);
            String icon =
                    session
                            ? "minecraft:player_head"
                            : neutral
                                    ? (material.equals("minecraft:player")
                                            ? "minecraft:player_head"
                                            : material + "_spawn_egg")
                                    : material;
            String label =
                    session ? "Sessions" : material.replace("minecraft:", "").replace('_', ' ');
            String tip = String.join(" / ", group.getValue()).replace('|', ' ').replace('_', ' ');
            if (compact && !neutral)
                tip +=
                        "\nGreen / red: "
                                + (type.startsWith("BLOCK")
                                        ? "placed / broken"
                                        : session ? "logins / logouts" : "added / removed");
            else if (neutral) tip += "\nKill count";
            result.add(
                    new Column(
                            label,
                            icon,
                            List.copyOf(plus),
                            List.copyOf(minus),
                            compact && !neutral,
                            neutral,
                            tip));
        }
        return List.copyOf(result);
    }
}
