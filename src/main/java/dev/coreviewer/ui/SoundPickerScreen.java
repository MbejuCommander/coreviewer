package dev.coreviewer.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.function.Consumer;

public final class SoundPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> select;
    private final List<String> sounds =
            BuiltInRegistries.SOUND_EVENT.keySet().stream()
                    .filter(id -> id.getNamespace().equals("minecraft"))
                    .map(Object::toString)
                    .sorted()
                    .toList();
    private List<String> filtered;
    private int page;
    private final List<Button> rows = new ArrayList<>();
    private String query = "";
    private static SoundInstance preview;

    public SoundPickerScreen(Screen parent, Consumer<String> select) {
        super(Component.literal("Choose a Vanilla Sound"));
        this.parent = parent;
        this.select = select;
        filtered = sounds;
    }

    public static void play(String value) {
        try {
            var sound = BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.parse(value));
            if (sound.isPresent()) {
                stopPreview();
                preview = SimpleSoundInstance.forUI(sound.get(), 1, 0.6f);
                Minecraft.getInstance().getSoundManager().play(preview);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static void stopPreview() {
        if (preview != null) {
            Minecraft.getInstance().getSoundManager().stop(preview);
            preview = null;
        }
    }

    @Override
    protected void init() {
        rows.clear();
        int x = width / 2 - 180;
        var search =
                addRenderableWidget(
                        new EditBox(font, x, 32, 360, 20, Component.literal("Search sounds")));
        search.setMaxLength(150);
        search.setValue(query);
        search.setResponder(
                v -> {
                    query = v;
                    filtered =
                            sounds.stream()
                                    .filter(s -> s.contains(v.toLowerCase(Locale.ROOT)))
                                    .toList();
                    page = 0;
                    refresh();
                });
        int count = Math.max(1, (height - 118) / 24);
        for (int i = 0; i < count; i++) {
            final int row = i;
            rows.add(
                    addRenderableWidget(
                            Button.builder(
                                            Component.empty(),
                                            b -> {
                                                int at = page * count + row;
                                                if (at < filtered.size()) {
                                                    select.accept(filtered.get(at));
                                                    onClose();
                                                }
                                            })
                                    .bounds(x, 60 + i * 24, 280, 20)
                                    .build()));
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Preview"),
                                    b -> {
                                        int at = page * count + row;
                                        if (at < filtered.size()) play(filtered.get(at));
                                    })
                            .bounds(x + 284, 60 + i * 24, 76, 20)
                            .build());
        }
        addRenderableWidget(
                Button.builder(
                                Component.literal("Previous"),
                                b -> {
                                    page = Math.max(0, page - 1);
                                    refresh();
                                })
                        .bounds(x, height - 50, 110, 20)
                        .build());
        addRenderableWidget(
                Button.builder(
                                Component.literal("Next"),
                                b -> {
                                    if ((page + 1) * rows.size() < filtered.size()) page++;
                                    refresh();
                                })
                        .bounds(x + 250, height - 50, 110, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.literal("Stop preview"), b -> stopPreview())
                        .bounds(x + 120, height - 50, 120, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.literal("Cancel"), b -> onClose())
                        .bounds(width / 2 - 60, height - 25, 120, 20)
                        .build());
        refresh();
        setInitialFocus(search);
    }

    private void refresh() {
        for (int i = 0; i < rows.size(); i++) {
            int at = page * rows.size() + i;
            rows.get(i).active = at < filtered.size();
            rows.get(i)
                    .setMessage(
                            Component.literal(
                                    at < filtered.size()
                                            ? filtered.get(at).replace("minecraft:", "")
                                            : ""));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        g.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        stopPreview();
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        stopPreview();
    }
}
