package dev.coreviewer.ui;

import dev.coreviewer.CoreTraceClient;
import dev.coreviewer.view.StaticView;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

public final class CsvLibraryScreen extends Screen {
    private final Screen parent;
    private int page;
    private boolean busy;

    public CsvLibraryScreen(Screen parent) {
        super(Component.literal("CSV Library"));
        this.parent = parent;
    }

    public static void openFolder() {
        FolderOpener.open(CoreTraceClient.service.csvFolder());
    }

    private Button button(String text, int x, int y, int w, Runnable action) {
        return addRenderableWidget(
                Button.builder(Component.literal(text), b -> action.run())
                        .bounds(x, y, w, 20)
                        .build());
    }

    private void action(CompletableFuture<String> work) {
        busy = true;
        children()
                .forEach(
                        child -> {
                            if (child instanceof AbstractWidget widget) widget.active = false;
                        });
        CoreTraceClient.stopCapture("CSV library changed.");
        StaticView.hide();
        work.thenAccept(
                message ->
                        Minecraft.getInstance()
                                .execute(
                                        () -> {
                                            busy = false;
                                            CoreTraceClient.tell(message);
                                            if (minecraft.gui.screen() == this) rebuildWidgets();
                                        }));
    }

    private void confirm(String description, Runnable action) {
        minecraft.gui.setScreen(
                new ConfirmScreen(
                        yes -> {
                            minecraft.gui.setScreen(this);
                            if (yes) action.run();
                        },
                        Component.literal("Delete CSV files?"),
                        Component.literal(description)));
    }

    @Override
    protected void init() {
        var service = CoreTraceClient.service;
        int w = Math.min(540, width - 20),
                x = (width - w) / 2,
                count = Math.max(1, (height - 135) / 28);
        var files = service.csvFiles();
        page = Math.min(page, Math.max(0, (files.size() - 1) / count));
        button("Open CSV folder", x, 30, w / 3 - 4, CsvLibraryScreen::openFolder);
        button(
                "Refresh",
                x + w / 3,
                30,
                w / 3 - 4,
                () -> action(service.refreshCsv(CoreTraceClient.serverIdentity())));
        button(
                "Delete all CSVs",
                x + w * 2 / 3,
                30,
                w / 3,
                () ->
                        confirm(
                                "Permanently delete every CSV in the shared folder and its"
                                    + " subfolders?",
                                () -> action(service.clear())));
        button(
                                "Select all",
                                x,
                                54,
                                w / 2 - 3,
                                () ->
                                        action(
                                                service.selectAllCsv(
                                                        true, CoreTraceClient.serverIdentity())))
                        .active =
                !service.config().simpleMode;
        button(
                                "Deselect all",
                                x + w / 2,
                                54,
                                w / 2,
                                () ->
                                        action(
                                                service.selectAllCsv(
                                                        false, CoreTraceClient.serverIdentity())))
                        .active =
                !service.config().simpleMode;
        for (int row = 0; row < count; row++) {
            int at = page * count + row;
            if (at >= files.size()) break;
            var file = files.get(at);
            int y = 82 + row * 28;
            var select =
                    button(
                            "",
                            x,
                            y,
                            26,
                            () ->
                                    action(
                                            service.selectCsv(
                                                    file.name(),
                                                    !file.selected(),
                                                    CoreTraceClient.serverIdentity())));
            select.setMessage(
                    Component.literal(file.selected() ? "✓" : "□")
                            .withStyle(
                                    file.selected() ? ChatFormatting.GREEN : ChatFormatting.WHITE));
            select.active = !service.config().simpleMode;
            var name =
                    button(
                            font.plainSubstrByWidth(file.name(), w - 143),
                            x + 30,
                            y,
                            w - 134,
                            () -> {});
            name.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    file.name()
                                            + "\n"
                                            + file.count()
                                            + " events; "
                                            + file.warning())));
            button("↑", x + w - 100, y, 28, () -> action(service.moveCsv(file.name(), -1)));
            button("↓", x + w - 70, y, 28, () -> action(service.moveCsv(file.name(), 1)));
            button(
                    "×",
                    x + w - 40,
                    y,
                    40,
                    () ->
                            confirm(
                                    "Permanently delete " + file.name() + "?",
                                    () -> action(service.deleteCsv(file.name()))));
        }
        button(
                "Previous",
                x,
                height - 28,
                90,
                () -> {
                    page = Math.max(0, page - 1);
                    rebuildWidgets();
                });
        button("Done", width / 2 - 45, height - 28, 90, this::onClose);
        button(
                "Next",
                x + w - 90,
                height - 28,
                90,
                () -> {
                    if ((page + 1) * count < files.size()) page++;
                    rebuildWidgets();
                });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        g.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        if (busy) g.centeredText(font, "Loading CSVs…", width / 2, height - 43, 0xFFFFFF55);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
