package dev.coreviewer.report;

import dev.coreviewer.model.*;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;

/** Builds a compact, vector-chart report without rendering on Minecraft's main thread. */
public final class ReportPdfWriter {
  private static final Color INK = rgb(0x172333),
      MUTED = rgb(0x536477),
      TEAL = rgb(0x168C91),
      RED = rgb(0xD86063),
      GREEN = rgb(0x2C9C63),
      BLUE = rgb(0x254B72),
      AMBER = rgb(0xD89427),
      PALE = rgb(0xF4F7FA),
      LINE = rgb(0xDCE4EB);
  private static final float WIDTH = PDRectangle.A4.getWidth(), HEIGHT = PDRectangle.A4.getHeight();
  private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
  private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'UTC'")
          .withLocale(Locale.ENGLISH)
          .withZone(ZoneOffset.UTC);

  private ReportPdfWriter() {}

  public static Path write(
      Path folder,
      ReportData data,
      List<String> sources,
      EnumSet<ReportChart> charts,
      Map<String, UUID> actorIds)
      throws IOException {
    return write(folder, data, sources, charts, actorIds, List.of());
  }

  public static Path write(
      Path folder,
      ReportData data,
      List<String> sources,
      EnumSet<ReportChart> charts,
      Map<String, UUID> actorIds,
      List<ReportBreakdown.Section> sections)
      throws IOException {
    return write(folder, data, sources, charts, actorIds, sections, Map.of());
  }

