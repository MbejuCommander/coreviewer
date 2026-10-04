package dev.coreviewer.capture;

import dev.coreviewer.config.CoreTraceConfig;

import java.util.*;
import java.util.function.*;
import java.util.regex.*;

/** Client-thread state machine. Only one read-only request can be outstanding. */
public final class CaptureEngine {
    private static final Pattern QUERY =
            Pattern.compile(
                    "/?(?:co|coreprotect) (?:lookup|l|near)(?: .*)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAGE = Pattern.compile("\\bPage (\\d+)/(\\d+)\\b");
    private final Supplier<CoreTraceConfig> settings;
    private final Consumer<String> sender;
    private final BiConsumer<List<ChatLine>, String> sink;
    private final List<ChatLine> lines = new ArrayList<>();
    private final Set<Integer> pages = new HashSet<>();
    private String server = "", queued;
    private long due, lastCommand, lastMessage, deadline;
    private boolean active, automatic, sending;
    private int expected;
    private long session;
    private Consumer<Boolean> started = a -> {}, completed = a -> {};

    public void lifecycle(Consumer<Boolean> started, Consumer<Boolean> completed) {
        this.started = started;
        this.completed = completed;
    }

    public long session() {
        return session;
    }

    public String server() {
        return server;
    }

    private String status = "Idle. Run /co lookup, /co l or /co near.";

    public CaptureEngine(
            Supplier<CoreTraceConfig> settings,
            Consumer<String> sender,
            BiConsumer<List<ChatLine>, String> sink) {
        this.settings = settings;
        this.sender = sender;
        this.sink = sink;
    }

    public String status() {
        return status;
    }

    public boolean active() {
        return active;
    }

    public void stop(String reason) {
        session++;
        active = false;
        queued = null;
        lines.clear();
        pages.clear();
        status = reason;
    }

    public void configurationChanged() {
        var c = settings.get();
        if (!c.enabled || !c.autoCapture) stop("Capture disabled.");
        else if (!c.autoPageAdvance) {
            queued = null;
            automatic = false;
        }
    }

    public void command(String command, String server, long now) {
        if (sending) return;
        if (!QUERY.matcher(command).matches()) {
            if (command.matches("/?(?:co|coreprotect)(?: .*)?"))
                stop("Another CoreProtect command interrupted capture.");
            return;
        }
        var c = settings.get();
        if (!c.enabled || !c.autoCapture) return;
        flush();
        stop("Waiting for CoreProtect...");
        this.server = server;
        active = true;
        automatic = c.autoPageAdvance;
        lastCommand = lastMessage = now;
        deadline = now + 30000;
        expected = 0;
        started.accept(automatic);
    }

    public String start(String query, String server, long now) {
        var c = settings.get();
        if (!c.enabled || !c.autoCapture) return "Enable Coreviewer and Auto Capture first.";
        if (!c.autoPageAdvance)
            return "Passive mode: run /co lookup manually or enable AUTO PAGE ADVANCE.";
        if (!QUERY.matcher(query).matches()
                || query.length() > 256
                || query.indexOf('\n') >= 0
                || query.indexOf('\r') >= 0)
            return "Only co lookup, co l and co near are accepted.";
        long previous = lastCommand;
        command(query, server, now);
        queued = query.startsWith("/") ? query.substring(1) : query;
        due = Math.max(now, previous + c.commandDelayMs);
        deadline = due + 30000;
        return status = "Lookup queued.";
    }

    public void receive(ChatLine line) {
        if (!active) return;
        configurationChanged();
        if (!active) return;
        String text = CoreProtectChatParser.clean(line.text());
        if (text.startsWith("[Coreviewer]")) return;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("no results")
                || lower.contains("no data")
                || lower.contains("do not have permission")
                || lower.contains("please wait before")
                || lower.contains("invalid parameter")) {
            flush();
            stop("CoreProtect ended lookup: " + text);
            return;
        }
        if (lines.size() >= 4096) {
            stop("Capture stopped: response limit exceeded.");
            return;
        }
        if (text.contains(" ago ") || text.contains("(x")) {
            lines.add(line);
            lastMessage = line.receivedAt();
        }
        var page = PAGE.matcher(text);
        if (!page.find()) return;
        try {
            int current = Integer.parseInt(page.group(1)), total = Integer.parseInt(page.group(2));
            if (current < 1
                    || total < current
                    || total > 10000
                    || (expected != 0 && current != expected)
                    || !pages.add(current)) {
                stop("Capture stopped: unexpected or repeated page.");
                return;
            }
            flush();
            status = "Captured page " + current + "/" + total;
            if (current == total) {
                active = false;
                queued = null;
                completed.accept(automatic);
                return;
            }
            if (automatic) {
                queued = "co l " + (current + 1);
                expected = current + 1;
                due = Math.max(lastCommand, line.receivedAt()) + settings.get().commandDelayMs;
                deadline = due + 30000;
            }
        } catch (NumberFormatException ex) {
            stop("Invalid pagination.");
        }
    }

    public void tick(long now) {
        if (!active) return;
        configurationChanged();
        if (!active) return;
        if (now >= deadline) {
            flush();
            stop("Lookup finished or timed out; no commands retried.");
            return;
        }
        if (!lines.isEmpty() && now - lastMessage >= 1500) flush();
        if (queued != null && now >= due && automatic) {
            String next = queued;
            queued = null;
            lastCommand = now;
            deadline = now + 30000;
            sending = true;
            try {
                sender.accept(next);
                status = "Waiting for /" + next;
            } catch (RuntimeException ex) {
                stop("Command failed: " + ex.getMessage());
            } finally {
                sending = false;
            }
        }
    }

    private void flush() {
        if (!lines.isEmpty()) {
            sink.accept(List.copyOf(lines), server);
            lines.clear();
        }
    }
}
