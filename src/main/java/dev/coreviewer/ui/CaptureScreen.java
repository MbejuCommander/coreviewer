package dev.coreviewer.ui;

import dev.coreviewer.CoreTraceClient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CaptureScreen extends Screen {
    private final Screen parent;
    private EditBox query;

    public CaptureScreen(Screen parent) {
        super(Component.literal("CoreProtect Capture"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 150, y = height / 2 - 65;
        query = new EditBox(font, x, y, 300, 20, Component.literal("Lookup parameters"));
        query.setMaxLength(220);
        query.setValue("r:" + CoreTraceClient.service.config().eventRadius + " t:1h");
        addRenderableWidget(query);
        addRenderableWidget(
                Button.builder(
                                Component.literal("Start lookup"),
                                b -> CoreTraceClient.startLookup(query.getValue()))
                        .bounds(x, y + 30, 146, 20)
                        .build());
        addRenderableWidget(
                Button.builder(
                                Component.literal("Stop capture"),
                                b -> CoreTraceClient.stopCapture("Stopped by moderator."))
                        .bounds(x + 154, y + 30, 146, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.literal("Done"), b -> onClose())
                        .bounds(x + 100, y + 135, 100, 20)
                        .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        int y = height / 2 - 65;
        g.centeredText(font, title, width / 2, y - 35, 0xFFFFFFFF);
        g.centeredText(font, "/co lookup parameters", width / 2, y - 15, 0xFFAAAAAA);
        var lines = font.split(Component.literal(CoreTraceClient.capture.status()), 300);
        for (int i = 0; i < Math.min(3, lines.size()); i++)
            g.text(font, lines.get(i), width / 2 - 150, y + 58 + i * 10, 0xFFFFD166);
        g.centeredText(
                font, "Auto Page OFF: run /co manually in chat.", width / 2, y + 98, 0xFFAAAAAA);
        g.centeredText(
                font, "English CoreProtect responses supported.", width / 2, y + 112, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
