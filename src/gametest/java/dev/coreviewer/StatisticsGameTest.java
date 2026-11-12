package dev.coreviewer;

import dev.coreviewer.model.*;
import dev.coreviewer.storage.EventCodec;
import dev.coreviewer.ui.*;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.*;

import java.nio.file.Files;
import java.util.*;

public final class StatisticsGameTest implements FabricClientGameTest {
    private void search(ClientGameTestContext context, String label, String value) {
        context.runOnClient(
                mc -> {
                    for (var child : mc.gui.screen().children())
                        if (child instanceof EditBox box
                                && box.getMessage().getString().equals(label + " search"))
                            box.setValue(value);
                });
        context.waitTicks(3);
    }

    private String firstPlayer(net.minecraft.client.gui.screens.Screen screen) {
        return screen.children().stream()
                .filter(
                        w ->
                                w instanceof Button b
                                        && b.getMessage().getString().startsWith("Player")
                                        && !b.getMessage().getString().equals("Players"))
                .map(w -> ((Button) w).getMessage().getString())
                .findFirst()
                .orElseThrow();
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            CoreTraceClient.service.clear().join();
            var config = CoreTraceClient.service.config();
            config.enabled = true;
            config.simpleMode = false;
            config.statisticsCompact = true;
            config.statisticsDetails = true;
            CoreTraceClient.service.configure(config).join();
            String server = context.computeOnClient(mc -> CoreTraceClient.serverIdentity());
            var source = new ArrayList<CoreTraceEvent>();
            for (int i = 0; i < 50; i++) {
                String actor = String.format(Locale.ROOT, "Player%02d", i);
                for (int j = 0; j < 3; j++) {
                    var ctx =
                            new EventContext(
                                    UUID.randomUUID(),
                                    1800000000000L + i * 100 + j,
                                    server,
                                    "world",
                                    null,
                                    new EventContext.Position(i, 64, j),
                                    actor,
                                    null,
                                    false,
                                    "CSV");
                    source.add(
                            j == 0
                                    ? new BlockEvent(ctx, EventType.BLOCK_PLACE, "minecraft:stone")
                                    : new ItemEvent(
                                            ctx,
                                            j == 1 ? EventType.ITEM_ADD : EventType.ITEM_REMOVE,
                                            "minecraft:diamond",
                                            j == 1 ? i + 1 : 50 - i));
                }
            }
            source.add(
                    new BlockEvent(
                            new EventContext(
                                    UUID.randomUUID(),
                                    1800000010000L,
                                    server,
                                    "world",
                                    null,
                                    new EventContext.Position(300, 64, 300),
                                    "Player00",
                                    null,
                                    false,
                                    "CSV"),
                            EventType.BLOCK_BREAK,
                            "minecraft:obsidian"));
            try {
                Files.writeString(
                        CoreTraceClient.service.csvFolder().resolve("stats.csv"),
                        EventCodec.csv(source));
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }
            CoreTraceClient.service.refreshCsv(server).join();
            CoreTraceClient.service.selectAllCsv(true, server).join();
            context.setScreen(() -> new StatisticsScreen(null));
            context.waitTicks(12);
            search(context, "Item", "diamond");
            context.clickScreenButton("Sort: TOTAL");
            context.clickScreenButton("↓");
            context.waitTicks(3);
            context.takeScreenshot("statistics-ranked-diamonds");
            var generalScreen = context.computeOnClient(mc -> mc.gui.screen());
            context.clickScreenButton("Player49");
            context.waitTicks(8);
            search(context, "Item", "diamond");
            context.runOnClient(
                    mc -> {
                        var icon =
                                (Button)
                                        mc.gui.screen().children().stream()
                                                .filter(
                                                        w ->
                                                                w instanceof Button b
                                                                        && b.getWidth() == 24
                                                                        && b.getMessage()
                                                                                .getString()
                                                                                .isEmpty())
                                                .findFirst()
                                                .orElseThrow();
                        icon.onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0));
                    });
            context.waitFor(mc -> mc.gui.screen() instanceof EventDetailsScreen);
            context.waitTicks(8);
            context.takeScreenshot("statistics-event-evidence");
            context.runOnClient(
                    mc ->
                            mc.gui
                                    .screen()
                                    .keyPressed(
                                            new net.minecraft.client.input.KeyEvent(
                                                    com.mojang.blaze3d.platform.InputConstants
                                                            .KEY_ESCAPE,
                                                    0,
                                                    0)));
            context.runOnClient(
                    mc -> {
                        if (!(mc.gui.screen() instanceof StatisticsScreen)
                                || mc.gui.screen() == generalScreen)
                            throw new AssertionError("Escape must return to personal statistics");
                        mc.gui
                                .screen()
                                .keyPressed(
                                        new net.minecraft.client.input.KeyEvent(
                                                com.mojang.blaze3d.platform.InputConstants
                                                        .KEY_ESCAPE,
                                                0,
                                                0));
                        if (mc.gui.screen() != generalScreen)
                            throw new AssertionError("Escape must restore original table");
                    });
            context.waitTicks(8);
            context.clickScreenButton("Reset filters");
            context.waitTicks(8);
            search(context, "Player", "Player40");
            context.takeScreenshot("statistics-player-search");
            context.clickScreenButton("↓ Next");
            context.waitTicks(3);
            context.clickScreenButton("↑ Previous");
            context.clickScreenButton("ALL ▾");
            context.waitTicks(3);
            context.takeScreenshot("statistics-category-dropdown");
            context.clickScreenButton("ITEMS");
            context.waitTicks(8);
            context.clickScreenButton("Reset filters");
            context.waitTicks(8);
            String first = context.computeOnClient(mc -> firstPlayer(mc.gui.screen()));
            context.runOnClient(
                    mc -> {
                        mc.gui.screen().setFocused(null);
                        mc.gui.screen().mouseScrolled(100, 200, 0, -1);
                        if (firstPlayer(mc.gui.screen()).equals(first))
                            throw new AssertionError("Wheel did not advance rows");
                        mc.gui
                                .screen()
                                .keyPressed(
                                        new net.minecraft.client.input.KeyEvent(
                                                com.mojang.blaze3d.platform.InputConstants.KEY_UP,
                                                0,
                                                0));
                        if (!firstPlayer(mc.gui.screen()).equals(first))
                            throw new AssertionError("Up did not restore rows");
                    });
            context.runOnClient(
                    mc -> {
                        var screen = mc.gui.screen();
                        var left = new net.minecraft.client.input.MouseButtonInfo(0, 0);
                        int verticalX = screen.width - 8;
                        screen.mouseClicked(
                                new net.minecraft.client.input.MouseButtonEvent(
                                        verticalX, 145, left),
                                false);
                        screen.mouseDragged(
                                new net.minecraft.client.input.MouseButtonEvent(
                                        verticalX, 170, left),
                                0,
                                25);
                        screen.mouseReleased(
                                new net.minecraft.client.input.MouseButtonEvent(
                                        verticalX, 170, left));
                        if (firstPlayer(screen).equals(first))
                            throw new AssertionError("Vertical scrollbar did not move rows");
                        screen.mouseClicked(
                                new net.minecraft.client.input.MouseButtonEvent(500, 110, left),
                                false);
                        screen.mouseDragged(
                                new net.minecraft.client.input.MouseButtonEvent(740, 110, left),
                                240,
                                0);
                        screen.mouseReleased(
                                new net.minecraft.client.input.MouseButtonEvent(740, 110, left));
                    });
            context.clickScreenButton("Reset filters");
            context.waitTicks(8);
            context.runOnClient(
                    mc -> {
                        for (var child : mc.gui.screen().children())
                            if (child instanceof EditBox box
                                    && box.getMessage().getString().equals("TP radius"))
                                box.setValue("50");
                    });
            context.waitTicks(12);
            context.takeScreenshot("statistics-radius-zones");
            context.clickScreenButton("TP: OFF");
            context.waitTicks(3);
            context.takeScreenshot("statistics-tp-selection");
            var commands = new java.util.concurrent.CopyOnWriteArrayList<String>();
            var intercept = new java.util.concurrent.atomic.AtomicBoolean(true);
            net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND
                    .register(
                            command -> {
                                if (intercept.get() && command.startsWith("co teleport")) {
                                    commands.add(command);
                                    return false;
                                }
                                return true;
                            });
            context.clickScreenButton("TP Now");
            context.waitFor(mc -> mc.gui.screen() == null);
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_U);
            context.waitFor(mc -> mc.gui.screen() instanceof TeleportTourScreen);
            context.waitTicks(8);
            if (!commands.isEmpty()) throw new AssertionError("Opening the preview sent a command");
            context.takeScreenshot("statistics-tp-preview");
            context.clickScreenButton("Next (B)");
            context.clickScreenButton("Next (B)");
            context.clickScreenButton("Back (V)");
            context.waitTicks(3);
            if (!commands.equals(
                    List.of(
                            "co teleport #world 0 64 0",
                            "co teleport #world 0 64 1",
                            "co teleport #world 0 64 0")))
                throw new AssertionError("Manual TP chronology: " + commands);
            context.clickScreenButton("Exit (Esc)");
            intercept.set(false);
            context.setScreen(() -> null);
        }
        context.setScreen(net.minecraft.client.gui.screens.TitleScreen::new);
    }
}
