package dev.coreviewer.report;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class PlayerHeadImagesTest {
  @Test
  void prefersCurrentNameAndFallsBackToRecordedUuid() {
    UUID known = UUID.randomUUID();
    UUID fetched = UUID.randomUUID();
    var lookups = new AtomicInteger();
    var heads =
        PlayerHeadImages.resolve(
            List.of("Jose", "Juan", "NoSkin"),
            Map.of("Jose", known),
            name -> {
              lookups.incrementAndGet();
              return name.equals("Jose") || name.equals("Juan") ? fetched : null;
            },
            id -> id.toString().getBytes(StandardCharsets.UTF_8));

    assertEquals(3, lookups.get());
    assertEquals(fetched.toString(), new String(heads.get("Jose"), StandardCharsets.UTF_8));
    assertEquals(fetched.toString(), new String(heads.get("Juan"), StandardCharsets.UTF_8));
    assertFalse(heads.containsKey("NoSkin"));

    var fallback = PlayerHeadImages.resolve(List.of("Jose"), Map.of("Jose", known),
        name -> null, id -> id.toString().getBytes(StandardCharsets.UTF_8));
    assertEquals(known.toString(), new String(fallback.get("Jose"), StandardCharsets.UTF_8));
  }

  @Test
  void parsesMojangNameResponseAndUpgradesTextureUrlToHttps() {
    byte[] response =
        "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}"
            .getBytes(StandardCharsets.UTF_8);
    assertEquals(
        UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"),
        PlayerHeadImages.parseNameLookup("notch", response));
    assertNull(PlayerHeadImages.parseNameLookup("OtherPlayer", response));
    assertEquals(
        "https://textures.minecraft.net/texture/abc123",
        PlayerHeadImages.skinUri("http://textures.minecraft.net/texture/abc123").toString());
    assertNull(PlayerHeadImages.skinUri("http://textures.minecraft.net.evil.example/texture/abc123"));
  }

  @Test
  void liveMojangLookupProducesAHeadWhenRequested() {
    Assumptions.assumeTrue("1".equals(System.getenv("COREVIEWER_LIVE_MOJANG")));
    var heads = PlayerHeadImages.resolve(List.of("Frecciatore", "Juno_72"), Map.of());
    assertTrue(heads.get("Frecciatore").length > 100);
    assertTrue(heads.get("Juno_72").length > 100);
  }
}
