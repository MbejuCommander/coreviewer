package dev.coreviewer.storage;

import dev.coreviewer.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.*;
import java.util.*;

/** CoreTrace schema 2 plus optional Coreviewer metadata. Missing evidence stays unknown. */
public final class CsvEvents {
    public record Result(List<CoreTraceEvent> events, int skipped, int unknownTime) {}

    public static Result read(String input, String server) throws IOException {
        return read(input, server, Set.of());
    }

    public static Result read(String input, String server, Set<String> entities)
            throws IOException {
        var rows = rows(input);
        if (rows.isEmpty()) throw new IOException("CSV has no header");
        var header = rows.removeFirst();
        if (!header.contains("action_id") && !header.contains("type"))
            throw new IOException("CSV needs action_id (CoreTrace) or type (legacy Coreviewer)");
        var events = new ArrayList<CoreTraceEvent>();
        int skipped = 0, unknown = 0;
        for (int row = 0; row < rows.size(); row++) {
            var values = rows.get(row);
            if (values.size() == 1 && values.getFirst().isBlank()) continue;
            var m = new HashMap<String, String>();
            for (int i = 0; i < Math.min(values.size(), header.size()); i++)
                m.put(header.get(i), values.get(i));
            try {
                EventType type = type(m);
                if (get(m, "type").isBlank() && get(m, "action_id").equals("entity_kill")) {
                    String object = get(m, "object");
                    String id = object.contains(":") ? object : "minecraft:" + object;
                    boolean mob = entities.contains(id) || object.contains(":");
                    type = mob ? EventType.MOB_KILL : EventType.PLAYER_KILL;
                    m.put("entity", mob ? id : "minecraft:player");
                    if (!mob) m.put("victim", object);
                }
                if (type == null) {
                    skipped++;
                    continue;
                }
                long time =
                        timestamp(
                                m.getOrDefault(
                                        "server_timestamp", m.getOrDefault("timestamp", "")));
                if (time == 0) unknown++;
                EventContext.Position pos = null;
                if (!get(m, "x").isBlank() && !get(m, "y").isBlank() && !get(m, "z").isBlank())
                    pos =
                            new EventContext.Position(
                                    Integer.parseInt(get(m, "x")),
                                    Integer.parseInt(get(m, "y")),
                                    Integer.parseInt(get(m, "z")));
                String id = m.getOrDefault("event_id", get(m, "id"));
                UUID uuid =
                        id.isBlank()
                                ? UUID.nameUUIDFromBytes(
                                        (server + "|" + row + "|" + values)
                                                .getBytes(StandardCharsets.UTF_8))
                                : UUID.fromString(id);
                String source = m.getOrDefault("source", "CORETRACE_CSV | " + get(m, "raw_text"));
                if (time == 0 && !source.contains("UNKNOWN_TIMESTAMP"))
                    source += " UNKNOWN_TIMESTAMP";
                var context =
                        new EventContext(
                                uuid,
                                time,
                                m.getOrDefault("server", server),
                                get(m, "world"),
                                nullable(get(m, "dimension")),
                                pos,
                                get(m, "actor"),
                                uuid(get(m, "actor_uuid")),
                                Boolean.parseBoolean(get(m, "simulated")),
                                source);
                String material = m.getOrDefault("object", get(m, "material"));
                if (!material.contains(":")) material = "minecraft:" + material;
                String quantity = m.getOrDefault("amount", get(m, "quantity"));
                int amount = quantity.isBlank() ? 0 : Integer.parseInt(quantity);
                CoreTraceEvent event =
                        switch (type) {
                            case BLOCK_BREAK, BLOCK_PLACE ->
                                    new BlockEvent(context, type, material);
                            case ITEM_ADD, ITEM_REMOVE ->
                                    new ItemEvent(context, type, material, amount);
                            case CONTAINER_ADD, CONTAINER_REMOVE ->
                                    new ContainerEvent(context, type, material, amount);
                            case SESSION_LOGIN, SESSION_LOGOUT -> new SessionEvent(context, type);
                            case PLAYER_KILL, MOB_KILL ->
                                    new KillEvent(
                                            context,
                                            type,
                                            nullable(get(m, "victim")),
                                            uuid(get(m, "victim_uuid")),
                                            m.getOrDefault("entity", material),
                                            nullable(get(m, "cause")));
                        };
                events.add(event);
            } catch (RuntimeException ex) {
                skipped++;
            }
        }
        return new Result(List.copyOf(events), skipped, unknown);
    }

    private static EventType type(Map<String, String> m) {
        if (!get(m, "type").isBlank()) return EventType.valueOf(get(m, "type"));
        if (!get(m, "event_type").isBlank()
                && !Set.of("block", "session", "container", "item", "kill")
                        .contains(get(m, "event_type"))) return null;
        return switch (get(m, "action_id")) {
            case "block_break" -> EventType.BLOCK_BREAK;
            case "block_place" -> EventType.BLOCK_PLACE;
            case "session_login" -> EventType.SESSION_LOGIN;
            case "session_logout" -> EventType.SESSION_LOGOUT;
            case "item_pickup", "ender_withdraw" -> EventType.ITEM_ADD;
            case "item_drop", "ender_deposit", "projectile_throw", "projectile_shoot" ->
                    EventType.ITEM_REMOVE;
            case "item_add" ->
                    get(m, "event_type").equals("container")
                            ? EventType.CONTAINER_ADD
                            : EventType.ITEM_ADD;
            case "item_remove" ->
                    get(m, "event_type").equals("container")
                            ? EventType.CONTAINER_REMOVE
                            : EventType.ITEM_REMOVE;
            case "entity_kill" -> EventType.MOB_KILL;
            default -> null;
        };
    }

    public static long timestamp(String value) {
        if (value.isBlank()) return 0;
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (DateTimeException ignored) {
        }
        try {
            return ZonedDateTime.parse(
                            value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.US))
                    .toInstant()
                    .toEpochMilli();
        } catch (DateTimeException ignored) {
            return 0;
        }
    }

    private static String get(Map<String, String> m, String key) {
        return m.getOrDefault(key, "");
    }

    private static String nullable(String s) {
        return s.isBlank() ? null : s;
    }

    private static UUID uuid(String s) {
        return s.isBlank() ? null : UUID.fromString(s);
    }

    public static List<List<String>> rows(String input) throws IOException {
        String text = input.startsWith("\uFEFF") ? input.substring(1) : input;
        if (text.startsWith("sep=,")) {
            int newline = text.indexOf('\n');
            text = newline < 0 ? "" : text.substring(newline + 1);
        }
        var result = new ArrayList<List<String>>();
        var row = new ArrayList<String>();
        var cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (!quoted && ch == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (!quoted && (ch == '\r' || ch == '\n')) {
                row.add(cell.toString());
                cell.setLength(0);
                result.add(row);
                row = new ArrayList<>();
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
            } else cell.append(ch);
        }
        if (quoted) throw new IOException("Unclosed CSV quote");
        if (!row.isEmpty() || !cell.isEmpty()) {
            row.add(cell.toString());
            result.add(row);
        }
        return result;
    }
}
