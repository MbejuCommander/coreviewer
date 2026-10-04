package dev.coreviewer.ui;

import dev.coreviewer.CoreTraceClient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class InvestigationScreen extends Screen {
    private final Screen parent;
    private int left, top;
    private Button demo, save, reload, clear;

    public InvestigationScreen(Screen parent) {
        super(Component.literal("Coreviewer investigator"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        left = width / 2 - 150;
        top = Math.max(8, (height - 224) / 2);
        button(
                "Settings",
                left,
                top + 42,
                146,
                () -> minecraft.gui.setScreen(CoreTraceConfigScreen.create(this)));
        demo =
                button(
                        "Generate 6 demo events",
                        left + 154,
                        top + 42,
                        146,
                        () -> action(() -> CoreTraceClient.service.simulate()));
        save =
                button(
                        "Save CSV + JSON",
                        left,
                        top + 68,
                        146,
                        () -> action(() -> CoreTraceClient.service.save()));
        reload =
                button(
                        "Reload JSON",
                        left + 154,
                        top + 68,
                        146,
                        () -> {
                            CoreTraceClient.stopCapture("Reloading.");
                            dev.coreviewer.view.StaticView.hide();
                            action(() -> CoreTraceClient.service.reload());
                        });
        clear =
                button(
                        "Clear local events",
                        left,
                        top + 94,
                        146,
                        () -> {
                            CoreTraceClient.stopCapture("History cleared.");
                            dev.coreviewer.view.StaticView.hide();
                            action(() -> CoreTraceClient.service.clear());
                        });
        button(
                "Open data folder",
                left + 154,
                top + 94,
                146,
                () ->
                        CompletableFuture.runAsync(
                                () -> {
                                    try {
                                        java.awt.Desktop.getDesktop()
                                                .open(CoreTraceClient.directory.toFile());
                                    } catch (Exception ex) {
                                        CoreTraceClient.tell(
                                                "Data folder: "
                                                        + CoreTraceClient.directory
                                                        + " (could not open automatically)");
                                    }
                                }));
        button(
                "Capture",
                left,
                top + 202,
                70,
                () -> minecraft.gui.setScreen(new CaptureScreen(this)));
        button(
                "Static",
                left + 77,
                top + 202,
                70,
                () -> minecraft.gui.setScreen(new StaticViewScreen(this)));
        button(
                "Replay",
                left + 154,
                top + 202,
                70,
                () -> minecraft.gui.setScreen(new ReplayScreen(this)));
        button("Done", left + 231, top + 202, 69, this::onClose);
    }

    private Button button(String label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(
                Button.builder(Component.literal(label), b -> action.run())
                        .bounds(x, y, w, 20)
                        .build());
    }

    private void action(Supplier<CompletableFuture<String>> work) {
        CoreTraceClient.notifyResult(work.get());
    }

    @Override
    public void tick() {
        boolean enabled = CoreTraceClient.service.enabled();
        demo.active = save.active = reload.active = clear.active = enabled;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        var s = CoreTraceClient.service;
        g.centeredText(font, title, width / 2, top, 0xFFFFFFFF);
        g.centeredText(font, "PHASE 5 — INDEXED INVESTIGATION", width / 2, top + 16, 0xFFFFD166);
        g.centeredText(
                font,
                s.enabled() ? "COREVIEWER ENABLED: ON" : "COREVIEWER ENABLED: OFF",
                width / 2,
                top + 29,
                s.enabled() ? 0xFF55FF99 : 0xFFFF5555);
        g.centeredText(
                font,
                "Events: "
                        + s.events().size()
                        + " | "
                        + (s.dirty() ? "Unsaved changes" : "Saved / unchanged"),
                width / 2,
                top + 125,
                0xFFFFFFFF);
        String status =
                CoreTraceClient.startupError.isEmpty() ? s.status() : CoreTraceClient.startupError;
        var lines = font.split(Component.literal(status), 300);
        for (int i = 0; i < Math.min(3, lines.size()); i++)
            g.text(font, lines.get(i), left, top + 143 + i * 10, 0xFFAAAAAA);
        g.centeredText(font, "Demo data is not server evidence.", width / 2, top + 183, 0xFFFFD166);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
