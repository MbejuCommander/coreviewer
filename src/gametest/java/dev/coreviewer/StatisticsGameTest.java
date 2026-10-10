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
            var source = new ArrayList<CoreTraceEvent>();
            for (int i = 0; i < 50; i++) {
                String actor = String.format(Locale.ROOT, "Player%02d", i);
                for (int j = 0; j < 3; j++) {
                    var ctx =
                            new EventContext(
                                    UUID.randomUUID(),
                                    1800000000000L + i * 100 + j,
                                    "test",
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
            try {
                Files.writeString(
                        CoreTraceClient.service.csvFolder().resolve("stats.csv"),
                        EventCodec.csv(source));
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }
            CoreTraceClient.service.refreshCsv("test").join();
            CoreTraceClient.service.selectAllCsv(true, "test").join();
            context.setScreen(() -> new StatisticsScreen(null));
            context.waitTicks(12);
            search(context, "Item", "diamond");
            context.clickScreenButton("Sort: TOTAL");
            context.clickScreenButton("↓");
            context.waitTicks(3);
            context.takeScreenshot("statistics-ranked-diamonds");
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
                                                                        && b.getY() == 97)
                                                .findFirst()
                                                .orElseThrow();
                        icon.onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0));
                    });
            context.waitFor(mc -> mc.gui.screen() instanceof EventDetailsScreen);
            context.waitTicks(8);
            context.takeScreenshot("statistics-event-evidence");
            context.clickScreenButton("Back");
            context.clickScreenButton("All players");
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
            context.setScreen(() -> null);
        }
        context.setScreen(net.minecraft.client.gui.screens.TitleScreen::new);
    }
}
