package dev.coreviewer.config;

import java.util.Set;

public final class CoreTraceConfig implements Cloneable {
    public CoreTraceConfig copy() {
        try {
            return (CoreTraceConfig) super.clone();
        } catch (CloneNotSupportedException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public int schemaVersion = 1;
    public boolean enabled = true;
    public int eventRadius = 100;
    public boolean autoCapture = true, autoPageAdvance = false;
    public int commandDelayMs = 1500;
    public boolean autoSave = true, backup = true;
    public boolean blockView = true, killView = true, itemView = true;
    public boolean throughWalls = true, outline = true, ghostBlocks = true;
    public boolean arrows = true, playerDeaths = true, mobDeaths = true, playerHeads = false;
    public int breakColor = 0xFF5555, placeColor = 0x55FF99, arrowColor = 0xFFD166;
    public int alpha = 160;
    public boolean labels = true, arrowsSamePlayer = true, tintGhosts = false;
    public int maxVisibleEvents = 10;
    public float arrowSize = 1, arrowSpeed = 1;
    public double blockBreakSpeed = 1, blockPlaceSpeed = 1, timelineSpeed = 1;
    public String playPauseKey = "key.keyboard.right.shift",
            forwardKey = "key.keyboard.comma",
            backwardKey = "key.keyboard.period";
    public boolean resetCaptureOnComplete = true, resetCaptureMessage = true;
    public boolean captureSounds = false;
    public String captureStartSound = "minecraft:block.note_block.pling",
            captureEndSound = "minecraft:entity.player.levelup";
    public boolean clearPreviousCapture = true, clearCaptureMessage = true;
    public boolean staticHud = true, replayHud = true, smartTimeline = true;
    public int staticHudColor = 0xFFFF55,
            replayHudColor = 0xFFFF55,
            staticHudSeconds = 10,
            replayHudSeconds = 10;
    public int replayVisibleEvents = 20;
    public String staticPreviousKey = "key.keyboard.v",
            staticNextKey = "key.keyboard.b",
            staticMenuKey = "key.keyboard.g";
    public String replayStartKey = "key.keyboard.j",
            replayRestartKey = "key.keyboard.k",
            replayStopKey = "key.keyboard.l",
            replayMenuKey = "key.keyboard.h";
    public transient int windowFrom = -1, windowTo = Integer.MAX_VALUE;

    public void validate() {
        if (schemaVersion != 1)
            throw new IllegalArgumentException("Unsupported config schema: " + schemaVersion);
        if (eventRadius < 1) eventRadius = 100;
        if (commandDelayMs < 1) commandDelayMs = 1500;
        alpha = Math.clamp(alpha, 0, 255);
        if (maxVisibleEvents < 1) maxVisibleEvents = 10;
        if (replayVisibleEvents < 1) replayVisibleEvents = 20;
        if (staticHudSeconds < 0) staticHudSeconds = 10;
        if (replayHudSeconds < 0) replayHudSeconds = 10;
        staticHudColor &= 0xFFFFFF;
        replayHudColor &= 0xFFFFFF;
        breakColor &= 0xFFFFFF;
        placeColor &= 0xFFFFFF;
        arrowColor &= 0xFFFFFF;
        arrowSize = Float.isFinite(arrowSize) ? Math.clamp(arrowSize, 0.25f, 4) : 1;
        arrowSpeed = Float.isFinite(arrowSpeed) ? Math.clamp(arrowSpeed, 0.25f, 4) : 1;
        var speeds = Set.of(0.25, 0.5, 1.0, 2.0, 4.0);
        if (!speeds.contains(blockBreakSpeed)) blockBreakSpeed = 1;
        if (!speeds.contains(blockPlaceSpeed)) blockPlaceSpeed = 1;
        if (!speeds.contains(timelineSpeed)) timelineSpeed = 1;
        if (playPauseKey == null || playPauseKey.isBlank())
            playPauseKey = "key.keyboard.right.shift";
        if (forwardKey == null || forwardKey.isBlank()) forwardKey = "key.keyboard.comma";
        if (backwardKey == null || backwardKey.isBlank()) backwardKey = "key.keyboard.period";
    }
}
