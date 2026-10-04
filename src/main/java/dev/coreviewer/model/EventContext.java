package dev.coreviewer.model;

import java.util.Objects;
import java.util.UUID;

/** Null UUID/cause/position means unavailable, never inferred evidence. */
public record EventContext(
        UUID id,
        long timestamp,
        String server,
        String world,
        String dimension,
        Position position,
        String actor,
        UUID actorUuid,
        boolean simulated,
        String source) {
    public EventContext {
        Objects.requireNonNull(id);
        Objects.requireNonNull(server);
        Objects.requireNonNull(world);
        Objects.requireNonNull(actor);
        Objects.requireNonNull(source);
        if (timestamp < 0) throw new IllegalArgumentException("Negative timestamp");
    }

    public record Position(int x, int y, int z) {}
}
