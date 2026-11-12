package dev.coreviewer.ui;

import dev.coreviewer.CoreTraceClient;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CoretracerImportScreen extends Screen {
  private final Screen parent;
  private final Path root =
      net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("coretrace/exports");
  private List<Path> files = List.of();
  private final Set<Path> selected = new HashSet<>();
  private String query = "", status = "Scanning exports...";
  private int page;
  private boolean busy = true, dirty, draggingScrollbar;
  private int maxPage;

  public CoretracerImportScreen(Screen parent) {
    super(Component.literal("Import Coretracer"));
    this.parent = parent;
    CompletableFuture.supplyAsync(
            () -> {
              try {
                if (!Files.isDirectory(root)) return List.<Path>of();
                try (var paths = Files.walk(root)) {
                  return paths
                      .filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                      .filter(
                          p -> {
                            String n = p.toString().toLowerCase(Locale.ROOT);
                            return n.endsWith(".csv") || n.endsWith(".xlsx");
                          })
                      .sorted()
                      .toList();
                }
              } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
              }
            })
        .whenComplete(
            (list, error) ->
                Minecraft.getInstance()
                    .execute(
                        () -> {
                          files = error == null ? list : List.of();
                          busy = false;
                          status =
                              error == null
                                  ? files.size() + " export files"
                                  : "Could not scan exports: " + error.getMessage();
                          if (minecraft != null && minecraft.gui.screen() == this) rebuildWidgets();
                        }));
  }

  private void button(String label, int x, int y, int w, Runnable action) {
    var b =
        addRenderableWidget(
            Button.builder(Component.literal(label), a -> action.run())
                .bounds(x, y, w, 20)
                .build());
    b.active = !busy;
  }

  protected void init() {
    int x = 10, w = width - 20;
    var search =
        addRenderableWidget(new EditBox(font, x, 30, w, 20, Component.literal("Search exports")));
    search.setValue(query);
    search.setHint(Component.literal("Search filename..."));
    search.setResponder(
        v -> {
          query = v;
          page = 0;
          dirty = true;
        });
    search.active = !busy;
    var visible =
        files.stream()
            .filter(
                p ->
                    root.relativize(p)
                        .toString()
                        .toLowerCase(Locale.ROOT)
                        .contains(query.toLowerCase(Locale.ROOT)))
            .toList();
    int count = Math.max(1, (height - 140) / 24);
    maxPage = Math.max(0, (visible.size() - 1) / count);
    page = Math.min(page, maxPage);
    button(
        "Select all",
        x,
        54,
        w / 2 - 2,
        () -> {
          selected.addAll(visible);
          rebuildWidgets();
        });
    button(
        "Deselect all",
        x + w / 2,
        54,
        w / 2,
        () -> {
          selected.removeAll(visible);
          rebuildWidgets();
        });
    for (int i = 0; i < count && page * count + i < visible.size(); i++) {
      var p = visible.get(page * count + i);
      button(
          (selected.contains(p) ? "§a✓ §r" : "□ ")
              + font.plainSubstrByWidth(root.relativize(p).toString(), w - 30),
          x,
          80 + i * 24,
          w,
          () -> {
            if (!selected.add(p)) selected.remove(p);
            rebuildWidgets();
          });
    }
    button(
        "Previous",
        x,
        height - 54,
        80,
        () -> {
          page = Math.max(0, page - 1);
          rebuildWidgets();
        });
    button(
        "Next",
        width - 90,
        height - 54,
        80,
        () -> {
          if ((page + 1) * count < visible.size()) page++;
          rebuildWidgets();
        });
    button("Back", x, height - 28, 80, this::onClose);
    button(
        "Import selected (" + selected.size() + ")",
        100,
        height - 28,
        Math.max(100, width - 110),
        this::importFiles);
  }

  public void tick() {
    if (dirty) {
      dirty = false;
      rebuildWidgets();
      for (var child : children())
        if (child instanceof EditBox box) {
          setFocused(box);
          box.setFocused(true);
        }
    }
  }

  private int trackX() {
    return width - 8;
  }

  private int trackY() {
    return 80;
  }

  private int trackHeight() {
    return Math.max(20, height - 170);
  }

  private void moveScrollbar(double y) {
    if (maxPage == 0) return;
    int h = trackHeight(), thumb = Math.min(h, Math.max(16, h / (maxPage + 1)));
    int next =
        Math.clamp(
            (int) Math.round((y - trackY() - thumb / 2.0) * maxPage / Math.max(1, h - thumb)),
            0,
            maxPage);
    if (next != page) {
      page = next;
      rebuildWidgets();
    }
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    if (vertical == 0 || maxPage == 0) return super.mouseScrolled(x, y, horizontal, vertical);
    int next = Math.clamp(page - (int) Math.signum(vertical), 0, maxPage);
    if (next != page) {
      page = next;
      rebuildWidgets();
    }
    return true;
  }

  @Override
  public boolean mouseClicked(
      net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
    if (event.button() == 0
        && maxPage > 0
        && event.x() >= trackX()
        && event.x() < trackX() + 6
        && event.y() >= trackY()
        && event.y() < trackY() + trackHeight()) {
      draggingScrollbar = true;
      moveScrollbar(event.y());
      return true;
    }
    return super.mouseClicked(event, doubleClick);
  }

  @Override
  public boolean mouseDragged(
      net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
    if (event.button() == 0 && draggingScrollbar) {
      moveScrollbar(event.y());
      return true;
    }
    return super.mouseDragged(event, dx, dy);
  }

  @Override
  public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
    if (draggingScrollbar) {
      draggingScrollbar = false;
      return true;
    }
    return super.mouseReleased(event);
  }

  private void importFiles() {
    if (selected.isEmpty()) return;
    if (CoreTraceClient.service.config().simpleMode) {
      status = "Disable Simple Mode before importing.";
      return;
    }
    var chosen = selected.stream().sorted().toList();
    String server = CoreTraceClient.serverIdentity();
    busy = true;
    status = "Importing...";
    rebuildWidgets();
    CoreTraceClient.stopCapture("Importing Coretracer exports.");
    dev.coreviewer.view.StaticView.hide();
    CompletableFuture.runAsync(
            () -> {
              try {
                for (var p : chosen) {
                  if (!p.toRealPath().startsWith(root.toRealPath()))
                    throw new java.io.IOException("Export is outside the exports folder");
                  if (p.toString().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
                    CoreTraceClient.service.importXlsx(p, server).join();
                  } else {
                    Path dir =
                        Files.createTempDirectory(
                            CoreTraceClient.service.csvFolder(), "coretracer-");
                    Files.copy(p, dir.resolve(p.getFileName()));
                  }
                }
                CoreTraceClient.service.refreshCsv(server).join();
              } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
              }
            })
        .whenComplete(
            (v, e) ->
                Minecraft.getInstance()
                    .execute(
                        () -> {
                          busy = false;
                          status =
                              e == null ? "Import complete." : "Import failed: " + e.getMessage();
                          if (e == null) selected.clear();
                          CoreTraceClient.tell(status);
                          if (minecraft.gui.screen() == this) rebuildWidgets();
                        }));
  }

  public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float dt) {
    super.extractRenderState(g, x, y, dt);
    g.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
    g.centeredText(font, status, width / 2, height - 68, 0xFFFFFF55);
    if (maxPage > 0) {
      int h = trackHeight(),
          thumb = Math.min(h, Math.max(16, h / (maxPage + 1))),
          pos = (h - thumb) * page / maxPage;
      g.fill(trackX(), trackY(), trackX() + 5, trackY() + h, 0xFF354B60);
      g.fill(trackX(), trackY() + pos, trackX() + 5, trackY() + pos + thumb, 0xFF8FE0D5);
    }
  }

  public void onClose() {
    if (!busy) minecraft.gui.setScreen(parent);
  }
}
