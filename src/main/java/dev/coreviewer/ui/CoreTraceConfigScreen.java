package dev.coreviewer.ui;

import com.mojang.blaze3d.platform.InputConstants;

import dev.coreviewer.CoreTraceClient;
import dev.coreviewer.config.CoreTraceConfig;

import me.shedaniel.clothconfig2.api.*;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.*;

public final class CoreTraceConfigScreen {
    private static Component text(String value) {
        return Component.literal(value);
    }

    public static Screen create(Screen parent) {
        return createEditor(parent, null);
    }

    public static Screen createEditor(Screen parent, String category) {
        CoreTraceConfig c = CoreTraceClient.service.config();
        var builder =
                ConfigBuilder.create()
                        .setParentScreen(parent)
                        .setTitle(text("Coreviewer investigator — Settings"))
                        .setShouldListSmoothScroll(c.menuAnimations)
                        .setShouldTabsSmoothScroll(c.menuAnimations);
        var e = builder.entryBuilder();
        var general = builder.getOrCreateCategory(text("GENERAL"));
        general.addEntry(
                e.startTextDescription(
                                text(
                                        "Indexed investigation, chat capture, replay and local"
                                            + " storage. Open /coreviewer for the data dashboard."))
                        .build());
        toggle(
                general,
                e,
                "COREVIEWER ENABLED",
                c.enabled,
                true,
                v -> c.enabled = v,
                "OFF blocks investigation actions. Configuration remains available. Static"
                        + " views are hidden and pending capture is cancelled.");
        number(general, e, "Event Radius", c.eventRadius, 100, 1, v -> c.eventRadius = v);
        toggle(
                general,
                e,
                "Follow Events with Teleport",
                c.teleportToEvents,
                false,
                v -> c.teleportToEvents = v,
                "When the next event is outside Event Radius, send a teleport using its recorded"
                    + " coordinates and world. Requires server permission. Missing coordinates,"
                    + " demos and Respect Radius prevent automatic teleport. Failed teleports are"
                    + " not retried.");
        general.addEntry(
                e.startStrField(text("Teleport Command"), c.teleportCommand)
                        .setDefaultValue("/co teleport #{world} {x} {y} {z}")
                        .setTooltip(
                                text(
                                        "{world}: exact CoreProtect world name. {x}, {y}, {z}:"
                                            + " recorded coordinates. Separate arguments with"
                                            + " spaces. Default: /co teleport #{world} {x} {y} {z}."
                                            + " Custom example: /tp {world} {x} {y} {z} only if"
                                            + " your server supports a world argument; vanilla /tp"
                                            + " does not. Keep # in the template if required."))
                        .setSaveConsumer(v -> c.teleportCommand = v)
                        .build());
        toggle(
                general,
                e,
                "Respect Radius",
                c.respectRadius,
                false,
                v -> c.respectRadius = v,
                "Restrict the timeline to Event Radius around your position when loading the view."
                    + " Outside events are excluded from pages and replay. Reload after changing"
                    + " radius or origin. Overrides automatic teleport.");
        toggle(
                general,
                e,
                "Menu Animations",
                c.menuAnimations,
                true,
                v -> c.menuAnimations = v,
                "Smooth category scrolling and subtle hover transitions. Turn OFF for reduced"
                        + " motion.");
        var capture = builder.getOrCreateCategory(text("CAPTURE"));
        capture.addEntry(
                e.startStrField(text("Capture Folder Name"), c.captureFolderName)
                        .setDefaultValue("")
                        .setTooltip(
                                text(
                                        "Each capture saves CSV files in its own subfolder. Blank"
                                                + " uses a timestamp and random ID. Repeated names"
                                                + " receive (2), (3), etc. Simple Mode replaces its"
                                                + " previous active CSV after new data is saved."))
                        .setErrorSupplier(
                                v -> {
                                    try {
                                        dev.coreviewer.storage.CaptureFolders.validate(v);
                                        return java.util.Optional.empty();
                                    } catch (Exception ex) {
                                        return java.util.Optional.of(text(ex.getMessage()));
                                    }
                                })
                        .setSaveConsumer(v -> c.captureFolderName = v.strip())
                        .build());
        capture.addEntry(
                e.startTextDescription(
                                text(
                                        "Capture English CoreProtect lookup results. Automatic"
                                                + " paging requires AUTO PAGE ADVANCE."))
                        .build());
        toggle(
                capture,
                e,
                "Auto Capture",
                c.autoCapture,
                true,
                v -> c.autoCapture = v,
                "Capture responses to your CoreProtect lookup commands.");
        toggle(
                capture,
                e,
                "AUTO PAGE ADVANCE",
                c.autoPageAdvance,
                false,
                v -> c.autoPageAdvance = v,
                "OFF captures manual queries only. ON advances pages after Command Delay.");
        number(
                capture,
                e,
                "Command Delay (ms)",
                c.commandDelayMs,
                1500,
                1,
                v -> c.commandDelayMs = v);
        toggle(
                capture,
                e,
                "Disable Capture After Completion",
                c.resetCaptureOnComplete,
                true,
                v -> c.resetCaptureOnComplete = v,
                "After successful automatic pagination, turn Auto Capture and Auto Page Advance"
                        + " OFF.");
        toggle(
                capture,
                e,
                "Notify Capture Reset",
                c.resetCaptureMessage,
                true,
                v -> c.resetCaptureMessage = v,
                "Show the reset confirmation in chat.");
        toggle(
                capture,
                e,
                "Capture Sounds",
                c.captureSounds,
                false,
                v -> c.captureSounds = v,
                "Play the selected sounds at capture start and successful completion.");
        capture.addEntry(
                new SoundPickerEntry(
                        "Capture Start Sound",
                        c.captureStartSound,
                        "minecraft:block.note_block.pling",
                        v -> c.captureStartSound = v));
        capture.addEntry(
                new SoundPickerEntry(
                        "Capture End Sound",
                        c.captureEndSound,
                        "minecraft:entity.player.levelup",
                        v -> c.captureEndSound = v));
        var csv = builder.getOrCreateCategory(text("CSV"));
        csv.addEntry(
                e.startTextDescription(
                                text(
                                        "Captures save automatically to coreviewer/csv."
                                            + " Import/export use this shared folder. Copy"
                                            + " CoreTrace CSVs here, Refresh, then select them."
                                            + " Imports without server metadata bind to the current"
                                            + " server when selected."))
                        .build());
        toggle(
                csv,
                e,
                "Simple Mode",
                c.simpleMode,
                false,
                v -> c.simpleMode = v,
                "Warning: Simple Mode keeps one active CSV. A new capture replaces it. Existing"
                        + " files move to coreviewer/csv-archive when enabled. File selection is"
                        + " disabled in this mode.");
        csv.addEntry(
                e.startTextDescription(
                                text(
                                        "⚠ Simple Mode replaces the previous capture. Save settings"
                                                + " before changing the CSV selection."))
                        .build());
        csv.addEntry(
                new ActionEntry(
                        "Open Import / Export Folder", CsvLibraryScreen::openFolder, () -> true));
        csv.addEntry(
                new ActionEntry(
                        "Select CSV Files",
                        () -> {
                            var mc = net.minecraft.client.Minecraft.getInstance();
                            CoreTraceClient.service
                                    .refreshCsv(CoreTraceClient.serverIdentity())
                                    .thenAccept(
                                            message ->
                                                    mc.execute(
                                                            () ->
                                                                    mc.gui.setScreen(
                                                                            new CsvLibraryScreen(
                                                                                    mc.gui
                                                                                            .screen()))));
                        },
                        () -> !c.simpleMode));
        var view = builder.getOrCreateCategory(text("STATIC VIEW"));
        view.addEntry(
                e.startTextDescription(
                                text(
                                        "Open /coreviewer static to bind a world and show history."
                                            + " Models show default appearances; recorded states"
                                            + " may be incomplete."))
                        .build());
        toggle(
                view,
                e,
                "Block View",
                c.blockView,
                true,
                v -> c.blockView = v,
                "Applies to the active static investigation view.");
        toggle(
                view,
                e,
                "Kill View",
                c.killView,
                true,
                v -> c.killView = v,
                "Applies to the active static investigation view.");
        toggle(
                view,
                e,
                "Item View",
                c.itemView,
                true,
                v -> c.itemView = v,
                "Applies to the active static investigation view.");
        toggle(
                view,
                e,
                "Container View",
                c.containerView,
                true,
                v -> c.containerView = v,
                "Chest-style item slots and quantities. Green means added; red means removed.");
        toggle(
                view,
                e,
                "Session View",
                c.sessionView,
                true,
                v -> c.sessionView = v,
                "Player holograms with available current skins and green login/red logout borders."
                    + " Missing skins use a default.");
        toggle(
                view,
                e,
                "Ghost Blocks",
                c.ghostBlocks,
                true,
                v -> c.ghostBlocks = v,
                "Default block models; historical orientation may be unavailable.");
        toggle(
                view,
                e,
                "Outline",
                c.outline,
                true,
                v -> c.outline = v,
                "Applies to the active static investigation view.");
        toggle(
                view,
                e,
                "Render Behind Walls",
                c.throughWalls,
                true,
                v -> c.throughWalls = v,
                "Blocks, markers, labels and arrows. Figures/items retain normal occlusion.");
        view.addEntry(
                e.startColorField(text("Block Break Color"), c.breakColor)
                        .setDefaultValue(0xFF5555)
                        .setSaveConsumer(v -> c.breakColor = v)
                        .build());
        view.addEntry(
                e.startColorField(text("Block Place Color"), c.placeColor)
                        .setDefaultValue(0x55FF99)
                        .setSaveConsumer(v -> c.placeColor = v)
                        .build());
        view.addEntry(
                e.startIntSlider(text("Alpha"), c.alpha, 0, 255)
                        .setDefaultValue(160)
                        .setSaveConsumer(v -> c.alpha = v)
                        .build());
        toggle(
                view,
                e,
                "Enable Arrows",
                c.arrows,
                true,
                v -> c.arrows = v,
                "Applies to the active static investigation view.");
        view.addEntry(
                e.startColorField(text("Arrow Color"), c.arrowColor)
                        .setDefaultValue(0xFFD166)
                        .setSaveConsumer(v -> c.arrowColor = v)
                        .build());
        view.addEntry(
                e.startFloatField(text("Arrow Size"), c.arrowSize)
                        .setMin(0.25f)
                        .setMax(4f)
                        .setDefaultValue(1f)
                        .setSaveConsumer(v -> c.arrowSize = v)
                        .build());
        view.addEntry(
                e.startFloatField(text("Arrow Speed"), c.arrowSpeed)
                        .setMin(0.25f)
                        .setMax(4f)
                        .setDefaultValue(1f)
                        .setSaveConsumer(v -> c.arrowSpeed = v)
                        .build());
        toggle(
                view,
                e,
                "Show Player Deaths",
                c.playerDeaths,
                true,
                v -> c.playerDeaths = v,
                "Render a stone memorial with the victim's available current skin and vanilla"
                    + " flowers. Missing skins and causes retain a fallback.");
        toggle(
                view,
                e,
                "Show Mob Deaths",
                c.mobDeaths,
                true,
                v -> c.mobDeaths = v,
                "Applies to the active static investigation view.");
        toggle(
                view,
                e,
                "Player Hologram Heads",
                c.playerHeads,
                false,
                v -> c.playerHeads = v,
                "Generic actor head icon; historical skins are unavailable.");
        toggle(
                view,
                e,
                "Event Labels",
                c.labels,
                true,
                v -> c.labels = v,
                "Actor, action and material. Kill labels include the available cause.");
        toggle(
                view,
                e,
                "Arrows Per Player",
                c.arrowsSamePlayer,
                true,
                v -> c.arrowsSamePlayer = v,
                "Connect consecutive block events for each actor. Equal or approximate timestamps"
                        + " are not ordered.");
        toggle(
                view,
                e,
                "Tint Ghost Textures",
                c.tintGhosts,
                false,
                v -> c.tintGhosts = v,
                "OFF preserves the block texture colors; ON applies break/place color tint.");
        number(
                view,
                e,
                "Maximum Visible Events",
                c.maxVisibleEvents,
                10,
                1,
                v -> c.maxVisibleEvents = v);
        key(view, e, "Previous Page", c.staticPreviousKey, v -> c.staticPreviousKey = v);
        key(view, e, "Next Page", c.staticNextKey, v -> c.staticNextKey = v);
        key(view, e, "Open Static Menu", c.staticMenuKey, v -> c.staticMenuKey = v);
        toggle(
                view,
                e,
                "Show Page Progress",
                c.staticHud,
                true,
                v -> c.staticHud = v,
                "Show chronological action range above the health bar.");
        number(
                view,
                e,
                "Page Message Duration (seconds, 0 = persistent)",
                c.staticHudSeconds,
                10,
                0,
                v -> c.staticHudSeconds = v);
        view.addEntry(
                e.startColorField(text("Page Message Color"), c.staticHudColor)
                        .setDefaultValue(0xFFFF55)
                        .setSaveConsumer(v -> c.staticHudColor = v)
                        .build());
        toggle(
                view,
                e,
                "Show Server Time",
                c.showServerTime,
                true,
                v -> c.showServerTime = v,
                "Show each event timestamp in UTC in static view and replay. Approximate chat times"
                        + " are marked ~; missing times remain unknown.");
        var replay = builder.getOrCreateCategory(text("REPLAY"));
        replay.addEntry(
                e.startTextDescription(
                                text(
                                        "Action reconstruction only; no recorded movement. Keys"
                                            + " apply in the world. Active shortcuts take priority"
                                            + " over vanilla keys."))
                        .build());
        replay.addEntry(new SmartTimelineEntry(c));
        speed(replay, e, "Block Break Speed", c.blockBreakSpeed, v -> c.blockBreakSpeed = v);
        speed(replay, e, "Block Place Speed", c.blockPlaceSpeed, v -> c.blockPlaceSpeed = v);
        speed(replay, e, "Timeline Speed", c.timelineSpeed, v -> c.timelineSpeed = v);
        key(replay, e, "Play / Pause", c.playPauseKey, v -> c.playPauseKey = v);
        key(replay, e, "Forward", c.forwardKey, v -> c.forwardKey = v);
        key(replay, e, "Backward", c.backwardKey, v -> c.backwardKey = v);
        key(replay, e, "Start / Load World", c.replayStartKey, v -> c.replayStartKey = v);
        key(replay, e, "Restart", c.replayRestartKey, v -> c.replayRestartKey = v);
        key(replay, e, "Stop", c.replayStopKey, v -> c.replayStopKey = v);
        key(replay, e, "Open Replay Menu", c.replayMenuKey, v -> c.replayMenuKey = v);
        number(
                replay,
                e,
                "Maximum Visible Events",
                c.replayVisibleEvents,
                10,
                1,
                v -> c.replayVisibleEvents = v);
        toggle(
                replay,
                e,
                "Show Replay Progress",
                c.replayHud,
                true,
                v -> c.replayHud = v,
                "Show reached/total actions and the current visual window. Hidden at completion or"
                        + " after the pause timeout.");
        number(
                replay,
                e,
                "Pause Message Duration (seconds, 0 = persistent)",
                c.replayHudSeconds,
                10,
                0,
                v -> c.replayHudSeconds = v);
        replay.addEntry(
                e.startColorField(text("Replay Message Color"), c.replayHudColor)
                        .setDefaultValue(0xFFFF55)
                        .setSaveConsumer(v -> c.replayHudColor = v)
                        .build());
        var stats = builder.getOrCreateCategory(text("STATISTICS"));
        stats.addEntry(
                e.startTextDescription(
                                text(
                                        "Uses selected CSVs. Event counts and item quantities"
                                                + " remain separate; unknown amounts are not"
                                                + " estimated."))
                        .build());
        stats.addEntry(
                new ActionEntry(
                        "Open Statistics",
                        () -> {
                            var mc = net.minecraft.client.Minecraft.getInstance();
                            mc.gui.setScreen(new StatisticsScreen(mc.gui.screen()));
                        },
                        () -> CoreTraceClient.service.enabled()));
        builder.setSavingRunnable(
                () -> CoreTraceClient.notifyResult(CoreTraceClient.service.configure(c)));
        if (category != null)
            builder.setFallbackCategory(builder.getOrCreateCategory(text(category)));
        return builder.build();
    }

