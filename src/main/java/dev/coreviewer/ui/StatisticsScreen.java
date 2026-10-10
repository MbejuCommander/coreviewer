package dev.coreviewer.ui;

import dev.coreviewer.CoreTraceClient;
import dev.coreviewer.model.CoreTraceEvent;
import dev.coreviewer.model.EventStatistics;
import dev.coreviewer.model.StatisticsQuery;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class StatisticsScreen extends Screen {
    private final Screen parent;
    private EventStatistics.Category category = EventStatistics.Category.ALL;
    private EventStatistics.Table table;
    private List<EventStatistics.Column> displayColumns = List.of();
    private String player;
    private final List<CoreTraceEvent> snapshot = CoreTraceClient.service.events();
    private List<EventStatistics.Column> allColumns = List.of();
    private List<String> orderedPlayers = List.of();
    private String itemSearch = "", playerSearch = "", pendingSearch;
    private EventStatistics.Column sortColumn;
    private StatisticsQuery.Metric metric = StatisticsQuery.Metric.TOTAL;
    private int sortOrder;
    private boolean categoryOpen;
    private final List<Button> dropdown = new ArrayList<>();
    private EditBox itemSearchBox, playerSearchBox;
    private int rowPage, columnPage;
    private long generation;
    private boolean compact, details;
    private final Map<String, net.minecraft.world.item.ItemStack> icons = new HashMap<>();

    private net.minecraft.world.item.ItemStack icon(String id) {
        return icons.computeIfAbsent(
                id,
                key -> {
                    if (key.startsWith("head:")) {
                        var head =
                                new net.minecraft.world.item.ItemStack(
                                        net.minecraft.world.item.Items.PLAYER_HEAD);
                        var info =
                                minecraft.getConnection() == null
                                        ? null
                                        : minecraft.getConnection().getPlayerInfo(key.substring(5));
                        if (info != null)
                            head.set(
                                    net.minecraft.core.component.DataComponents.PROFILE,
                                    net.minecraft.world.item.component.ResolvableProfile
                                            .createResolved(info.getProfile()));
                        return head;
                    }
                    var identifier = net.minecraft.resources.Identifier.tryParse(key);
                    return new net.minecraft.world.item.ItemStack(
                            identifier == null
                                    ? net.minecraft.world.item.Items.PAPER
                                    : net.minecraft.core.registries.BuiltInRegistries.ITEM
                                            .getOptional(identifier)
                                            .orElse(net.minecraft.world.item.Items.PAPER));
                });
    }

    public StatisticsScreen(Screen parent) {
        super(Component.literal("Investigation Statistics"));
        this.parent = parent;
        var c = CoreTraceClient.service.config();
        compact = c.statisticsCompact;
        details = c.statisticsDetails;
        refresh();
    }

    private record Prepared(EventStatistics.Table table, List<EventStatistics.Column> columns) {}

    private void refresh() {
        long token = ++generation;
        var events = snapshot;
        var selected = category;
        var actor = player;
        boolean useCompact = compact;
        CompletableFuture.supplyAsync(
                        () -> {
                            var result = EventStatistics.build(events, selected, actor, true);
                            return new Prepared(
                                    result, EventStatistics.columns(result, useCompact));
                        })
                .thenAccept(
                        result ->
                                Minecraft.getInstance()
                                        .execute(
                                                () -> {
                                                    if (token != generation) return;
                                                    table = result.table();
                                                    allColumns = result.columns();
                                                    displayColumns =
                                                            StatisticsQuery.columns(
                                                                    allColumns, itemSearch);
                                                    sortColumn = null;
                                                    sortOrder = 0;
                                                    orderedPlayers =
                                                            StatisticsQuery.players(
                                                                    table, null, metric, 0);
                                                    rowPage = columnPage = 0;
                                                    if (minecraft != null
                                                            && minecraft.gui.screen() == this)
                                                        rebuildWidgets();
                                                }));
    }

    private void savePreferences() {
        var c = CoreTraceClient.service.config();
        c.statisticsCompact = compact;
        c.statisticsDetails = details;
        CoreTraceClient.service.configure(c);
    }

    private Button button(String text, int x, int y, int w, Runnable action) {
        return addRenderableWidget(new AnimatedButton(text, "", x, y, w, 20, action));
    }

    private int tableWidth() {
        return Math.min(880, width - 24);
    }

    private int left() {
        return (width - tableWidth()) / 2;
    }

    private int playerWidth() {
        return Math.min(152, Math.max(108, tableWidth() / 4));
    }

    private int rowTop() {
        return details ? 134 : 128;
    }

    private int rows() {
        return Math.max(1, (height - rowTop() - 72) / 24);
    }

    private int columns() {
        return Math.max(
                1,
                Math.min(
                        displayColumns.size(),
                        (tableWidth() - playerWidth()) / (details ? 80 : 58)));
    }

    @Override
    protected void init() {
        int x = left(), w = tableWidth();
        dropdown.clear();
        var categoryButton =
                button(
                        category.name() + " ▾",
                        x,
                        36,
                        102,
                        () -> {
                            categoryOpen = !categoryOpen;
                            rebuildWidgets();
                        });
        button(
                        "Sort: " + metric.name(),
                        x + 108,
                        36,
                        112,
                        () -> {
                            metric = StatisticsQuery.Metric.values()[(metric.ordinal() + 1) % 3];
                            sortRows();
                            rebuildWidgets();
                        })
                .setTooltip(
                        Tooltip.create(
                                Component.literal(
                                        "Choose which count to rank in compact columns:"
                                            + " added/placed, removed/broken, or total. Then click"
                                            + " a column arrow.")));
        button(
                "Reset filters",
                x + 226,
                36,
                Math.min(110, w - 226),
                () -> {
                    category = EventStatistics.Category.ALL;
                    itemSearch = playerSearch = "";
                    sortColumn = null;
                    sortOrder = 0;
                    metric = StatisticsQuery.Metric.TOTAL;
                    refresh();
                });
        button(
                                "←",
                                x,
                                64,
                                28,
                                () -> {
                                    columnPage = Math.max(0, columnPage - 1);
                                    rebuildWidgets();
                                })
                        .active =
                columnPage > 0;
        button(
                "Show details: " + (details ? "ON" : "OFF"),
                x + 32,
                64,
                104,
                () -> {
                    details = !details;
                    rowPage = columnPage = 0;
                    savePreferences();
                    rebuildWidgets();
                });
        button(
                "Compact: " + (compact ? "ON" : "OFF"),
                x + 140,
                64,
                86,
                () -> {
                    compact = !compact;
                    savePreferences();
                    refresh();
                });
        button(
                                "→",
                                x + w - 28,
                                64,
                                28,
                                () -> {
                                    columnPage++;
                                    rebuildWidgets();
                                })
                        .active =
                (columnPage + 1) * columns() < displayColumns.size();
        button(
                                "↑ Previous",
                                x,
                                height - 27,
                                90,
                                () -> {
                                    rowPage = Math.max(0, rowPage - 1);
                                    rebuildWidgets();
                                })
                        .active =
                rowPage > 0;
        button(
                                "↓ Next",
                                x + w - 90,
                                height - 27,
                                90,
                                () -> {
                                    rowPage++;
                                    rebuildWidgets();
                                })
                        .active =
                table != null && (rowPage + 1) * rows() < orderedPlayers.size();
        button(
                player == null ? "Done" : "All players",
                width / 2 - 56,
                height - 27,
                112,
                () -> {
                    if (player == null) onClose();
                    else {
                        player = null;
                        refresh();
                    }
                });
        itemSearchBox =
                searchBox(
                        x + 248,
                        64,
                        Math.max(38, w - 280),
                        "Item",
                        itemSearch,
                        value -> {
                            itemSearch = value;
                            pendingSearch = "item";
                        });
        playerSearchBox =
                searchBox(
                        x + 21,
                        110,
                        playerWidth() - 25,
                        "Player",
                        playerSearch,
                        value -> {
                            playerSearch = value;
                            pendingSearch = "player";
                        });
        if (table == null) return;
        int cell = (w - playerWidth()) / columns();
        for (int col = 0; col < columns(); col++) {
            int at = columnPage * columns() + col;
            if (at >= displayColumns.size()) break;
            var column = displayColumns.get(at);
            int cx = x + playerWidth() + col * cell;
            button(
                            sortColumn == column && sortOrder == 2 ? "↑" : "↓",
                            cx + cell - 19,
                            96,
                            17,
                            () -> {
                                sortOrder = sortColumn == column ? (sortOrder + 1) % 3 : 1;
                                sortColumn = column;
                                sortRows();
                                rebuildWidgets();
                            })
                    .setTooltip(
                            Tooltip.create(
                                    Component.literal(
                                            "Sort "
                                                    + column.label()
                                                    + " by "
                                                    + metric
                                                    + ". Cycle: highest first, lowest first, player"
                                                    + " A–Z.")));
            if (player != null)
                button(
                                "",
                                cx + cell / 2 - 12,
                                97,
                                24,
                                () ->
                                        minecraft.gui.setScreen(
                                                new EventDetailsScreen(
                                                        this, snapshot, player, column)))
                        .setTooltip(
                                Tooltip.create(
                                        Component.literal(
                                                "Open individual events and coordinates")));
        }
        for (int r = 0; r < rows(); r++) {
            int at = rowPage * rows() + r;
            if (at >= orderedPlayers.size()) break;
            String actor = orderedPlayers.get(at);
            int y = rowTop() + r * 24 + 2;
            button(
                            "",
                            x + 3,
                            y,
                            23,
                            () -> {
                                player = actor;
                                category = EventStatistics.Category.ALL;
                                itemSearch = playerSearch = "";
                                refresh();
                            })
                    .setTooltip(Tooltip.create(Component.literal("Open " + actor + " statistics")));
            button(
                    font.plainSubstrByWidth(actor, playerWidth() - 42),
                    x + 29,
                    y,
                    playerWidth() - 34,
                    () -> {
                        player = actor;
                        category = EventStatistics.Category.ALL;
                        itemSearch = playerSearch = "";
                        refresh();
                    });
        }
        if (categoryOpen) {
            for (var child : children())
                if (child instanceof AbstractWidget widget) widget.active = false;
            categoryButton.active = true;
            int dy = 58;
            for (var choice : EventStatistics.Category.values()) {
                dropdown.add(
                        button(
                                choice.name(),
                                x,
                                dy,
                                124,
                                () -> {
                                    category = choice;
                                    categoryOpen = false;
                                    refresh();
                                    rebuildWidgets();
                                }));
                dy += 21;
            }
        }
    }

    private EditBox searchBox(
            int x,
            int y,
            int w,
            String hint,
            String value,
            java.util.function.Consumer<String> change) {
        var box = new EditBox(font, x, y, w, 18, Component.literal(hint + " search"));
        box.setMaxLength(96);
        box.setHint(Component.literal(hint + "…"));
        box.setValue(value);
        box.setResponder(change);
        return addRenderableWidget(box);
    }

    private void sortRows() {
        if (table != null)
            orderedPlayers = StatisticsQuery.players(table, sortColumn, metric, sortOrder);
        rowPage = 0;
    }

    @Override
    public void tick() {
        if (pendingSearch == null) return;
        String focus = pendingSearch;
        pendingSearch = null;
        int cursor = (focus.equals("item") ? itemSearchBox : playerSearchBox).getCursorPosition();
        if (focus.equals("item")) {
            displayColumns = StatisticsQuery.columns(allColumns, itemSearch);
            columnPage = 0;
        } else {
            int found = StatisticsQuery.findPlayer(orderedPlayers, playerSearch);
            if (found >= 0) rowPage = found / rows();
        }
        rebuildWidgets();
        var box = focus.equals("item") ? itemSearchBox : playerSearchBox;
        setFocused(box);
        box.setFocused(true);
        box.setCursorPosition(cursor);
        box.setHighlightPos(cursor);
    }

    private void magnifier(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x + 1, y, x + 6, y + 1, 0xFF9CB7C9);
        g.fill(x, y + 1, x + 1, y + 6, 0xFF9CB7C9);
        g.fill(x + 6, y + 1, x + 7, y + 6, 0xFF9CB7C9);
        g.fill(x + 1, y + 6, x + 6, y + 7, 0xFF9CB7C9);
        g.fill(x + 6, y + 6, x + 8, y + 8, 0xFF9CB7C9);
        g.fill(x + 8, y + 8, x + 10, y + 10, 0xFF9CB7C9);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, 0xEE0B121D);
        int x = left(),
                w = tableWidth(),
                top = 94,
                bottom = height - 44,
                pw = playerWidth(),
                cols = columns(),
                cell = (w - pw) / cols;
        g.fill(x, top, x + w, bottom, 0xFF142031);
        for (int r = 0; r < rows(); r++) {
            int y = rowTop() + r * 24;
            if (y + 24 > height - 67) break;
            g.fill(x, y, x + w, y + 24, r % 2 == 0 ? 0xFF1A293B : 0xFF172434);
            g.fill(x, y + 23, x + w, y + 24, 0xFF324459);
        }
        for (int col = 0; col < cols; col++) {
            int at = columnPage * cols + col;
            if (at >= displayColumns.size()) break;
            var c = displayColumns.get(at);
            int cx = x + pw + col * cell;
            int color = c.neutral() ? 0xFF293D53 : c.negative().isEmpty() ? 0xFF284B40 : 0xFF50343E;
            if (c.paired()) {
                g.fill(cx, top, cx + cell / 2, rowTop(), 0xFF284B40);
                g.fill(cx + cell / 2, top, cx + cell, rowTop(), 0xFF50343E);
            } else g.fill(cx, top, cx + cell, rowTop(), color);
            g.fill(cx, top, cx + 1, bottom, 0xFF40546B);
        }
        g.fill(x, height - 66, x + w, height - 44, 0xFF263B50);
        g.fill(x, top, x + w, top + 1, 0xFF5A748A);
        g.fill(x, bottom - 1, x + w, bottom, 0xFF5A748A);
        g.fill(x, top, x + 1, bottom, 0xFF5A748A);
        g.fill(x + w - 1, top, x + w, bottom, 0xFF5A748A);
    }

    private Component value(EventStatistics.Column c, Map<String, Long> row) {
        long plus = c.plus(row), minus = c.minus(row);
        if (c.paired())
            return Component.literal(Long.toString(plus))
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(Long.toString(minus)).withStyle(ChatFormatting.RED));
        return Component.literal(Long.toString(plus + minus))
                .withStyle(
                        c.neutral()
                                ? ChatFormatting.AQUA
                                : c.negative().isEmpty()
                                        ? ChatFormatting.GREEN
                                        : ChatFormatting.RED);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        g.centeredText(font, title, width / 2, 10, 0xFFF0F6FA);
        g.centeredText(
                font,
                compact
                        ? "GREEN: placed / added / login    RED: broken / removed / logout"
                        : "Selected CSVs · Counts and known item quantities",
                width / 2,
                24,
                0xFF9CB7C9);
        int x = left(),
                w = tableWidth(),
                pw = playerWidth(),
                cols = columns(),
                cell = (w - pw) / cols;
        g.text(font, "PLAYER", x + 8, 98, 0xFFB8CEDF);
        magnifier(g, x + 232, 68);
        magnifier(g, x + 6, 113);
        if (table == null) {
            g.centeredText(font, "Analyzing selected CSVs…", width / 2, rowTop() + 8, 0xFFB8CEDF);
            return;
        }
        for (int col = 0; col < cols; col++) {
            int at = columnPage * cols + col;
            if (at >= displayColumns.size()) break;
            var c = displayColumns.get(at);
            int cx = x + pw + col * cell, center = cx + cell / 2;
            if (minecraft.level != null) g.item(icon(c.icon()), center - 8, 98);
            if (details)
                g.centeredText(
                        font,
                        font.plainSubstrByWidth(c.label(), cell - 8),
                        center,
                        120,
                        0xFFE0EAF2);
            if (mx >= cx && mx < cx + cell && my >= 94 && my < rowTop())
                g.setTooltipForNextFrame(font, Component.literal(c.tooltip()), mx, my);
            for (int row = 0; row < rows(); row++) {
                int p = rowPage * rows() + row;
                if (p >= orderedPlayers.size()) break;
                g.centeredText(
                        font,
                        value(c, table.values().get(orderedPlayers.get(p))),
                        center,
                        rowTop() + row * 24 + 8,
                        0xFFFFFFFF);
            }
            g.centeredText(font, value(c, table.totals()), center, height - 59, 0xFFFFFFFF);
        }
        if (minecraft.level != null)
            for (int r = 0; r < rows() && rowPage * rows() + r < orderedPlayers.size(); r++)
                g.item(
                        icon("head:" + orderedPlayers.get(rowPage * rows() + r)),
                        x + 6,
                        rowTop() + r * 24 + 4);
        if (table.events() == 0)
            g.centeredText(
                    font, "No events in this selection", width / 2, rowTop() + 10, 0xFFA8BCCB);
        g.text(font, "TOTAL", x + 8, height - 59, 0xFFFFD166);
        g.centeredText(
                font,
                (player == null ? "All players" : player)
                        + " | "
                        + table.events()
                        + " events | Rows "
                        + (orderedPlayers.isEmpty() ? 0 : rowPage * rows() + 1)
                        + "–"
                        + Math.min((rowPage + 1) * rows(), orderedPlayers.size())
                        + "/"
                        + orderedPlayers.size()
                        + (table.unknownAmounts() == 0
                                ? ""
                                : " | " + table.unknownAmounts() + " unknown"),
                width / 2,
                height - 39,
                0xFF9CB7C9);
        if (categoryOpen) {
            g.fill(x - 1, 57, x + 126, 185, 0xFF0B121D);
            for (var choice : dropdown) choice.extractRenderState(g, mx, my, dt);
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
