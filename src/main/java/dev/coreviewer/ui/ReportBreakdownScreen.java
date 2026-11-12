package dev.coreviewer.ui;

import dev.coreviewer.model.CoreTraceEvent;
import dev.coreviewer.report.ReportBreakdown;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Selects optional action/material, file and worksheet report sections. */
public final class ReportBreakdownScreen extends Screen {
  private final Screen parent;
  private final Consumer<ReportBreakdown.Options> save;
  private final List<String> details;
  private final Set<String> selected;
  private boolean byFile, bySheet, searchDirty, dragging;
  private String search = "";
  private int page;

  public ReportBreakdownScreen(
      Screen parent,
      List<CoreTraceEvent> events,
      ReportBreakdown.Options initial,
      Consumer<ReportBreakdown.Options> save) {
    super(Component.literal("Report Breakdown"));
    this.parent = parent;
    this.save = save;
    this.details = events.stream().map(ReportBreakdown::detailKey).distinct().sorted().toList();
    this.selected = new HashSet<>(initial.details());
    this.byFile = initial.byFile();
    this.bySheet = initial.bySheet();
  }

  private List<String> visible() {
    String needle = search.toLowerCase(Locale.ROOT);
    return details.stream()
        .filter(key -> ReportBreakdown.detailLabel(key).toLowerCase(Locale.ROOT).contains(needle))
        .toList();
  }

  private int left() {
    return Math.max(20, width / 2 - 250);
  }

  private int listWidth() {
    return Math.min(500, width - 40);
  }

  private int rowTop() {
    return 142;
  }

  private int rows() {
    return Math.max(1, (height - 28 - rowTop()) / 21);
  }

  private int maxPage() {
    return Math.max(0, (visible().size() - 1) / rows());
  }

  private Button button(String label, int x, int y, int w, Runnable action) {
    return addRenderableWidget(
        Button.builder(Component.literal(label), ignored -> action.run()).bounds(x, y, w, 20).build());
  }

  @Override
  protected void init() {
    int x = left(), w = listWidth();
    var file = button("Split by file: " + (byFile ? "ON" : "OFF"), x, 41, w / 2 - 3, () -> {
      byFile = !byFile;
      if (!byFile) bySheet = false;
      rebuildWidgets();
    });
    file.setTooltip(Tooltip.create(Component.literal("Give each selected CSV or XLSX workbook its own summary.")));
    var sheet = button("Split by sheet: " + (bySheet ? "ON" : "OFF"), x + w / 2 + 3, 41, w / 2 - 3, () -> {
      bySheet = !bySheet;
      if (bySheet) byFile = true;
      rebuildWidgets();
    });
    sheet.setTooltip(Tooltip.create(Component.literal("Give each XLSX sheet its own summary; implies split by file. CSV has no sheets.")));
    var searchBox = addRenderableWidget(new EditBox(font, x, 66, w, 20, Component.literal("Search actions")));
    searchBox.setHint(Component.literal("Search action, block, item or entity..."));
    searchBox.setValue(search);
    searchBox.setResponder(value -> {
      search = value;
      page = 0;
      searchDirty = true;
    });
    button("Select shown", x, 89, w / 2 - 3, () -> {
      selected.addAll(visible());
      rebuildWidgets();
    });
    button("Clear shown", x + w / 2 + 3, 89, w / 2 - 3, () -> {
      selected.removeAll(visible());
      rebuildWidgets();
    });
    page = Math.min(page, maxPage());
    var list = visible();
    for (int i = 0; i < rows(); i++) {
      int at = page * rows() + i;
      if (at >= list.size()) break;
      String key = list.get(at);
      String fullLabel = (selected.contains(key) ? "✓  " : "□  ") + ReportBreakdown.detailLabel(key);
      var item = button(
          font.plainSubstrByWidth(fullLabel, w - 26),
          x,
          rowTop() + 21 * i,
          w - 8,
          () -> {
            if (!selected.add(key)) selected.remove(key);
            rebuildWidgets();
          });
      item.setTooltip(Tooltip.create(Component.literal(ReportBreakdown.detailLabel(key))));
      item.setMessage(Component.literal(item.getMessage().getString())
          .withStyle(selected.contains(key) ? ChatFormatting.GREEN : ChatFormatting.WHITE));
    }
    button("Cancel", x, height - 22, w / 2 - 3, this::onClose);
    button("Done", x + w / 2 + 3, height - 22, w / 2 - 3, () -> {
      save.accept(new ReportBreakdown.Options(selected, byFile, bySheet));
      minecraft.gui.setScreen(parent);
    });
  }

  @Override
  public void tick() {
    if (!searchDirty) return;
    searchDirty = false;
    rebuildWidgets();
    for (var child : children())
      if (child instanceof EditBox box) {
        setFocused(box);
        box.setFocused(true);
        break;
      }
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    if (vertical == 0) return super.mouseScrolled(x, y, horizontal, vertical);
    page = Math.clamp(page - (int) Math.signum(vertical), 0, maxPage());
    rebuildWidgets();
    return true;
  }

  private void dragTo(double y) {
    int max = maxPage();
    if (max == 0) return;
    int track = Math.max(8, rows() * 21 - 2), thumb = Math.min(track, Math.max(14, track / (max + 1)));
    int next = Math.clamp((int) Math.round((y - rowTop() - thumb / 2.0) * max / Math.max(1, track - thumb)), 0, max);
    if (page != next) {
      page = next;
      rebuildWidgets();
    }
  }

  @Override
  public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
    int sx = left() + listWidth() - 5;
    if (event.button() == 0 && maxPage() > 0 && event.x() >= sx && event.x() < sx + 5
        && event.y() >= rowTop() && event.y() < rowTop() + rows() * 21) {
      dragging = true;
      dragTo(event.y());
      return true;
    }
    return super.mouseClicked(event, doubleClick);
  }

  @Override
  public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
    if (dragging && event.button() == 0) {
      dragTo(event.y());
      return true;
    }
    return super.mouseDragged(event, dx, dy);
  }

  @Override
  public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
    if (dragging) {
      dragging = false;
      return true;
    }
    return super.mouseReleased(event);
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
    super.extractRenderState(g, mx, my, dt);
    g.centeredText(font, title, width / 2, 9, 0xFFFFFFFF);
    g.centeredText(font, "Optional detail pages: " + selected.size() + " selected", width / 2, 28, 0xFF9CB7C9);
    g.centeredText(font, "Choose action + material pairs; all options start OFF.", width / 2, 117, 0xFFB7C7D7);
    int max = maxPage();
    if (max == 0) return;
    int track = Math.max(8, rows() * 21 - 2), thumb = Math.min(track, Math.max(14, track / (max + 1)));
    int pos = (track - thumb) * page / max, sx = left() + listWidth() - 5;
    g.fill(sx, rowTop(), sx + 4, rowTop() + track, 0xFF354B60);
    g.fill(sx, rowTop() + pos, sx + 4, rowTop() + pos + thumb, 0xFF8FE0D5);
  }

  @Override
  public void onClose() {
    minecraft.gui.setScreen(parent);
  }
}
