package dev.coreviewer.ui;

import dev.coreviewer.replay.ReplayController;
import dev.coreviewer.view.StaticView;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class ReplayScreen extends Screen {

    private final Screen parent;

    private List<String> worlds;

    private int selected;

    private String message = "Choose a server world, or try a temporary demo.";

    private TimelineSlider timeline;

    private int left, top;

    public ReplayScreen(Screen parent) {
        super(Component.literal("COREVIEWER — ACTION REPLAY"));
        this.parent = parent;
    }

    @Override
    protected void init() {

        ReplayController.engine.pause();

        worlds = StaticView.worlds();

        selected = Math.max(0, worlds.indexOf(StaticView.world()));

        left = width / 2 - 150;
        top = Math.max(8, (height - 228) / 2);

        addRenderableWidget(
                Button.builder(
                                Component.literal(worldLabel()),
                                b -> {
                                    if (!worlds.isEmpty())
                                        selected = (selected + 1) % worlds.size();

                                    b.setMessage(Component.literal(worldLabel()));
                                })
                        .bounds(left, top + 30, 300, 20)
                        .build());

        button(
                "Load world",
                0,
                56,
                146,
                () ->
                        message =
                                worlds.isEmpty()
                                        ? "Capture positioned records on this server first."
                                        : ReplayController.load(worlds.get(selected), false));

        button("Load demo", 154, 56, 146, () -> message = ReplayController.load("", true));

        timeline = addRenderableWidget(new TimelineSlider(left, top + 85));

        button("Previous", 0, 111, 94, () -> ReplayController.engine.previous());

        button(
                "Play in world",
                103,
                111,
                94,
                () -> {
                    if (ReplayController.active()) {
                        ReplayController.engine.play();
                        minecraft.gui.setScreen(null);
                    } else message = "Load a replay first.";
                });

        button("Next", 206, 111, 94, () -> ReplayController.engine.next());

        button("Restart", 0, 137, 94, () -> ReplayController.engine.seekFraction(0));

        button(
                "Stop",
                103,
                137,
                94,
                () -> {
                    StaticView.hide();
                    message = "Replay stopped.";
                });

        button(
                "Settings",
                206,
                137,
                94,
                () -> minecraft.gui.setScreen(CoreTraceConfigScreen.create(this)));

        button("View paused", 0, 202, 146, () -> minecraft.gui.setScreen(null));

        button("Done", 154, 202, 146, this::onClose);
    }

    private String worldLabel() {
        return worlds.isEmpty()
                ? "No captured worlds on this server"
                : "Server world: " + worlds.get(selected);
    }

    private void button(String text, int x, int y, int w, Runnable action) {

        addRenderableWidget(
                Button.builder(Component.literal(text), b -> action.run())
                        .bounds(left + x, top + y, w, 20)
                        .build());
    }

    @Override
    public void tick() {
        timeline.sync();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {

        super.extractRenderState(g, mx, my, dt);

        g.centeredText(font, title, width / 2, top, 0xFFFFFFFF);

        g.centeredText(
                font,
                "Bind server world to this dimension. Actions, not movement.",
                width / 2,
                top + 15,
                0xFFFFD166);

        String detail =
                ReplayController.active()
                        ? ReplayController.time()
                                + " | "
                                + ReplayController.engine.visible().size()
                                + "/"
                                + ReplayController.engine.events().size()
                                + " actions"
                        : "No replay loaded";

        g.centeredText(font, detail, width / 2, top + 164, 0xFFFFFFFF);

        var lines = font.split(Component.literal(message), 300);

        for (int i = 0; i < Math.min(2, lines.size()); i++)
            g.text(font, lines.get(i), left, top + 178 + i * 10, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private static final class TimelineSlider extends AbstractSliderButton {

        TimelineSlider(int x, int y) {
            super(x, y, 300, 20, Component.empty(), 0);
            sync();
        }

        void sync() {
            value = ReplayController.engine.fraction();
            active = ReplayController.active();
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(
                    Component.literal(
                            "Timeline: " + Math.round(value * 100) + "% (scrub to seek)"));
        }

        @Override
        protected void applyValue() {
            ReplayController.engine.seekFraction(value);
        }
    }
}
