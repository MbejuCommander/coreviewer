package dev.coreviewer.ui;

import dev.coreviewer.CoreTraceClient;
import dev.coreviewer.view.*;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class StaticViewScreen extends Screen {
    private final Screen parent;
    private List<String> worlds;
    private int selected;

    public StaticViewScreen(Screen parent) {
        super(Component.literal("Static Investigation"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        worlds = StaticView.worlds();
        selected = Math.max(0, worlds.indexOf(StaticView.world()));
        int x = width / 2 - 150, y = height / 2 - 65;
        var worldButton =
                Button.builder(
                                Component.literal(worldLabel()),
                                b -> {
                                    if (!worlds.isEmpty())
                                        selected = (selected + 1) % worlds.size();
                                    b.setMessage(Component.literal(worldLabel()));
                                })
                        .bounds(x, y, 300, 20)
                        .build();
        worldButton.active = !worlds.isEmpty();
        addRenderableWidget(worldButton);
        addRenderableWidget(
                Button.builder(
                                Component.literal("Show selected world"),
                                b -> {
                                    if (worlds.isEmpty()) {
                                        CoreTraceClient.tell(
                                                "Capture positioned CoreProtect records on this"
                                                        + " server first.");
                                        return;
                                    }
                                    String result = StaticView.show(worlds.get(selected));
                                    CoreTraceClient.tell(result);
                                    if (StaticView.active()) minecraft.gui.setScreen(null);
                                })
                        .bounds(x, y + 26, 146, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.literal("Hide view"), b -> StaticView.hide())
                        .bounds(x + 154, y + 26, 146, 20)
                        .build());
        addRenderableWidget(
                Button.builder(
                                Component.literal("Preview demo"),
                                b -> {
                                    CoreTraceClient.tell(StaticView.preview());
                                    if (StaticView.active()) minecraft.gui.setScreen(null);
                                })
                        .bounds(x, y + 52, 146, 20)
                        .build());
        addRenderableWidget(
                Button.builder(
                                Component.literal("Settings"),
                                b -> minecraft.gui.setScreen(CoreTraceConfigScreen.create(this)))
                        .bounds(x + 154, y + 52, 146, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.literal("Previous page"), b -> StaticView.changePage(-1))
                        .bounds(x, y + 78, 146, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.literal("Next page"), b -> StaticView.changePage(1))
                        .bounds(x + 154, y + 78, 146, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.literal("Done"), b -> onClose())
                        .bounds(x + 100, y + 140, 100, 20)
                        .build());
    }

    private String worldLabel() {
        return worlds.isEmpty()
                ? "No positioned records on this server"
                : "Server world: " + worlds.get(selected);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        int y = height / 2 - 65;
        g.centeredText(font, title, width / 2, y - 43, 0xFFFFFFFF);
        g.centeredText(
                font,
                "Bind the selected server world to this dimension.",
                width / 2,
                y - 27,
                0xFFFFD166);
        g.centeredText(
                font,
                "Binding ends when you leave or change dimensions.",
                width / 2,
                y - 15,
                0xFFAAAAAA);
        var text =
                StaticView.active()
                        ? "Visible: " + StaticRenderer.visibleCount + " | " + StaticView.status
                        : StaticView.status;
        var lines = font.split(Component.literal(text), 300);
        for (int i = 0; i < Math.min(1, lines.size()); i++)
            g.text(font, lines.get(i), width / 2 - 150, y + 102 + i * 10, 0xFFAAAAAA);
        g.centeredText(
                font,
                "Demo previews are temporary and never saved.",
                width / 2,
                y + 119,
                0xFFFFD166);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
