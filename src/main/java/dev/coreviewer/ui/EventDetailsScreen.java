package dev.coreviewer.ui;

import dev.coreviewer.model.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Bounded, read-only evidence page. No per-frame scans or world modifications. */
public final class EventDetailsScreen extends Screen {
    private final Screen parent;
    private final List<CoreTraceEvent> snapshot;
    private final String player;
    private final EventStatistics.Column column;
    private StatisticsQuery.EvidencePage result;
    private int page, pageSize;
    private long generation;
    private boolean loading;

    public EventDetailsScreen(
            Screen parent,
            List<CoreTraceEvent> snapshot,
            String player,
            EventStatistics.Column column) {
        super(Component.literal(player + " — " + column.label()));
        this.parent = parent;
        this.snapshot = snapshot;
        this.player = player;
        this.column = column;
    }

    private void load() {
        loading = true;
        long token = ++generation;
        int requested = page, size = pageSize;
        CompletableFuture.supplyAsync(
                        () -> StatisticsQuery.evidence(snapshot, player, column, requested, size))
                .thenAccept(
                        data ->
                                Minecraft.getInstance()
                                        .execute(
                                                () -> {
                                                    if (token != generation) return;
                                                    result = data;
                                                    loading = false;
                                                    if (minecraft.gui.screen() == this)
                                                        rebuildWidgets();
                                                }));
    }

    @Override
    protected void init() {
        int nextSize = Math.max(1, Math.min(20, (height - 92) / 40));
        if (nextSize != pageSize) {
            pageSize = nextSize;
            page = 0;
            result = null;
        }
        addRenderableWidget(
                                Button.builder(
                                                Component.literal("↑ Previous"),
                                                b -> {
                                                    page--;
                                                    load();
                                                    rebuildWidgets();
                                                })
                                        .bounds(12, height - 28, 90, 20)
                                        .build())
                        .active =
                !loading && page > 0;
        addRenderableWidget(
                                Button.builder(
                                                Component.literal("↓ Next"),
                                                b -> {
                                                    page++;
                                                    load();
                                                    rebuildWidgets();
                                                })
                                        .bounds(width - 102, height - 28, 90, 20)
                                        .build())
                        .active =
                !loading && result != null && (page + 1) * pageSize < result.total();
        addRenderableWidget(
                Button.builder(Component.literal("Back"), b -> onClose())
                        .bounds(width / 2 - 42, height - 28, 84, 20)
                        .build());
        if (result == null && !loading) load();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, 0xF00B121D);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        g.centeredText(font, title, width / 2, 12, 0xFFF0F6FA);
        g.centeredText(
                font,
                loading
                        ? "Loading evidence…"
                        : result == null
                                ? ""
                                : "Events "
                                        + (result.total() == 0 ? 0 : page * pageSize + 1)
                                        + "–"
                                        + Math.min((page + 1) * pageSize, result.total())
                                        + " / "
                                        + result.total(),
                width / 2,
                28,
                0xFF9CB7C9);
        if (result == null || loading) return;
        int y = 46;
        for (var event : result.events()) {
            boolean removed =
                    event.type().name().contains("BREAK")
                            || event.type().name().contains("REMOVE")
                            || event.type() == EventType.SESSION_LOGOUT;
            int color = removed ? 0xFFFF8585 : 0xFF85E6AC;
            g.fill(12, y, width - 12, y + 38, removed ? 0xFF352630 : 0xFF213C36);
            long quantity =
                    event instanceof ItemEvent i
                            ? i.quantity()
                            : event instanceof ContainerEvent c ? c.quantity() : 1;
            String line =
                    event.type().name().replace('_', ' ')
                            + " | Quantity: "
                            + (quantity == 0 ? "Unknown" : quantity)
                            + " | "
                            + StatisticsQuery.time(event);
            g.text(font, font.plainSubstrByWidth(line, width - 40), 20, y + 4, color);
            var pos = event.context().position();
            String location =
                    event.context().world()
                            + " | "
                            + (pos == null
                                    ? "Coordinates unavailable"
                                    : "X: " + pos.x() + "  Y: " + pos.y() + "  Z: " + pos.z());
            g.text(font, font.plainSubstrByWidth(location, width - 40), 20, y + 15, 0xFFE0EAF2);
            String extra =
                    event instanceof KillEvent k
                            ? "Victim: "
                                    + Objects.toString(k.victim(), k.entity())
                                    + " | Cause: "
                                    + Objects.toString(k.cause(), "Unknown")
                            : "Source: " + event.context().source();
            g.text(font, font.plainSubstrByWidth(extra, width - 40), 20, y + 26, 0xFF9CB7C9);
            if (mx >= 12 && mx < width - 12 && my >= y && my < y + 38)
                g.setTooltipForNextFrame(
                        font, Component.literal(line + "\n" + location + "\n" + extra), mx, my);
            y += 40;
        }
    }

    @Override
    public void onClose() {
        generation++;
        minecraft.gui.setScreen(parent);
    }
}
