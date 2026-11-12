package dev.coreviewer.model;

import java.util.*;

/**
 * Newest nearby representative per actor, world, material and action; each actor is then
 * chronological.
 */
public final class TeleportRoute {
  private record Group(String actor, String world, String action) {}

  public static double radius(String input) {
    double value = input.isBlank() ? 0 : Double.parseDouble(input.strip());
    if (!Double.isFinite(value) || value < 0)
      throw new IllegalArgumentException("Enter a nonnegative radius, or leave blank.");
    return value;
  }

  public static List<CoreTraceEvent> select(
      List<CoreTraceEvent> source,
      Set<String> players,
      Set<String> actions,
      double radius,
      String server) {
    if (!Double.isFinite(radius) || radius < 0)
      throw new IllegalArgumentException("Invalid radius");
    var sorted =
        source.stream()
            .filter(
                e ->
                    !e.context().simulated()
                        && e.context().position() != null
                        && e.context().timestamp() > 0
                        && e.context().world().matches("[A-Za-z0-9_.:/-]+")
                        && players.contains(e.context().actor())
                        && actions.contains(StatisticsQuery.key(e)))
            .sorted(
                Comparator.comparingLong((CoreTraceEvent e) -> e.context().timestamp()).reversed())
            .toList();
    var last = new HashMap<Group, CoreTraceEvent>();
    var kept = new ArrayList<CoreTraceEvent>();
    for (var e : sorted) {
      var c = e.context();
      var group = new Group(c.actor(), c.world(), StatisticsQuery.key(e));
      var previous = last.get(group);
      if (radius > 0 && previous != null) {
        var a = c.position();
        var b = previous.context().position();
        double dx = (double) a.x() - b.x(),
            dy = (double) a.y() - b.y(),
            dz = (double) a.z() - b.z();
        if (Math.hypot(Math.hypot(dx, dy), dz) <= radius) continue;
      }
      kept.add(e);
      last.put(group, e);
    }
    var firstTime = new HashMap<String, Long>();
    for (var event : kept)
      firstTime.merge(event.context().actor(), event.context().timestamp(), Math::min);
    kept.sort(
        Comparator.comparingLong((CoreTraceEvent e) -> firstTime.get(e.context().actor()))
            .thenComparing(e -> e.context().actor(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(e -> e.context().actor())
            .thenComparingLong(e -> e.context().timestamp()));
    return List.copyOf(kept);
  }
}
