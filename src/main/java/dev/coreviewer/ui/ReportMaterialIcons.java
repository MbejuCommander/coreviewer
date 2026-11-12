package dev.coreviewer.ui;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Captures small vanilla textures on the render thread before background PDF generation. */
final class ReportMaterialIcons {
  private ReportMaterialIcons() {}

  static Map<String, byte[]> capture(Collection<String> materials) {
    var result = new HashMap<String, byte[]>();
    var manager = Minecraft.getInstance().getResourceManager();
    materials.stream().distinct().limit(256).forEach(material -> {
      if (!material.startsWith("minecraft:")) return;
      String id = material.substring("minecraft:".length());
      if (!id.matches("[a-z0-9_]+")) return;
      String[] paths = {
        "textures/block/" + id + ".png",
        "textures/item/" + id + ".png",
        "textures/block/" + id + "_side.png",
        "textures/block/" + id + "_top.png"
      };
      for (String path : paths) {
        var resource = manager.getResource(Identifier.fromNamespaceAndPath("minecraft", path));
        if (resource.isEmpty()) continue;
        try (var stream = resource.get().open()) {
          byte[] bytes = stream.readNBytes(1_000_001);
          if (bytes.length <= 1_000_000) result.put(material, bytes);
        } catch (Exception ignored) {
          // Keep the labelled color chip when the resource is unavailable.
        }
        if (result.containsKey(material)) break;
      }
    });
    return Map.copyOf(result);
  }
}
