package dev.coreviewer.ui;

import me.shedaniel.clothconfig2.gui.entries.StringListEntry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class SoundPickerEntry extends StringListEntry {
    private final Button browse;

    public SoundPickerEntry(String label, String value, String fallback, Consumer<String> save) {
        super(Component.literal(label), value, Component.literal("Reset"), () -> fallback, save);
        browse =
                Button.builder(
                                Component.literal("Search sounds / Preview"),
                                b -> {
                                    var mc = Minecraft.getInstance();
                                    mc.gui.setScreen(
                                            new SoundPickerScreen(mc.gui.screen(), this::setValue));
                                })
                        .bounds(0, 0, 180, 20)
                        .build();
        widgets = new java.util.ArrayList<>(widgets);
        widgets.add(browse);
        setErrorSupplier(
                () -> {
                    try {
                        var id = net.minecraft.resources.Identifier.parse(getValue());
                        if (net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT
                                .getOptional(id)
                                .isPresent()) return java.util.Optional.empty();
                    } catch (RuntimeException ignored) {
                    }
                    return java.util.Optional.of(
                            Component.literal("Choose an existing sound using Search sounds."));
                });
    }

    @Override
    public int getItemHeight() {
        return super.getItemHeight() + 24;
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
        super.extractRenderState(g, index, y, x, w, h, mx, my, hovered, dt);
        browse.setX(x + w - 180);
        browse.setY(y + 24);
        browse.extractRenderState(g, mx, my, dt);
    }
}
