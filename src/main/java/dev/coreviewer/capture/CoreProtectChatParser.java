package dev.coreviewer.capture;

import dev.coreviewer.model.*;

import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;

/** Parses English CoreProtect lookup rows, including their separate coordinate line. */
public final class CoreProtectChatParser {
    private static final Pattern ROW =
            Pattern.compile(
                    "^([0-9]+(?:[.,][0-9]+)?)/?([mhd]) ago [+-] (\\S+)"
                            + " (broke|placed|killed|added|removed|picked"
                            + " up|dropped|deposited|withdrew|threw|shot) (?:x([0-9]+)"
                            + " )?([\\w:#.-]+?)(?:\\s*\\(↓\\))?\\.(?:\\s.*)?$");
    private static final Pattern POS =
            Pattern.compile("\\(x(-?\\d+)/y(-?\\d+)/z(-?\\d+)/([^()]+)\\)");
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.US);

    public record Result(List<CoreTraceEvent> events, int skipped) {}

    public Result parse(List<ChatLine> lines, String server, Set<String> entityNames) {
        var events = new ArrayList<CoreTraceEvent>();
        ChatLine pending = null;
        int skipped = 0;
        for (ChatLine line : lines) {
            String text = clean(line.text());
            if (ROW.matcher(text).matches()) {
                if (pending != null) {
                    try {
                        events.add(event(pending, null, server, entityNames));
                    } catch (RuntimeException ex) {
                        skipped++;
                    }
                }
                pending = line;
            } else if (text.contains(" ago ")) {
                if (pending != null) skipped++;
                pending = null;
                skipped++;
            }
            var pos = POS.matcher(text);
            if (pending != null && pos.find()) {
                try {
                    events.add(event(pending, pos, server, entityNames));
                } catch (RuntimeException ex) {
                    skipped++;
                }
                pending = null;
            }
        }
        if (pending != null) {
            try {
                events.add(event(pending, null, server, entityNames));
            } catch (RuntimeException ex) {
                skipped++;
            }
        }
        return new Result(List.copyOf(events), skipped);
    }

    private CoreTraceEvent event(ChatLine row, Matcher pos, String server, Set<String> entities) {
        var m = ROW.matcher(clean(row.text()));
        if (!m.matches()) throw new IllegalArgumentException();
        Long exact = null;
        for (String hover : row.hover()) {
            try {
                exact = ZonedDateTime.parse(clean(hover), DATE).toInstant().toEpochMilli();
                break;
            } catch (DateTimeException ignored) {
            }
        }
        double unit =
                switch (m.group(2)) {
                    case "m" -> 60000;
                    case "h" -> 3600000;
                    default -> 86400000;
                };
        long timestamp =
                exact != null
                        ? exact
                        : Math.max(
                                0,
                                row.receivedAt()
                                        - (long)
                                                (Double.parseDouble(m.group(1).replace(',', '.'))
                                                        * unit));
        String verb = m.group(4), material = m.group(6);
        var context =
                new EventContext(
                        UUID.randomUUID(),
                        timestamp,
                        server,
                        pos == null ? "" : pos.group(4),
                        null,
                        pos == null
                                ? null
                                : new EventContext.Position(
                                        Integer.parseInt(pos.group(1)),
                                        Integer.parseInt(pos.group(2)),
                                        Integer.parseInt(pos.group(3))),
                        m.group(3),
                        null,
                        false,
                        (exact == null ? "COREPROTECT_CHAT_APPROXIMATE" : "COREPROTECT_CHAT")
                                + " | "
                                + clean(row.text()));
        String identifier = material.contains(":") ? material : "minecraft:" + material;
        if (verb.equals("broke") || verb.equals("placed"))
            return new BlockEvent(
                    context,
                    verb.equals("broke") ? EventType.BLOCK_BREAK : EventType.BLOCK_PLACE,
                    identifier);
        if (verb.equals("killed")) {
            // CoreProtect renders mob registry identifiers but player usernames without a type tag.
            boolean mob = entities.contains(identifier);
            return new KillEvent(
                    context,
                    mob ? EventType.MOB_KILL : EventType.PLAYER_KILL,
                    mob ? null : material,
                    null,
                    mob ? identifier : "minecraft:player",
                    null);
        }
        if (m.group(5) == null) throw new IllegalArgumentException("Missing quantity");
        boolean add = Set.of("added", "picked up", "withdrew").contains(verb);
        return new ItemEvent(
                context,
                add ? EventType.ITEM_ADD : EventType.ITEM_REMOVE,
                identifier,
                Integer.parseInt(m.group(5)));
    }

    public static String clean(String text) {
        return text.replaceAll("§[0-9a-fk-or]", "").strip();
    }
}
