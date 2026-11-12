package dev.coreviewer;

import dev.coreviewer.model.*;
import dev.coreviewer.storage.EventCodec;
import dev.coreviewer.ui.ReportBuilderScreen;
import dev.coreviewer.ui.TeleportTourScreen;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;

public final class ReportGameTest implements FabricClientGameTest {
  @Override
  public void runTest(ClientGameTestContext context) {
    try (var world = context.worldBuilder().create()) {
      world.getConnection().waitForChunksRender();
      var config = new dev.coreviewer.config.CoreTraceConfig();
      CoreTraceClient.service.configure(config).join();
      CoreTraceClient.service.clear().join();
      String server = context.computeOnClient(mc -> CoreTraceClient.serverIdentity());
      var events =
          new ArrayList<CoreTraceEvent>(List.of(
              block("Jose", 1791450000000L, "minecraft:ancient_debris"),
              block("Juan", 1791450010000L, "minecraft:dirt"),
              block("Jose", 1791450020000L, "minecraft:ancient_debris")));
      boolean liveHeads = "1".equals(System.getenv("COREVIEWER_LIVE_MOJANG"));
      if (liveHeads) {
        events.add(block("Frecciatore", 1791450030000L, "minecraft:ancient_debris"));
        events.add(block("Juno_72", 1791450040000L, "minecraft:ancient_debris"));
      }
      try {
        Files.writeString(
            CoreTraceClient.service.csvFolder().resolve("report-test.csv"), EventCodec.csv(events));
      } catch (Exception ex) {
        throw new AssertionError(ex);
      }
      CoreTraceClient.service.refreshCsv(server).join();
      CoreTraceClient.service.selectAllCsv(true, server).join();
      context.setScreen(() -> new ReportBuilderScreen(null));
      context.waitTicks(6);
      context.takeScreenshot("report-builder-all");
      if (liveHeads) {
        context.waitTicks(240);
        context.takeScreenshot("report-builder-live-heads");
      }
      context.clickScreenButton("Sort: Name");
      context.waitFor(mc -> hasButton(mc, "Sort: Breaks"));
      context.takeScreenshot("report-builder-breaks-descending");
      context.runOnClient(
          mc -> {
            for (var child : mc.gui.screen().children())
              if (child instanceof EditBox box
                  && box.getMessage().getString().equals("Search materials"))
                box.setValue("ancient");
          });
      context.waitFor(mc -> hasButton(mc, "ancient debris") && !hasButton(mc, "dirt"));
      context.takeScreenshot("report-builder-search");
      context.clickScreenButton("Breakdown...");
      context.waitFor(mc -> hasButton(mc, "Split by file: OFF"));
      context.clickScreenButton("Split by sheet: OFF");
      context.waitFor(mc -> hasButton(mc, "Split by file: ON") && hasButton(mc, "Split by sheet: ON"));
      context.takeScreenshot("report-breakdown-before-selection");
      context.clickScreenButton("□  BLOCK BREAK / ancient debris");
      context.takeScreenshot("report-breakdown-selected");
      context.clickScreenButton("Done");
      context.clickScreenButton("Create PDF");
      Path reports = CoreTraceClient.directory.resolve("reports");
      context.waitFor(
          mc -> {
            try (var files = Files.list(reports)) {
              return files.anyMatch(p -> p.getFileName().toString().endsWith(".pdf"));
            } catch (Exception ignored) {
              return false;
            }
          });
      context.setScreen(() -> null);
      var route =
          TeleportRoute.select(
              events,
              Set.of("Jose", "Juan"),
              Set.of(StatisticsQuery.key(events.get(0)), StatisticsQuery.key(events.get(1))),
              0,
              server);
      context.runOnClient(mc -> new TeleportTourScreen(null, route, server).start());
      context.runOnClient(
          mc ->
              TeleportTourScreen.handleKey(
                  com.mojang.blaze3d.platform.InputConstants.PRESS,
                  new net.minecraft.client.input.KeyEvent(
                      com.mojang.blaze3d.platform.InputConstants.KEY_U, 0, 0)));
      context.waitFor(mc -> mc.gui.screen() instanceof TeleportTourScreen);
      context.clickScreenButton("▼");
      context.waitFor(mc -> hasButton(mc, "Juan"));
      context.runOnClient(
          mc -> {
            for (var child : mc.gui.screen().children())
              if (child instanceof EditBox box) box.setValue("Juan");
          });
      context.waitFor(mc -> hasButton(mc, "Juan") && !hasButton(mc, "Jose"));
      context.clickScreenButton("Juan");
      context.waitTicks(5);
      context.takeScreenshot("tp-player-selected-preview");
      context.runOnClient(mc -> TeleportTourScreen.stop());
    }
  }

  private static CoreTraceEvent block(String actor, long time, String material) {
    return new BlockEvent(
        new EventContext(
            UUID.randomUUID(),
            time,
            "server",
            "world",
            null,
            new EventContext.Position(0, 64, 0),
            actor,
            null,
            false,
            "GAME_TEST"),
        EventType.BLOCK_BREAK,
        material);
  }

  private static boolean hasButton(net.minecraft.client.Minecraft mc, String text) {
    return mc.gui.screen() != null
        && mc.gui.screen().children().stream()
            .anyMatch(
                child ->
                    child instanceof Button button
                        && button.getMessage().getString().contains(text));
  }
}
