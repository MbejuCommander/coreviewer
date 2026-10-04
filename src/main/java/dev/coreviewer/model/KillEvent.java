package dev.coreviewer.model;

import java.util.Objects;
import java.util.UUID;

public record KillEvent(
        EventContext context,
        EventType type,
        String victim,
        UUID victimUuid,
        String entity,
        String cause)
        implements CoreTraceEvent {
    public KillEvent {
        Objects.requireNonNull(context);
        Objects.requireNonNull(entity);
        if (type != EventType.PLAYER_KILL && type != EventType.MOB_KILL)
            throw new IllegalArgumentException("Not a kill action");
    }
}
