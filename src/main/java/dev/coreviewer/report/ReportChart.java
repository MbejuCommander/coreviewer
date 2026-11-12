package dev.coreviewer.report;

public enum ReportChart {
  PLAYER_BARS("Player bars"),
  STACKED_CATEGORIES("Stacked categories"),
  DONUT("Category donut"),
  TIMELINE("Activity timeline");

  private final String label;

  ReportChart(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
