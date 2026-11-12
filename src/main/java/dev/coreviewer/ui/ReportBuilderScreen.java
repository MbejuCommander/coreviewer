package dev.coreviewer.ui;

import dev.coreviewer.CoreTraceClient;
import dev.coreviewer.model.*;
import dev.coreviewer.report.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Report-specific selector; the Statistics table and its filters remain unchanged. */
public final class ReportBuilderScreen extends Screen {
  private final Screen parent;
  private final List<CoreTraceEvent> snapshot;
  private final List<String> players;
  private final List<String> selectors;
  private final Map<String, UUID> actorIds = new HashMap<>();
  private final CompletableFuture<Map<String, byte[]>> headLookup;
  private final Map<String, Identifier> headTextures = new HashMap<>();
  private boolean headsRegistered;
  private final Set<String> chosenPlayers = new HashSet<>();
  private final Set<String> chosenSelectors = new HashSet<>();
  private final EnumSet<ReportChart> charts = EnumSet.allOf(ReportChart.class);
  private ReportBreakdown.Options breakdown = new ReportBreakdown.Options(Set.of(), false, false);
  private ReportData.SortMetric sortMetric = ReportData.SortMetric.NAME;
  private boolean descending;
  private EventStatistics.Category category = EventStatistics.Category.ALL;
  private String playerSearch = "",
      eventSearch = "",
      status = "Choose players, materials and charts.";
  private long statusExpiresAt;
  private int playerPage, eventPage;
  private boolean searchDirty, busy, draggingPlayers, draggingEvents;

  public ReportBuilderScreen(Screen parent) {
    super(Component.literal("Create Investigation Report"));
    this.parent = parent;
    snapshot = CoreTraceClient.service.events();
    players =
        snapshot.stream()
            .map(e -> e.context().actor())
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    selectors = snapshot.stream().map(ReportData::selector).distinct().sorted().toList();
    chosenPlayers.addAll(players);
    chosenSelectors.addAll(selectors);
    for (var event : snapshot)
      if (event.context().actorUuid() != null)
        actorIds.putIfAbsent(event.context().actor(), event.context().actorUuid());
    headLookup = PlayerHeadImages.prefetch(players, Map.copyOf(actorIds),
        (name, bytes) -> Minecraft.getInstance().execute(() -> {
          if (Minecraft.getInstance().gui.screen() == this) registerHeadTexture(name, bytes);
        }));
  }

  private List<String> visiblePlayers() {
    String needle = playerSearch.toLowerCase(Locale.ROOT);
    return players.stream().filter(name -> name.toLowerCase(Locale.ROOT).contains(needle)).toList();
  }

  private List<String> visibleSelectors() {
    String needle = eventSearch.toLowerCase(Locale.ROOT).replace('_', ' ');
    return selectors.stream()
        .filter(
            key ->
                (category == EventStatistics.Category.ALL || key.startsWith(category.name() + "|"))
                    && display(key).toLowerCase(Locale.ROOT).contains(needle))
        .toList();
  }

  private static String display(String selector) {
    String[] pair = selector.split("\\|", 2);
    String material = pair.length > 1 ? pair[1] : "";
    return pair[0] + "  /  " + material.replace("minecraft:", "").replace('_', ' ');
  }

  private String sortButtonLabel() {
    return "Sort: "
        + switch (sortMetric) {
          case NAME -> "Name";
          case BREAKS -> "Breaks";
          case PLACES -> "Places";
          case ITEMS -> "Items";
          case CONTAINERS -> "Boxes";
          case KILLS -> "Kills";
          case SESSIONS -> "Sessions";
          case CLICKS -> "Clicks";
          case EVENTS -> "Total";
        };
  }

