package dev.coreviewer;

import dev.coreviewer.ui.*;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public final class PhaseOneGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(854, 480);
        CoreTraceClient.service.initialize().join();
        CoreTraceClient.service.clear().join();
        context.setScreen(() -> new InvestigationScreen(null));
        context.waitTicks(2);
        context.takeScreenshot("phase1-dashboard");
        context.clickScreenButton("Settings");
        context.waitTicks(2);
        context.takeScreenshot("phase1-cloth-config");
        context.setScreen(() -> new InvestigationScreen(null));
        CoreTraceClient.service.simulate().join();
        if (CoreTraceClient.service.events().size() != 6)
            throw new AssertionError("Expected six simulated events");
        CoreTraceClient.service.reload().join();
        if (CoreTraceClient.service.events().size() != 6)
            throw new AssertionError("Reload lost events");
        if (CoreTraceClient.service.csvFiles().isEmpty()) throw new AssertionError("Missing CSV");
        context.waitTicks(2);
        context.takeScreenshot("phase1-simulated-data");
        context.clickScreenButton("Settings");
        context.waitTicks(2);
        context.runOnClient(
                mc -> {
                    var screen =
                            (me.shedaniel.clothconfig2.gui.AbstractConfigScreen) mc.gui.screen();
                    var entry =
                            screen.getCategorizedEntries().values().stream()
                                    .flatMap(java.util.List::stream)
                                    .filter(
                                            e ->
                                                    e.getFieldName()
                                                            .getString()
                                                            .equals("COREVIEWER ENABLED"))
                                    .findFirst()
                                    .orElseThrow();
                    var toggle = entry.children().getFirst();
                    ((net.minecraft.client.gui.components.Button) toggle)
                            .onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0));
                    screen.saveAll(false);
                });
        context.waitFor(mc -> !CoreTraceClient.service.enabled());
        CoreTraceClient.service.simulate().join();
        if (CoreTraceClient.service.events().size() != 6)
            throw new AssertionError("OFF accepted an event");
        context.waitTicks(2);
        context.takeScreenshot("phase1-disabled");
        var config = CoreTraceClient.service.config();
        config.enabled = true;
        CoreTraceClient.service.configure(config).join();
        context.runOnClient(
                mc -> {
                    CoreTraceClient.capture.command(
                            "co lookup r:100 t:1h", "integration-test", System.currentTimeMillis());
                    var event =
                            net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
                                    .GAME
                                    .invoker();
                    var time =
                            net.minecraft.network.chat.Component.literal("0.02/m ago")
                                    .withStyle(
                                            s ->
                                                    s.withHoverEvent(
                                                            new net.minecraft.network.chat
                                                                    .HoverEvent.ShowText(
                                                                    net.minecraft.network.chat
                                                                            .Component.literal(
                                                                            "2026-09-26 03:00:00"
                                                                                    + " UTC"))));
                    event.onReceiveGameMessage(time.append(" - Alice broke diamond_ore."), false);
                    event.onReceiveGameMessage(
                            net.minecraft.network.chat.Component.literal(
                                    "^ (x-12/y64/z30/world) (a:block)"),
                            false);
                    event.onReceiveGameMessage(
                            net.minecraft.network.chat.Component.literal("Page 1/1"), false);
                });
        CoreTraceClient.service.save().join();
        if (CoreTraceClient.service.events().size() != 7)
            throw new AssertionError("Chat capture failed");
        var captured =
                CoreTraceClient.service.events().stream()
                        .filter(e -> !e.context().simulated())
                        .findFirst()
                        .orElseThrow();
        if (captured.context().simulated()
                || captured.context().position().x() != -12
                || captured.context().timestamp()
                        != java.time.Instant.parse("2026-09-26T03:00:00Z").toEpochMilli())
            throw new AssertionError("Chat component metadata was lost");
        context.setScreen(() -> new CaptureScreen(null));
        context.waitTicks(2);
        context.takeScreenshot("phase2-capture");
        context.setScreen(net.minecraft.client.gui.screens.TitleScreen::new);
    }
}
