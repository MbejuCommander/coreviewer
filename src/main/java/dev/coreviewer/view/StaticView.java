package dev.coreviewer.view;

import dev.coreviewer.CoreTraceClient;
import dev.coreviewer.model.*;
import dev.coreviewer.replay.ReplayController;

import net.minecraft.client.Minecraft;

import java.util.*;

/** Session-only binding; never infers a server world from a client dimension name. */
public final class StaticView {
    private static Object level, connection;
    private static String world = "", server = "";
    private static List<CoreTraceEvent> demo = List.of();
    private static EventIndex demoIndex = EventIndex.EMPTY;
    private static boolean enabled;
    private static int page;
    private static long pageShown;
    private static EventIndex pageIndex;

    public static int total() {
        return sourceIndex().timeline(server, world, isDemo()).events().size();
    }

    public static int page() {
        if (pageIndex != sourceIndex()) {
            pageIndex = sourceIndex();
            page = 0;
            pageShown = System.nanoTime();
        }
        int size = CoreTraceClient.service.config().maxVisibleEvents;
        page = Math.min(page, Math.max(0, (total() - 1) / size));
        return page;
    }

    public static void changePage(int delta) {
        if (!active() || ReplayController.active()) return;
        int current = page();
        int size = CoreTraceClient.service.config().maxVisibleEvents;
        page = (int) Math.clamp((long) current + delta, 0, Math.max(0, (total() - 1) / size));
        pageShown = System.nanoTime();
    }

    public static long pageShown() {
        page();
        return pageShown;
    }

    public static String status = "Static View is hidden.";

    public static boolean active() {
        var mc = Minecraft.getInstance();
        return enabled
                && CoreTraceClient.service.enabled()
                && mc.level == level
                && mc.getConnection() == connection
                && level != null;
    }

    public static void hide() {
        ReplayController.stop();
        StaticRenderer.reset();
        enabled = false;
        level = null;
        connection = null;
        demo = List.of();
        demoIndex = EventIndex.EMPTY;
        status = "Static View is hidden.";
    }

    public static String world() {
        return world;
    }

    public static String server() {
        return server;
    }

    public static boolean isDemo() {
        return !demo.isEmpty();
    }

    public static List<CoreTraceEvent> events() {
        return ReplayController.active() ? ReplayController.engine.visible() : sourceEvents();
    }

    public static List<CoreTraceEvent> sourceEvents() {
        return isDemo() ? demo : CoreTraceClient.service.events();
    }

    public static EventIndex sourceIndex() {
        return isDemo() ? demoIndex : CoreTraceClient.service.snapshot().index();
    }

    public static EventIndex index() {
        return ReplayController.active() ? ReplayController.index() : sourceIndex();
    }

    public static List<String> worlds() {
        return CoreTraceClient.service.snapshot().index().worlds(CoreTraceClient.serverIdentity());
    }

    public static String show(String selectedWorld) {
        var mc = Minecraft.getInstance();
        if (!CoreTraceClient.service.enabled()) return "Enable Coreviewer first.";
        if (mc.level == null) return "Join a world first.";
        if (!worlds().contains(selectedWorld))
            return "No positioned records for this world/server.";
        demo = List.of();
        demoIndex = EventIndex.EMPTY;
        bind(selectedWorld);
        return status = "Showing " + world + " in " + mc.level.dimension().identifier();
    }

    private static void bind(String selectedWorld) {
        ReplayController.stop();
        page = 0;
        pageIndex = null;
        pageShown = System.nanoTime();
        var mc = Minecraft.getInstance();
        level = mc.level;
        connection = mc.getConnection();
        server = CoreTraceClient.serverIdentity();
        world = selectedWorld;
        enabled = true;
    }

    public static String preview() {
        var mc = Minecraft.getInstance();
        if (!CoreTraceClient.service.enabled() || mc.player == null)
            return "Join a world and enable Coreviewer first.";
        bind("SIMULATED_PREVIEW");
        var origin = mc.player.blockPosition();
        var list = new ArrayList<CoreTraceEvent>();
        long now = System.currentTimeMillis();
        String[] blocks = {"minecraft:diamond_ore", "minecraft:stone", "minecraft:oak_planks"};
        for (int i = 0; i < 7; i++) {
            var p =
                    new EventContext.Position(
                            origin.getX() - 6 + i * 2, origin.getY(), origin.getZ() + 12);
            var ctx =
                    new EventContext(
                            UUID.randomUUID(),
                            now - 7000 + i * 1000,
                            server,
                            world,
                            mc.level.dimension().identifier().toString(),
                            p,
                            "DemoModerator",
                            null,
                            true,
                            "SIMULATED");
            list.add(
                    switch (i) {
                        case 0, 1, 2 ->
                                new BlockEvent(
                                        ctx,
                                        i == 2 ? EventType.BLOCK_PLACE : EventType.BLOCK_BREAK,
                                        blocks[i]);
                        case 3 ->
                                new KillEvent(
                                        ctx,
                                        EventType.PLAYER_KILL,
                                        "DemoVictim",
                                        null,
                                        "minecraft:player",
                                        null);
                        case 4 ->
                                new KillEvent(
                                        ctx,
                                        EventType.MOB_KILL,
                                        null,
                                        null,
                                        "minecraft:zombie",
                                        null);
                        default ->
                                new ItemEvent(
                                        ctx,
                                        i == 5 ? EventType.ITEM_ADD : EventType.ITEM_REMOVE,
                                        "minecraft:diamond",
                                        i);
                    });
        }
        demo = List.copyOf(list);
        demoIndex = new EventIndex(demo);
        return status = "SIMULATED PREVIEW — temporary, not saved. Look toward +Z.";
    }
}
