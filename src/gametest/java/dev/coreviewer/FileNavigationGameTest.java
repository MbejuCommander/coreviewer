package dev.coreviewer;

import dev.coreviewer.model.BlockEvent;
import dev.coreviewer.model.EventContext;
import dev.coreviewer.model.EventType;
import dev.coreviewer.storage.EventCodec;
import dev.coreviewer.ui.CoretracerImportScreen;
import dev.coreviewer.ui.CsvLibraryScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;

public final class FileNavigationGameTest implements FabricClientGameTest {
  @Override
  public void runTest(ClientGameTestContext context) {
    try (var world = context.worldBuilder().create()) {
      world.getConnection().waitForChunksRender();
      CoreTraceClient.service.clear().join();
      String server = context.computeOnClient(mc -> CoreTraceClient.serverIdentity());
      var event =
          new BlockEvent(
              new EventContext(
                  UUID.randomUUID(),
                  1800000000000L,
                  server,
                  "world",
                  null,
                  new EventContext.Position(0, 64, 0),
                  "FileTester",
                  null,
                  false,
                  "GAME_TEST"),
              EventType.BLOCK_BREAK,
              "minecraft:stone");
      String csv = EventCodec.csv(List.of(event));
      Path exports =
          net.fabricmc.loader.api.FabricLoader.getInstance()
              .getGameDir()
              .resolve("coretrace/exports/file-navigation-test");
      try {
        Files.createDirectories(exports);
        for (int i = 0; i < 28; i++) {
          String filename = "nav-" + String.format("%02d", i) + ".csv";
          Files.writeString(CoreTraceClient.service.csvFolder().resolve(filename), csv);
          Files.writeString(exports.resolve(filename), csv);
        }
      } catch (Exception e) {
        throw new AssertionError(e);
      }
      CoreTraceClient.service.refreshCsv(server).join();
      context.setScreen(() -> new CsvLibraryScreen(null));
      context.waitFor(mc -> hasFile(mc.gui.screen(), "nav-00.csv"));
      context.runOnClient(mc -> firstSearch(mc.gui.screen()).setValue("nav-27"));
      context.waitFor(mc -> hasFile(mc.gui.screen(), "nav-27.csv"));
      context.runOnClient(
          mc -> {
            if (hasFile(mc.gui.screen(), "nav-00.csv"))
              throw new AssertionError("Investigation filename search did not filter live");
            firstSearch(mc.gui.screen()).setValue("");
          });
      context.waitFor(mc -> hasFile(mc.gui.screen(), "nav-00.csv"));
      context.runOnClient(mc -> mc.gui.screen().mouseScrolled(100, 160, 0, -1));
      context.waitFor(mc -> !hasFile(mc.gui.screen(), "nav-00.csv"));
      context.runOnClient(
          mc -> {
            var screen = mc.gui.screen();
            var left = new net.minecraft.client.input.MouseButtonInfo(0, 0);
            int x = (screen.width + Math.min(540, screen.width - 20)) / 2 + 6;
            screen.mouseClicked(
                new net.minecraft.client.input.MouseButtonEvent(x, 131, left), false);
            screen.mouseDragged(
                new net.minecraft.client.input.MouseButtonEvent(x, screen.height - 40, left),
                0,
                screen.height - 171);
            screen.mouseReleased(
                new net.minecraft.client.input.MouseButtonEvent(x, screen.height - 40, left));
          });
      context.waitFor(mc -> hasFile(mc.gui.screen(), "nav-27.csv"));
      context.takeScreenshot("investigation-search-scrollbar");
      context.setScreen(() -> new CoretracerImportScreen(null));
      context.waitFor(mc -> hasFile(mc.gui.screen(), "nav-00.csv"));
      context.runOnClient(mc -> firstSearch(mc.gui.screen()).setValue("nav-27"));
      context.waitFor(mc -> hasFile(mc.gui.screen(), "nav-27.csv"));
      context.runOnClient(mc -> firstSearch(mc.gui.screen()).setValue(""));
      context.waitFor(mc -> hasFile(mc.gui.screen(), "nav-00.csv"));
      context.runOnClient(mc -> mc.gui.screen().mouseScrolled(100, 160, 0, -1));
      context.waitFor(mc -> !hasFile(mc.gui.screen(), "nav-00.csv"));
      context.runOnClient(
          mc -> {
            var screen = mc.gui.screen();
            var left = new net.minecraft.client.input.MouseButtonInfo(0, 0);
            int x = screen.width - 6;
            screen.mouseClicked(
                new net.minecraft.client.input.MouseButtonEvent(x, 81, left), false);
            screen.mouseDragged(
                new net.minecraft.client.input.MouseButtonEvent(x, screen.height - 92, left),
                0,
                screen.height - 173);
            screen.mouseReleased(
                new net.minecraft.client.input.MouseButtonEvent(x, screen.height - 92, left));
          });
      context.waitFor(mc -> hasFile(mc.gui.screen(), "nav-27.csv"));
      context.takeScreenshot("coretracer-import-scrollbar");
      context.setScreen(() -> null);
    }
  }

  private static EditBox firstSearch(net.minecraft.client.gui.screens.Screen screen) {
    return screen.children().stream()
        .filter(EditBox.class::isInstance)
        .map(EditBox.class::cast)
        .findFirst()
        .orElseThrow();
  }

  private static boolean hasFile(net.minecraft.client.gui.screens.Screen screen, String filename) {
    return screen != null
        && screen.children().stream()
            .anyMatch(
                child ->
                    child instanceof Button button
                        && button.getMessage().getString().contains(filename));
  }
}
