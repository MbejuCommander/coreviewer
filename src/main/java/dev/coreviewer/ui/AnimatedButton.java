package dev.coreviewer.ui;

import dev.coreviewer.CoreTraceClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class AnimatedButton extends Button {
    private double hover;
    private long frame = System.nanoTime();
    private final String subtitle;

    public AnimatedButton(
            String label, String subtitle, int x, int y, int w, int h, Runnable action) {
        super(x, y, w, h, Component.literal(label), b -> action.run(), DEFAULT_NARRATION);
        this.subtitle = subtitle;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float dt) {
        long now = System.nanoTime();
        double target = isHoveredOrFocused() && active ? 1 : 0;
        boolean motion =
                CoreTraceClient.service == null || CoreTraceClient.service.config().menuAnimations;
        hover = motion ? hover + (target - hover) * Math.min(1, (now - frame) / 1e9 * 14) : target;
        frame = now;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        var font = Minecraft.getInstance().font;
        g.fill(x, y, x + w, y + h, active ? 0xF0192635 : 0xF0141D29);
        g.fill(x, y, x + w, y + 1, 0xFF354B60);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF354B60);
        g.fill(x, y, x + 2, y + h, active ? 0xFF54CFBF : 0xFF45656C);
        if (hover > .01)
            g.fill(x + 2, y + 1, x + w, y + h - 1, ((int) (hover * 45) << 24) | 0x65D6C8);
        g.centeredText(
                font,
                getMessage(),
                x + w / 2,
                y + (subtitle.isEmpty() ? (h - 8) / 2 : 9),
                active ? 0xFFF0F6FA : 0xFF7C98A7);
        if (!subtitle.isEmpty())
            g.centeredText(
                    font, font.plainSubstrByWidth(subtitle, w - 12), x + w / 2, y + 25, 0xFFA4B7C5);
    }
}
