package dev.coreviewer.model;

import java.util.Objects;

public record ItemEvent(EventContext context, EventType type, String item, int quantity)
        implements CoreTraceEvent {
    public ItemEvent {
        Objects.requireNonNull(context);
        Objects.requireNonNull(item);
        if (type != EventType.ITEM_ADD && type != EventType.ITEM_REMOVE)
            throw new IllegalArgumentException("Not an item action");
        if (quantity < 1) throw new IllegalArgumentException("Quantity must be positive");
    }
}
