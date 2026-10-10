package dev.coreviewer;

import dev.coreviewer.config.CoreTraceConfig;
import dev.coreviewer.model.*;
import dev.coreviewer.replay.ReplayController;
import dev.coreviewer.storage.EventCodec;
import dev.coreviewer.ui.*;
import dev.coreviewer.view.*;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.nio.file.*;
import java.util.*;

public final class LibraryGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world =
                context.worldBuilder()
                        .adjustSettings(settings -> settings.setAllowCommands(true))
                        .create()) {
            world.getServer().runCommand("tp @p 0 -60 -8 0 10");
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
            var c = new CoreTraceConfig();
            c.maxVisibleEvents = 20;
            c.eventRadius = 100;
            c.autoCapture = false;
            CoreTraceClient.service.configure(c).join();
            CoreTraceClient.service.clear().join();
            String server = context.computeOnClient(mc -> CoreTraceClient.serverIdentity());
            var events = new ArrayList<CoreTraceEvent>();
            long time = 1800000000000L;
            for (int i = 0; i < 6; i++) {
                var ctx =
                        new EventContext(
                                UUID.randomUUID(),
                                time + i * 1000,
                                server,
                                "world",
                                null,
                                new EventContext.Position(-5 + i * 2, -60, 4),
                                "Jose",
                                null,
                                false,
                                "GAME_TEST");
                events.add(
                        switch (i) {
                            case 0 ->
                                    new BlockEvent(
                                            ctx, EventType.BLOCK_BREAK, "minecraft:diamond_ore");
                            case 1 ->
                                    new ContainerEvent(
                                            ctx, EventType.CONTAINER_ADD, "minecraft:diamond", 12);
                            case 2 ->
                                    new ContainerEvent(
                                            ctx,
                                            EventType.CONTAINER_REMOVE,
                                            "minecraft:iron_ingot",
                                            6);
                            case 3 -> new SessionEvent(ctx, EventType.SESSION_LOGIN);
                            case 4 -> new SessionEvent(ctx, EventType.SESSION_LOGOUT);
                            default ->
                                    new ItemEvent(ctx, EventType.ITEM_ADD, "minecraft:diamond", 3);
                        });
            }
            try {
                Files.writeString(
                        CoreTraceClient.service.csvFolder().resolve("later.csv"),
                        EventCodec.csv(events.subList(3, 6)));
                Files.writeString(
                        CoreTraceClient.service.csvFolder().resolve("earlier.csv"),
                        EventCodec.csv(events.subList(0, 3)));
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }
            CoreTraceClient.service.refreshCsv(server).join();
            CoreTraceClient.service.selectAllCsv(true, server).join();
            if (CoreTraceClient.service.events().size() != 6
                    || CoreTraceClient.service.events().getFirst().type() != EventType.BLOCK_BREAK)
                throw new AssertionError("Merged chronology");
            context.setScreen(() -> CoreTraceConfigScreen.create(null));
            context.waitTicks(10);
            context.takeScreenshot("direct-settings");
            context.setScreen(() -> CoreTraceConfigScreen.createEditor(null, "REPLAY"));
            context.waitTicks(5);
            context.takeScreenshot("smart-timeline-field");
            var inputPosition =
                    context.computeOnClient(
                            mc -> {
                                var settings =
                                        (me.shedaniel.clothconfig2.gui.AbstractConfigScreen)
                                                mc.gui.screen();
                                var entry =
                                        (SmartTimelineEntry)
                                                settings.getCategorizedEntries().values().stream()
                                                        .flatMap(List::stream)
                                                        .filter(
                                                                e ->
                                                                        e
                                                                                instanceof
                                                                                SmartTimelineEntry)
                                                        .findFirst()
                                                        .orElseThrow();
                                entry.setValue("");
                                var input =
                                        (net.minecraft.client.gui.components.EditBox)
                                                entry.children().stream()
                                                        .filter(
                                                                w ->
                                                                        w
                                                                                instanceof
                                                                                net.minecraft.client
                                                                                        .gui
                                                                                        .components
                                                                                        .EditBox)
                                                        .findFirst()
                                                        .orElseThrow();
                                return new double[] {
                                    2 * (input.getX() + 8), 2 * (input.getY() + 8)
                                };
                            });
            context.getInput().setCursorPos(inputPosition[0], inputPosition[1]);
            context.getInput()
                    .pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
            context.getInput().typeChars("7.5");
            context.runOnClient(
                    mc ->
                            ((me.shedaniel.clothconfig2.gui.AbstractConfigScreen) mc.gui.screen())
                                    .saveAll(false));
            context.waitFor(mc -> CoreTraceClient.service.config().smartTimelineSeconds == 7.5);
            context.runOnClient(
                    mc -> {
                        var settings =
                                (me.shedaniel.clothconfig2.gui.AbstractConfigScreen)
                                        mc.gui.screen();
                        var entry =
                                (SmartTimelineEntry)
                                        settings.getCategorizedEntries().values().stream()
                                                .flatMap(List::stream)
                                                .filter(e -> e instanceof SmartTimelineEntry)
                                                .findFirst()
                                                .orElseThrow();
                        entry.setValue("");
                        settings.saveAll(false);
                    });
            context.waitFor(mc -> CoreTraceClient.service.config().smartTimelineSeconds == 2);
            context.setScreen(() -> CoreTraceConfigScreen.createEditor(null, "CSV"));
            context.runOnClient(
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
                                                                .equals("Select CSV Files"))
                                        .findFirst()
                                        .orElseThrow();
                        var button =
                                (net.minecraft.client.gui.components.Button)
                                        entry.children().getFirst();
                        button.onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0));
                    });
            context.waitFor(mc -> mc.gui.screen() instanceof CsvLibraryScreen);
            context.waitTicks(4);
            context.takeScreenshot("library-selection");
            context.clickScreenButton("Deselect all");
            context.waitFor(mc -> CoreTraceClient.service.events().isEmpty());
            context.clickScreenButton("Select all");
            context.waitFor(mc -> CoreTraceClient.service.events().size() == 6);
            context.setScreen(() -> new StatisticsScreen(null));
            context.waitTicks(10);
            context.takeScreenshot("library-statistics-all");
            context.clickScreenButton("Show details: ON");
            context.waitTicks(4);
            context.takeScreenshot("statistics-icons-only");
            context.clickScreenButton("Show details: OFF");
            context.clickScreenButton("Compact: ON");
            context.waitTicks(8);
            context.takeScreenshot("statistics-expanded");
            context.clickScreenButton("Compact: OFF");
            context.waitTicks(8);
            context.clickScreenButton("ALL ▾");
            context.clickScreenButton("CONTAINERS");
            context.waitTicks(6);
            context.clickScreenButton("Jose");
            context.waitTicks(6);
            context.takeScreenshot("library-player-statistics");
            context.setScreen(() -> null);
            context.runOnClient(mc -> mc.options.improvedTransparency().set(true));
            context.waitTicks(20);
            context.runOnClient(mc -> StaticView.show("world"));
            context.waitFor(mc -> StaticView.total() == 6);
            context.waitTicks(12);
            context.takeScreenshot("library-container-session");
            world.getServer().runCommand("tp @p -3 -58 0 0 0");context.waitTicks(10);
            context.takeScreenshot("container-slot-closeup");
            world.getServer().runCommand("tp @p 0 -60 -8 0 12");context.waitTicks(8);
            context.runOnClient(
                    mc -> {
                        if (StaticRenderer.figureCount != 2 || StaticRenderer.itemCount != 3)
                            throw new AssertionError(
                                    "Container/session models missing: "
                                            + StaticRenderer.figureCount
                                            + "/"
                                            + StaticRenderer.itemCount);
                    });
            c.throughWalls = false;
            CoreTraceClient.service.configure(c).join();
            context.waitTicks(12);
            context.takeScreenshot("oit-depth-tested");
            c.throughWalls = true;
            context.runOnClient(mc -> mc.options.improvedTransparency().set(false));
            context.waitTicks(20);
            c.respectRadius = true;
            c.eventRadius = 1;
            CoreTraceClient.service.configure(c).join();
            context.runOnClient(mc -> StaticView.show("world"));
            context.waitTicks(12);
            context.runOnClient(
                    mc -> {
                        if (StaticView.total() != 0)
                            throw new AssertionError("Radius filter included remote events");
                    });
            c.respectRadius = false;
            c.teleportToEvents = true;
            c.teleportCommand = "/tp @s {x} {y} {z}";
            CoreTraceClient.service.configure(c).join();
            context.runOnClient(mc -> StaticView.show("world"));
            context.waitFor(mc -> mc.player.distanceToSqr(-5, -60, 4) < 4);
            c.teleportToEvents = false;
            c.eventRadius = 100;
            CoreTraceClient.service.configure(c).join();
            context.runOnClient(mc -> ReplayController.load("world", false));
            context.waitFor(mc -> ReplayController.active());
            context.runOnClient(
                    mc -> {
                        if (ReplayController.engine.events().size() != 6)
                            throw new AssertionError("Replay missing selected events");
                        ReplayController.engine.seekFraction(1);
                    });
            context.waitTicks(8);
            context.takeScreenshot("library-replay");
            context.runOnClient(mc -> StaticView.hide());
        }
        context.setScreen(() -> new CsvLibraryScreen(null));
        context.clickScreenButton("Open CSV folder");
        context.waitTicks(10);
        context.setScreen(net.minecraft.client.gui.screens.TitleScreen::new);
    }
}
