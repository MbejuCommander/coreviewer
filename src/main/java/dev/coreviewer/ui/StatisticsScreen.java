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
    private final List<CoreTraceEvent> snapshot;
    private Map<String, UUID> actorIds = Map.of();
    private List<EventStatistics.Column> allColumns = List.of();
    private List<String> orderedPlayers = List.of();
    private String itemSearch = "", playerSearch = "", pendingSearch;
    private EventStatistics.Column sortColumn;
    private StatisticsQuery.Metric metric = StatisticsQuery.Metric.TOTAL;
    private int sortOrder;
    private boolean tpMode;
    private String tpRadius = "";
    private final Set<String> excludedPlayers = new HashSet<>(), excludedActions = new HashSet<>();
    private Map<String, Long> zoneCounts = Map.of();
    private long zoneGeneration, radiusEditedAt;
    private boolean zoneDirty, draggingHorizontal, draggingVertical;
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
                        head.set(
                                net.minecraft.core.component.DataComponents.PROFILE,
                                dev.coreviewer.view.PlayerAppearance.profile(
                                        key.substring(5), actorIds.get(key.substring(5))));
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
        this(parent, null);
    }

    private StatisticsScreen(Screen parent, String actor) {
        super(Component.literal("Investigation Statistics"));
        this.parent = parent;
        this.snapshot =
                parent instanceof StatisticsScreen previous
                        ? previous.snapshot
                        : CoreTraceClient.service.events();
        this.player = actor;
        if (parent instanceof StatisticsScreen previous) {
            tpMode = previous.tpMode;
            tpRadius = previous.tpRadius;
            excludedPlayers.addAll(previous.excludedPlayers);
            excludedActions.addAll(previous.excludedActions);
        }
        var c = CoreTraceClient.service.config();
        compact = c.statisticsCompact;
        details = c.statisticsDetails;
        refresh();
    }

    private record Prepared(
            EventStatistics.Table table,
            List<EventStatistics.Column> columns,
            Map<String, UUID> actorIds) {}

    private void refresh() {
        long token = ++generation;
        var events = snapshot;
        var selected = category;
        var actor = player;
        boolean useCompact = compact;
        CompletableFuture.supplyAsync(
                        () -> {
                            var result = EventStatistics.build(events, selected, actor, true);
                            var ids = new HashMap<String, UUID>();
                            for (var event : events)
                                if (event.context().actorUuid() != null)
                                    ids.put(event.context().actor(), event.context().actorUuid());
                            return new Prepared(
                                    result,
                                    EventStatistics.columns(result, useCompact),
                                    Map.copyOf(ids));
                        })
                .thenAccept(
                        result ->
                                Minecraft.getInstance()
                                        .execute(
                                                () -> {
                                                    if (token != generation) return;
                                                    table = result.table();
                                                    actorIds = result.actorIds();
                                                    icons.clear();
                                                    zoneDirty = true;
                                                    radiusEditedAt = 0;
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
        return height < 300 ? 130 : details ? 158 : 152;
    }

    private int rowHeight() {
        return height < 300 ? 18 : 24;
    }

    private int headerY(int y) {
        return height < 300 ? y - 22 : y;
    }

    private int toolbarY(int y) {
        return height < 300 ? y - (y == 36 ? 4 : 10) : y;
    }

    private int rows() {
        return Math.max(1, (height - rowTop() - (tpMode ? 90 : 66)) / rowHeight());
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
        rowPage = Math.clamp(rowPage, 0, Math.max(0, (orderedPlayers.size() - 1) / rows()));
        columnPage =
                Math.clamp(columnPage, 0, Math.max(0, (displayColumns.size() - 1) / columns()));
        dropdown.clear();
        var radius = new EditBox(font, x, toolbarY(86), 100, 20, Component.literal("TP radius"));
        radius.setValue(tpRadius);
        radius.setHint(Component.literal("Radius: 0"));
        radius.setTooltip(
                Tooltip.create(
                        Component.literal(
                                "0 or blank keeps all events. A positive radius retains the latest"
                                    + " nearby location separately for each player, world, material"
                                    + " and action.")));
        radius.setResponder(
                value -> {
                    tpRadius = value;
                    radiusEditedAt = System.nanoTime();
                    zoneDirty = true;
                    zoneCounts = Map.of();
                });
        addRenderableWidget(radius);
        button(
                "TP: " + (tpMode ? "ON" : "OFF"),
                x + 106,
                toolbarY(86),
                80,
                () -> {
                    tpMode = !tpMode;
                    rebuildWidgets();
                });
        if (tpMode)
            button("TP Now", x + 192, toolbarY(86), 90, this::openTour)
                    .setTooltip(
                            Tooltip.create(
                                    Component.literal(
                                            "Open location preview. Only Previous or Next sends a"
                                                    + " teleport command.")));

        var categoryButton =
                button(
                        category.name() + " ▾",
                        x,
                        toolbarY(36),
                        102,
                        () -> {
                            categoryOpen = !categoryOpen;
                            rebuildWidgets();
                        });
        button(
                        "Sort: " + metric.name(),
                        x + 108,
                        toolbarY(36),
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
                toolbarY(36),
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
                                toolbarY(64),
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
                toolbarY(64),
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
                toolbarY(64),
                86,
                () -> {
                    compact = !compact;
                    savePreferences();
                    refresh();
                });
        button(
                                "→",
                                x + w - 28,
                                toolbarY(64),
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
                    onClose();
                });
        itemSearchBox =
                searchBox(
                        x + 248,
                        toolbarY(64),
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
                        headerY(134),
                        playerWidth() - 25,
                        "Player",
                        playerSearch,
                        value -> {
                            playerSearch = value;
                            pendingSearch = "player";
                        });
        if (table == null) return;
        if (tpMode) {
            button(
                    "Players",
                    x,
                    height - 91,
                    playerWidth() / 2 - 3,
                    () -> {
                        if (excludedPlayers.containsAll(table.players()))
                            excludedPlayers.removeAll(table.players());
                        else excludedPlayers.addAll(table.players());
                        rebuildWidgets();
                    });
            button(
                    "Actions",
                    x + playerWidth() / 2,
                    height - 91,
                    playerWidth() / 2 - 3,
                    () -> {
                        var keys = new HashSet<String>();
                        for (var col : allColumns) {
                            keys.addAll(col.positive());
                            keys.addAll(col.negative());
                        }
                        if (excludedActions.containsAll(keys)) excludedActions.removeAll(keys);
                        else excludedActions.addAll(keys);
                        rebuildWidgets();
                    });
        }
        int cell = (w - playerWidth()) / columns();
        for (int col = 0; col < columns(); col++) {
            int at = columnPage * columns() + col;
            if (at >= displayColumns.size()) break;
            var column = displayColumns.get(at);
            int cx = x + playerWidth() + col * cell;
            button(
                            sortColumn == column && sortOrder == 2 ? "↑" : "↓",
                            cx + cell - 19,
                            headerY(120),
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
            if (tpMode) {
                int offset = 0;
                for (var keys : List.of(column.positive(), column.negative())) {
                    if (keys.isEmpty()) continue;
                    var label =
                            Component.literal(excludedActions.containsAll(keys) ? "[ ]" : "[x]")
                                    .withStyle(
                                            keys == column.negative()
                                                    ? ChatFormatting.RED
                                                    : ChatFormatting.GREEN);
                    var check =
                            button(
                                    label.getString(),
                                    cx + offset,
                                    height - 91,
                                    Math.min(28, cell / 2 - 3),
                                    () -> {
                                        if (excludedActions.containsAll(keys))
                                            excludedActions.removeAll(keys);
                                        else excludedActions.addAll(keys);
                                        rebuildWidgets();
                                    });
                    ((AnimatedButton) check)
                            .background(keys == column.negative() ? 0xFF50343E : 0xFF284B40);
                    check.setMessage(label);
                    check.setTooltip(Tooltip.create(Component.literal(String.join(", ", keys))));
                    offset += Math.min(28, cell / 2 - 3) + 2;
                }
            }
            if (player != null)
                button(
                                "",
                                cx + cell / 2 - 12,
                                headerY(121),
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
            int y = rowTop() + r * rowHeight() + 2;
            button(
                            tpMode ? (excludedPlayers.contains(actor) ? "[ ]" : "[x]") : "",
                            x + 3,
                            y,
                            23,
                            () -> {
                                if (tpMode) {
                                    if (!excludedPlayers.add(actor)) excludedPlayers.remove(actor);
                                    rebuildWidgets();
                                    return;
                                }
                                openPlayer(actor);
                            })
                    .setTooltip(
                            Tooltip.create(
                                    Component.literal(
                                            (tpMode ? "Include/exclude " : "Open statistics for ")
                                                    + actor)));
            button(
                    font.plainSubstrByWidth(actor, playerWidth() - (tpMode ? 60 : 42)),
                    x + (tpMode ? 47 : 29),
                    y,
                    playerWidth() - (tpMode ? 52 : 34),
                    () -> {
                        openPlayer(actor);
                    });
        }
        if (height < 300)
            for (var child : children())
                if (child instanceof Button b
                        && b.getY() >= rowTop()
                        && b.getY() < height - (tpMode ? 91 : 66)) b.setHeight(16);
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
        if (zoneDirty && table != null && System.nanoTime() - radiusEditedAt > 500_000_000L) {
            zoneDirty = false;
            long token = ++zoneGeneration;
            final double radius;
            try {
                radius = dev.coreviewer.model.TeleportRoute.radius(tpRadius);
            } catch (IllegalArgumentException ex) {
                zoneCounts = Map.of();
                return;
            }
            if (radius == 0) {
                zoneCounts = Map.of();
                return;
            }
            var actors = Set.copyOf(table.players());
            var actions = new HashSet<String>();
            for (var col : allColumns) {
                actions.addAll(col.positive());
                actions.addAll(col.negative());
            }
            CompletableFuture.supplyAsync(
                            () -> {
                                var result = new HashMap<String, Long>();
                                for (var event :
                                        dev.coreviewer.model.TeleportRoute.select(
                                                snapshot, actors, actions, radius, "")) {
                                    result.merge(
                                            event.context().actor()
                                                    + "|"
                                                    + StatisticsQuery.key(event),
                                            1L,
                                            Long::sum);
                                    result.merge("|" + StatisticsQuery.key(event), 1L, Long::sum);
                                }
                                return Map.copyOf(result);
                            })
                    .thenAccept(
                            result ->
                                    Minecraft.getInstance()
                                            .execute(
                                                    () -> {
                                                        if (token == zoneGeneration) {
                                                            zoneCounts = result;
                                                            if (minecraft.gui.screen() == this)
                                                                rebuildWidgets();
                                                        }
                                                    }));
        }
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
                top = headerY(118),
                bottom = height - 44,
                pw = playerWidth(),
                cols = columns(),
                cell = (w - pw) / cols;
        g.fill(x, top, x + w, bottom, 0xFF142031);
        for (int r = 0; r < rows(); r++) {
            int y = rowTop() + r * rowHeight();
            if (y + rowHeight() > height - 67) break;
            g.fill(x, y, x + w, y + rowHeight(), r % 2 == 0 ? 0xFF1A293B : 0xFF172434);
            g.fill(x, y + rowHeight() - 1, x + w, y + rowHeight(), 0xFF324459);
        }
        for (int col = 0; col < cols; col++) {
            int at = columnPage * cols + col;
            if (at >= displayColumns.size()) break;
            var c = displayColumns.get(at);
            int cx = x + pw + col * cell;
            int color = c.neutral() ? 0xFF293D53 : c.negative().isEmpty() ? 0xFF284B40 : 0xFF50343E;
            if (compact) {
                g.fill(cx, top, cx + cell, rowTop(), 0xFF293D53);
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

    private void renderValue(
            GuiGraphicsExtractor g,
            EventStatistics.Column c,
            Map<String, Long> values,
            String actor,
            int center,
            int y,
            int cell,
            int mx,
            int my) {
        var raw = value(c, values);
        double radius;
        try {
            radius = dev.coreviewer.model.TeleportRoute.radius(tpRadius);
        } catch (IllegalArgumentException ex) {
            radius = 0;
        }
        if (radius <= 0) {
            g.centeredText(font, raw, center, y, 0xFFFFFFFF);
            return;
        }
        long zones = 0;
        for (String key : c.positive())
            zones +=
                    actor == null ? zoneTotal(key) : zoneCounts.getOrDefault(actor + "|" + key, 0L);
        for (String key : c.negative())
            zones +=
                    actor == null ? zoneTotal(key) : zoneCounts.getOrDefault(actor + "|" + key, 0L);
        String count = Long.toString(zones);
        int pearlX = center + cell / 2 - 18;
        int start = center - cell / 2 + 4;
        g.text(font, raw, start, y, 0xFFFFFFFF);
        int after = Math.min(pearlX - font.width(" / " + count) - 3, start + font.width(raw));
        g.text(font, " / " + count, after, y, 0xFF9CE4D1);
        g.item(icon("minecraft:ender_pearl"), pearlX, y - 4);
        if (mx >= pearlX && mx < pearlX + 16 && my >= y - 4 && my < y + 12)
            g.setTooltipForNextFrame(
                    font,
                    Component.literal(
                            "Retained TP stops within this radius; actions and worlds remain"
                                    + " separate."),
                    mx,
                    my);
    }

    private long zoneTotal(String key) {
        return zoneCounts.getOrDefault("|" + key, 0L);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        g.centeredText(font, title, width / 2, 10, 0xFFF0F6FA);
        g.centeredText(
                font,
                compact
                        ? "GREEN: placed / added / login    RED: broken / removed / logout"
                        : "Selected files · Counts and known item quantities",
                width / 2,
                24,
                0xFF9CB7C9);
        int x = left(),
                w = tableWidth(),
                pw = playerWidth(),
                cols = columns(),
                cell = (w - pw) / cols;
        g.text(font, "PLAYER", x + 8, headerY(122), 0xFFB8CEDF);
        magnifier(g, x + 232, toolbarY(64) + 4);
        magnifier(g, x + 6, headerY(137));
        if (table == null) {
            g.centeredText(font, "Analyzing selected files…", width / 2, rowTop() + 8, 0xFFB8CEDF);
            return;
        }
        for (int col = 0; col < cols; col++) {
            int at = columnPage * cols + col;
            if (at >= displayColumns.size()) break;
            var c = displayColumns.get(at);
            int cx = x + pw + col * cell, center = cx + cell / 2;
            if (minecraft.level != null) {
                g.item(icon(c.icon()), center - 8, headerY(122));
                String key =
                        !c.positive().isEmpty() ? c.positive().getFirst() : c.negative().getFirst();
                if (key.startsWith("BLOCK_CLICK")) {
                    for (int py = 0; py < 12; py++)
                        for (int px = 0; px <= py / 2; px++)
                            g.fill(
                                    cx + 3 + px,
                                    headerY(121) + py,
                                    cx + 4 + px,
                                    headerY(122) + py,
                                    px == 0 || px == py / 2 ? 0xFF151515 : 0xFFFFFFFF);
                } else {
                    String marker =
                            key.startsWith("BLOCK")
                                    ? "minecraft:iron_pickaxe"
                                    : key.startsWith("SESSION")
                                            ? "minecraft:oak_door"
                                            : key.contains("KILL") ? "minecraft:iron_sword" : null;
                    if (marker != null) g.item(icon(marker), cx + 2, headerY(121));
                    else
                        g.blitSprite(
                                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                                        "minecraft",
                                        "statistics/"
                                                + (key.startsWith("CONTAINER")
                                                        ? "item_picked_up"
                                                        : "item_dropped")),
                                cx + 2,
                                headerY(121),
                                16,
                                16);
                }
            }
            if (details)
                g.centeredText(
                        font,
                        font.plainSubstrByWidth(c.label(), cell - 8),
                        center,
                        headerY(144),
                        0xFFE0EAF2);
            if (mx >= cx && mx < cx + cell && my >= headerY(118) && my < rowTop())
                g.setTooltipForNextFrame(font, Component.literal(c.tooltip()), mx, my);
            for (int row = 0; row < rows(); row++) {
                int p = rowPage * rows() + row;
                if (p >= orderedPlayers.size()) break;
                renderValue(
                        g,
                        c,
                        table.values().get(orderedPlayers.get(p)),
                        orderedPlayers.get(p),
                        center,
                        rowTop() + row * rowHeight() + (height < 300 ? 5 : 8),
                        cell,
                        mx,
                        my);
            }
            renderValue(g, c, table.totals(), null, center, height - 59, cell, mx, my);
        }
        if (minecraft.level != null)
            for (int r = 0; r < rows() && rowPage * rows() + r < orderedPlayers.size(); r++)
                g.item(
                        icon("head:" + orderedPlayers.get(rowPage * rows() + r)),
                        x + (tpMode ? 28 : 6),
                        rowTop() + r * rowHeight() + (height < 300 ? 1 : 4));
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
        drawScrollbars(g);
        if (categoryOpen) {
            g.fill(x - 1, 57, x + 126, 185, 0xFF0B121D);
            for (var choice : dropdown) choice.extractRenderState(g, mx, my, dt);
        }
    }

    private void openPlayer(String actor) {
        if (player == null) minecraft.gui.setScreen(new StatisticsScreen(this, actor));
    }

    private void openTour() {
        final double radius;
        try {
            radius = dev.coreviewer.model.TeleportRoute.radius(tpRadius);
        } catch (IllegalArgumentException ex) {
            CoreTraceClient.tell(ex.getMessage());
            return;
        }
        if (table == null) return;
        var players = new HashSet<>(table.players());
        players.removeAll(excludedPlayers);
        var actions = new HashSet<String>();
        for (var column : allColumns) {
            actions.addAll(column.positive());
            actions.addAll(column.negative());
        }
        actions.removeAll(excludedActions);
        String server = CoreTraceClient.serverIdentity();
        CompletableFuture.supplyAsync(
                        () ->
                                dev.coreviewer.model.TeleportRoute.select(
                                        snapshot, players, actions, radius, server))
                .thenAccept(
                        route ->
                                Minecraft.getInstance()
                                        .execute(
                                                () -> {
                                                    if (minecraft.gui.screen() == this)
                                                        new TeleportTourScreen(this, route, server)
                                                                .start();
                                                }));
    }

    private int horizontalTrackY() {
        return height < 300 ? headerY(118) - 4 : headerY(118) - 8;
    }

    private int verticalTrackX() {
        return left() + tableWidth() + 4;
    }

    private int verticalTrackY() {
        return rowTop();
    }

    private int verticalTrackHeight() {
        return Math.max(16, height - 67 - rowTop());
    }

    private int horizontalTrackX() {
        return left() + playerWidth();
    }

    private int horizontalTrackWidth() {
        return tableWidth() - playerWidth();
    }

    private int maxRows() {
        return Math.max(0, (orderedPlayers.size() - 1) / rows());
    }

    private int maxColumns() {
        return Math.max(0, (displayColumns.size() - 1) / columns());
    }

    private void drawScrollbars(GuiGraphicsExtractor g) {
        if (maxColumns() > 0) {
            int x = horizontalTrackX(), y = horizontalTrackY(), w = horizontalTrackWidth();
            int thumb = Math.max(16, w / (maxColumns() + 1));
            int pos = (w - thumb) * columnPage / maxColumns();
            g.fill(x, y, x + w, y + 5, 0xFF354B60);
            g.fill(x + pos, y, x + pos + thumb, y + 5, 0xFF8FE0D5);
        }
        if (maxRows() > 0) {
            int x = verticalTrackX(), y = verticalTrackY(), h = verticalTrackHeight();
            int thumb = Math.max(16, h / (maxRows() + 1));
            int pos = (h - thumb) * rowPage / maxRows();
            g.fill(x, y, x + 5, y + h, 0xFF354B60);
            g.fill(x, y + pos, x + 5, y + pos + thumb, 0xFF8FE0D5);
        }
    }

    private void moveScrollbar(double x, double y) {
        if (draggingHorizontal && maxColumns() > 0) {
            int w = horizontalTrackWidth(), thumb = Math.max(16, w / (maxColumns() + 1));
            columnPage =
                    Math.clamp(
                            (int)
                                    Math.round(
                                            (x - horizontalTrackX() - thumb / 2.0)
                                                    * maxColumns()
                                                    / Math.max(1, w - thumb)),
                            0,
                            maxColumns());
        }
        if (draggingVertical && maxRows() > 0) {
            int h = verticalTrackHeight(), thumb = Math.max(16, h / (maxRows() + 1));
            rowPage =
                    Math.clamp(
                            (int)
                                    Math.round(
                                            (y - verticalTrackY() - thumb / 2.0)
                                                    * maxRows()
                                                    / Math.max(1, h - thumb)),
                            0,
                            maxRows());
        }
        rebuildWidgets();
    }

    @Override
    public boolean mouseClicked(
            net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            boolean edit =
                    children().stream()
                            .anyMatch(
                                    child ->
                                            child instanceof EditBox box
                                                    && box.isMouseOver(event.x(), event.y()));
            if (!edit) {
                if (getFocused() instanceof EditBox box) box.setFocused(false);
                setFocused(null);
            }
            int hx = horizontalTrackX(),
                    hy = horizontalTrackY(),
                    vx = verticalTrackX(),
                    vy = verticalTrackY();
            if (maxColumns() > 0
                    && event.x() >= hx
                    && event.x() < hx + horizontalTrackWidth()
                    && event.y() >= hy
                    && event.y() < hy + 6) {
                draggingHorizontal = true;
                moveScrollbar(event.x(), event.y());
                return true;
            }
            if (maxRows() > 0
                    && event.x() >= vx
                    && event.x() < vx + 6
                    && event.y() >= vy
                    && event.y() < vy + verticalTrackHeight()) {
                draggingVertical = true;
                moveScrollbar(event.x(), event.y());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(
            net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (event.button() == 0 && (draggingHorizontal || draggingVertical)) {
            moveScrollbar(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (draggingHorizontal || draggingVertical) {
            draggingHorizontal = draggingVertical = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    private boolean scrollTable(int vertical, int horizontal) {
        if (table == null || categoryOpen) return false;
        rowPage =
                Math.clamp(
                        rowPage + vertical, 0, Math.max(0, (orderedPlayers.size() - 1) / rows()));
        columnPage =
                Math.clamp(
                        columnPage + horizontal,
                        0,
                        Math.max(0, (displayColumns.size() - 1) / columns()));
        rebuildWidgets();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return scrollTable((int) -Math.signum(vertical), (int) -Math.signum(horizontal));
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN) {
            if (getFocused() instanceof EditBox box) {
                box.setFocused(false);
                setFocused(null);
                return true;
            }
        }
        return switch (event.key()) {
            case com.mojang.blaze3d.platform.InputConstants.KEY_UP -> scrollTable(-1, 0);
            case com.mojang.blaze3d.platform.InputConstants.KEY_DOWN -> scrollTable(1, 0);
            case com.mojang.blaze3d.platform.InputConstants.KEY_LEFT -> scrollTable(0, -1);
            case com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT -> scrollTable(0, 1);
            default -> super.keyPressed(event);
        };
    }

    @Override
    public void onClose() {
        if (categoryOpen) {
            categoryOpen = false;
            rebuildWidgets();
            return;
        }
        minecraft.gui.setScreen(parent);
    }
}
