package dev.coreviewer.ui;

import dev.coreviewer.config.CoreTraceConfig;

import me.shedaniel.clothconfig2.gui.entries.StringListEntry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.*;

public final class SmartTimelineEntry extends StringListEntry {
    private final Button toggle;
    private final CoreTraceConfig config;
    private final boolean initial;
    private boolean enabled;

    public SmartTimelineEntry(CoreTraceConfig c) {
        super(
                Component.literal("Smart Timeline (seconds)"),
                Double.toString(c.smartTimelineSeconds),
                Component.literal("Reset"),
                () -> "2",
                v -> c.smartTimelineSeconds = CoreTraceConfig.parseSmartTimeline(v),
                () ->
                        Optional.of(
                                new Component[] {
                                    Component.literal(
                                            "Compress idle gaps. Enter seconds (decimals allowed)."
                                                + " Blank restores 2 seconds. The adjacent switch"
                                                + " enables or disables Smart Timeline.")
                                }));
        config = c;
        initial = enabled = c.smartTimeline;
        toggle =
                Button.builder(
                                Component.literal(enabled ? "ON" : "OFF"),
                                b -> {
                                    enabled = !enabled;
                                    b.setMessage(Component.literal(enabled ? "ON" : "OFF"));
                                })
                        .bounds(0, 0, 50, 20)
                        .build();
        widgets = new ArrayList<>(widgets);
        widgets.add(toggle);
        setErrorSupplier(
                () -> {
                    try {
                        CoreTraceConfig.parseSmartTimeline(getValue());
                        return Optional.empty();
                    } catch (Exception ex) {
                        return Optional.of(
                                Component.literal("Enter seconds >= 0, or leave blank for 2."));
                    }
                });
    }

    @Override
    public boolean isEdited() {
        return super.isEdited() || enabled != initial;
    }

    @Override
    public void save() {
        super.save();
        config.smartTimeline = enabled;
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor g,
            int index,
            int y,
            int x,
            int w,
            int h,
            int mx,
            int my,
            boolean hovered,
            float dt) {
        super.extractRenderState(g, index, y, x, w - 56, h, mx, my, hovered, dt);
        toggle.setX(x + w - 50);
        toggle.setY(y);
        toggle.extractRenderState(g, mx, my, dt);
    }
}
