package dev.coreviewer.ui;

import dev.coreviewer.CoreTraceClient;
import dev.coreviewer.model.*;
import dev.coreviewer.view.EventNavigation;
import dev.coreviewer.view.PlayerAppearance;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** No scheduler: every command comes directly from a navigation button. */
public final class TeleportTourScreen extends Screen {
  private final Screen parent;
  private final List<CoreTraceEvent> route;
  private static TeleportTourScreen active;
  private boolean locked;
  private int index = -1;
  private int pendingIndex = -1;
  private boolean playerMenu, searchDirty;
  private int playerPage;
  private String playerQuery = "";
  private String status = "Preview only. Next visits the first location.";

  public TeleportTourScreen(Screen parent, List<CoreTraceEvent> route, String server) {
    super(Component.literal("Investigation TP"));
    this.parent = parent;
    this.route = List.copyOf(route);
  }

  public void start() {
    active = this;
    var client = Minecraft.getInstance();
    client.gui.setScreen(this);
    client.gui.setScreen(null);
  }

  public static void stop() {
    var tour = active;
    active = null;
    if (tour != null && Minecraft.getInstance().gui.screen() == tour)
      Minecraft.getInstance().gui.setScreen(null);
  }

  public static boolean handleKey(int action, net.minecraft.client.input.KeyEvent event) {
    var tour = active;
    if (tour == null
        || Minecraft.getInstance().level == null
        || Minecraft.getInstance().gui.screen() != null
            && Minecraft.getInstance().gui.screen() != tour) return false;
    var c = CoreTraceClient.service.config();
    int code = event.key();
    if (code == key(c.tpMovementLockKey)) {
      if (action == com.mojang.blaze3d.platform.InputConstants.PRESS) {
        tour.locked = !tour.locked;
        tour.minecraft.gui.setScreen(tour.locked ? tour : null);
      }
      return true;
    }
    if (code == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && !tour.locked) {
      if (action == com.mojang.blaze3d.platform.InputConstants.PRESS) tour.onClose();
      return true;
    }
    if (code == key(c.linkedTpModeKeys ? c.staticPreviousKey : c.tpPreviousKey)) {
      if (action == com.mojang.blaze3d.platform.InputConstants.PRESS) tour.visit(-1);
      return true;
    }
    if (code == key(c.linkedTpModeKeys ? c.staticNextKey : c.tpNextKey)) {
      if (action == com.mojang.blaze3d.platform.InputConstants.PRESS) tour.visit(1);
      return true;
    }
    return false;
  }

  private static int key(String name) {
    try {
      return com.mojang.blaze3d.platform.InputConstants.getKey(name).getValue();
    } catch (RuntimeException ex) {
      return -1;
    }
  }

  private static String label(String name) {
    try {
      return com.mojang.blaze3d.platform.InputConstants.getKey(name).getDisplayName().getString();
    } catch (RuntimeException ex) {
      return name;
    }
  }

  public static void drawHud(GuiGraphicsExtractor g) {
    if (active != null
        && Minecraft.getInstance().level != null
        && Minecraft.getInstance().gui.screen() == null
        && CoreTraceClient.service.enabled()) active.draw(g, g.guiWidth());
  }

