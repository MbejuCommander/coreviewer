package dev.coreviewer.model;

import java.util.Objects;

public record BlockEvent(EventContext context, EventType type, String block)
        implements CoreTraceEvent {
    public BlockEvent {
        Objects.requireNonNull(context);
        Objects.requireNonNull(block);
        if (type != EventType.BLOCK_BREAK && type != EventType.BLOCK_PLACE)
            throw new IllegalArgumentException("Not a block action");
    }
}