  private Button button(String label, int x, int y, int w, int h, Runnable action) {
    var value =
        addRenderableWidget(
            Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, w, h).build());
    value.active = !busy;
    return value;
  }

  private int left() {
    return 20;
  }

  private int usable() {
    return width - 40;
  }

  private int playerWidth() {
    return Math.max(130, usable() * 36 / 100);
  }

  private int eventX() {
    return left() + playerWidth() + 8;
  }

  private int eventWidth() {
    return Math.max(110, left() + usable() - eventX());
  }

  private int rows() {
    return Math.max(1, ((width < 540 ? height - 46 : height - 22) - rowTop()) / 20);
  }

  private int rowTop() {
    return 155;
  }

  @Override
  protected void init() {
    int x = left(), w = usable(), pw = playerWidth(), ex = eventX(), ew = eventWidth();
    ReportChart[] options = ReportChart.values();
    for (int i = 0; i < options.length; i++) {
      var chart = options[i];
      button(
          (charts.contains(chart) ? "✓ " : "□ ") + chart.label(),
          x + i * w / options.length,
          40,
          w / options.length - 3,
          20,
          () -> {
            if (!charts.add(chart)) charts.remove(chart);
            rebuildWidgets();
          });
    }
    var categories = EventStatistics.Category.values();
    for (int i = 0; i < categories.length; i++) {
      var selected = categories[i];
      var categoryButton =
          button(
              (category == selected ? "• " : "")
                  + (selected == EventStatistics.Category.CONTAINERS
                      ? "BOXES"
                      : selected == EventStatistics.Category.SESSIONS
                          ? "SESSION"
                          : selected.name()),
              x + i * w / categories.length,
              63,
              w / categories.length - 3,
              20,
              () -> {
                category = selected;
                eventPage = 0;
                rebuildWidgets();
              });
      categoryButton.setTooltip(Tooltip.create(Component.literal(selected.name())));
    }
    var playerBox =
        addRenderableWidget(new EditBox(font, x, 86, pw, 20, Component.literal("Search players")));
    playerBox.setValue(playerSearch);
    playerBox.setHint(Component.literal("Search player..."));
    playerBox.setResponder(
        value -> {
          playerSearch = value;
          playerPage = 0;
          searchDirty = true;
        });
    var eventBox =
        addRenderableWidget(
            new EditBox(font, ex, 86, ew, 20, Component.literal("Search materials")));
    eventBox.setValue(eventSearch);
    eventBox.setHint(Component.literal("Search block, item or entity..."));
    eventBox.setResponder(
        value -> {
          eventSearch = value;
          eventPage = 0;
          searchDirty = true;
        });
    var sortButton =
        button(
        sortButtonLabel(),
        x,
        109,
        pw * 2 / 3 - 2,
        20,
        () -> {
          var sortOptions = ReportData.SortMetric.values();
          sortMetric = sortOptions[(sortMetric.ordinal() + 1) % sortOptions.length];
          descending = sortMetric != ReportData.SortMetric.NAME;
          rebuildWidgets();
        });
    sortButton.setTooltip(
        Tooltip.create(Component.literal("Order PDF players by " + sortMetric.label() + ".")));
    var directionButton =
        button(
        sortMetric == ReportData.SortMetric.NAME ? descending ? "Z-A" : "A-Z" : descending ? "↓" : "↑",
        x + pw * 2 / 3,
        109,
        pw / 3,
        20,
        () -> {
          descending = !descending;
          rebuildWidgets();
        });
    directionButton.setTooltip(
        Tooltip.create(
            Component.literal(
                sortMetric == ReportData.SortMetric.NAME
                    ? "Switch alphabetical order."
                    : descending
                        ? "Most first; click for fewest first."
                        : "Fewest first; click for most first.")));
    button(
        "All players",
        x,
        132,
        pw / 2 - 2,
        20,
        () -> {
          chosenPlayers.addAll(visiblePlayers());
          rebuildWidgets();
        });
    button(
        "None",
        x + pw / 2,
        132,
        pw / 2,
        20,
        () -> {
          chosenPlayers.removeAll(visiblePlayers());
          rebuildWidgets();
        });
    button(
        "All shown",
        ex,
        132,
        ew / 2 - 2,
        20,
        () -> {
          chosenSelectors.addAll(visibleSelectors());
          rebuildWidgets();
        });
    button(
        "None shown",
        ex + ew / 2,
        132,
        ew / 2,
        20,
        () -> {
          chosenSelectors.removeAll(visibleSelectors());
          rebuildWidgets();
        });
    var leftRows = visiblePlayers();
    var rightRows = visibleSelectors();
    int pageSize = rows();
    playerPage = Math.min(playerPage, Math.max(0, (leftRows.size() - 1) / pageSize));
    eventPage = Math.min(eventPage, Math.max(0, (rightRows.size() - 1) / pageSize));
    for (int i = 0; i < pageSize; i++) {
      int y = rowTop() + i * 20;
      int playerAt = playerPage * pageSize + i;
      if (playerAt < leftRows.size()) {
        String actor = leftRows.get(playerAt);
        var item =
            button(
                (chosenPlayers.contains(actor) ? "✓  " : "□  ")
                    + font.plainSubstrByWidth(actor, pw - 24),
                x,
                y,
                pw - 7,
                18,
                () -> {
                  if (!chosenPlayers.add(actor)) chosenPlayers.remove(actor);
                  rebuildWidgets();
                });
        item.setMessage(
            Component.literal(item.getMessage().getString())
                .withStyle(
                    chosenPlayers.contains(actor) ? ChatFormatting.GREEN : ChatFormatting.WHITE));
      }
      int eventAt = eventPage * pageSize + i;
      if (eventAt < rightRows.size()) {
        String key = rightRows.get(eventAt);
        String label = (chosenSelectors.contains(key) ? "✓  " : "□  ") + display(key);
        var item =
            button(
                font.plainSubstrByWidth(label, ew - 26),
                ex,
                y,
                ew - 7,
                18,
                () -> {
                  if (!chosenSelectors.add(key)) chosenSelectors.remove(key);
                  rebuildWidgets();
                });
        item.setTooltip(Tooltip.create(Component.literal(display(key))));
        item.setMessage(
            Component.literal(item.getMessage().getString())
                .withStyle(
                    chosenSelectors.contains(key) ? ChatFormatting.GREEN : ChatFormatting.WHITE));
      }
    }
    boolean compactActions = width < 540;
    int smallAction = compactActions ? Math.min(140, (width - 50) / 2)
        : Math.min(140, Math.max(80, (width / 2 - 70) / 2));
    int actionY = compactActions ? height - 45 : height - 22;
    button("Open reports folder", x, actionY, smallAction, 20,
        () -> FolderOpener.open(CoreTraceClient.directory.resolve("reports")));
    var breakdownButton = button("Breakdown...", x + smallAction + 5, actionY, smallAction, 20,
        this::openBreakdown);
    breakdownButton.setTooltip(Tooltip.create(Component.literal(
        "Choose separate action/material pages and split the report by file or XLSX sheet.")));
    button("Back", width / 2 - 45, height - 22, 90, 20, this::onClose);
    var create = button("Create PDF", width - 160, height - 22, 140, 20, this::createReport);
    create.active =
        !busy
            && !chosenPlayers.isEmpty()
            && !chosenSelectors.isEmpty()
            && !charts.isEmpty()
            && CoreTraceClient.service.csvFiles().stream()
                .anyMatch(dev.coreviewer.storage.CsvLibrary.FileEntry::selected);
    if (busy)
      for (var child : children())
        if (child instanceof AbstractWidget widget) widget.active = false;
  }

  private void openBreakdown() {
    var eligible = snapshot.stream().filter(event -> chosenSelectors.contains(ReportData.selector(event))).toList();
    minecraft.gui.setScreen(new ReportBreakdownScreen(this, eligible, breakdown, value -> breakdown = value));
  }

  @Override
  public void tick() {
    registerReadyHeads();
    if (statusExpiresAt != 0 && System.nanoTime() >= statusExpiresAt) {
      status = "Choose players, materials and charts.";
      statusExpiresAt = 0;
    }
    if (!searchDirty) return;
    searchDirty = false;
    boolean playerFocused =
        getFocused() instanceof EditBox box
            && box.getMessage().getString().equals("Search players");
    rebuildWidgets();
    for (var child : children())
      if (child instanceof EditBox box
          && box.getMessage()
              .getString()
              .equals(playerFocused ? "Search players" : "Search materials")) {
        setFocused(box);
        box.setFocused(true);
        break;
      }
  }

  private void registerReadyHeads() {
    if (headsRegistered || !headLookup.isDone() || minecraft == null) return;
    headsRegistered = true;
    Map<String, byte[]> faces;
    try {
      faces = headLookup.getNow(Map.of());
    } catch (Exception ignored) {
      return;
    }
    faces.forEach(this::registerHeadTexture);
  }

  private void registerHeadTexture(String name, byte[] bytes) {
    if (headTextures.containsKey(name) || minecraft == null) return;
    NativeImage image;
    try {
      image = NativeImage.read(bytes);
    } catch (Exception ignored) {
      return;
    }
    var id = Identifier.fromNamespaceAndPath("coreviewer", "report/head/" + UUID.randomUUID());
    try {
      minecraft.getTextureManager().register(id,
          new DynamicTexture(() -> "Coreviewer report head", image));
      headTextures.put(name, id);
    } catch (Exception ignored) {
      image.close();
    }
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    if (vertical == 0) return super.mouseScrolled(x, y, horizontal, vertical);
    int pageSize = rows();
    if (x < eventX()) {
      playerPage =
          Math.clamp(
              playerPage - (int) Math.signum(vertical),
              0,
              Math.max(0, (visiblePlayers().size() - 1) / pageSize));
    } else {
      eventPage =
          Math.clamp(
              eventPage - (int) Math.signum(vertical),
              0,
              Math.max(0, (visibleSelectors().size() - 1) / pageSize));
    }
    rebuildWidgets();
    return true;
  }

  private int trackHeight() {
    return Math.max(8, rows() * 20 - 2);
  }

  private int trackX(boolean playersSide) {
    return playersSide ? left() + playerWidth() - 5 : eventX() + eventWidth() - 5;
  }

  private int maxPage(boolean playersSide) {
    return Math.max(0, ((playersSide ? visiblePlayers() : visibleSelectors()).size() - 1) / rows());
  }

  private void dragTo(double y, boolean playersSide) {
    int max = maxPage(playersSide);
    if (max == 0) return;
    int h = trackHeight(), thumb = Math.min(h, Math.max(14, h / (max + 1)));
    int next =
        Math.clamp(
            (int) Math.round((y - rowTop() - thumb / 2.0) * max / Math.max(1, h - thumb)), 0, max);
    if (playersSide && playerPage != next) {
      playerPage = next;
      rebuildWidgets();
    } else if (!playersSide && eventPage != next) {
      eventPage = next;
      rebuildWidgets();
    }
  }

  @Override
  public boolean mouseClicked(
      net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
    if (event.button() == 0 && event.y() >= rowTop() && event.y() < rowTop() + trackHeight()) {
      for (boolean playersSide : new boolean[] {true, false}) {
        int x = trackX(playersSide);
        if (maxPage(playersSide) > 0 && event.x() >= x && event.x() < x + 5) {
          draggingPlayers = playersSide;
          draggingEvents = !playersSide;
          dragTo(event.y(), playersSide);
          return true;
        }
      }
    }
    return super.mouseClicked(event, doubleClick);
  }

  @Override
  public boolean mouseDragged(
      net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
    if (event.button() == 0 && (draggingPlayers || draggingEvents)) {
      dragTo(event.y(), draggingPlayers);
      return true;
    }
    return super.mouseDragged(event, dx, dy);
  }

  @Override
  public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
    if (draggingPlayers || draggingEvents) {
      draggingPlayers = draggingEvents = false;
      return true;
    }
    return super.mouseReleased(event);
  }

  private void createReport() {
    var sourceFiles =
        CoreTraceClient.service.csvFiles().stream()
            .filter(dev.coreviewer.storage.CsvLibrary.FileEntry::selected)
            .map(dev.coreviewer.storage.CsvLibrary.FileEntry::name)
            .toList();
    if (sourceFiles.isEmpty()) {
      status = "Select investigation files first.";
      return;
    }
    busy = true;
    statusExpiresAt = 0;
    status = "Generating PDF and resolving available player skins...";
    rebuildWidgets();
    var names = List.copyOf(chosenPlayers);
    var keys = Set.copyOf(chosenSelectors);
    var selectedCharts = EnumSet.copyOf(charts);
    var selectedSort = sortMetric;
    boolean selectedDescending = descending;
    var selectedBreakdown = breakdown;
    var materialIcons = ReportMaterialIcons.capture(
        snapshot.stream().filter(event -> keys.contains(ReportData.selector(event)))
            .map(ReportData::material).distinct().toList());
    var actors = Map.copyOf(actorIds);
    Path folder = CoreTraceClient.directory.resolve("reports");
    CoreTraceClient.service.reportSources()
        .thenApplyAsync(
            sourceSnapshots -> {
              try {
                var data = ReportData.build(snapshot, names, keys).orderBy(selectedSort, selectedDescending);
                var sections = ReportBreakdown.plan(sourceSnapshots, data, keys, selectedBreakdown);
                return ReportPdfWriter.write(folder, data, sourceFiles, selectedCharts, actors, sections, materialIcons);
              } catch (Exception ex) {
                throw new java.util.concurrent.CompletionException(ex);
              }
            })
        .whenComplete(
            (path, error) ->
                Minecraft.getInstance()
                    .execute(
                        () -> {
                          busy = false;
                          status =
                              error == null
                                  ? "Saved: " + path
                                  : "PDF failed: "
                                      + (error.getCause() == null
                                          ? error.getMessage()
                                          : error.getCause().getMessage());
                          statusExpiresAt = error == null
                              ? System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30)
                              : 0;
                          CoreTraceClient.tell(status);
                          if (minecraft.gui.screen() == this) rebuildWidgets();
                        }));
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
    super.extractRenderState(g, mx, my, dt);
    g.centeredText(font, title, width / 2, 9, 0xFFFFFFFF);
    int fileCount =
        (int)
            CoreTraceClient.service.csvFiles().stream()
                .filter(dev.coreviewer.storage.CsvLibrary.FileEntry::selected)
                .count();
    String summary =
        fileCount
            + " selected files | "
            + chosenPlayers.size()
            + " players | "
            + chosenSelectors.size()
            + " materials";
    boolean notice = busy || !status.equals("Choose players, materials and charts.");
    g.centeredText(
        font,
        font.plainSubstrByWidth(notice ? status : summary, width - 40),
        width / 2,
        29,
        notice ? 0xFFFFD166 : 0xFF9CB7C9);
    drawScrollbar(g, left() + playerWidth() - 5, playerPage, visiblePlayers().size());
    drawScrollbar(g, eventX() + eventWidth() - 5, eventPage, visibleSelectors().size());
    var shown = visiblePlayers();
    for (int i = 0; i < rows(); i++) {
      int index = playerPage * rows() + i;
      if (index >= shown.size()) break;
      String name = shown.get(index);
      String label = (chosenPlayers.contains(name) ? "✓  " : "□  ")
          + font.plainSubstrByWidth(name, playerWidth() - 24);
      int hx = Math.max(left() + 3,
          left() + (playerWidth() - 7 - font.width(label)) / 2 - 19);
      int hy = rowTop() + i * 20 + 1;
      Identifier texture = headTextures.get(name);
      // The identifier overload takes x0,y0,x1,y1,u0,u1,v0,v1, not width/height.
      if (texture != null) g.blit(texture, hx, hy, hx + 16, hy + 16, 0f, 1f, 0f, 1f);
      else {
        g.fill(hx, hy, hx + 16, hy + 16, 0xFF168C91);
        g.centeredText(font, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT),
            hx + 8, hy + 3, 0xFFFFFFFF);
      }
    }
  }

  @Override
  public void removed() {
    if (minecraft != null)
      for (var id : headTextures.values()) minecraft.getTextureManager().release(id);
    headTextures.clear();
    headsRegistered = false;
    super.removed();
  }

  private void drawScrollbar(GuiGraphicsExtractor g, int x, int page, int count) {
    int max = Math.max(0, (count - 1) / rows());
    if (max == 0) return;
    int h = Math.max(8, rows() * 20 - 2), thumb = Math.max(14, h / (max + 1));
    int y = rowTop(), pos = (h - thumb) * page / max;
    g.fill(x, y, x + 4, y + h, 0xFF354B60);
    g.fill(x, y + pos, x + 4, y + pos + thumb, 0xFF8FE0D5);
  }

  @Override
  public void onClose() {
    minecraft.gui.setScreen(parent);
  }
}
