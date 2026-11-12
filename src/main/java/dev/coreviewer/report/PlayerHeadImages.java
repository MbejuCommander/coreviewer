package dev.coreviewer.report;

import com.google.gson.JsonParser;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.imageio.ImageIO;

/** Retrieves current Mojang skin faces by recorded UUID or current player name. */
public final class PlayerHeadImages {
  private static final HttpClient HTTP =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(3))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private static final Map<String, UUID> UUID_CACHE =
      Collections.synchronizedMap(
          new LinkedHashMap<String, UUID>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, UUID> eldest) {
              return size() > 256;
            }
          });
  private static final Map<UUID, byte[]> FACE_CACHE =
      Collections.synchronizedMap(
          new LinkedHashMap<UUID, byte[]>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, byte[]> eldest) {
              return size() > 256;
            }
          });

  private PlayerHeadImages() {}

  public static Map<String, byte[]> resolve(List<String> names, Map<String, UUID> actors) {
    return resolve(names, actors, PlayerHeadImages::lookupUuid, PlayerHeadImages::face);
  }

  /** Starts the same lookup used by the PDF while the player is choosing report options. */
  public static CompletableFuture<Map<String, byte[]>> prefetch(
      List<String> names, Map<String, UUID> actors, BiConsumer<String, byte[]> onFace) {
    return CompletableFuture.supplyAsync(
        () -> resolve(names, actors, PlayerHeadImages::lookupUuid, PlayerHeadImages::face, onFace));
  }

  static Map<String, byte[]> resolve(
      List<String> names,
      Map<String, UUID> actors,
      Function<String, UUID> lookup,
      Function<UUID, byte[]> skinFace) {
    return resolve(names, actors, lookup, skinFace, null);
  }

  private static Map<String, byte[]> resolve(
      List<String> names,
      Map<String, UUID> actors,
      Function<String, UUID> lookup,
      Function<UUID, byte[]> skinFace,
      BiConsumer<String, byte[]> onFace) {
    var pool =
        Executors.newFixedThreadPool(
            4,
            r -> {
              var thread = new Thread(r, "Coreviewer report skins");
              thread.setDaemon(true);
              return thread;
            });
    try {
      var jobs = new LinkedHashMap<String, CompletableFuture<byte[]>>();
      names.stream()
          .distinct()
          .forEach(
              name ->
                  jobs.put(
                      name,
                      CompletableFuture.supplyAsync(
                          () -> {
                            // CSV UUIDs can be offline or stale. A current name lookup is
                            // authoritative for the current skin shown in the report.
                            UUID current = lookup.apply(name);
                            byte[] image = current == null ? null : skinFace.apply(current);
                            UUID recorded = actors.get(name);
                            return image != null || recorded == null || recorded.equals(current)
                                ? image
                                : skinFace.apply(recorded);
                          }, pool)
                          .whenComplete((image, error) -> {
                            if (image == null || onFace == null) return;
                            try {
                              onFace.accept(name, image);
                            } catch (Exception ignored) {
                              // A preview callback must never interrupt report skin lookup.
                            }
                          })));
      var result = new HashMap<String, byte[]>();
      jobs.forEach(
          (name, job) -> {
            try {
              byte[] image = job.get(45, TimeUnit.SECONDS);
              if (image != null) result.put(name, image);
            } catch (Exception ignored) {
              // An offline player or unavailable skin must not prevent evidence export.
            }
          });
      return Map.copyOf(result);
    } finally {
      pool.shutdownNow();
    }
  }

  private static UUID lookupUuid(String name) {
    if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) return null;
    String key = name.toLowerCase(Locale.ROOT);
    UUID cached = UUID_CACHE.get(key);
    if (cached != null) return cached;
    try {
      byte[] response =
          request(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name), 10_000);
      UUID uuid = parseNameLookup(name, response);
      if (uuid != null) UUID_CACHE.put(key, uuid);
      return uuid;
    } catch (Exception ignored) {
      return null;
    }
  }

  static UUID parseNameLookup(String name, byte[] response) {
    if (response == null) return null;
    try {
      var profile =
          JsonParser.parseString(new String(response, java.nio.charset.StandardCharsets.UTF_8))
              .getAsJsonObject();
      if (!profile.get("name").getAsString().equalsIgnoreCase(name)) return null;
      String id = profile.get("id").getAsString();
      if (!id.matches("[0-9a-fA-F]{32}")) return null;
      return UUID.fromString(
          id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16)
              + "-" + id.substring(16, 20) + "-" + id.substring(20));
    } catch (Exception ignored) {
      return null;
    }
  }

  private static byte[] face(UUID uuid) {
    if (uuid == null) return null;
    byte[] cached = FACE_CACHE.get(uuid);
    if (cached != null) return cached;
    try {
      String id = uuid.toString().replace("-", "");
      var profile =
          request(
              URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + id),
              100_000);
      if (profile == null) return null;
      var root =
          JsonParser.parseString(new String(profile, java.nio.charset.StandardCharsets.UTF_8))
              .getAsJsonObject();
      String encoded = null;
      for (var property : root.getAsJsonArray("properties")) {
        var object = property.getAsJsonObject();
        if (object.get("name").getAsString().equals("textures")) {
          encoded = object.get("value").getAsString();
          break;
        }
      }
      if (encoded == null) return null;
      var textures =
          JsonParser.parseString(
                  new String(
                      Base64.getDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8))
              .getAsJsonObject()
              .getAsJsonObject("textures");
      if (textures == null || !textures.has("SKIN")) return null;
      URI skin = skinUri(textures.getAsJsonObject("SKIN").get("url").getAsString());
      if (skin == null) return null;
      byte[] png = request(skin, 1_000_000);
      if (png == null) return null;
      BufferedImage source = ImageIO.read(new ByteArrayInputStream(png));
      if (source == null
          || source.getWidth() < 48
          || source.getHeight() < 16
          || source.getWidth() > 1024
          || source.getHeight() > 1024) return null;
      int scale = source.getWidth() / 64;
      if (scale < 1 || source.getHeight() < 32 * scale) return null;
      var face = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = face.createGraphics();
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
      g.drawImage(source, 0, 0, 32, 32, 8 * scale, 8 * scale, 16 * scale, 16 * scale, null);
      if (source.getHeight() >= 16 * scale && source.getWidth() >= 48 * scale)
        g.drawImage(source, 0, 0, 32, 32, 40 * scale, 8 * scale, 48 * scale, 16 * scale, null);
      g.dispose();
      var out = new ByteArrayOutputStream();
      ImageIO.write(face, "png", out);
      byte[] image = out.toByteArray();
      FACE_CACHE.put(uuid, image);
      return image;
    } catch (Exception ignored) {
      return null;
    }
  }

  static URI skinUri(String raw) {
    try {
      URI uri = URI.create(raw);
      if (!"textures.minecraft.net".equalsIgnoreCase(uri.getHost())
          || !("http".equalsIgnoreCase(uri.getScheme())
              || "https".equalsIgnoreCase(uri.getScheme()))
          || uri.getUserInfo() != null
          || uri.getPort() != -1
          || !uri.getPath().matches("/texture/[0-9a-fA-F]+")) return null;
      return URI.create("https://textures.minecraft.net" + uri.getPath());
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static byte[] request(URI uri, int maxBytes) throws Exception {
    var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(6)).GET().build();
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        byte[] body = response.body();
        if (response.statusCode() == 200)
          return body != null && body.length <= maxBytes ? body : null;
        if (response.statusCode() != 429 && response.statusCode() < 500) return null;
      } catch (java.io.IOException ex) {
        if (attempt == 2) throw ex;
      }
      if (attempt < 2) Thread.sleep(350L * (attempt + 1));
    }
    return null;
  }
}
