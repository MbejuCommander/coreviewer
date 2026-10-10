package dev.coreviewer.ui;

import me.shedaniel.clothconfig2.gui.entries.StringListEntry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

public final class ActionEntry extends StringListEntry {
    private final Button button;
    private final BooleanSupplier enabled;

    public ActionEntry(String label, Runnable action, BooleanSupplier enabled) {
        super(Component.literal(label), "", Component.literal("Reset"), () -> "", v -> {});
        this.enabled = enabled;
        button =
                Button.builder(Component.literal(label), b -> action.run())
                        .bounds(0, 0, 240, 20)
                        .build();
        widgets = new java.util.ArrayList<>();
        widgets.add(button);
    }

    @Override
    public int getItemHeight() {
        return 24;
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
        button.setX(x);
        button.setY(y);
        button.setWidth(w);
        button.active = enabled.getAsBoolean();
        button.extractRenderState(g, mx, my, dt);
    }
}
