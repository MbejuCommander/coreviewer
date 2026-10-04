package dev.coreviewer;

import dev.coreviewer.ui.StaticViewScreen;
import dev.coreviewer.view.*;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public final class PhaseThreeGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("tp @p 0 -60 -8 0 12");
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
            var c = CoreTraceClient.service.config();
            c.enabled = true;
            c.playerHeads = true;
            CoreTraceClient.service.configure(c).join();
            context.runOnClient(
                    mc -> {
                        StaticView.preview();
                        mc.gui.setScreen(null);
                    });
            context.waitTicks(10);
            context.runOnClient(
                    mc -> {
                        if (StaticRenderer.visibleCount != 7)
                            throw new AssertionError(
                                    "Expected 7 visible previews, got "
                                            + StaticRenderer.visibleCount);
                        if (StaticRenderer.figureCount != 2 || StaticRenderer.itemCount != 2)
                            throw new AssertionError(
                                    "Missing death/item models: " + StaticRenderer.error);
                    });
            context.takeScreenshot("phase3-models-and-arrows");
            c.ghostBlocks = false;
            CoreTraceClient.service.configure(c).join();
            context.waitTicks(3);
            context.takeScreenshot("phase3-outlines");
            world.getServer().runCommand("fill -6 -60 -4 0 -57 -4 minecraft:stone");
            world.getConnection().waitForChunksRender();
            c.ghostBlocks = true;
            CoreTraceClient.service.configure(c).join();
            context.waitTicks(5);
            context.takeScreenshot("phase3-through-wall");
            c.throughWalls = false;
            CoreTraceClient.service.configure(c).join();
            context.waitTicks(3);
            context.takeScreenshot("phase3-depth-tested");
            context.setScreen(() -> new StaticViewScreen(null));
            context.waitTicks(2);
            context.takeScreenshot("phase3-world-selection");
            c.enabled = false;
            CoreTraceClient.service.configure(c).join();
            context.setScreen(() -> null);
            context.waitTicks(3);
            context.runOnClient(
                    mc -> {
                        if (StaticView.active() || StaticRenderer.visibleCount != 0)
                            throw new AssertionError("Master OFF did not hide overlays");
                    });
            c.enabled = true;
            CoreTraceClient.service.configure(c).join();
        }
        context.setScreen(net.minecraft.client.gui.screens.TitleScreen::new);
    }
}
