package dev.coreviewer.model;

import java.util.Objects;

public record ContainerEvent(EventContext context, EventType type, String item, int quantity)
        implements CoreTraceEvent {
    public ContainerEvent {
        Objects.requireNonNull(context);
        Objects.requireNonNull(item);
        if (type != EventType.CONTAINER_ADD && type != EventType.CONTAINER_REMOVE)
            throw new IllegalArgumentException("Not a container action");
        if (quantity < 0) throw new IllegalArgumentException("Negative quantity");
    }
}
