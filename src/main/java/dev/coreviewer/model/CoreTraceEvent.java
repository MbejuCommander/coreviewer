package dev.coreviewer.model;

public sealed interface CoreTraceEvent
        permits BlockEvent, KillEvent, ItemEvent, ContainerEvent, SessionEvent {
    EventContext context();

    EventType type();
}