  public static Path write(
      Path folder,
      ReportData data,
      List<String> sources,
      EnumSet<ReportChart> charts,
      Map<String, UUID> actorIds,
      List<ReportBreakdown.Section> sections,
      Map<String, byte[]> materialIcons)
      throws IOException {
    if (data.players().isEmpty()) throw new IllegalArgumentException("Select at least one player.");
    if (charts.isEmpty()) throw new IllegalArgumentException("Select at least one chart.");
    Files.createDirectories(folder);
    var heads =
        PlayerHeadImages.resolve(data.players().stream().map(ReportData.Player::name).toList(), actorIds);
    String stamp =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now());
    Path target =
        folder.resolve(
            "investigation-" + stamp + "-" + UUID.randomUUID().toString().substring(0, 8) + ".pdf");
    Path temporary = Files.createTempFile(folder, ".report-", ".tmp");
    try (var document = new PDDocument()) {
      overview(document, data, sources, charts);
      boolean sourcesShown = players(document, data, heads, sources);
      boolean hasBlocks = data.players().stream().anyMatch(row -> row.breaks() + row.places() > 0);
      if (hasBlocks
          && charts.contains(ReportChart.PLAYER_BARS)
          && charts.contains(ReportChart.STACKED_CATEGORIES)
          && data.players().size() <= 8) combinedPlayers(document, data, heads, materialIcons);
      else {
        if (hasBlocks && charts.contains(ReportChart.PLAYER_BARS))
          playerBars(document, data, heads, materialIcons);
        if (!data.events().isEmpty() && charts.contains(ReportChart.STACKED_CATEGORIES))
          stacked(document, data, heads);
      }
      if (!data.events().isEmpty()
          && charts.contains(ReportChart.DONUT) && charts.contains(ReportChart.TIMELINE))
        combinedOverall(document, data);
      else {
        if (!data.events().isEmpty() && charts.contains(ReportChart.DONUT)) donut(document, data);
        if (charts.contains(ReportChart.TIMELINE)) timeline(document, data);
      }
      if (!sourcesShown) sourcePages(document, sources, data);
      for (var section : sections) {
        if (section.detailed()) {
          var names = data.players().stream().map(ReportData.Player::name).toList();
          var measures = ReportBreakdown.measure(section, names);
          if (charts.contains(ReportChart.PLAYER_BARS)
              || charts.contains(ReportChart.STACKED_CATEGORIES))
            detailBars(document, section, measures, heads, materialIcons);
          if (charts.contains(ReportChart.DONUT) && measures.total() > 0)
            detailDonut(document, section, measures, heads, names, materialIcons);
          if (charts.contains(ReportChart.TIMELINE))
            timeline(document, ReportData.build(section.events(), names, Set.copyOf(data.selectedMaterials())));
        } else {
          var names = data.players().stream().map(ReportData.Player::name).toList();
          var scopeData =
              ReportData.build(section.events(), names, Set.copyOf(data.selectedMaterials()))
                  .orderLike(data);
          sourceSummary(document, section, scopeData, heads, charts, materialIcons);
        }
      }
      document.save(temporary.toFile());
    } catch (Exception ex) {
      Files.deleteIfExists(temporary);
      if (ex instanceof IOException io) throw io;
      throw new IOException("Could not create report", ex);
    }
    try {
      return Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException ex) {
      return Files.move(temporary, target);
    }
  }

  private static void overview(
      PDDocument doc, ReportData data, List<String> sources, EnumSet<ReportChart> charts)
      throws IOException {
    try (var p = new Page(doc, "Evidence overview")) {
      p.text(40, 91, "Summary from selected investigation files", 10, MUTED, false);
      p.text(40, 108, "Player order: " + data.playerOrder(), 8, MUTED, false);
      p.box(40, 128, 515, 84, Color.WHITE, LINE);
      p.text(55, 145, "REPORT SCOPE", 8, MUTED, true);
      p.text(
          55,
          166,
          data.players().size() + " selected players | " + sources.size() + " selected files",
          12,
          INK,
          true);
      String range =
          data.firstTimestamp() == 0
              ? "No reliable timestamps"
              : TIME.format(Instant.ofEpochMilli(data.firstTimestamp()))
                  + " - "
                  + TIME.format(Instant.ofEpochMilli(data.lastTimestamp()));
      p.text(55, 190, range, 9, MUTED, false);
      long blocksBroken = data.players().stream().mapToLong(ReportData.Player::breaks).sum();
      long blocksPlaced = data.players().stream().mapToLong(ReportData.Player::places).sum();
      long item = data.players().stream().mapToLong(ReportData.Player::items).sum();
      long container = data.players().stream().mapToLong(ReportData.Player::containers).sum();
      card(p, 40, "BLOCKS BROKEN", blocksBroken, RED);
      card(p, 171, "BLOCKS PLACED", blocksPlaced, GREEN);
      card(p, 302, "KNOWN ITEM QTY", item, TEAL);
      card(p, 433, "KNOWN BOX QTY", container, BLUE);
      p.text(40, 345, "Most frequent materials (actions)", 14, INK, true);
      var materials =
          data.materials().entrySet().stream()
              .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
              .limit(8)
              .toList();
      long max = materials.stream().mapToLong(Map.Entry::getValue).max().orElse(1);
      for (int i = 0; i < materials.size(); i++) {
        var entry = materials.get(i);
        float y = 380 + i * 36;
        p.text(42, y, shortName(entry.getKey(), 28), 9, INK, false);
        p.box(214, y, 270, 12, PALE, null);
        p.box(
            214, y, Math.max(2, 270f * entry.getValue() / max), 12, i % 2 == 0 ? TEAL : BLUE, null);
        p.text(496, y, entry.getValue().toString(), 9, INK, true);
      }
      p.box(40, 702, 515, 60, rgb(0xEAF4F4), null);
      p.text(54, 714, "How to read this report", 10, TEAL, true);
      p.text(
          54,
          734,
          "Charts: "
              + charts.stream()
                  .map(ReportChart::label)
                  .reduce((a, b) -> a + ", " + b)
                  .orElse("none"),
          8,
          MUTED,
          false);
      p.text(
          54,
          750,
          "Blocks count actions; items and boxes count known units. Unknown amounts are not estimated.",
          8,
          MUTED,
          false);
    }
  }

  private static void card(Page p, float x, String title, long value, Color color)
      throws IOException {
    p.box(x, 230, 122, 82, Color.WHITE, LINE);
    p.box(x, 230, 122, 5, color, null);
    p.text(x + 10, 248, title, 7.5f, MUTED, true);
    p.text(x + 10, 270, Long.toString(value), 24, color, true);
  }

  private static boolean players(
      PDDocument doc, ReportData data, Map<String, byte[]> heads, List<String> sources)
      throws IOException {
    var rows = data.players();
    int perPage = 18;
    boolean sourcesShown = false;
    String[] labels = {"BREAK", "PLACE", "ITEM QTY", "BOX QTY", "KILL", "SESSION", "CLICK"};
    long[] totals = new long[labels.length];
    boolean[] present = new boolean[labels.length];
    for (var event : data.events()) {
      int column = switch (event.type()) {
        case BLOCK_BREAK -> 0;
        case BLOCK_PLACE -> 1;
        case ITEM_ADD, ITEM_REMOVE -> 2;
        case CONTAINER_ADD, CONTAINER_REMOVE -> 3;
        case PLAYER_KILL, MOB_KILL -> 4;
        case SESSION_LOGIN, SESSION_LOGOUT -> 5;
        case BLOCK_CLICK -> 6;
      };
      present[column] = true;
    }
    for (var row : rows) {
      long[] values = {row.breaks(), row.places(), row.items(), row.containers(),
          row.kills(), row.sessions(), row.clicks()};
      for (int i = 0; i < totals.length; i++) totals[i] += values[i];
    }
    var visibleColumns = new ArrayList<Integer>();
    for (int i = 0; i < totals.length; i++) if (present[i]) visibleColumns.add(i);
    for (int start = 0; start < rows.size(); start += perPage) {
      try (var p = new Page(doc, "Player summary")) {
        p.text(40, 92, "Every selected player appears, including zero matches.", 9, MUTED, false);
        p.box(40, 122, 515, 28, BLUE, null);
        p.text(50, 130, "PLAYER", 7, Color.WHITE, true);
        int columnWidth = visibleColumns.isEmpty() ? 0 : 330 / visibleColumns.size();
        for (int i = 0; i < visibleColumns.size(); i++)
          p.text(223 + i * columnWidth, 130, labels[visibleColumns.get(i)], 6.6f, Color.WHITE, true);
        for (int i = start; i < Math.min(rows.size(), start + perPage); i++) {
          var row = rows.get(i);
          float y = 150 + (i - start) * 33;
          p.box(40, y, 515, 33, i % 2 == 0 ? Color.WHITE : PALE, null);
          p.head(50, y + 7, row.name(), heads.get(row.name()));
          p.text(75, y + 10, shortPlayerName(row.name(), 22), 8.4f, INK, true);
          long[] values = {
            row.breaks(),
            row.places(),
            row.items(),
            row.containers(),
            row.kills(),
            row.sessions(),
            row.clicks()
          };
          for (int j = 0; j < visibleColumns.size(); j++) {
            int column = visibleColumns.get(j);
            p.text(
                223 + j * columnWidth,
                y + 10,
                Long.toString(values[column]),
                8.5f,
                column == 0 ? RED : column == 1 ? GREEN : INK,
                column < 2);
          }
          if (visibleColumns.isEmpty()) p.text(223, y + 10, "No matching actions", 8, MUTED, false);
          p.line(40, y + 33, 555, y + 33, LINE);
        }
        int rowCount = Math.min(perPage, rows.size() - start);
        float end = 150 + rowCount * 33;
        int filters = data.allMaterials() ? 1 : data.selectedMaterials().size();
        if (start + rowCount == rows.size()
            && !sources.isEmpty()
            && sources.size() + filters <= 9
            && end + 70 + (sources.size() + filters) * 20 < 748) {
          p.text(40, end + 20, "Selected source files", 12, INK, true);
          for (int i = 0; i < sources.size(); i++)
            p.text(
                42,
                end + 45 + i * 20,
                (i + 1) + ".  " + shortName(sources.get(i), 84),
                8.2f,
                MUTED,
                false);
          float filtersTop = end + 45 + sources.size() * 20;
          p.text(40, filtersTop + 5, "Material filters", 11, INK, true);
          if (data.allMaterials())
            p.text(
                42,
                filtersTop + 25,
                "All available materials (" + data.selectedMaterials().size() + ")",
                8.2f,
                MUTED,
                false);
          else
            for (int i = 0; i < data.selectedMaterials().size(); i++)
              p.text(
                  42,
                  filtersTop + 25 + i * 20,
                  (i + 1) + ".  " + shortName(data.selectedMaterials().get(i), 84),
                  8.2f,
                  MUTED,
                  false);
          sourcesShown = true;
        }
        p.text(
            40,
            757,
            "Events in selected categories: "
                + data.events().size()
                + " | Unknown item/container amounts: "
                + data.unknownAmounts(),
            8,
            MUTED,
            false);
      }
    }
    return sourcesShown;
  }

  private static void combinedPlayers(PDDocument doc, ReportData data, Map<String, byte[]> heads,
      Map<String, byte[]> materialIcons)
      throws IOException {
    var actions = actionCounts(data);
    String blockMaterial = singleBlockMaterial(data);
    try (var p = new Page(doc, "Player comparisons")) {
      p.text(40, 96, "Block breaks (red) and placements (green)", 11, INK, true);
      long maxBlocks =
          data.players().stream().mapToLong(r -> Math.max(r.breaks(), r.places())).max().orElse(1);
      if (maxBlocks == 0) maxBlocks = 1;
      for (int i = 0; i < data.players().size(); i++) {
        var row = data.players().get(i);
        float y = 127 + i * 32;
        p.head(42, y + 4, row.name(), heads.get(row.name()));
        p.text(68, y + 6, shortPlayerName(row.name(), 19), 8.2f, INK, true);
        if (blockMaterial != null) {
          p.materialIcon(188, y, blockMaterial, materialIcons.get(blockMaterial), RED);
          p.materialIcon(188, y + 16, blockMaterial, materialIcons.get(blockMaterial), GREEN);
        }
        p.box(210, y + 3, 270, 9, PALE, null);
        if (row.breaks() > 0) p.box(210, y + 3, 270f * row.breaks() / maxBlocks, 9, RED, null);
        p.text(490, y + 1, Long.toString(row.breaks()), 8, RED, true);
        p.box(210, y + 17, 270, 9, PALE, null);
        if (row.places() > 0) p.box(210, y + 17, 270f * row.places() / maxBlocks, 9, GREEN, null);
        p.text(490, y + 15, Long.toString(row.places()), 8, GREEN, true);
      }
      p.text(40, 412, "Event categories (stacked)", 11, INK, true);
      Color[] palette = {RED, GREEN, TEAL, BLUE, AMBER, rgb(0x776AA9), rgb(0x8B9298)};
      long maxEvents = data.players().stream().mapToLong(ReportData.Player::events).max().orElse(1);
      if (maxEvents == 0) maxEvents = 1;
      for (int i = 0; i < data.players().size(); i++) {
        var row = data.players().get(i);
        float y = 446 + i * 30;
        p.head(42, y, row.name(), heads.get(row.name()));
        p.text(68, y + 2, shortPlayerName(row.name(), 19), 8.2f, INK, true);
        p.box(210, y + 3, 270, 13, PALE, null);
        float cursor = 210;
        long[] values = actions.getOrDefault(row.name(), new long[7]);
        for (int j = 0; j < values.length; j++) {
          float segment = 270f * values[j] / maxEvents;
          if (segment > 0) p.box(cursor, y + 3, segment, 13, palette[j], null);
          cursor += segment;
        }
        p.text(490, y + 2, Long.toString(row.events()), 8, INK, true);
      }
      String[] legend = {"Break", "Place", "Item", "Container", "Kill", "Session", "Click"};
      for (int i = 0; i < legend.length; i++) {
        float x = 42 + i * 73;
        p.box(x, 745, 8, 8, palette[i], null);
        p.text(x + 11, 743, legend[i], 7, MUTED, false);
      }
    }
  }

  private static void combinedOverall(PDDocument doc, ReportData data) throws IOException {
    try (var p = new Page(doc, "Distribution and timeline")) {
      p.text(40, 97, "Category distribution", 12, INK, true);
      EventStatistics.Category[] categories = {
        EventStatistics.Category.BLOCKS,
        EventStatistics.Category.ITEMS,
        EventStatistics.Category.CONTAINERS,
        EventStatistics.Category.KILLS,
        EventStatistics.Category.SESSIONS,
        EventStatistics.Category.CLICKS
      };
      Color[] colors = {RED, TEAL, BLUE, AMBER, GREEN, rgb(0x776AA9)};
      long total = data.events().size();
      float angle = 90;
      for (int i = 0; i < categories.length; i++) {
        long value = data.categories().getOrDefault(categories[i], 0L);
        if (value == 0 || total == 0) continue;
        float sweep = 360f * value / total;
        p.arc(175, 287, 78, angle, -sweep, colors[i], 30);
        float middle = angle - sweep / 2;
        double radians = Math.toRadians(middle);
        long percentage = Math.round(100.0 * value / total);
        if (percentage >= 7) {
          String label = percentage + "%";
          p.text(175 + (float) Math.cos(radians) * 78 - label.length() * 2.3f,
              287 - (float) Math.sin(radians) * 78 - 5, label, 7.5f, Color.WHITE, true);
        }
        float ax = 175 + (float) Math.cos(radians) * 96;
        float ay = 287 - (float) Math.sin(radians) * 96;
        p.line(ax, ay, ax + (ax < 175 ? -8 : 8), ay + (ay < 287 ? -6 : 6), colors[i]);
        angle -= sweep;
      }
      p.text(163, 275, Long.toString(total), 20, INK, true);
      for (int i = 0; i < categories.length; i++) {
        float y = 170 + i * 30;
        p.box(330, y + 2, 11, 11, colors[i], null);
        p.text(351, y, categories[i].name(), 8.7f, INK, false);
        p.text(
            458,
            y,
            Long.toString(data.categories().getOrDefault(categories[i], 0L))
                + " (" + Math.round(100.0 * data.categories().getOrDefault(categories[i], 0L) / total) + "%)",
            8.7f,
            MUTED,
            true);
      }
      p.line(40, 418, 555, 418, LINE);
      p.text(40, 435, "Activity timeline (UTC)", 12, INK, true);
      long first = data.firstTimestamp(), last = data.lastTimestamp();
      if (first == 0) {
        p.text(40, 490, "No reliable timestamps in selected events.", 10, MUTED, false);
        return;
      }
      long[] bins = new long[12];
      for (var event : data.events())
        if (event.context().timestamp() > 0) {
          long span = Math.max(1, last - first);
          int at = (int) Math.min(11, (event.context().timestamp() - first) * 12.0 / span);
          bins[at]++;
        }
      long peak = Math.max(1, Arrays.stream(bins).max().orElse(1));
      p.line(58, 685, 538, 685, LINE);
      for (int i = 0; i < bins.length; i++) {
        float x = 66 + i * 40, h = 180f * bins[i] / peak;
        p.box(x, 685 - h, 24, h, i % 2 == 0 ? TEAL : BLUE, null);
        p.text(x + 4, 693, Integer.toString(i + 1), 8, MUTED, false);
        if (bins[i] > 0) p.text(x + 4, 671 - h, Long.toString(bins[i]), 8, INK, true);
      }
      p.text(52, 730, TIME.format(Instant.ofEpochMilli(first)), 8, MUTED, false);
      p.text(326, 730, TIME.format(Instant.ofEpochMilli(last)), 8, MUTED, false);
    }
  }

  private static void playerBars(PDDocument doc, ReportData data, Map<String, byte[]> heads,
      Map<String, byte[]> materialIcons)
      throws IOException {
    var rows = data.players();
    String blockMaterial = singleBlockMaterial(data);
    for (int start = 0; start < rows.size(); start += 19) {
      try (var p = new Page(doc, "Player comparison | bars")) {
        p.text(40, 92, "Block breaks (red) and placements (green) by player", 9, MUTED, false);
        long max = rows.stream().mapToLong(r -> Math.max(r.breaks(), r.places())).max().orElse(1);
        if (max == 0) max = 1;
        for (int i = start; i < Math.min(rows.size(), start + 19); i++) {
          var row = rows.get(i);
          float y = 125 + (i - start) * 33;
          p.head(42, y + 4, row.name(), heads.get(row.name()));
          p.text(68, y + 6, shortPlayerName(row.name(), 20), 8.2f, INK, true);
          if (blockMaterial != null) {
            p.materialIcon(188, y, blockMaterial, materialIcons.get(blockMaterial), RED);
            p.materialIcon(188, y + 16, blockMaterial, materialIcons.get(blockMaterial), GREEN);
          }
          p.box(210, y + 3, 265, 9, PALE, null);
          if (row.breaks() > 0) p.box(210, y + 3, 265f * row.breaks() / max, 9, RED, null);
          p.text(484, y + 1, Long.toString(row.breaks()), 8, RED, true);
          p.box(210, y + 17, 265, 9, PALE, null);
          if (row.places() > 0) p.box(210, y + 17, 265f * row.places() / max, 9, GREEN, null);
          p.text(484, y + 15, Long.toString(row.places()), 8, GREEN, true);
        }
      }
    }
  }

  private static void stacked(PDDocument doc, ReportData data, Map<String, byte[]> heads)
      throws IOException {
    var actions = actionCounts(data);
    Color[] palette = {RED, GREEN, TEAL, BLUE, AMBER, rgb(0x776AA9), rgb(0x8B9298)};
    var rows = data.players();
    long max = rows.stream().mapToLong(ReportData.Player::events).max().orElse(1);
    if (max == 0) max = 1;
    for (int start = 0; start < rows.size(); start += 19) {
      try (var p = new Page(doc, "Player comparison | stacked")) {
        p.text(40, 92, "Selected event categories by player", 9, MUTED, false);
        for (int i = start; i < Math.min(rows.size(), start + 19); i++) {
          var row = rows.get(i);
          float y = 127 + (i - start) * 33;
          p.head(42, y, row.name(), heads.get(row.name()));
          p.text(68, y + 2, shortPlayerName(row.name(), 20), 8.2f, INK, true);
          p.box(210, y + 3, 270, 13, PALE, null);
          float cursor = 210;
          long[] v = actions.getOrDefault(row.name(), new long[7]);
          for (int j = 0; j < v.length; j++) {
            float width = 270f * v[j] / max;
            if (width > 0) p.box(cursor, y + 3, width, 13, palette[j], null);
            cursor += width;
          }
          p.text(493, y + 2, Long.toString(row.events()), 8, INK, true);
        }
        String[] legend = {"Break", "Place", "Item", "Container", "Kill", "Session", "Click"};
        for (int i = 0; i < legend.length; i++) {
          float x = 42 + i * 73;
          p.box(x, 765, 8, 8, palette[i], null);
          p.text(x + 11, 763, legend[i], 7, MUTED, false);
        }
      }
    }
  }

  private static Map<String, long[]> actionCounts(ReportData data) {
    var counts = new HashMap<String, long[]>();
    for (var event : data.events()) {
      var row = counts.computeIfAbsent(event.context().actor(), ignored -> new long[7]);
      switch (event.type()) {
        case BLOCK_BREAK -> row[0]++;
        case BLOCK_PLACE -> row[1]++;
        case ITEM_ADD, ITEM_REMOVE -> row[2]++;
        case CONTAINER_ADD, CONTAINER_REMOVE -> row[3]++;
        case PLAYER_KILL, MOB_KILL -> row[4]++;
        case SESSION_LOGIN, SESSION_LOGOUT -> row[5]++;
        case BLOCK_CLICK -> row[6]++;
      }
    }
    return counts;
  }

  private static void donut(PDDocument doc, ReportData data) throws IOException {
    try (var p = new Page(doc, "Category distribution | donut")) {
      p.text(40, 92, "Share of selected event types across all selected players", 9, MUTED, false);
      EventStatistics.Category[] categories = {
        EventStatistics.Category.BLOCKS,
        EventStatistics.Category.ITEMS,
        EventStatistics.Category.CONTAINERS,
        EventStatistics.Category.KILLS,
        EventStatistics.Category.SESSIONS,
        EventStatistics.Category.CLICKS
      };
      Color[] colors = {RED, TEAL, BLUE, AMBER, GREEN, rgb(0x776AA9)};
      long total = data.events().size();
      var segments = new ArrayList<DonutSegment>();
      for (int i = 0; i < categories.length; i++) {
        long value = data.categories().getOrDefault(categories[i], 0L);
        if (value > 0) segments.add(new DonutSegment(categories[i].name(), value, colors[i], null));
      }
      if (total > 0) labeledDonut(p, segments, total, "events", null);
      p.text(441, 137, "CATEGORY KEY", 7.5f, MUTED, true);
      for (int i = 0; i < segments.size(); i++) {
        var segment = segments.get(i);
        float y = 153 + i * 27;
        p.box(441, y, 3, 23, segment.color(), null);
        p.text(449, y + 1, segment.label(), 7, INK, true);
        p.text(449, y + 12,
            segment.value() + "  |  " + Math.round(100.0 * segment.value() / total) + "%",
            6.6f, MUTED, false);
      }
    }
  }

  private static void timeline(PDDocument doc, ReportData data) throws IOException {
    try (var p = new Page(doc, "Activity timeline")) {
      p.text(40, 92, "Recorded events grouped into 12 equal time periods (UTC)", 9, MUTED, false);
      long first = data.firstTimestamp(), last = data.lastTimestamp();
      if (first == 0) {
        p.text(40, 150, "No reliable timestamps in the selected evidence.", 11, MUTED, false);
        return;
      }
      long[] bins = new long[12];
      for (var e : data.events())
        if (e.context().timestamp() > 0) {
          long span = Math.max(1, last - first);
          int at = (int) Math.min(11, (e.context().timestamp() - first) * 12.0 / span);
          bins[at]++;
        }
      long peak = Arrays.stream(bins).max().orElse(1);
      if (peak == 0) peak = 1;
      p.line(58, 665, 538, 665, LINE);
      for (int i = 0; i < bins.length; i++) {
        float x = 66 + i * 40, h = 440f * bins[i] / peak;
        p.box(x, 665 - h, 24, h, i % 2 == 0 ? TEAL : BLUE, null);
        p.text(x + 3, 674, Integer.toString(i + 1), 8, MUTED, false);
        p.text(x + 2, 650 - h, Long.toString(bins[i]), 8, INK, true);
      }
      p.text(52, 721, TIME.format(Instant.ofEpochMilli(first)), 9, MUTED, false);
      p.text(330, 721, TIME.format(Instant.ofEpochMilli(last)), 9, MUTED, false);
    }
  }

  private static void sourceSummary(
      PDDocument doc,
      ReportBreakdown.Section section,
      ReportData data,
      Map<String, byte[]> heads,
      EnumSet<ReportChart> charts,
      Map<String, byte[]> materialIcons)
      throws IOException {
    var names = data.players().stream().map(ReportData.Player::name).toList();
    var counts = new HashMap<String, Long>();
    data.events().forEach(event -> counts.merge(event.context().actor(), 1L, Long::sum));
    int perPage = 15;
    for (int start = 0; start < names.size(); start += perPage) {
      try (var p = new Page(doc, "Source summary")) {
        p.text(40, 92, "File / sheet: " + shortName(section.origin(), 76), 10, MUTED, false);
        p.text(40, 113, data.events().size() + " recorded actions in selected materials", 11, INK, true);
        p.text(40, 141, "Player", 8, MUTED, true);
        p.text(470, 141, "ACTIONS", 8, MUTED, true);
        long peak = Math.max(1, counts.values().stream().mapToLong(Long::longValue).max().orElse(0));
        for (int i = start; i < Math.min(names.size(), start + perPage); i++) {
          String name = names.get(i);
          float y = 162 + (i - start) * 33;
          p.box(40, y, 515, 31, i % 2 == 0 ? Color.WHITE : PALE, null);
          p.head(47, y + 6, name, heads.get(name));
          p.text(72, y + 9, shortPlayerName(name, 20), 8.5f, INK, true);
          if (charts.contains(ReportChart.PLAYER_BARS)
              || charts.contains(ReportChart.STACKED_CATEGORIES)) {
            p.box(215, y + 10, 250, 10, PALE, null);
            long value = counts.getOrDefault(name, 0L);
            if (value > 0) p.box(215, y + 10, 250f * value / peak, 10, TEAL, null);
          }
          p.text(480, y + 8, Long.toString(counts.getOrDefault(name, 0L)), 9, INK, true);
        }
        p.text(40, 716, "This source overview counts actions; item quantities appear in detail pages.", 8, MUTED, false);
      }
    }
    if (charts.contains(ReportChart.DONUT) && !data.events().isEmpty()) {
      var rows = names.stream()
          .map(name -> new ReportBreakdown.PlayerValue(name, counts.getOrDefault(name, 0L), 0))
          .toList();
      detailDonut(doc, section, new ReportBreakdown.Measures(
          rows, data.events().size(), 0, 0, 0,
          counts.values().stream().mapToLong(Long::longValue).max().orElse(0), 0, "actions"),
          heads, names, materialIcons);
    }
    if (charts.contains(ReportChart.TIMELINE)) timeline(doc, data);
  }

  private static void detailBars(
      PDDocument doc,
      ReportBreakdown.Section section,
      ReportBreakdown.Measures measures,
      Map<String, byte[]> heads,
      Map<String, byte[]> materialIcons)
      throws IOException {
    var rows = measures.players();
    int perPage = 14;
    Color accent = actionColor(section.detail());
    for (int start = 0; start < rows.size(); start += perPage) {
      try (var p = new Page(doc, "Action / material breakdown")) {
        p.text(40, 91, "File / sheet: " + shortName(section.origin(), 76), 9, MUTED, false);
        p.box(40, 111, 515, 24, actionTint(accent), null);
        p.box(40, 111, 4, 24, accent, null);
        String material = section.detail().split("\\|", 2)[1];
        p.materialIcon(50, 114, material, materialIcons.get(material), accent);
        p.text(76, 116, shortName(section.title(), 62), 10, INK, true);
        String[] labels = {"TOTAL", "MEAN ALL", "MEAN ACTIVE", "MEDIAN", "MAXIMUM"};
        String[] values = {
          Long.toString(measures.total()),
          number(measures.meanAll()),
          number(measures.meanActive()),
          number(measures.median()),
          Long.toString(measures.maximum())
        };
        for (int i = 0; i < labels.length; i++) {
          float x = 40 + i * 104;
          p.box(x, 150, 99, 67, Color.WHITE, LINE);
          p.text(x + 8, 160, labels[i], 7, MUTED, true);
          p.text(x + 8, 178, values[i], 17, accent, true);
        }
        p.text(40, 228, "Total " + measures.unit() + "; averages include zero-value selected players as stated.", 8, MUTED, false);
        long scale = Math.max(1, measures.maximum());
        float meanX = 205 + (float) (270.0 * measures.meanAll() / scale);
        for (int i = start; i < Math.min(rows.size(), start + perPage); i++) {
          var row = rows.get(i);
          float y = 273 + (i - start) * 32;
          p.head(42, y + 1, row.name(), heads.get(row.name()));
          p.text(67, y + 4, shortPlayerName(row.name(), 19), 8, INK, true);
          p.box(205, y + 4, 270, 12, PALE, null);
          if (row.value() > 0) p.box(205, y + 4, 270f * row.value() / scale, 12, accent, null);
          p.text(486, y + 3, Long.toString(row.value()), 8.5f, INK, true);
        }
        p.dotted(meanX, 258, 735, TEAL);
        p.text(Math.min(meanX + 4, 486), 245, "Mean: " + number(measures.meanAll()), 8, TEAL, true);
        if (measures.unknownActions() > 0)
          p.text(40, 742, measures.unknownActions() + " actions have unknown quantities; they are not counted as zero.", 8, MUTED, false);
      }
    }
  }

  private static void detailDonut(
      PDDocument doc,
      ReportBreakdown.Section section,
      ReportBreakdown.Measures measures,
      Map<String, byte[]> heads,
      List<String> allNames,
      Map<String, byte[]> materialIcons)
      throws IOException {
    var positive = measures.players().stream()
        .filter(row -> row.value() > 0)
        .sorted(Comparator.comparingLong(ReportBreakdown.PlayerValue::value).reversed()
            .thenComparing(ReportBreakdown.PlayerValue::name, String.CASE_INSENSITIVE_ORDER))
        .toList();
    if (positive.isEmpty()) return;
    var individual = positive.subList(0, Math.min(12, positive.size()));
    long others = positive.stream().skip(12).mapToLong(ReportBreakdown.PlayerValue::value).sum();
    try (var p = new Page(doc, "Player distribution / donut")) {
      p.text(40, 92, "File / sheet: " + shortName(section.origin(), 76), 9, MUTED, false);
      p.text(40, 111, shortName(section.detailed() ? section.title() : "All selected actions", 70), 10, INK, true);
      var segments = new ArrayList<DonutSegment>();
      for (var row : individual)
        segments.add(new DonutSegment(row.name(), row.value(),
            playerColor(row.name(), allNames), heads.get(row.name())));
      if (others > 0)
        segments.add(new DonutSegment("Others (" + (positive.size() - 12) + ")",
            others, rgb(0x8B9298), null));
      String block = singleDonutBlock(section.events());
      labeledDonut(p, segments, measures.total(), measures.unit(),
          block == null ? null : materialIcons.get(block));
      p.text(441, 137, "PLAYER KEY", 7.5f, MUTED, true);
      for (int i = 0; i < individual.size(); i++) {
        var row = individual.get(i);
        float y = 153 + i * 27;
        p.box(441, y, 3, 23, playerColor(row.name(), allNames), null);
        p.head(448, y + 2, row.name(), heads.get(row.name()));
        p.text(469, y, shortPlayerName(row.name(), 18), 7, INK, true);
        p.text(469, y + 12,
            row.value() + "  |  " + Math.round(100.0 * row.value() / measures.total()) + "%",
            6.6f, MUTED, false);
      }
      if (others > 0) {
        float y = 153 + individual.size() * 27;
        p.box(441, y, 3, 23, rgb(0x8B9298), null);
        p.text(449, y + 1, "Others (" + (positive.size() - 12) + ")", 7, INK, true);
        p.text(449, y + 12,
            others + "  |  " + Math.round(100.0 * others / measures.total()) + "%",
            6.6f, MUTED, false);
      }
      p.text(40, 610, "Tiny slices show their percentage in the compact key.", 9, MUTED, false);
      p.text(40, 632, "Zero-value players appear in the complete ranking on the following page.", 9, MUTED, false);
    }
    for (int start = 0; start < measures.players().size(); start += 19) {
      try (var p = new Page(doc, "Complete player ranking")) {
        p.text(40, 92, shortName(section.origin() + " / " + section.title(), 77), 9, MUTED, false);
        p.text(40, 112, "All selected players, including zero values", 9, MUTED, false);
        var ranked = measures.players().stream()
            .sorted(Comparator.comparingLong(ReportBreakdown.PlayerValue::value).reversed()
                .thenComparing(ReportBreakdown.PlayerValue::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
        for (int i = start; i < Math.min(ranked.size(), start + 19); i++) {
          var row = ranked.get(i);
          float y = 140 + (i - start) * 31;
          p.box(40, y, 515, 29, i % 2 == 0 ? Color.WHITE : PALE, null);
          if (row.value() > 0) p.box(48, y + 5, 4, 19, playerColor(row.name(), allNames), null);
          p.head(58, y + 5, row.name(), heads.get(row.name()));
          p.text(84, y + 8, shortPlayerName(row.name(), 31), 9, INK, true);
          p.text(480, y + 8, Long.toString(row.value()), 9, INK, true);
        }
      }
    }
  }

  private record DonutSegment(String label, long value, Color color, byte[] head) {}

  private record DonutCallout(String label, Color color, float angle, float preferredY) {}

  private static String singleDonutBlock(List<CoreTraceEvent> events) {
    String block = null;
    for (var event : events) {
      if (!(event instanceof BlockEvent value)) return null;
      if (block == null) block = value.block();
      else if (!block.equals(value.block())) return null;
    }
    return block;
  }

  private static void labeledDonut(
      Page p, List<DonutSegment> segments, long total, String unit, byte[] centerTexture)
      throws IOException {
    final float cx = 211, cy = 348, radius = 99;
    float start = 90;
    var left = new ArrayList<DonutCallout>();
    var right = new ArrayList<DonutCallout>();
    for (var segment : segments) {
      float sweep = 360f * segment.value() / total;
      p.arc(cx, cy, radius, start, -sweep, segment.color(), 42);
      float middle = start - sweep / 2;
      double radians = Math.toRadians(middle);
      double share = 100.0 * segment.value() / total;
      if (share >= 4) {
        float px = cx + (float) Math.cos(radians) * radius;
        float py = cy - (float) Math.sin(radians) * radius;
        String percentage = Math.round(share) + "%";
        p.text(px - percentage.length() * 2.4f, py - 6, percentage, 8.5f,
            Color.WHITE, true);
      }
      var callout = new DonutCallout(segment.label(), segment.color(), middle,
          cy - (float) Math.sin(radians) * 125);
      (Math.cos(radians) < 0 ? left : right).add(callout);
      start -= sweep;
    }
    if (centerTexture != null) p.donutCenterTexture(cx, cy, 74, centerTexture);
    drawDonutCallouts(p, left, true, cx, cy);
    drawDonutCallouts(p, right, false, cx, cy);
    String center = Long.toString(total);
    p.text(cx - center.length() * 6.5f, cy - 20, center, 22, INK, true);
    p.text(cx - Math.min(27, unit.length() * 2.5f), cy + 12, unit, 8.5f,
        centerTexture == null ? MUTED : INK, centerTexture != null);
  }

  private static void drawDonutCallouts(
      Page p, List<DonutCallout> callouts, boolean left, float cx, float cy)
      throws IOException {
    callouts.sort(Comparator.comparingDouble(DonutCallout::preferredY));
    float previous = 144;
    for (var callout : callouts) {
      float y = Math.max(Math.clamp(callout.preferredY(), 151, 520), previous + 16);
      previous = y;
      double angle = Math.toRadians(callout.angle());
      float ax = cx + (float) Math.cos(angle) * 122;
      float ay = cy - (float) Math.sin(angle) * 122;
      String name = shortPlayerName(callout.label(), left ? 16 : 17);
      float lx = left ? 28 + BOLD.getStringWidth(name) * 7.2f / 1000 : 337;
      p.line(ax, ay, lx, y, callout.color());
      float dx = lx - ax, dy = y - ay;
      float length = Math.max(1, (float) Math.hypot(dx, dy));
      float ux = dx / length, uy = dy / length;
      p.line(ax, ay, ax + ux * 7 - uy * 3, ay + uy * 7 + ux * 3, callout.color());
      p.line(ax, ay, ax + ux * 7 + uy * 3, ay + uy * 7 - ux * 3, callout.color());
      p.text(left ? 24 : 341, y - 9, name, 7.2f, INK, true);
    }
  }

  private static Color actionColor(String key) {
    if (key.startsWith("BLOCK_BREAK") || key.startsWith("ITEM_REMOVE")
        || key.startsWith("CONTAINER_REMOVE") || key.startsWith("SESSION_LOGOUT")) return RED;
    if (key.startsWith("BLOCK_PLACE") || key.startsWith("ITEM_ADD")
        || key.startsWith("CONTAINER_ADD") || key.startsWith("SESSION_LOGIN")) return GREEN;
    return BLUE;
  }

  private static String singleBlockMaterial(ReportData data) {
    var blocks = data.selectedMaterials().stream()
        .filter(key -> key.startsWith("BLOCKS|"))
        .map(key -> key.substring("BLOCKS|".length()))
        .distinct().toList();
    return blocks.size() == 1 ? blocks.getFirst() : null;
  }

  private static Color actionTint(Color color) {
    return new Color((color.getRed() + 255 * 5) / 6, (color.getGreen() + 255 * 5) / 6,
        (color.getBlue() + 255 * 5) / 6);
  }

  private static Color playerColor(String name, List<String> allNames) {
    var alphabetic = allNames.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    int index = Math.max(0, alphabetic.indexOf(name));
    return Color.getHSBColor((float) ((index * 0.61803398875) % 1), .62f, .76f);
  }

  private static String number(double value) {
    return value == Math.floor(value) ? Long.toString((long) value)
        : String.format(Locale.ROOT, "%.1f", value);
  }

  private static void sourcePages(PDDocument doc, List<String> sources, ReportData data)
      throws IOException {
    var entries = new ArrayList<String>();
    sources.forEach(source -> entries.add("SOURCE  " + source));
    if (data.allMaterials())
      entries.add("FILTER  All available materials (" + data.selectedMaterials().size() + ")");
    else data.selectedMaterials().forEach(material -> entries.add("FILTER  " + material));
    int perPage = 32;
    if (entries.isEmpty()) return;
    for (int start = 0; start < entries.size(); start += perPage) {
      try (var p = new Page(doc, "Report selection")) {
        p.text(40, 92, "Selected investigation files and material filters", 9, MUTED, false);
        for (int i = start; i < Math.min(entries.size(), start + perPage); i++) {
          float y = 125 + (i - start) * 20;
          p.text(42, y, (i + 1) + ".  " + shortName(entries.get(i), 88), 8.2f, INK, false);
          p.line(40, y + 18, 555, y + 18, LINE);
        }
      }
    }
  }

  private static String shortName(String input, int max) {
    String s =
        input.replace("minecraft:", "").replace('_', ' ').replace("|", " / ").replace('\\', '/');
    return s.length() <= max ? s : s.substring(0, max - 3) + "...";
  }

  private static String shortPlayerName(String name, int max) {
    return name.length() <= max ? name : name.substring(0, max - 3) + "...";
  }

  private static Color rgb(int value) {
    return new Color(value);
  }

  private static final class Page implements AutoCloseable {
    private final PDDocument document;
    private final PDPageContentStream stream;

    Page(PDDocument document, String title) throws IOException {
      this.document = document;
      var page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      stream = new PDPageContentStream(document, page);
      box(0, 0, WIDTH, 9, TEAL, null);
      text(40, 22, "COREVIEWER", 10, TEAL, true);
      text(440, 22, "INVESTIGATION REPORT", 8, MUTED, true);
      line(40, 48, 555, 48, LINE);
      text(40, 64, title, 23, INK, true);
      line(40, 790, 555, 790, LINE);
      text(
          40,
          802,
          "Generated " + TIME.format(Instant.now()) + " | Review original evidence",
          8,
          MUTED,
          false);
      text(503, 802, "Page " + document.getNumberOfPages(), 8, MUTED, false);
    }

    void box(float x, float top, float width, float height, Color color, Color border)
        throws IOException {
      if (width <= 0 || height <= 0) return;
      stream.setNonStrokingColor(color);
      stream.addRect(x, HEIGHT - top - height, width, height);
      stream.fill();
      if (border != null) {
        stream.setStrokingColor(border);
        stream.addRect(x, HEIGHT - top - height, width, height);
        stream.stroke();
      }
    }

    void line(float x1, float y1, float x2, float y2, Color color) throws IOException {
      stream.setStrokingColor(color);
      stream.moveTo(x1, HEIGHT - y1);
      stream.lineTo(x2, HEIGHT - y2);
      stream.stroke();
    }

    void dotted(float x, float top, float bottom, Color color) throws IOException {
      stream.setStrokingColor(color);
      stream.setLineWidth(1.2f);
      for (float y = top; y < bottom; y += 8) {
        stream.moveTo(x, HEIGHT - y);
        stream.lineTo(x, HEIGHT - Math.min(y + 3, bottom));
      }
      stream.stroke();
      stream.setLineWidth(1);
    }

    void text(float x, float top, String value, float size, Color color, boolean bold)
        throws IOException {
      stream.beginText();
      stream.setFont(bold ? BOLD : REGULAR, size);
      stream.setNonStrokingColor(color);
      stream.newLineAtOffset(x, HEIGHT - top - size);
      stream.showText(ascii(value));
      stream.endText();
    }

    void head(float x, float top, String name, byte[] png) throws IOException {
      if (png != null) {
        try {
          var image = PDImageXObject.createFromByteArray(document, png, name);
          stream.drawImage(image, x, HEIGHT - top - 18, 18, 18);
          return;
        } catch (IOException ignored) {
          /* Use fallback avatar below. */
        }
      }
      box(x, top, 18, 18, TEAL, null);
      text(
          x + 4,
          top + 3,
          name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT),
          10,
          Color.WHITE,
          true);
    }

    void materialIcon(float x, float top, String material, byte[] png, Color action)
        throws IOException {
      box(x, top, 18, 18, actionTint(action), action);
      if (png != null) {
        try {
          var image = PDImageXObject.createFromByteArray(document, png, material);
          stream.drawImage(image, x + 2, HEIGHT - top - 16, 14, 14);
          return;
        } catch (IOException ignored) {
          // The action tint and initial still identify the record.
        }
      }
      String label = material.replace("minecraft:", "");
      text(x + 5, top + 3, label.isEmpty() ? "?" : label.substring(0, 1).toUpperCase(Locale.ROOT),
          9, action, true);
    }

    void donutCenterTexture(float cx, float cy, float radius, byte[] png) throws IOException {
      PDImageXObject image;
      try {
        image = PDImageXObject.createFromByteArray(document, png, "donut block texture");
      } catch (IOException ignored) {
        return;
      }
      float y = HEIGHT - cy, k = radius * .55228475f;
      stream.saveGraphicsState();
      try {
        stream.moveTo(cx + radius, y);
        stream.curveTo(cx + radius, y + k, cx + k, y + radius, cx, y + radius);
        stream.curveTo(cx - k, y + radius, cx - radius, y + k, cx - radius, y);
        stream.curveTo(cx - radius, y - k, cx - k, y - radius, cx, y - radius);
        stream.curveTo(cx + k, y - radius, cx + radius, y - k, cx + radius, y);
        stream.closePath();
        stream.clip();
        var transparency = new PDExtendedGraphicsState();
        transparency.setNonStrokingAlphaConstant(.70f);
        stream.setGraphicsStateParameters(transparency);
        stream.drawImage(image, cx - radius, y - radius, radius * 2, radius * 2);
      } finally {
        stream.restoreGraphicsState();
      }
    }

    void arc(
        float cx, float cy, float radius, float start, float sweep, Color color, float thickness)
        throws IOException {
      int steps = Math.max(2, (int) Math.ceil(Math.abs(sweep) / 5));
      stream.setStrokingColor(color);
      stream.setLineWidth(thickness);
      for (int i = 0; i <= steps; i++) {
        double angle = Math.toRadians(start + sweep * i / steps);
        float x = cx + (float) Math.cos(angle) * radius;
        float y = cy - (float) Math.sin(angle) * radius;
        if (i == 0) stream.moveTo(x, HEIGHT - y);
        else stream.lineTo(x, HEIGHT - y);
      }
      stream.stroke();
      stream.setLineWidth(1);
    }

    @Override
    public void close() throws IOException {
      stream.close();
    }
  }

  private static String ascii(String input) {
    var result = new StringBuilder();
    input
        .codePoints()
        .forEach(
            cp -> {
              if (cp >= 32 && cp <= 126) result.appendCodePoint(cp);
              else result.append(String.format("\\u%04X", cp));
            });
    return result.toString();
  }
}
