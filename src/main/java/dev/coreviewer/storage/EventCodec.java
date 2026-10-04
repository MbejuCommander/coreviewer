package dev.coreviewer.storage;

import com.google.gson.*;

import dev.coreviewer.model.*;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

public final class EventCodec {
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    public static String json(List<CoreTraceEvent> events) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        JsonArray rows = new JsonArray();
        for (var event : events) rows.add(GSON.toJsonTree(event));
        root.add("events", rows);
        return GSON.toJson(root) + "\n";
    }

    public static List<CoreTraceEvent> parseJson(String data) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(data).getAsJsonObject();
            if (root.get("schemaVersion").getAsInt() != 1)
                throw new IllegalArgumentException("Unsupported events schema");
            List<CoreTraceEvent> events = new ArrayList<>();
            Set<UUID> ids = new HashSet<>();
            for (JsonElement row : root.getAsJsonArray("events")) {
                EventType type = EventType.valueOf(row.getAsJsonObject().get("type").getAsString());
                CoreTraceEvent event =
                        switch (type) {
                            case BLOCK_BREAK, BLOCK_PLACE -> GSON.fromJson(row, BlockEvent.class);
                            case PLAYER_KILL, MOB_KILL -> GSON.fromJson(row, KillEvent.class);
                            case ITEM_ADD, ITEM_REMOVE -> GSON.fromJson(row, ItemEvent.class);
                        };
                if (!ids.add(event.context().id()))
                    throw new IllegalArgumentException("Duplicate event ID");
                events.add(event);
            }
            return List.copyOf(events);
        } catch (RuntimeException ex) {
            throw new IOException("Invalid events.json; original file was not modified", ex);
        }
    }

    public static String csv(List<CoreTraceEvent> events) {
        StringBuilder out =
                new StringBuilder(
                        "id,type,timestamp,server,world,dimension,x,y,z,actor,actor_uuid,material,quantity,victim,victim_uuid,entity,cause,simulated,source\r\n");
        for (var event : events) {
            var c = event.context();
            var p = c.position();
            String material = "",
                    quantity = "",
                    victim = "",
                    victimId = "",
                    entity = "",
                    cause = "";
            if (event instanceof BlockEvent b) material = b.block();
            if (event instanceof ItemEvent i) {
                material = i.item();
                quantity = "" + i.quantity();
            }
            if (event instanceof KillEvent k) {
                victim = nullable(k.victim());
                victimId = nullable(k.victimUuid());
                entity = k.entity();
                cause = nullable(k.cause());
            }
            String[] values = {
                c.id().toString(),
                event.type().name(),
                Instant.ofEpochMilli(c.timestamp()).toString(),
                c.server(),
                c.world(),
                nullable(c.dimension()),
                p == null ? "" : "" + p.x(),
                p == null ? "" : "" + p.y(),
                p == null ? "" : "" + p.z(),
                c.actor(),
                nullable(c.actorUuid()),
                material,
                quantity,
                victim,
                victimId,
                entity,
                cause,
                "" + c.simulated(),
                c.source()
            };
            out.append(
                            Arrays.stream(values)
                                    .map(EventCodec::escape)
                                    .collect(java.util.stream.Collectors.joining(",")))
                    .append("\r\n");
        }
        return out.toString();
    }

    private static String nullable(Object o) {
        return o == null ? "" : o.toString();
    }

    public static String escape(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
