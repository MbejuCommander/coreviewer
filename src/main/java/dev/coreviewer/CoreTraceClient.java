package dev.coreviewer;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

import com.mojang.brigadier.arguments.StringArgumentType;

import dev.coreviewer.capture.*;
import dev.coreviewer.config.*;
import dev.coreviewer.replay.ReplayController;
import dev.coreviewer.storage.LegacyDataMigration;
import dev.coreviewer.ui.*;
import dev.coreviewer.view.*;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.message.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.*;

import org.slf4j.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class CoreTraceClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("Coreviewer");
    public static InvestigationService service;
    public static CaptureEngine capture;
    public static Path directory;
    public static String startupError = "";
    private Object lastLevel;

    @Override
    public void onInitializeClient() {
        Path gameDirectory = FabricLoader.getInstance().getGameDir();
        directory = gameDirectory.resolve("coreviewer");
        CoreTraceConfig config;
        try {
            boolean imported =
                    LegacyDataMigration.copyIfMissing(
                            gameDirectory.resolve("coretrace"), directory);
            config = new ConfigStore(directory).load();
            if (imported) {
                config.autoCapture = false;
                config.autoPageAdvance = false;
                new ConfigStore(directory).save(config);
            }
        } catch (Exception ex) {
            LOG.error("Configuration could not be loaded", ex);
            startupError = ex.getMessage();
            config = new CoreTraceConfig();
            config.enabled = false;
        }
        service = new InvestigationService(directory, config);
        Set<String> entities = new HashSet<>();
        BuiltInRegistries.ENTITY_TYPE.keySet().forEach(id -> entities.add(id.toString()));
        capture =
                new CaptureEngine(
                        service::config,
                        command -> {
                            var connection = Minecraft.getInstance().getConnection();
                            if (connection == null)
                                throw new IllegalStateException("Not connected");
                            connection.sendCommand(command);
                        },
                        (lines, server) -> service.capture(lines, server, entities));
        var gameClient = Minecraft.getInstance();
        capture.lifecycle(
                automatic -> {
                    service.beginCapture(automatic, capture.server());
                    var c = service.config();
                    if (c.captureSounds) SoundPickerScreen.play(c.captureStartSound);
                },
                automatic -> {
                    long session = capture.session();
                    var original = service.config();
                    service.finishCapture()
                            .thenAccept(
                                    message ->
                                            gameClient.execute(
                                                    () -> {
                                                        if (capture.session() != session
                                                                || !service.enabled()) return;
                                                        if (message.startsWith("Storage error")
                                                                || message.equals(
                                                                        "Capture cancelled.")) {
                                                            tell(message);
                                                            return;
                                                        }
                                                        if (original.captureSounds)
                                                            SoundPickerScreen.play(
                                                                    original.captureEndSound);
                                                        if (automatic
                                                                && original.clearPreviousCapture
                                                                && original.clearCaptureMessage)
                                                            tell(message);
                                                        if (automatic
                                                                && original.resetCaptureOnComplete) {
                                                            var c = service.config();
                                                            c.autoCapture = false;
                                                            c.autoPageAdvance = false;
                                                            service.configure(c);
                                                            if (original.resetCaptureMessage)
                                                                tell(
                                                                        "Capture complete. Auto"
                                                                            + " Capture and Auto"
                                                                            + " Page Advance are"
                                                                            + " now OFF.");
                                                        }
                                                    }));
                });
        service.onConfigurationChanged(
                () -> {
                    capture.configurationChanged();
                    gameClient.execute(
                            () -> {
                                ReplayController.configure(service.config());
                                if (!service.enabled()) StaticView.hide();
                            });
                });
        StaticRenderer.register();
        ReplayController.register(config);
        if (config.enabled) service.initialize();
        ClientLifecycleEvents.CLIENT_STARTED.register(
                mc -> ReplayController.configure(service.config()));
        ClientLifecycleEvents.CLIENT_STOPPING.register(
                mc -> {
                    StaticView.hide();
                    service.close();
                });
        ClientSendMessageEvents.COMMAND.register(
                command -> capture.command(command, serverIdentity(), System.currentTimeMillis()));
        ClientReceiveMessageEvents.GAME.register(
                (message, overlay) -> {
                    if (overlay || !capture.active() || !service.enabled()) return;
                    var hover = new ArrayList<String>();
                    message.visit(
                            (style, part) -> {
                                if (style.getHoverEvent() instanceof HoverEvent.ShowText text)
                                    hover.add(text.value().getString());
                                return Optional.empty();
                            },
                            Style.EMPTY);
                    capture.receive(
                            new ChatLine(message.getString(), hover, System.currentTimeMillis()));
                });
        ClientTickEvents.END_CLIENT_TICK.register(
                mc -> {
                    ReplayController.tick();
                    if (!service.enabled()) return;
                    if (mc.level != lastLevel) {
                        stopCapture("World changed.");
                        lastLevel = mc.level;
                        StaticView.hide();
                    }
                    capture.tick(System.currentTimeMillis());
                });
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, mc) -> {
                    stopCapture("Disconnected.");
                    StaticView.hide();
                });
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registry) -> {
                    var root =
                            literal("coreviewer")
                                    .executes(
                                            ctx -> {
                                                openDashboard();
                                                return 1;
                                            });
                    root.then(
                            literal("config")
                                    .executes(
                                            ctx -> {
                                                var mc = Minecraft.getInstance();
                                                mc.schedule(
                                                        () ->
                                                                mc.gui.setScreen(
                                                                        CoreTraceConfigScreen
                                                                                .create(
                                                                                        mc.gui
                                                                                                .screen())));
                                                return 1;
                                            }));
                    root.then(
                            literal("simulate")
                                    .executes(
                                            ctx -> {
                                                notifyResult(service.simulate());
                                                return 1;
                                            }));
                    root.then(
                            literal("save")
                                    .executes(
                                            ctx -> {
                                                notifyResult(service.save());
                                                return 1;
                                            }));
                    root.then(
                            literal("reload")
                                    .executes(
                                            ctx -> {
                                                stopCapture("Reloading.");
                                                StaticView.hide();
                                                notifyResult(service.reload());
                                                return 1;
                                            }));
                    root.then(
                            literal("clear")
                                    .executes(
                                            ctx -> {
                                                stopCapture("History cleared.");
                                                StaticView.hide();
                                                notifyResult(service.clear());
                                                return 1;
                                            }));
                    root.then(
                            literal("stop")
                                    .executes(
                                            ctx -> {
                                                stopCapture("Capture stopped.");
                                                tell(capture.status());
                                                return 1;
                                            }));
                    root.then(
                            literal("status")
                                    .executes(
                                            ctx -> {
                                                tell(capture.status() + " " + service.status());
                                                return 1;
                                            }));
                    root.then(
                            literal("lookup")
                                    .executes(
                                            ctx -> {
                                                startLookup(
                                                        "r:"
                                                                + service.config().eventRadius
                                                                + " t:1h");
                                                return 1;
                                            })
                                    .then(
                                            argument(
                                                            "parameters",
                                                            StringArgumentType.greedyString())
                                                    .executes(
                                                            ctx -> {
                                                                startLookup(
                                                                        StringArgumentType
                                                                                .getString(
                                                                                        ctx,
                                                                                        "parameters"));
                                                                return 1;
                                                            })));
                    root.then(
                            literal("static")
                                    .executes(
                                            ctx -> {
                                                openStaticView();
                                                return 1;
                                            }));
                    root.then(
                            literal("replay")
                                    .executes(
                                            ctx -> {
                                                var mc = Minecraft.getInstance();
                                                mc.schedule(
                                                        () ->
                                                                mc.gui.setScreen(
                                                                        new ReplayScreen(
                                                                                mc.gui.screen())));
                                                return 1;
                                            }));
                    dispatcher.register(root);
                });
    }

    public static void startLookup(String parameters) {
        if (Minecraft.getInstance().getConnection() == null) {
            tell("Join a server first.");
            return;
        }
        tell(
                capture.start(
                        "co lookup " + parameters.strip(),
                        serverIdentity(),
                        System.currentTimeMillis()));
    }

    public static void stopCapture(String reason) {
        capture.stop(reason);
        service.cancelCapture();
    }

    public static String serverIdentity() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.getServerData() != null
                ? connection.getServerData().ip
                : "singleplayer";
    }

    public static void openStaticView() {
        var mc = Minecraft.getInstance();
        mc.schedule(() -> mc.gui.setScreen(new StaticViewScreen(mc.gui.screen())));
    }

    public static void openDashboard() {
        var mc = Minecraft.getInstance();
        mc.schedule(() -> mc.gui.setScreen(new InvestigationScreen(mc.gui.screen())));
    }

    public static void notifyResult(CompletableFuture<String> work) {
        work.thenAccept(CoreTraceClient::tell);
    }

    public static void tell(String message) {
        var mc = Minecraft.getInstance();
        mc.execute(
                () ->
                        mc.gui
                                .chatListener()
                                .handleSystemMessage(
                                        Component.literal("[Coreviewer] " + message), false));
    }
}
