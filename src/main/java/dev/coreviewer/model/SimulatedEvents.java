package dev.coreviewer.model;

import java.util.*;

/** Always local and explicitly synthetic; never samples a real player or changes a world. */
public final class SimulatedEvents {
    private SimulatedEvents() {}

    public static List<CoreTraceEvent> create(long now) {
        return List.of(
                new BlockEvent(
                        context(now - 5000, 0, "DemoModerator"),
                        EventType.BLOCK_BREAK,
                        "minecraft:diamond_ore"),
                new BlockEvent(
                        context(now - 4000, 1, "DemoModerator"),
                        EventType.BLOCK_PLACE,
                        "minecraft:stone"),
                new KillEvent(
                        context(now - 3000, 2, "DemoZombie"),
                        EventType.PLAYER_KILL,
                        "DemoVictim",
                        null,
                        "minecraft:player",
                        "Killed by Zombie (simulated)"),
                new KillEvent(
                        context(now - 2000, 3, "DemoModerator"),
                        EventType.MOB_KILL,
                        null,
                        null,
                        "minecraft:zombie",
                        "Killed by Player (simulated)"),
                new ItemEvent(
                        context(now - 1000, 4, "DemoModerator"),
                        EventType.ITEM_ADD,
                        "minecraft:diamond",
                        3),
                new ItemEvent(
                        context(now, 5, "DemoModerator"),
                        EventType.ITEM_REMOVE,
                        "minecraft:diamond",
                        2));
    }

    private static EventContext context(long time, int step, String actor) {
        return new EventContext(
                UUID.randomUUID(),
                time,
                "simulation",
                "simulation_world",
                "minecraft:overworld",
                new EventContext.Position(100 + step, 64, 100),
                actor,
                null,
                true,
                "SIMULATED");
    }
}
