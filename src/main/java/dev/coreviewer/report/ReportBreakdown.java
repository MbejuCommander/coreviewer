package dev.coreviewer.report;

import dev.coreviewer.model.*;
import dev.coreviewer.storage.CsvLibrary;
import java.util.*;

/** Plans independent report sections while keeping source and worksheet boundaries explicit. */
public final class ReportBreakdown {
  private ReportBreakdown() {}

  public record Options(Set<String> details, boolean byFile, boolean bySheet) {
    public Options {
      details = Set.copyOf(details);
    }
  }

  public record Section(String title, String origin, List<CoreTraceEvent> events, String detail) {
    public boolean detailed() {
      return detail != null;
    }
  }

  public record PlayerValue(String name, long value, int unknown) {}

  public record Measures(
      List<PlayerValue> players,
      long total,
      double meanAll,
      double meanActive,
      double median,
      long maximum,
      int unknownActions,
      String unit) {}

  public static String detailKey(CoreTraceEvent event) {
    return event.type().name() + "|" + ReportData.material(event);
  }

  public static String detailLabel(String key) {
    String[] parts = key.split("\\|", 2);
    return parts[0].replace('_', ' ') + " / "
        + (parts.length > 1 ? parts[1].replace("minecraft:", "").replace('_', ' ') : "");
  }

  public static List<Section> plan(
      List<CsvLibrary.SourceFile> sources,
      ReportData base,
      Set<String> selectedSelectors,
      Options options) {
    var sections = new ArrayList<Section>();
    List<CoreTraceEvent> global = base.events();
    var selectedDetails = options.details().stream().sorted().toList();
    if (!options.byFile() && !options.bySheet()) {
      appendDetails(sections, "All selected files", global, selectedDetails);
      if (sections.size() > 300)
        throw new IllegalArgumentException("Too many report sections. Select fewer action/material pairs.");
      return List.copyOf(sections);
    }

    var groups = new LinkedHashMap<String, List<CsvLibrary.SourceFile>>();
    for (var source : sources) groups.computeIfAbsent(fileGroupKey(source), ignored -> new ArrayList<>()).add(source);
    Set<String> players = new HashSet<>();
    for (var row : base.players()) players.add(row.name());
    for (var group : groups.entrySet()) {
      String fileName = fileLabel(group.getValue().getFirst());
      var fileEvents = new ArrayList<CoreTraceEvent>();
      for (var source : group.getValue()) fileEvents.addAll(source.events());
      var included = selected(fileEvents, players, selectedSelectors);
      sections.add(new Section(fileName, fileName, included, null));
      if (options.bySheet() && group.getValue().stream().anyMatch(ReportBreakdown::isWorkbookSheet)) {
        for (var source : group.getValue()) {
          String origin = fileName + " / " + sheetLabel(source);
          var sheetEvents = selected(source.events(), players, selectedSelectors);
          sections.add(new Section(sheetLabel(source), origin, sheetEvents, null));
          appendDetails(sections, origin, sheetEvents, selectedDetails);
        }
      } else {
        appendDetails(sections, fileName, included, selectedDetails);
      }
    }
    if (sections.size() > 300)
      throw new IllegalArgumentException("Too many report sections. Select fewer action/material pairs.");
    return List.copyOf(sections);
  }

  private static void appendDetails(
      List<Section> sections,
      String origin,
      List<CoreTraceEvent> events,
      List<String> selectedDetails) {
    if (selectedDetails.isEmpty()) return;
    var byKey = new HashMap<String, List<CoreTraceEvent>>();
    for (var event : events)
      byKey.computeIfAbsent(detailKey(event), ignored -> new ArrayList<>()).add(event);
    for (String key : selectedDetails) {
      var matching = byKey.get(key);
      if (matching != null && !matching.isEmpty())
        sections.add(new Section(detailLabel(key), origin, List.copyOf(matching), key));
    }
  }

  private static List<CoreTraceEvent> selected(
      List<CoreTraceEvent> events, Set<String> players, Set<String> selectors) {
    var ids = new HashSet<UUID>();
    return events.stream()
        .filter(event -> players.contains(event.context().actor()))
        .filter(event -> selectors.contains(ReportData.selector(event)))
        .filter(event -> ids.add(event.context().id()))
        .sorted(Comparator.comparingLong(event -> event.context().timestamp()))
        .toList();
  }

  public static Measures measure(Section section, List<String> orderedPlayers) {
    var counts = new HashMap<String, Long>();
    var unknown = new HashMap<String, Integer>();
    for (String name : orderedPlayers) {
      counts.put(name, 0L);
      unknown.put(name, 0);
    }
    boolean quantities = false;
    for (var event : section.events()) {
      String actor = event.context().actor();
      if (!counts.containsKey(actor)) continue;
      int amount =
          event instanceof ItemEvent item ? item.quantity()
              : event instanceof ContainerEvent container ? container.quantity() : 1;
      if (event instanceof ItemEvent || event instanceof ContainerEvent) {
        quantities = true;
        if (amount == 0) {
          unknown.merge(actor, 1, Integer::sum);
          continue;
        }
      }
      counts.merge(actor, (long) amount, Long::sum);
    }
    var rows = new ArrayList<PlayerValue>();
    for (String name : orderedPlayers) rows.add(new PlayerValue(name, counts.get(name), unknown.get(name)));
    long total = rows.stream().mapToLong(PlayerValue::value).sum();
    long active = rows.stream().filter(row -> row.value() > 0).count();
    var sorted = rows.stream().mapToLong(PlayerValue::value).sorted().toArray();
    double median = sorted.length == 0 ? 0
        : sorted.length % 2 == 1 ? sorted[sorted.length / 2]
            : (sorted[sorted.length / 2 - 1] / 2.0 + sorted[sorted.length / 2] / 2.0);
    return new Measures(
        List.copyOf(rows),
        total,
        rows.isEmpty() ? 0 : (double) total / rows.size(),
        active == 0 ? 0 : (double) total / active,
        median,
        sorted.length == 0 ? 0 : sorted[sorted.length - 1],
        rows.stream().mapToInt(PlayerValue::unknown).sum(),
        quantities ? "known items" : "events");
  }

  private static boolean isWorkbookSheet(CsvLibrary.SourceFile source) {
    return !source.workbook().isBlank() || source.name().split("/", 2)[0].startsWith("import-");
  }

  private static String fileGroupKey(CsvLibrary.SourceFile source) {
    String[] parts = source.name().split("/", 2);
    return isWorkbookSheet(source) && parts.length > 1 ? parts[0] : source.name();
  }

  public static String fileLabel(CsvLibrary.SourceFile source) {
    if (!source.workbook().isBlank()) return source.workbook();
    String[] parts = source.name().split("/", 2);
    if (parts.length > 1 && parts[0].startsWith("import-")) return parts[0].substring(7) + ".xlsx";
    return source.name();
  }

  public static String sheetLabel(CsvLibrary.SourceFile source) {
    if (!source.sheet().isBlank()) return source.sheet();
    String leaf = source.name().substring(source.name().lastIndexOf('/') + 1);
    return leaf.replaceFirst("(?i)\\.csv$", "").replaceFirst("^\\d{2}-", "");
  }
}
