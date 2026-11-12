package dev.coreviewer.ui;

import dev.coreviewer.CoreTraceClient;
import dev.coreviewer.view.StaticView;
import java.awt.FileDialog;
import java.awt.Frame;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;

public final class CsvLibraryScreen extends Screen {
  private final Screen parent;
  private int page;
  private boolean busy;
  private boolean searchDirty;
  private boolean draggingScrollbar;
  private String query = "";
  private int maxPage;

  public CsvLibraryScreen(Screen parent) {
    super(Component.literal("Investigation Files"));
    this.parent = parent;
  }

  public static void openFolder() {
    FolderOpener.open(CoreTraceClient.service.csvFolder());
  }

  private void chooseWorkbook() {
    CompletableFuture.runAsync(
        () -> {
          var dialog = new FileDialog((Frame) null, "Import .xlsx workbook", FileDialog.LOAD);
          dialog.setFile("*.xlsx");
          dialog.setVisible(true);
          String selected = dialog.getFile();
          String directory = dialog.getDirectory();
          dialog.dispose();
          if (selected == null || directory == null) return;
          Path path = Path.of(directory, selected);
          Minecraft.getInstance()
              .execute(
                  () ->
                      action(
                          CoreTraceClient.service.importXlsx(
                              path, CoreTraceClient.serverIdentity())));
        });
  }

  private Button button(String text, int x, int y, int w, Runnable action) {
    return addRenderableWidget(
        Button.builder(Component.literal(text), b -> action.run()).bounds(x, y, w, 20).build());
  }

  private void action(CompletableFuture<String> work) {
    busy = true;
    children()
        .forEach(
            child -> {
              if (child instanceof AbstractWidget widget) widget.active = false;
            });
    CoreTraceClient.stopCapture("CSV library changed.");
    StaticView.hide();
    work.handle(
            (message, error) ->
                error == null
                    ? message
                    : "Investigation file operation failed: "
                        + (error.getCause() == null
                            ? error.getMessage()
                            : error.getCause().getMessage()))
        .thenAccept(
            message ->
                Minecraft.getInstance()
                    .execute(
                        () -> {
                          busy = false;
                          CoreTraceClient.tell(message);
                          if (minecraft.gui.screen() == this) rebuildWidgets();
                        }));
  }

  private void confirm(String description, Runnable action) {
    minecraft.gui.setScreen(
        new ConfirmScreen(
            yes -> {
              minecraft.gui.setScreen(this);
              if (yes) action.run();
            },
            Component.literal("Delete CSV files?"),
            Component.literal(description)));
  }

