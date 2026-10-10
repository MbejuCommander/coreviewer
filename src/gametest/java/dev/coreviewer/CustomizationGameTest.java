package dev.coreviewer;

import com.mojang.blaze3d.platform.InputConstants;

import dev.coreviewer.capture.ChatLine;
import dev.coreviewer.config.*;
import dev.coreviewer.replay.ReplayController;
import dev.coreviewer.ui.*;
import dev.coreviewer.view.*;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.EditBox;

import java.util.*;

public final class CustomizationGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("tp @p 0 -60 -8 0 12");
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
            var c = new CoreTraceConfig();
            c.maxVisibleEvents = 3;
            c.replayVisibleEvents = 2;
            CoreTraceClient.service.configure(c).join();
            context.getInput().pressKey(InputConstants.KEY_G);
            context.waitTicks(2);
            context.runOnClient(
                    mc -> {
                        if (!(mc.gui.screen() instanceof StaticViewScreen))
                            throw new AssertionError("G menu shortcut failed");
                    });
            context.clickScreenButton("Preview demo");
            context.waitTicks(5);
            context.runOnClient(
                    mc -> {
                        if (StaticRenderer.visibleCount != 3)
                            throw new AssertionError("First static page wrong");
                    });
            context.getInput().pressKey(InputConstants.KEY_B);
            context.waitTicks(5);
            context.runOnClient(
                    mc -> {
                        if (StaticView.page() != 1 || StaticRenderer.visibleCount != 3)
                            throw new AssertionError("B next page failed");
                    });
            context.takeScreenshot("custom-static-page");
            context.getInput().pressKey(InputConstants.KEY_B);
            context.waitTicks(4);
            context.runOnClient(
                    mc -> {
                        if (StaticRenderer.visibleCount != 1)
                            throw new AssertionError("Last page should have 1 action");
                    });
            context.getInput().pressKey(InputConstants.KEY_V);
            context.waitTicks(3);
            context.runOnClient(
                    mc -> {
                        if (StaticView.page() != 1)
                            throw new AssertionError("V previous page failed");
                    });
            context.getInput().pressKey(InputConstants.KEY_H);
            context.waitTicks(2);
            context.runOnClient(
                    mc -> {
                        if (!(mc.gui.screen() instanceof ReplayScreen))
                            throw new AssertionError("H menu shortcut failed");
                    });
            context.clickScreenButton("Load demo");
            context.clickScreenButton("View paused");
            context.waitTicks(2);
            context.getInput().pressKey(InputConstants.KEY_J);
            context.waitTicks(2);
            context.runOnClient(
                    mc -> {
                        if (!ReplayController.active()
                                || ReplayController.engine.visibleCount() != 0)
                            throw new AssertionError("J load failed");
                    });
            context.getInput().pressKey(InputConstants.KEY_RSHIFT);
            context.waitTicks(3);
            context.runOnClient(
                    mc -> {
                        if (!ReplayController.engine.playing())
                            throw new AssertionError("Right shift play failed");
                    });
            context.getInput().pressKey(InputConstants.KEY_RSHIFT);
            context.waitTicks(2);
            context.runOnClient(
                    mc -> {
                        if (ReplayController.engine.playing())
                            throw new AssertionError("Right shift pause failed");
                        ReplayController.engine.seekFraction(0);
                    });
            for (int i = 0; i < 3; i++) context.getInput().pressKey(InputConstants.KEY_COMMA);
            context.waitTicks(5);
            context.runOnClient(
                    mc -> {
                        if (ReplayController.engine.visibleCount() != 3
                                || StaticRenderer.visibleCount != 2)
                            throw new AssertionError("Rolling replay window failed");
                    });
            context.takeScreenshot("custom-replay-window");
            context.getInput().pressKey(InputConstants.KEY_PERIOD);
            context.waitTicks(2);
            context.runOnClient(
                    mc -> {
                        if (ReplayController.engine.visibleCount() != 2)
                            throw new AssertionError("Period backward failed");
                    });
            context.getInput().pressKey(InputConstants.KEY_K);
            context.waitTicks(2);
            context.runOnClient(
                    mc -> {
                        if (ReplayController.engine.visibleCount() != 0)
                            throw new AssertionError("K restart failed");
                    });
            context.getInput().pressKey(InputConstants.KEY_L);
            context.waitTicks(2);
            context.runOnClient(
                    mc -> {
                        if (ReplayController.active() || mc.gui.screen() != null)
                            throw new AssertionError(
                                    "L must stop replay without opening advancements");
                    });
            // Validate empty numeric entries and picker integration against real Cloth entries.
            context.setScreen(() -> CoreTraceConfigScreen.createEditor(null, null));
            context.runOnClient(
                    mc -> {
                        var screen =
                                (me.shedaniel.clothconfig2.gui.AbstractConfigScreen)
                                        mc.gui.screen();
                        var entries =
                                screen.getCategorizedEntries().values().stream()
                                        .flatMap(List::stream)
                                        .toList();
                        for (var entry : entries)
                            if (entry.getFieldName().getString().equals("Event Radius")
                                    || entry.getFieldName()
                                            .getString()
                                            .equals("Command Delay (ms)"))
                                ((me.shedaniel.clothconfig2.gui.entries.StringListEntry) entry)
                                        .setValue("");
                        screen.saveAll(false);
                    });
            context.waitTicks(3);
            context.runOnClient(
                    mc -> {
                        if (CoreTraceClient.service.config().eventRadius != 100
                                || CoreTraceClient.service.config().commandDelayMs != 1500)
                            throw new AssertionError("Blank number defaults failed");
                    });
            context.getInput().setCursorPos(200, 106);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(3);
            context.takeScreenshot("custom-capture-settings");
            context.getInput().setCursorPos(300, 175);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.getInput().typeChars("Capture Start Sound");
            context.waitTicks(3);
            double[] browse =
                    context.computeOnClient(
                            mc -> {
                                var screen =
                                        (me.shedaniel.clothconfig2.gui.AbstractConfigScreen)
                                                mc.gui.screen();
                                var entry =
                                        screen.getCategorizedEntries().values().stream()
                                                .flatMap(List::stream)
                                                .filter(
                                                        e ->
                                                                e.getFieldName()
                                                                        .getString()
                                                                        .equals(
                                                                                "Capture Start"
                                                                                        + " Sound"))
                                                .findFirst()
                                                .orElseThrow();
                                var button =
                                        entry.children().stream()
                                                .filter(
                                                        child ->
                                                                child
                                                                                instanceof
                                                                                net.minecraft.client
                                                                                                .gui
                                                                                                .components
                                                                                                .Button
                                                                                        b
                                                                        && b.getMessage()
                                                                                .getString()
                                                                                .equals(
                                                                                        "Search"
                                                                                            + " sounds"
                                                                                            + " / Preview"))
                                                .map(
                                                        child ->
                                                                (net.minecraft.client.gui.components
                                                                                .Button)
                                                                        child)
                                                .findFirst()
                                                .orElseThrow();
                                return new double[] {
                                    2 * (button.getX() + button.getWidth() / 2.0),
                                    2 * (button.getY() + button.getHeight() / 2.0)
                                };
                            });
            context.takeScreenshot("custom-sound-setting-entry");
            context.getInput().setCursorPos(browse[0], browse[1]);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            context.runOnClient(
                    mc -> {
                        if (!(mc.gui.screen() instanceof SoundPickerScreen))
                            throw new AssertionError("Sound picker not opened from Cloth");
                        for (var child : mc.gui.screen().children())
                            if (child instanceof EditBox edit)
                                edit.setValue("block.note_block.pling");
                    });
            context.waitTicks(2);
            context.clickScreenButton("Preview");
            context.waitTicks(2);
            context.takeScreenshot("custom-sound-search");
            context.clickScreenButton("block.note_block.pling");
            context.waitTicks(2);
            context.setScreen(() -> null);
            // Drive real capture lifecycle, with two successful single-page responses.
            for (int n = 0; n < 2; n++) {
                var cfg = CoreTraceClient.service.config();
                cfg.autoCapture = true;
                cfg.autoPageAdvance = true;
                cfg.simpleMode = true;
                cfg.resetCaptureOnComplete = true;
                CoreTraceClient.service.configure(cfg).join();
                final String material = n == 0 ? "stone" : "gold_block";
                context.runOnClient(
                        mc -> {
                            long now = System.currentTimeMillis();
                            CoreTraceClient.capture.command(
                                    "co lookup r:10 t:1h", CoreTraceClient.serverIdentity(), now);
                            CoreTraceClient.capture.receive(
                                    new ChatLine(
                                            "0.02/m ago - Alice broke " + material + ".",
                                            List.of(),
                                            now));
                            CoreTraceClient.capture.receive(
                                    new ChatLine("^ (x0/y-60/z0/world) (a:block)", List.of(), now));
                            CoreTraceClient.capture.receive(
                                    new ChatLine("Page 1/1", List.of(), now));
                        });
                context.waitFor(mc -> !CoreTraceClient.service.config().autoCapture);
                context.runOnClient(
                        mc -> {
                            if (CoreTraceClient.service.config().autoPageAdvance)
                                throw new AssertionError("Auto reset left paging ON");
                        });
            }
            context.runOnClient(
                    mc -> {
                        var own =
                                CoreTraceClient.service.events().stream()
                                        .filter(
                                                e ->
                                                        e.context()
                                                                .server()
                                                                .equals(
                                                                        CoreTraceClient
                                                                                .serverIdentity()))
                                        .toList();
                        if (own.size() != 1
                                || !((dev.coreviewer.model.BlockEvent) own.getFirst())
                                        .block()
                                        .equals("minecraft:gold_block"))
                            throw new AssertionError("Automatic replacement failed");
                    });
            context.takeScreenshot("custom-capture-completed");
        }
        context.setScreen(net.minecraft.client.gui.screens.TitleScreen::new);
    }
}
