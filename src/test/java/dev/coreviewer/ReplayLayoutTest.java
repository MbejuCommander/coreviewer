package dev.coreviewer;

import dev.coreviewer.config.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReplayLayoutTest {
    @TempDir Path root;
    @Test void existingConfigReceivesFullSizeAndDefaultPlacement() throws Exception {
        java.nio.file.Files.writeString(root.resolve("config.json"), "{\"enabled\":true,\"schemaVersion\":1}");
        var loaded = new ConfigStore(root).load();
        assertEquals(1f, loaded.replayToolbarScale);
        assertEquals(-1, loaded.replayToolbarX);
        assertEquals(-1, loaded.replayToolbarY);
    }
    @Test void draggingPersistsWithoutReconfiguringCaptureOrIndex() throws Exception {
        var config = new CoreTraceConfig();
        config.autoCapture = false;
        config.replayToolbarScale = .75f;
        try (var service = new InvestigationService(root, config)) {
            service.initialize().get();
            var snapshot = service.snapshot();
            service.onConfigurationChanged(() -> fail("Layout must not reconfigure capture"));
            service.positionReplayToolbar(.75, .25).get();
            assertSame(snapshot, service.snapshot());
            var saved = new ConfigStore(root).load();
            assertEquals(.75, saved.replayToolbarX);
            assertEquals(.25, saved.replayToolbarY);
            assertFalse(saved.autoCapture);
            assertEquals(.75f, saved.replayToolbarScale);
            service.positionReplayToolbar(Double.NaN, 2).get();
            assertEquals(-1, service.config().replayToolbarX);
            assertEquals(1, service.config().replayToolbarY);
            var updated = service.config(); updated.replayToolbarScale = .2f; updated.validate();
            assertEquals(.75f, updated.replayToolbarScale);
        }
    }
}
