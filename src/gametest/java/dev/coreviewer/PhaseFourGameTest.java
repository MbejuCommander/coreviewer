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
                        if (StaticRenderer.visibleCount != 7
                                || StaticRenderer.figureCount != 2
                                || StaticRenderer.itemCount != 2)
                            throw new AssertionError(
                                    "Final replay scene incomplete: " + StaticRenderer.error);
                        if (CoreTraceClient.service.events().size() != stored)
                            throw new AssertionError("Demo changed stored evidence");
                    });
            context.takeScreenshot("phase4-completed-actions");
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
