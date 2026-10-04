package dev.coreviewer.capture;

import java.util.List;

/** Immutable snapshot: no Minecraft objects cross the worker boundary. */
public record ChatLine(String text, List<String> hover, long receivedAt) {
    public ChatLine {
        hover = List.copyOf(hover);
    }
}
