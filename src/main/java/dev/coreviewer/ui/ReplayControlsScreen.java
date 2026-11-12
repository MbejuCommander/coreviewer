package dev.coreviewer.ui;

import dev.coreviewer.CoreTraceClient;
import dev.coreviewer.replay.ReplayController;
import dev.coreviewer.view.StaticView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** Transparent, non-pausing controls. A screen owns the cursor and consumes world clicks. */
public final class ReplayControlsScreen extends Screen {
    private static final String[] LABELS = {"|<", "<", "<<", "||", ">", ">>", "[]", "X"};
    private static final String[] TIPS = {
        "Restart paused", "Previous event", "Play reverse", "Play / Pause",
        "Play forward", "Next event", "Stop replay", "Hide cursor (Esc)"
    };
    private final List<Button> buttons = new ArrayList<>();
    private int barX, barY;
    private boolean dragging;
    private double offsetX, offsetY;

    public ReplayControlsScreen() { super(Component.literal("Replay controls")); }

    private static float scale() { return CoreTraceClient.service.config().replayToolbarScale; }
    private static int buttonWidth(int width) {
        return Math.max(16, Math.round(Math.max(20, Math.min(34, (width - 16) / 8 - 2)) * scale()));
    }
    private static int buttonHeight() { return Math.round(20 * scale()); }
    private static int gripHeight() { return Math.round(16 * scale()); }
    private static int gap() { return Math.max(1, Math.round(2 * scale())); }
    private static int barWidth(int width) { return 8 * buttonWidth(width) + 7 * gap(); }
    private static int maxX(int width) { return Math.max(8, width - barWidth(width) - 8); }
    private static int maxY(int height) { return Math.max(8, height - gripHeight() - buttonHeight() - 8); }
    private static int savedX(int width) {
        double x = CoreTraceClient.service.config().replayToolbarX;
        return x < 0 ? 8 : 8 + (int) Math.round(x * (maxX(width) - 8));
    }
    private static int savedY(int height) {
        double y = CoreTraceClient.service.config().replayToolbarY;
        return y < 0 ? Math.min(84, maxY(height)) : 8 + (int) Math.round(y * (maxY(height) - 8));
    }

    @Override
    protected void init() {
        buttons.clear();
        dragging = false;
        barX = savedX(width); barY = savedY(height);
        int w = buttonWidth(width);
        for (int i = 0; i < LABELS.length; i++) {
            final int action = i;
            buttons.add(addRenderableWidget(Button.builder(Component.literal(LABELS[i]), b -> activate(action))
                    .bounds(barX + i * (w + gap()), barY + gripHeight(), w, buttonHeight())
                    .tooltip(Tooltip.create(Component.literal(TIPS[i]))).build()));
        }
    }

    private void activate(int action) {
        var e = ReplayController.engine;
        switch (action) {
            case 0 -> e.restart();
            case 1 -> e.previous();
            case 2 -> e.playReverse();
            case 3 -> e.toggle();
            case 4 -> e.play();
            case 5 -> e.next();
            case 6 -> { StaticView.hide(); onClose(); }
            case 7 -> onClose();
            default -> throw new IllegalArgumentException("Unknown control");
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && event.x() >= barX && event.x() < barX + barWidth(width)
                && event.y() >= barY && event.y() < barY + gripHeight()) {
            dragging = true; offsetX = event.x() - barX; offsetY = event.y() - barY;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging && event.button() == 0) {
            barX = (int) Math.clamp(event.x() - offsetX, 8, maxX(width));
            barY = (int) Math.clamp(event.y() - offsetY, 8, maxY(height));
            for (int i = 0; i < buttons.size(); i++) {
                buttons.get(i).setX(barX + i * (buttonWidth(width) + gap()));
                buttons.get(i).setY(barY + gripHeight());
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    private void savePosition() {
        if (!dragging) return;
        dragging = false;
        CoreTraceClient.service.positionReplayToolbar(
                (barX - 8d) / Math.max(1, maxX(width) - 8),
                (barY - 8d) / Math.max(1, maxY(height) - 8));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging && event.button() == 0) { savePosition(); return true; }
        return super.mouseReleased(event);
    }

    @Override
    public void removed() { savePosition(); }

    private static void drawGrip(GuiGraphicsExtractor g, int x, int y, int width) {
        g.fill(x, y, x + barWidth(width), y + gripHeight() - 1, 0xD020303E);
        g.text(Minecraft.getInstance().font, ":: Drag replay controls", x + 5, y + 3, 0xFF8FE0D5);
    }

    public static void drawPreview(GuiGraphicsExtractor g) {
        int w = buttonWidth(g.guiWidth()), x = savedX(g.guiWidth()), y = savedY(g.guiHeight());
        for (int i = 0; i < LABELS.length; i++) {
            int left = x + i * (w + gap());
            g.fill(left, y + gripHeight(), left + w, y + gripHeight() + buttonHeight(), 0xB020303E);
            g.centeredText(Minecraft.getInstance().font, LABELS[i], left + w / 2, y + gripHeight() + Math.max(1, (buttonHeight() - 9) / 2), 0xFFE0E8EF);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        drawGrip(g, barX, barY, width);
        super.extractRenderState(g, mx, my, dt);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float dt) {}

    @Override
    public void tick() { if (!ReplayController.active()) onClose(); }

    @Override
    public void onClose() { minecraft.gui.setScreen(null); }
}
