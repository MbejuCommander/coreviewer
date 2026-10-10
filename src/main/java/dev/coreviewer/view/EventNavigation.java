package dev.coreviewer.view;

import dev.coreviewer.CoreTraceClient;
import dev.coreviewer.model.*;
import dev.coreviewer.replay.ReplayController;

import net.minecraft.client.Minecraft;

import java.util.*;

/** One command per target, with a delay; never retries a denied or failed teleport. */
public final class EventNavigation {
    private static UUID last;
    private static long sentAt;
    private static Object teleportConnection;
    private static long teleportUntil;

    public static void reset() {
        last = null;
        teleportConnection = null;
        teleportUntil = 0;
    }

    public static boolean consumeWorldChange(Object connection) {
        boolean expected =
                connection == teleportConnection && System.currentTimeMillis() < teleportUntil;
        teleportConnection = null;
        teleportUntil = 0;
        return expected;
    }

    public static String command(String template, CoreTraceEvent event) {
        var c = event.context();
        var p = c.position();
        if (p == null || c.world().isBlank() || !c.world().matches("[A-Za-z0-9_.:/-]+"))
            throw new IllegalArgumentException("World or coordinates unavailable");
        String result =
                template.replace("{world}", c.world())
                        .replace("{x}", Integer.toString(p.x()))
                        .replace("{y}", Integer.toString(p.y()))
                        .replace("{z}", Integer.toString(p.z()))
                        .strip();
        if (result.startsWith("/")) result = result.substring(1);
        if (result.isBlank()
                || result.contains("\n")
                || result.contains("\r")
                || result.contains("{")
                || result.contains("}"))
            throw new IllegalArgumentException("Invalid teleport command template");
        return result;
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        var config = CoreTraceClient.service.config();
        if (!config.enabled
                || !config.teleportToEvents
                || config.respectRadius
                || !StaticView.active()
                || StaticView.isDemo()
                || mc.player == null
                || mc.getConnection() == null
                || mc.gui.screen() != null
                || CoreTraceClient.capture.active()) return;
        var events =
                StaticView.index()
                        .timeline(StaticView.server(), StaticView.world(), false)
                        .events();
        int at =
                ReplayController.active()
                        ? Math.max(0, ReplayController.engine.visibleCount() - 1)
                        : StaticView.page() * config.maxVisibleEvents;
        if (at >= events.size()) return;
        var event = events.get(at);
        if (event.context().id().equals(last) || event.context().position() == null) return;
        if (StaticView.within(
                event, mc.player.getX(), mc.player.getY(), mc.player.getZ(), config.eventRadius)) {
            last = event.context().id();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - sentAt < Math.max(1500, config.commandDelayMs)) return;
        last = event.context().id();
        sentAt = now;
        try {
            String command = command(config.teleportCommand, event);
            teleportConnection = mc.getConnection();
            teleportUntil = now + 10000;
            mc.getConnection().sendCommand(command);
        } catch (IllegalArgumentException ex) {
            CoreTraceClient.tell("Teleport skipped: " + ex.getMessage());
        }
    }
}