  protected void init() {
    var c = CoreTraceClient.service.config();
    String back = label(c.linkedTpModeKeys ? c.staticPreviousKey : c.tpPreviousKey);
    String next = label(c.linkedTpModeKeys ? c.staticNextKey : c.tpNextKey);
    addRenderableWidget(
                Button.builder(Component.literal("Back (" + back + ")"), b -> visit(-1))
                    .bounds(8, 80, 120, 20)
                    .build())
            .active =
        pendingIndex < 0 && index > 0;
    addRenderableWidget(
                Button.builder(Component.literal("Next (" + next + ")"), b -> visit(1))
                    .bounds(133, 80, 120, 20)
                    .build())
            .active =
        pendingIndex >= 0 || index + 1 < route.size();
    addRenderableWidget(
        Button.builder(Component.literal("Exit (Esc)"), b -> onClose())
            .bounds(258, 80, 90, 20)
            .build());
    addRenderableWidget(
        Button.builder(
                Component.literal(playerMenu ? "▲" : "▼"),
                b -> {
                  playerMenu = !playerMenu;
                  playerPage = 0;
                  rebuildWidgets();
                })
            .bounds(playerStart(width, previewActor()), 8, 20, 20)
            .build());
    if (playerMenu) {
      int x = Math.max(8, width - 218);
      var search =
          addRenderableWidget(
              new EditBox(font, x, 34, 210, 20, Component.literal("Search players")));
      search.setValue(playerQuery);
      search.setHint(Component.literal("Search player..."));
      search.setResponder(
          value -> {
            playerQuery = value;
            playerPage = 0;
            searchDirty = true;
          });
      var names = matchingPlayers();
      int rows = Math.max(1, Math.min(7, (height - 100) / 23));
      playerPage = Math.min(playerPage, Math.max(0, (names.size() - 1) / rows));
      for (int i = 0; i < rows && playerPage * rows + i < names.size(); i++) {
        String actor = names.get(playerPage * rows + i);
        addRenderableWidget(
            Button.builder(Component.literal(actor), b -> choosePlayer(actor))
                .bounds(x, 58 + i * 23, 210, 20)
                .build());
      }
      if (names.size() > rows) {
        addRenderableWidget(
            Button.builder(
                    Component.literal("<"),
                    b -> {
                      playerPage = Math.max(0, playerPage - 1);
                      rebuildWidgets();
                    })
                .bounds(x, 62 + rows * 23, 38, 20)
                .build());
        addRenderableWidget(
            Button.builder(
                    Component.literal(">"),
                    b -> {
                      playerPage = Math.min((names.size() - 1) / rows, playerPage + 1);
                      rebuildWidgets();
                    })
                .bounds(x + 172, 62 + rows * 23, 38, 20)
                .build());
      }
    }
  }

  private List<String> matchingPlayers() {
    return route.stream()
        .map(e -> e.context().actor())
        .distinct()
        .filter(
            name -> name.toLowerCase(Locale.ROOT).contains(playerQuery.toLowerCase(Locale.ROOT)))
        .toList();
  }

  private String previewActor() {
    return route.isEmpty()
        ? ""
        : route.get(pendingIndex >= 0 ? pendingIndex : Math.max(0, index)).context().actor();
  }

  private int playerStart(int availableWidth, String actor) {
    int textWidth = font.width(font.plainSubstrByWidth(actor, 130));
    return Math.max(8, availableWidth - textWidth - 53);
  }

  private void choosePlayer(String actor) {
    for (int i = 0; i < route.size(); i++) {
      if (route.get(i).context().actor().equals(actor)) {
        pendingIndex = i;
        playerMenu = false;
        status = "Selected " + actor + ". Next visits their first location.";
        rebuildWidgets();
        return;
      }
    }
  }

  @Override
  public void tick() {
    if (searchDirty) {
      searchDirty = false;
      rebuildWidgets();
      for (var child : children())
        if (child instanceof EditBox box) {
          setFocused(box);
          box.setFocused(true);
          break;
        }
    }
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    if (playerMenu && x >= width - 220 && vertical != 0) {
      int rows = Math.max(1, Math.min(7, (height - 100) / 23));
      int max = Math.max(0, (matchingPlayers().size() - 1) / rows);
      playerPage = Math.clamp(playerPage - (int) Math.signum(vertical), 0, max);
      rebuildWidgets();
      return true;
    }
    return super.mouseScrolled(x, y, horizontal, vertical);
  }

