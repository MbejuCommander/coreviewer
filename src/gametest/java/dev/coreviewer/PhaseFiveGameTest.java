package dev.coreviewer;

import dev.coreviewer.ui.InvestigationScreen;
import dev.coreviewer.view.*;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public final class PhaseFiveGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("tp @p 0 -60 -8 0 12");
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
            var c = CoreTraceClient.service.config();
            c.enabled = true;
            c.ghostBlocks = true;
            c.playerHeads = true;
            CoreTraceClient.service.configure(c).join();
            context.runOnClient(
                    mc -> {
                        var commands = ClientCommands.getActiveDispatcher().getRoot();
                        var root = commands.getChild("coreviewer");
                        if (root == null) throw new AssertionError("Missing /coreviewer command");
                        for (String sub :
                                new String[] {
                                    "static",
                                    "replay",
                                    "config",
                                    "lookup",
                                    "clear",
                                    "reload",
                                    "stop",
                                    "save",
                                    "simulate",
                                    "status"
                                })
                            if (root.getChild(sub) == null)
                                throw new AssertionError("Missing /coreviewer " + sub);
                        mc.getConnection().sendCommand("coreviewer");
                    });
            context.waitTicks(3);
            context.runOnClient(
                    mc -> {
                        if (!(mc.gui.screen() instanceof InvestigationScreen))
                            throw new AssertionError("/coreviewer did not open dashboard");
                        if (!mc.gui
                                .screen()
                                .getTitle()
                                .getString()
                                .equals("Coreviewer investigator"))
                            throw new AssertionError("Dashboard rename incomplete");
                    });
            context.takeScreenshot("phase5-coreviewer-dashboard");
            context.runOnClient(
                    mc -> {
                        StaticView.preview();
                        mc.gui.setScreen(null);
                    });
            context.waitTicks(10);
            context.runOnClient(
                    mc -> {
                        if (StaticRenderer.visibleCount != 7
                                || StaticRenderer.cachedModels() < 4
                                || StaticRenderer.selectionCacheHits() == 0)
                            throw new AssertionError("Indexed/cached preview incomplete");
                        if (StaticRenderer.examinedEvents != 7)
                            throw new AssertionError("Unexpected index scope");
                    });
            int generation = context.computeOnClient(mc -> StaticRenderer.resourceInvalidations);
            var reload = context.computeOnClient(mc -> mc.reloadResourcePacks());
            context.waitFor(mc -> reload.isDone());
            reload.join();
            context.waitFor(mc -> mc.gui.overlay() == null);
            context.waitTicks(10);
            context.runOnClient(
                    mc -> {
                        if (StaticRenderer.resourceInvalidations <= generation
                                || StaticRenderer.visibleCount != 7
                                || StaticRenderer.itemCount != 2)
                            throw new AssertionError(
                                    "Resource reload did not rebuild cached models");
                    });
            context.takeScreenshot("phase5-cached-scene-after-reload");
            context.runOnClient(mc -> StaticView.hide());
            context.waitTicks(2);
            context.runOnClient(
                    mc -> {
                        if (StaticRenderer.cachedModels() != 0)
                            throw new AssertionError("Hidden scene retained model cache");
                    });
        }
        context.setScreen(net.minecraft.client.gui.screens.TitleScreen::new);
    }
}