    private static void number(
            ConfigCategory category,
            ConfigEntryBuilder e,
            String label,
            int value,
            int fallback,
            int min,
            Consumer<Integer> save) {
        category.addEntry(
                e.startStrField(text(label), Integer.toString(value))
                        .setDefaultValue(Integer.toString(fallback))
                        .setErrorSupplier(
                                v -> {
                                    try {
                                        if (!v.isBlank() && Integer.parseInt(v.strip()) < min)
                                            throw new IllegalArgumentException();
                                        return java.util.Optional.empty();
                                    } catch (Exception ex) {
                                        return java.util.Optional.of(
                                                text(
                                                        "Enter a whole number >= "
                                                                + min
                                                                + ", or leave blank for "
                                                                + fallback));
                                    }
                                })
                        .setSaveConsumer(
                                v ->
                                        save.accept(
                                                v.isBlank()
                                                        ? fallback
                                                        : Integer.parseInt(v.strip())))
                        .build());
    }

    private static void toggle(
            ConfigCategory category,
            ConfigEntryBuilder e,
            String label,
            boolean value,
            boolean fallback,
            Consumer<Boolean> save,
            String tip) {
        category.addEntry(
                e.startBooleanToggle(text(label), value)
                        .setYesNoTextSupplier(v -> text(v ? "ON" : "OFF"))
                        .setDefaultValue(fallback)
                        .setSaveConsumer(save)
                        .setTooltip(text(tip))
                        .build());
    }

    private static void speed(
            ConfigCategory category,
            ConfigEntryBuilder e,
            String label,
            double value,
            Consumer<Double> save) {
        category.addEntry(
                e.startSelector(
                                text(label + " (x)"),
                                new Double[] {0.25, 0.5, 1.0, 2.0, 4.0},
                                value)
                        .setDefaultValue(1.0)
                        .setSaveConsumer(save)
                        .build());
    }

    private static void key(
            ConfigCategory category,
            ConfigEntryBuilder e,
            String label,
            String value,
            Consumer<String> save) {
        InputConstants.Key key;
        try {
            key = InputConstants.getKey(value);
        } catch (Exception ex) {
            key = InputConstants.UNKNOWN;
        }
        category.addEntry(
                e.startKeyCodeField(text(label), key)
                        .setKeySaveConsumer(v -> save.accept(v.getName()))
                        .build());
    }
}