  @Override
  protected void init() {
    var service = CoreTraceClient.service;
    int w = Math.min(540, width - 20),
        x = (width - w) / 2,
        count = Math.max(1, (height - 183) / 28);
    var files =
        service.csvFiles().stream()
            .filter(
                file ->
                    file.name().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
            .toList();
    maxPage = Math.max(0, (files.size() - 1) / count);
    page = Math.min(page, maxPage);
    boolean coretracerLoaded =
        net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("coretrace");
    if (coretracerLoaded)
      button(
          "Import Coretracer",
          x,
          78,
          w,
          () -> minecraft.gui.setScreen(new CoretracerImportScreen(this)));
    button("Open data folder", x, 30, w / 4 - 4, CsvLibraryScreen::openFolder);
    button("Import .xlsx", x + w / 4, 30, w / 4 - 4, this::chooseWorkbook);
    button(
        "Refresh",
        x + w / 2,
        30,
        w / 4 - 4,
        () -> action(service.refreshCsv(CoreTraceClient.serverIdentity())));
    button(
        "Delete all CSVs",
        x + w * 3 / 4,
        30,
        w / 4,
        () ->
            confirm(
                "Permanently delete every CSV in the shared folder and its"
                    + " subfolders? CoreTrace companion files in otherwise empty"
                    + " capture folders will also be deleted.",
                () -> action(service.clear())));
    button(
                "Select all",
                x,
                54,
                w / 2 - 3,
                () -> action(service.selectAllCsv(true, CoreTraceClient.serverIdentity())))
            .active =
        !service.config().simpleMode;
    button(
                "Deselect all",
                x + w / 2,
                54,
                w / 2,
                () -> action(service.selectAllCsv(false, CoreTraceClient.serverIdentity())))
            .active =
        !service.config().simpleMode;
    for (int row = 0; row < count; row++) {
      int at = page * count + row;
      if (at >= files.size()) break;
      var file = files.get(at);
      int y = 130 + row * 28;
      var select =
          button(
              "",
              x,
              y,
              26,
              () ->
                  action(
                      service.selectCsv(
                          file.name(), !file.selected(), CoreTraceClient.serverIdentity())));
      select.setMessage(
          Component.literal(file.selected() ? "✓" : "□")
              .withStyle(file.selected() ? ChatFormatting.GREEN : ChatFormatting.WHITE));
      select.active = !service.config().simpleMode;
      var name =
          button(font.plainSubstrByWidth(file.name(), w - 143), x + 30, y, w - 134, () -> {});
      name.setTooltip(
          Tooltip.create(
              Component.literal(file.name() + "\n" + file.count() + " events; " + file.warning())));
      button("↑", x + w - 100, y, 28, () -> action(service.moveCsv(file.name(), -1)));
      button("↓", x + w - 70, y, 28, () -> action(service.moveCsv(file.name(), 1)));
      button(
          "×",
          x + w - 40,
          y,
          40,
          () ->
              confirm(
                  "Permanently delete "
                      + file.name()
                      + "? If this is the last CSV, its CoreTrace companion"
                      + " files will also be deleted when no other files"
                      + " remain.",
                  () -> action(service.deleteCsv(file.name()))));
    }
    String[] toolbar = {
      "Open data folder",
      "Import Coretracer",
      "Import .xlsx",
      "Refresh",
      "Delete all CSVs",
      "Select all",
      "Deselect all"
    };
    for (var child : children())
      if (child instanceof Button b) {
        for (int i = 0; i < toolbar.length; i++)
          if (b.getMessage().getString().equals(toolbar[i])) {
            int row = i < 3 ? 0 : i < 5 ? 1 : 2,
                slots = row == 0 ? 3 : 2,
                col = i < 3 ? i : i < 5 ? i - 3 : i - 5;
            b.setX(x + col * w / slots);
            b.setY(30 + 24 * row);
            b.setWidth(w / slots - 3);
          }
      }
    if (!coretracerLoaded) {
      for (var child : children()) {
        if (child instanceof Button b) {
          String label = b.getMessage().getString();
          if (label.equals("Open data folder")) b.setWidth(w / 2 - 3);
          if (label.equals("Import .xlsx")) {
            b.setX(x + w / 2);
            b.setWidth(w / 2);
          }
        }
      }
    }
    addSearch(x, w);
    button(
        "Previous",
        x,
        height - 28,
        90,
        () -> {
          page = Math.max(0, page - 1);
          rebuildWidgets();
        });
    button("Done", width / 2 - 45, height - 28, 90, this::onClose);
    button(
        "Next",
        x + w - 90,
        height - 28,
        90,
        () -> {
          if ((page + 1) * count < files.size()) page++;
          rebuildWidgets();
        });
  }

  private void addSearch(int x, int w) {
    var search =
        addRenderableWidget(
            new EditBox(
                font, x + 22, 103, w - 22, 20, Component.literal("Search investigation files")));
    search.setValue(query);
    search.setHint(Component.literal("Search filename..."));
    search.setResponder(
        value -> {
          query = value;
          page = 0;
          searchDirty = true;
        });
    search.active = !busy;
  }

  @Override
  public void tick() {
    if (searchDirty) {
      searchDirty = false;
      rebuildWidgets();
      for (var child : children()) {
        if (child instanceof EditBox box) {
          setFocused(box);
          box.setFocused(true);
          break;
        }
      }
    }
  }

  private int trackX() {
    return (width + Math.min(540, width - 20)) / 2 + 4;
  }

  private int trackY() {
    return 130;
  }

  private int trackHeight() {
    return Math.max(20, height - 168);
  }

  private void moveScrollbar(double y) {
    if (maxPage == 0) return;
    int h = trackHeight();
    int thumb = Math.min(h, Math.max(16, h / (maxPage + 1)));
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

  @Override
  public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
    super.extractRenderState(g, mx, my, dt);
    g.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
    g.fill(
        (width - Math.min(540, width - 20)) / 2 + 2,
        109,
        (width - Math.min(540, width - 20)) / 2 + 8,
        110,
        0xFF9CB7C9);
    g.fill(
        (width - Math.min(540, width - 20)) / 2 + 1,
        110,
        (width - Math.min(540, width - 20)) / 2 + 2,
        115,
        0xFF9CB7C9);
    g.fill(
        (width - Math.min(540, width - 20)) / 2 + 8,
        110,
        (width - Math.min(540, width - 20)) / 2 + 9,
        115,
        0xFF9CB7C9);
    g.fill(
        (width - Math.min(540, width - 20)) / 2 + 2,
        115,
        (width - Math.min(540, width - 20)) / 2 + 8,
        116,
        0xFF9CB7C9);
    g.fill(
        (width - Math.min(540, width - 20)) / 2 + 8,
        115,
        (width - Math.min(540, width - 20)) / 2 + 12,
        119,
        0xFF9CB7C9);
    if (maxPage > 0) {
      int h = trackHeight(), thumb = Math.min(h, Math.max(16, h / (maxPage + 1)));
      int pos = (h - thumb) * page / maxPage;
      g.fill(trackX(), trackY(), trackX() + 5, trackY() + h, 0xFF354B60);
      g.fill(trackX(), trackY() + pos, trackX() + 5, trackY() + pos + thumb, 0xFF8FE0D5);
    }
    if (busy)
      g.centeredText(font, "Loading investigation files…", width / 2, height - 43, 0xFFFFFF55);
  }

  @Override
  public void onClose() {
    minecraft.gui.setScreen(parent);
  }
}
