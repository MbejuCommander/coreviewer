package dev.coreviewer;

import com.mojang.blaze3d.platform.InputConstants;

import dev.coreviewer.replay.ReplayController;
import dev.coreviewer.ui.ReplayScreen;
import dev.coreviewer.view.*;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public final class PhaseFourGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("tp @p 0 -60 -8 0 12");
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
            var c = CoreTraceClient.service.config();
            c.enabled = true;
            c.throughWalls = true;
            c.playPauseKey = "key.keyboard.f6";
            c.timelineSpeed = 1;
            c.replayToolbarScale = .75f;
            c.blockBreakSpeed = .5;
            c.blockPlaceSpeed = 2;
            CoreTraceClient.service.configure(c).join();
            int stored = CoreTraceClient.service.events().size();
            context.setScreen(() -> new ReplayScreen(null));
            context.clickScreenButton("Load demo");
            context.clickScreenButton("Next");
            context.clickScreenButton("View paused");
            context.waitTicks(5);
            context.runOnClient(
                    mc -> {
                        if (!ReplayController.active() || StaticRenderer.visibleCount != 1)
                            throw new AssertionError("First action missing");
                    });
            context.takeScreenshot("phase4-first-action");
            context.getInput().pressKey(InputConstants.KEY_COMMA);
            context.waitTicks(3);
            context.runOnClient(
                    mc -> {
                        if (ReplayController.engine.visible().size() != 2)
                            throw new AssertionError("Forward key failed");
                    });
            context.getInput().pressKey(InputConstants.KEY_PERIOD);
            context.waitTicks(3);
            context.runOnClient(
                    mc -> {
                        if (ReplayController.engine.visible().size() != 1)
                            throw new AssertionError("Backward key failed");
                    });
            double before = context.computeOnClient(mc -> ReplayController.engine.cursor());
            context.getInput().pressKey(InputConstants.KEY_F6);
            context.waitTicks(8);
            context.runOnClient(
                    mc -> {
                        if (!ReplayController.engine.playing()
                                || ReplayController.engine.cursor() <= before)
                            throw new AssertionError("Play key failed");
                    });
            context.getInput().pressKey(InputConstants.KEY_F6);
            context.waitTicks(2);
            double paused = context.computeOnClient(mc -> ReplayController.engine.cursor());
            context.waitTicks(5);
            context.runOnClient(
                    mc -> {
                        if (ReplayController.engine.playing()
                                || ReplayController.engine.cursor() != paused)
                            throw new AssertionError("Pause drift");
                    });
            context.setScreen(() -> new ReplayScreen(null));
            context.waitTicks(2);
            context.takeScreenshot("phase4-replay-controls");
            context.runOnClient(
                    mc -> {
                        ReplayController.engine.seekFraction(1);
                        mc.gui.setScreen(null);
                    });
            context.waitTicks(5);
            context.runOnClient(
                    mc -> {
                        if (StaticRenderer.visibleCount != 8
                                || StaticRenderer.figureCount != 2
                                || StaticRenderer.itemCount != 2)
                            throw new AssertionError(
                                    "Final replay scene incomplete: " + StaticRenderer.error);
                        if (CoreTraceClient.service.events().size() != stored)
                            throw new AssertionError("Demo changed stored evidence");
                    });
            context.takeScreenshot("phase4-completed-actions");
            context.getInput().pressKey(InputConstants.KEY_R);
            context.waitTicks(3);
            context.getInput().pressKey(InputConstants.KEY_U);
            context.waitTicks(3);
            double reverseBefore = context.computeOnClient(mc -> ReplayController.engine.cursor());
            context.waitTicks(4);
            context.runOnClient(mc -> {
                if (!(mc.gui.screen() instanceof dev.coreviewer.ui.ReplayControlsScreen)
                        || !ReplayController.engine.playing() || !ReplayController.engine.reversed()
                        || ReplayController.engine.cursor() >= reverseBefore)
                    throw new AssertionError("Reverse playback or non-pausing cursor failed");
            });
            context.clickScreenButton("||");
            context.waitTicks(2);
            context.runOnClient(mc -> {
                if (ReplayController.engine.playing()) throw new AssertionError("Pause button failed");
            });
            context.runOnClient(mc -> {
                var screen = (dev.coreviewer.ui.ReplayControlsScreen) mc.gui.screen();
                var left = new net.minecraft.client.input.MouseButtonInfo(0, 0);
                if (!screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(12, 88, left), false))
                    throw new AssertionError("Drag grip did not accept click");
                screen.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(10000, 10000, left), 100, 100);
                screen.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(10000, 10000, left));
                var saved = CoreTraceClient.service.config();
                if (saved.replayToolbarX != 1 || saved.replayToolbarY != 1)
                    throw new AssertionError("Dragged position was not clamped/saved");
                mc.gui.setScreen(new dev.coreviewer.ui.ReplayControlsScreen());
                var restored = mc.gui.screen().children().stream()
                        .filter(child -> child instanceof net.minecraft.client.gui.components.Button)
                        .map(child -> (net.minecraft.client.gui.components.Button) child).findFirst().orElseThrow();
                if (restored.getX() <= 8 || restored.getY() <= 100 || restored.getWidth() >= 34 || restored.getHeight() >= 20)
                    throw new AssertionError("Toolbar did not restore its position");
            });
            context.takeScreenshot("replay-toolbar");
            context.clickScreenButton("|<");
            context.clickScreenButton(">");
            context.waitTicks(4);
            context.runOnClient(mc -> {
                if (!ReplayController.engine.playing() || ReplayController.engine.reversed())
                    throw new AssertionError("Forward button failed");
            });
            context.getInput().pressKey(InputConstants.KEY_U);
            context.waitTicks(2);
            context.runOnClient(mc -> {
                if (mc.gui.screen() != null) throw new AssertionError("Cursor shortcut failed to close");
            });

            c.enabled = false;
            CoreTraceClient.service.configure(c).join();
            context.waitTicks(3);
            context.runOnClient(
                    mc -> {
                        if (ReplayController.active()
                                || ReplayController.engine.loaded()
                                || StaticRenderer.visibleCount != 0)
                            throw new AssertionError("OFF retained replay");
                    });
            c.enabled = true;
            CoreTraceClient.service.configure(c).join();
            context.runOnClient(
                    mc -> {
                        ReplayController.load("", true);
                        StaticView.preview();
                        if (ReplayController.engine.loaded())
                            throw new AssertionError("Static view did not replace replay");
                    });
        }
        context.setScreen(net.minecraft.client.gui.screens.TitleScreen::new);
        context.runOnClient(
                mc -> {
                    if (ReplayController.active())
                        throw new AssertionError("Disconnect retained replay");
                });
    }
}