  private void visit(int direction) {
    if (route.isEmpty()
        || (direction < 0 && (pendingIndex >= 0 || index <= 0))
        || (direction > 0 && pendingIndex < 0 && index + 1 >= route.size())) return;
    if (!CoreTraceClient.service.enabled() || minecraft.getConnection() == null) {
      status = "Disconnected or Coreviewer disabled. Return to Statistics.";
      return;
    }
    int next =
        pendingIndex >= 0
            ? pendingIndex
            : index < 0 ? 0 : Math.clamp(index + direction, 0, route.size() - 1);
    var event = route.get(next);
    try {
      String command = EventNavigation.command("/co teleport #{world} {x} {y} {z}", event);
      minecraft.getConnection().sendCommand(command);
      index = next;
      pendingIndex = -1;
      status =
          "Requested location "
              + (index + 1)
              + " / "
              + route.size()
              + ". Server permissions apply.";
    } catch (IllegalArgumentException ex) {
      status = ex.getMessage();
    }
    if (minecraft.gui.screen() == this) rebuildWidgets();
  }

  public boolean isPauseScreen() {
    return false;
  }

  public void extractBackground(GuiGraphicsExtractor g, int x, int y, float dt) {}

  public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float dt) {
    super.extractRenderState(g, x, y, dt);
    draw(g, width);
  }

  private void draw(GuiGraphicsExtractor g, int w) {
    var c = CoreTraceClient.service.config();
    g.text(font, font.plainSubstrByWidth(status, w - 16), 8, 62, 0xFFFFD166);
    g.text(
        font,
        label(c.tpMovementLockKey)
            + ": "
            + (locked ? "unlock movement / hide buttons" : "lock movement / show buttons")
            + " | Esc: exit",
        8,
        106,
        0xFF8FE0D5);
    if (!locked) {
      String back = label(c.linkedTpModeKeys ? c.staticPreviousKey : c.tpPreviousKey);
      String next = label(c.linkedTpModeKeys ? c.staticNextKey : c.tpNextKey);
      g.fill(8, 80, 128, 100, 0xB020303E);
      g.fill(133, 80, 253, 100, 0xB020303E);
      g.centeredText(font, "Back (" + back + ")", 68, 86, 0xFFFFFFFF);
      g.centeredText(font, "Next (" + next + ")", 193, 86, 0xFFFFFFFF);
    }
    if (route.isEmpty()) {
      g.text(font, "No matching positioned events.", 8, 20, 0xFFFFFFFF);
      return;
    }
    var event = route.get(pendingIndex >= 0 ? pendingIndex : Math.max(0, index));
    var context = event.context();
    String actorLabel = font.plainSubstrByWidth(context.actor(), 130);
    int arrowX = playerStart(w, context.actor());
    int playerX = arrowX + 24;
    g.text(font, actorLabel, playerX, 12, 0xFFFFFFFF);
    var head = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
    head.set(
        net.minecraft.core.component.DataComponents.PROFILE,
        PlayerAppearance.profile(context.actor(), context.actorUuid()));
    g.item(head, playerX + font.width(actorLabel) + 4, 8);
    if (!locked) g.text(font, "▼", arrowX + 6, 12, 0xFFFFFFFF);
    if (locked && playerMenu)
      g.text(
          font,
          "Choose player (no teleport until Next)",
          Math.max(8, w - 218),
          58 + Math.min(7, matchingPlayers().size()) * 23 + 24,
          0xFF8FE0D5);
    g.text(
        font,
        context.world()
            + " "
            + context.position().x()
            + ", "
            + context.position().y()
            + ", "
            + context.position().z(),
        8,
        32,
        0xFFFFFFFF);
    g.text(
        font,
        font.plainSubstrByWidth(
            event.type().name().replace('_', ' ')
                + " | "
                + StatisticsQuery.key(event).split("\\|", 2)[1]
                + " | "
                + StatisticsQuery.time(event),
            w - 16),
        8,
        46,
        0xFFFFFFFF);
  }

  public void onClose() {
    if (active == this) active = null;
    minecraft.gui.setScreen(parent);
  }
}
