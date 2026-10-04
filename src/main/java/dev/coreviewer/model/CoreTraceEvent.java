package dev.coreviewer.model;

public sealed interface CoreTraceEvent permits BlockEvent, KillEvent, ItemEvent {
    EventContext context();

    EventType type();
}
