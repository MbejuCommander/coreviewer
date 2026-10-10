package dev.coreviewer.model;

import java.util.Objects;

public record SessionEvent(EventContext context, EventType type) implements CoreTraceEvent {
    public SessionEvent {
        Objects.requireNonNull(context);
        if (type != EventType.SESSION_LOGIN && type != EventType.SESSION_LOGOUT)
            throw new IllegalArgumentException("Not a session action");
    }
}
