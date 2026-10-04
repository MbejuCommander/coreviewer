package dev.coreviewer.replay;

import com.mojang.blaze3d.platform.InputConstants;

import dev.coreviewer.CoreTraceClient;
import dev.coreviewer.config.CoreTraceConfig;
import dev.coreviewer.view.StaticView;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.time.*;
import java.time.format.DateTimeFormatter;

public final class ReplayController {
    public static final ReplayEngine engine = new ReplayEngine();
    private static KeyMapping play,
            forward,
            backward,
            start,
            restart,
            stop,
            staticPrevious,
            staticNext,
            staticMenu,
            replayMenu;
    private static final java.util.Set<KeyMapping> pendingKeys = new java.util.HashSet<>();

    public static boolean handleKey(int action, net.minecraft.client.input.KeyEvent event) {
        if (play == null || CoreTraceClient.service == null || !CoreTraceClient.service.enabled())
            return false;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.gui.screen() != null || !mc.isWindowActive()) return false;
        var eligible =
                new java.util.ArrayList<KeyMapping>(
                        java.util.List.of(start, staticMenu, replayMenu));
        if (active()) eligible.addAll(java.util.List.of(play, forward, backward, restart, stop));
        else if (StaticView.active())
            eligible.addAll(java.util.List.of(staticPrevious, staticNext));
        boolean handled = false;
        for (var binding : eligible)
            if (binding.matches(event)) {
                handled = true;
                if (action == InputConstants.PRESS) pendingKeys.add(binding);
            }
        return handled;
    }

    private static long pausedAt;
    private static boolean wasPlaying;
    private static long lastTick;
    private static dev.coreviewer.view.EventIndex index = dev.coreviewer.view.EventIndex.EMPTY;

    public static dev.coreviewer.view.EventIndex index() {
        return index;
    }

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS 'UTC'").withZone(ZoneOffset.UTC);

    public static boolean active() {
        return engine.loaded() && StaticView.active();
    }

    public static void stop() {
        index = dev.coreviewer.view.EventIndex.EMPTY;
        engine.loadPrepared(new dev.coreviewer.view.EventIndex.Timeline(java.util.List.of(), false));
        lastTick = 0;
    }

    public static String load(String world, boolean demo) {
        String message = demo ? StaticView.preview() : StaticView.show(world);
        if (!StaticView.active() || (!demo && !StaticView.world().equals(world))) return message;
        index = StaticView.sourceIndex();
        engine.loadPrepared(
                index.timeline(StaticView.server(), StaticView.world(), StaticView.isDemo()));
        lastTick = 0;
        pausedAt = System.nanoTime();
        return engine.loaded()
                ? "Loaded " + engine.events().size() + " actions. Paused before the first event."
                : "No positioned actions to replay.";
    }

    public static String time() {
        return (engine.approximate() ? "~ " : "")
                + TIME.format(Instant.ofEpochMilli((long) engine.cursor()));
    }

    public static void register(CoreTraceConfig c) {
        var category =
                KeyMapping.Category.register(
                        Identifier.fromNamespaceAndPath("coreviewer", "replay"));
        play =
                KeyMappingHelper.registerKeyMapping(
                        new KeyMapping("key.coreviewer.play", InputConstants.KEY_RSHIFT, category));
        forward =
                KeyMappingHelper.registerKeyMapping(
                        new KeyMapping(
                                "key.coreviewer.forward", InputConstants.KEY_COMMA, category));
        backward =
                KeyMappingHelper.registerKeyMapping(
                        new KeyMapping(
                                "key.coreviewer.backward", InputConstants.KEY_PERIOD, category));
        start = key("start", InputConstants.KEY_J, category);
        restart = key("restart", InputConstants.KEY_K, category);
        stop = key("stop", InputConstants.KEY_L, category);
        staticPrevious = key("previous_page", InputConstants.KEY_V, category);
        staticNext = key("next_page", InputConstants.KEY_B, category);
        staticMenu = key("static_menu", InputConstants.KEY_G, category);
        replayMenu = key("replay_menu", InputConstants.KEY_H, category);
        configure(c);
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("coreviewer", "replay"),
                (g, dt) -> {
                    if (!CoreTraceClient.service.enabled()) return;
                    var settings = CoreTraceClient.service.config();
                    var client = Minecraft.getInstance();
                    if (StaticView.active()
                            && !active()
                            && settings.staticHud
                            && (settings.staticHudSeconds == 0
                                    || System.nanoTime() - StaticView.pageShown()
                                            < settings.staticHudSeconds * 1e9)) {
                        long from = (long) StaticView.page() * settings.maxVisibleEvents,
                                to = Math.min(StaticView.total(), from + settings.maxVisibleEvents);
                        g.centeredText(
                                client.font,
                                "Actions "
                                        + (to == 0 ? 0 : from + 1)
                                        + "–"
                                        + to
                                        + "/"
                                        + StaticView.total(),
                                g.guiWidth() / 2,
                                g.guiHeight() - 64,
                                0xFF000000 | settings.staticHudColor);
                    }
                    if (!active()) return;
                    int reached = engine.visibleCount(), total = engine.events().size();
                    if (settings.replayHud
                            && reached < total
                            && (engine.playing()
                                    || settings.replayHudSeconds == 0
                                    || System.nanoTime() - pausedAt
                                            < settings.replayHudSeconds * 1e9))
                        g.centeredText(
                                client.font,
                                "Actions "
                                        + reached
                                        + "/"
                                        + total
                                        + " | Window: "
                                        + Math.min(reached, settings.replayVisibleEvents),
                                g.guiWidth() / 2,
                                g.guiHeight() - 78,
                                0xFF000000 | settings.replayHudColor);
                    var mc = Minecraft.getInstance();
                    String status =
                            (StaticView.isDemo() ? "DEMO | " : "")
                                    + "REPLAY "
                                    + (engine.playing() ? "PLAYING" : "PAUSED")
                                    + " | "
                                    + Math.round(engine.fraction() * 100)
                                    + "% | "
                                    + CoreTraceClient.service.config().timelineSpeed
                                    + "x";
                    g.text(mc.font, status, 8, 8, 0xFFFFD166);
                    g.text(mc.font, time(), 8, 20, 0xFFFFFFFF);
                    g.text(
                            mc.font,
                            play.getTranslatedKeyMessage().getString()
                                    + ": play/pause | "
                                    + forward.getTranslatedKeyMessage().getString()
                                    + ": forward | "
                                    + backward.getTranslatedKeyMessage().getString()
                                    + ": backward",
                            8,
                            32,
                            0xFFFFFFFF);
                    g.text(
                            mc.font,
                            label(start)
                                    + ": start | "
                                    + label(restart)
                                    + ": restart | "
                                    + label(stop)
                                    + ": stop",
                            8,
                            44,
                            0xFFFFFFFF);
                    g.text(
                            mc.font,
                            label(replayMenu)
                                    + ": replay menu | "
                                    + label(staticMenu)
                                    + ": static menu",
                            8,
                            56,
                            0xFFFFFFFF);
                });
    }

    public static void configure(CoreTraceConfig c) {
        if (play == null) return;
        set(play, c.playPauseKey);
        set(forward, c.forwardKey);
        set(backward, c.backwardKey);
        set(start, c.replayStartKey);
        set(restart, c.replayRestartKey);
        set(stop, c.replayStopKey);
        set(staticPrevious, c.staticPreviousKey);
        set(staticNext, c.staticNextKey);
        set(staticMenu, c.staticMenuKey);
        set(replayMenu, c.replayMenuKey);
        KeyMapping.resetMapping();
    }

    private static void set(KeyMapping mapping, String value) {
        try {
            mapping.setKey(InputConstants.getKey(value));
        } catch (RuntimeException ex) {
            mapping.setKey(InputConstants.UNKNOWN);
        }
    }

    private static KeyMapping key(String name, int code, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.coreviewer." + name, code, category));
    }

    private static String label(KeyMapping key) {
        return key.getTranslatedKeyMessage().getString();
    }

    private static boolean pressed(KeyMapping key) {
        boolean value = pendingKeys.remove(key);
        while (key.consumeClick()) value = true;
        return value;
    }

    public static void startSelected() {
        var worlds = StaticView.worlds();
        if (StaticView.active() && StaticView.isDemo()) {
            load("", true);
            return;
        }
        if (worlds.contains(StaticView.world()))
            CoreTraceClient.tell(load(StaticView.world(), false));
        else if (worlds.size() == 1) CoreTraceClient.tell(load(worlds.getFirst(), false));
        else {
            var mc = Minecraft.getInstance();
            mc.gui.setScreen(new dev.coreviewer.ui.ReplayScreen(null));
        }
    }

    public static void tick() {
        long now = System.nanoTime();
        double elapsed = lastTick == 0 ? 0 : (now - lastTick) / 1e6;
        lastTick = now;
        boolean toggle = pressed(play),
                next = pressed(forward),
                previous = pressed(backward),
                begin = pressed(start),
                again = pressed(restart),
                end = pressed(stop);
        boolean prevPage = pressed(staticPrevious),
                nextPage = pressed(staticNext),
                openStatic = pressed(staticMenu),
                openReplay = pressed(replayMenu);
        var mc = Minecraft.getInstance();
        if (!CoreTraceClient.service.enabled()) return;
        if (mc.gui.screen() != null || !mc.isWindowActive()) {
            engine.pause();
        } else if (mc.level != null) {
            if (openStatic) mc.gui.setScreen(new dev.coreviewer.ui.StaticViewScreen(null));
            else if (openReplay) mc.gui.setScreen(new dev.coreviewer.ui.ReplayScreen(null));
            else {
                if (begin) startSelected();
                if (end) StaticView.hide();
                if (prevPage) StaticView.changePage(-1);
                if (nextPage) StaticView.changePage(1);
                if (active()) {
                    engine.advance(elapsed, CoreTraceClient.service.config());
                    if (toggle) engine.toggle();
                    if (next) engine.next();
                    if (previous) engine.previous();
                    if (again) engine.seekFraction(0);
                }
            }
        }
        if (engine.playing() || wasPlaying || pausedAt == 0) pausedAt = now;
        wasPlaying = engine.playing();
    }
}
