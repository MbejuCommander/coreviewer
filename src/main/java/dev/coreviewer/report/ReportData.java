package dev.coreviewer.report;

import dev.coreviewer.model.*;
import java.util.*;

/** Immutable evidence summary. Selected players with no matching events remain in the report. */
public record ReportData(
    List<Player> players,
    String playerOrder,
    List<CoreTraceEvent> events,
    List<String> selectedMaterials,
    boolean allMaterials,
    Map<EventStatistics.Category, Long> categories,
    Map<String, Long> materials,
    long unknownAmounts,
    long firstTimestamp,
    long lastTimestamp) {
  public record Player(
      String name,
      long breaks,
      long places,
      long items,
      long containers,
      long kills,
      long sessions,
      long clicks,
      long events) {}

  public enum SortMetric {
    NAME("Name"),
    BREAKS("Blocks broken"),
    PLACES("Blocks placed"),
    ITEMS("Item quantities"),
    CONTAINERS("Container quantities"),
    KILLS("Kills"),
    SESSIONS("Sessions"),
    CLICKS("Clicks"),
    EVENTS("All events");

    private final String label;

    SortMetric(String label) {
      this.label = label;
    }

    public String label() {
      return label;
    }

    long count(Player row) {
      return switch (this) {
        case NAME -> 0;
        case BREAKS -> row.breaks();
        case PLACES -> row.places();
        case ITEMS -> row.items();
        case CONTAINERS -> row.containers();
        case KILLS -> row.kills();
        case SESSIONS -> row.sessions();
        case CLICKS -> row.clicks();
        case EVENTS -> row.events();
      };
    }
  }

  public ReportData orderBy(SortMetric metric, boolean descending) {
    Comparator<Player> comparator =
        metric == SortMetric.NAME
            ? Comparator.comparing(Player::name, String.CASE_INSENSITIVE_ORDER)
            : Comparator.comparingLong(metric::count);
    if (descending) comparator = comparator.reversed();
    if (metric != SortMetric.NAME)
      comparator = comparator.thenComparing(Player::name, String.CASE_INSENSITIVE_ORDER);
    String direction =
        metric == SortMetric.NAME
            ? descending ? "Z-A" : "A-Z"
            : descending ? "high to low" : "low to high";
    return new ReportData(
        players.stream().sorted(comparator).toList(),
        metric.label() + " (" + direction + ")",
        events,
        selectedMaterials,
        allMaterials,
        categories,
        materials,
        unknownAmounts,
        firstTimestamp,
        lastTimestamp);
  }

  public ReportData orderLike(ReportData reference) {
    var rows = new HashMap<String, Player>();
    players.forEach(row -> rows.put(row.name(), row));
    return new ReportData(
        reference.players().stream().map(row -> rows.get(row.name())).filter(Objects::nonNull).toList(),
        reference.playerOrder(),
        events,
        selectedMaterials,
        allMaterials,
        categories,
        materials,
        unknownAmounts,
        firstTimestamp,
        lastTimestamp);
  }

  public static String material(CoreTraceEvent e) {
    return e instanceof BlockEvent b
        ? b.block()
        : e instanceof ItemEvent i
            ? i.item()
            : e instanceof ContainerEvent c
                ? c.item()
                : e instanceof KillEvent k
                    ? k.entity()
                    : e instanceof SessionEvent ? "session" : "unknown";
  }

  public static String selector(CoreTraceEvent e) {
    return EventStatistics.category(e).name() + "|" + material(e);
  }

  public static ReportData build(
      List<CoreTraceEvent> source, Collection<String> chosenPlayers, Set<String> chosenSelectors) {
    var names = chosenPlayers.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    var availableMaterials =
        source.stream().map(ReportData::selector).collect(java.util.stream.Collectors.toSet());
    var selectedMaterials =
        chosenSelectors.stream().filter(availableMaterials::contains).sorted().toList();
    boolean allMaterials = chosenSelectors.containsAll(availableMaterials);
    Set<String> allowed = Set.copyOf(names);
    var included =
        source.stream()
            .filter(e -> allowed.contains(e.context().actor()))
            .filter(e -> chosenSelectors.contains(selector(e)))
            .sorted(Comparator.comparingLong(e -> e.context().timestamp()))
            .toList();
    var categoryCounts =
        new EnumMap<EventStatistics.Category, Long>(EventStatistics.Category.class);
    var materialCounts = new TreeMap<String, Long>();
    var counters = new HashMap<String, long[]>();
    for (String name : names) counters.put(name, new long[8]);
    long unknown = 0, first = Long.MAX_VALUE, last = 0;
    for (var e : included) {
      var category = EventStatistics.category(e);
      categoryCounts.merge(category, 1L, Long::sum);
      materialCounts.merge(selector(e), 1L, Long::sum);
      long[] row = counters.get(e.context().actor());
      switch (e.type()) {
        case BLOCK_BREAK -> row[0]++;
        case BLOCK_PLACE -> row[1]++;
        default -> {
          switch (category) {
            case ITEMS -> row[2] += e instanceof ItemEvent item ? item.quantity() : 0;
            case CONTAINERS -> row[3] += e instanceof ContainerEvent container ? container.quantity() : 0;
            case KILLS -> row[4]++;
            case SESSIONS -> row[5]++;
            case CLICKS -> row[6]++;
            default -> {}
          }
        }
      }
      row[7]++;
      if (e instanceof ItemEvent i && i.quantity() == 0
          || e instanceof ContainerEvent c && c.quantity() == 0) unknown++;
      long time = e.context().timestamp();
      if (time > 0) {
        first = Math.min(first, time);
        last = Math.max(last, time);
      }
    }
    var playerRows =
        names.stream()
            .map(
                name -> {
                  long[] r = counters.get(name);
                  return new Player(name, r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7]);
                })
            .toList();
    return new ReportData(
        playerRows,
        "Name (A-Z)",
        List.copyOf(included),
        selectedMaterials,
        allMaterials,
        Map.copyOf(categoryCounts),
        Map.copyOf(materialCounts),
        unknown,
        first == Long.MAX_VALUE ? 0 : first,
        last);
  }
}
