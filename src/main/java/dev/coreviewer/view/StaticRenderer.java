package dev.coreviewer.view;

import com.mojang.authlib.GameProfile;
import com.mojang.renderpearl.api.pipeline.*;

import dev.coreviewer.CoreTraceClient;
import dev.coreviewer.config.CoreTraceConfig;
import dev.coreviewer.model.*;
import dev.coreviewer.replay.ReplayController;

import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;

import java.util.*;

public final class StaticRenderer {
    private record Visual(
            CoreTraceEvent event,
            List<BlockStateModelPart> parts,
            int[] tints,
            EntityRenderState entity,
            ItemStackRenderState item,
            ItemStackRenderState head,
            float scale) {}

    private record Frame(
            CoreTraceConfig config,
            List<Visual> visuals,
            List<StaticScene.Link> arrows,
            double clock) {}

    private static final RenderStateDataKey<Frame> FRAME =
            RenderStateDataKey.create(() -> "coreviewer static scene");

    private record BlockParts(Object model, List<BlockStateModelPart> parts) {}

    private static final BoundedCache<String, BlockParts> blockModels = new BoundedCache<>(256);
    private static final BoundedCache<String, ItemStackRenderState> itemModels =
            new BoundedCache<>(256);
    private static Object modelSet;
    private static final AsyncSceneCache sceneCache = new AsyncSceneCache();

    public static long selectionQueries() {
        return sceneCache.queries;
    }

    public static long selectionCacheHits() {
        return sceneCache.hits;
    }

    public static int cachedModels() {
        return blockModels.size() + itemModels.size();
    }

    public static int examinedEvents, resourceInvalidations;
    private static final Map<UUID, Entity> figures = new HashMap<>();
    private static RenderType xray, depth;
    private static Object lastLevel;
    private static int nextDisplayId = -1_000_000;
    public static int visibleCount, figureCount, itemCount;
    public static String error = "";

    public static void reset() {
        sceneCache.clear();
        blockModels.clear();
        itemModels.clear();
        modelSet = null;
        examinedEvents = 0;
        figures.clear();
        lastLevel = null;
        visibleCount = figureCount = itemCount = 0;
    }

    private static RenderType blocks(boolean through) {
        if (through && xray != null) return xray;
        if (!through && depth != null) return depth;
        var pipeline =
                RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                        .withLocation(
                                Identifier.fromNamespaceAndPath("coreviewer", "ghost_" + through))
                        .withCull(false)
                        .withShaderDefine("ALPHA_CUTOUT", 0.001f)
                        .withShaderDefine("PER_FACE_LIGHTING")
                        .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                        // 26.3 uses reversed depth: GREATER, not LESS. Never write historical
                        // geometry into depth.
                        .withDepthStencilState(
                                new DepthStencilState(
                                        through
                                                ? CompareOp.ALWAYS_PASS
                                                : CompareOp.GREATER_THAN_OR_EQUAL,
                                        false))
                        .build();
        var type =
                RenderType.create(
                        "coreviewer_ghost_" + through,
                        RenderSetup.builder(pipeline)
                                .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                                .useLightmap()
                                .useOverlay()
                                .sortOnUpload()
                                .createRenderSetup());
        if (through) xray = type;
        else depth = type;
        return type;
    }

    public static void register() {
        LevelExtractionEvents.END_EXTRACTION.register(
                context -> {
                    context.levelState().setData(FRAME, null);
                    visibleCount = figureCount = itemCount = 0;
                    if (!StaticView.active()) {
                        figures.clear();
                        return;
                    }
                    var mc = Minecraft.getInstance();
                    if (lastLevel != mc.level) {
                        figures.clear();
                        sceneCache.clear();
                        blockModels.clear();
                        itemModels.clear();
                        lastLevel = mc.level;
                    }
                    var currentModels = mc.getModelManager().getBlockStateModelSet();
                    if (modelSet != currentModels) {
                        resourceInvalidations++;
                        modelSet = currentModels;
                        blockModels.clear();
                        itemModels.clear();
                        figures.clear();
                        sceneCache.clear();
                    }
                    var c = CoreTraceClient.service.config();
                    if (c.alpha == 0) return;
                    boolean replay = ReplayController.active();
                    int count =
                            replay ? ReplayController.engine.visibleCount() : StaticView.total();
                    c.windowFrom =
                            replay
                                    ? Math.max(0, count - c.replayVisibleEvents)
                                    : (int) ((long) StaticView.page() * c.maxVisibleEvents);
                    c.windowTo =
                            replay
                                    ? count
                                    : (int)
                                            Math.min(
                                                    count,
                                                    (long) c.windowFrom + c.maxVisibleEvents);
                    if (replay) c.maxVisibleEvents = c.replayVisibleEvents;
                    var camera = context.levelState().cameraRenderState.pos;
                    var selected =
                            sceneCache.select(
                                    StaticView.index(),
                                    StaticView.server(),
                                    StaticView.world(),
                                    StaticView.isDemo(),
                                    camera.x,
                                    camera.y,
                                    camera.z,
                                    c,
                                    ReplayController.active()
                                            ? ReplayController.engine.cursor()
                                            : Double.POSITIVE_INFINITY,
                                    ReplayController.active()
                                            ? ReplayController.engine.visibleCount()
                                            : -1,
                                    System.nanoTime(),
                                    (cx, cz) -> context.level().hasChunk(cx, cz));
                    examinedEvents = selected.examined();
                    var visuals = new ArrayList<Visual>();
                    var loaded = new ArrayList<CoreTraceEvent>();
                    var retained = new HashSet<UUID>();
                    for (var event : selected.events()) {
                        var p = event.context().position();
                        var pos = new BlockPos(p.x(), p.y(), p.z());
                        if (!context.level().hasChunkAt(pos)) continue;
                        double dx = p.x() + .5 - camera.x,
                                dy = p.y() + .5 - camera.y,
                                dz = p.z() + .5 - camera.z;
                        if (dx * dx + dy * dy + dz * dz > (double) c.eventRadius * c.eventRadius)
                            continue;
                        var parts = new ArrayList<BlockStateModelPart>();
                        int[] tints = new int[0];
                        EntityRenderState entity = null;
                        ItemStackRenderState item = null, head = null;
                        try {
                            if (event instanceof BlockEvent b && c.ghostBlocks) {
                                var id = Identifier.tryParse(b.block());
                                var block =
                                        id == null
                                                ? Optional
                                                        .<net.minecraft.world.level.block.Block>
                                                                empty()
                                                : BuiltInRegistries.BLOCK.getOptional(id);
                                if (block.isPresent()) {
                                    var state = block.get().defaultBlockState();
                                    var model = currentModels.get(state);
                                    var cached = blockModels.get(b.block());
                                    if (cached == null || cached.model() != model) {
                                        var geometry = new ArrayList<BlockStateModelPart>();
                                        model.collectParts(RandomSource.create(42), geometry);
                                        cached = new BlockParts(model, List.copyOf(geometry));
                                        blockModels.put(b.block(), cached);
                                    }
                                    parts.addAll(cached.parts());
                                    var sources = mc.getBlockColors().getTintSources(state);
                                    tints = new int[sources.size()];
                                    for (int i = 0; i < tints.length; i++)
                                        tints[i] =
                                                sources.get(i)
                                                        .colorInWorld(state, context.level(), pos);
                                }
                            } else if (event instanceof KillEvent k && figureCount < 128) {
                                var figure =
                                        figures.computeIfAbsent(
                                                event.context().id(),
                                                ignored -> {
                                                    var display = figure(k);
                                                    if (display != null)
                                                        display.setId(nextDisplayId--);
                                                    return display;
                                                });
                                if (figure != null) {
                                    retained.add(event.context().id());
                                    figure.setPos(p.x() + .5, p.y(), p.z() + .5);
                                    figure.setOldPosAndRot();
                                    entity =
                                            mc.getEntityRenderDispatcher().extractEntity(figure, 0);
                                    entity.ageInTicks = 0;
                                    entity.lightCoords = 0xF000F0;
                                    entity.shadowPieces.clear();
                                    entity.shadowRadius = 0;
                                    entity.nameTag = null;
                                    entity.displayFireAnimation = false;
                                    if (entity instanceof LivingEntityRenderState living) {
                                        living.deathTime = 20;
                                        living.walkAnimationPos = 0;
                                        living.walkAnimationSpeed = 0;
                                        living.bodyRot = 0;
                                        living.yRot = 0;
                                        living.xRot = 0;
                                    }
                                    figureCount++;
                                }
                            } else if (event instanceof ItemEvent i) {
                                var id = Identifier.tryParse(i.item());
                                var material =
                                        id == null
                                                ? Optional.<Item>empty()
                                                : BuiltInRegistries.ITEM.getOptional(id);
                                if (material.isPresent()) {
                                    item = cachedItem(i.item(), new ItemStack(material.get()));
                                    itemCount++;
                                }
                                if (c.playerHeads)
                                    head =
                                            cachedItem(
                                                    "minecraft:player_head",
                                                    new ItemStack(Items.PLAYER_HEAD));
                            }
                        } catch (RuntimeException ex) {
                            String next = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                            if (!next.equals(error))
                                CoreTraceClient.LOG.warn("Static preview fallback: " + next, ex);
                            error = next;
                        }
                        visuals.add(
                                new Visual(
                                        event,
                                        List.copyOf(parts),
                                        tints,
                                        entity,
                                        item,
                                        head,
                                        replayScale(event, c)));
                        loaded.add(event);
                    }
                    figures.keySet().retainAll(retained);
                    visibleCount = visuals.size();
                    context.levelState()
                            .setData(
                                    FRAME,
                                    new Frame(
                                            c,
                                            List.copyOf(visuals),
                                            c.arrows
                                                    ? StaticScene.links(loaded, c.arrowsSamePlayer)
                                                    : List.of(),
                                            ReplayController.active()
                                                    ? ReplayController.engine.cursor() / 1000d
                                                    : System.nanoTime() / 1e9));
                });
        LevelRenderEvents.COLLECT_SUBMITS.register(
                context -> {
                    Frame f = context.levelState().getData(FRAME);
                    if (f == null || !StaticView.active()) return;
                    var camera = context.levelState().cameraRenderState;
                    var pose = context.poseStack();
                    var collector = context.submitNodeCollector();
                    for (var v : f.visuals()) {
                        var p = v.event().context().position();
                        pose.pushPose();
                        pose.translate(
                                p.x() - camera.pos.x, p.y() - camera.pos.y, p.z() - camera.pos.z);
                        if (!v.parts().isEmpty() && v.scale() > 0) {
                            pose.pushPose();
                            pose.translate(.5, .5, .5);
                            pose.scale(v.scale(), v.scale(), v.scale());
                            pose.translate(-.5, -.5, -.5);
                            int rgb =
                                    f.config().tintGhosts ? color(v.event(), f.config()) : 0xFFFFFF;
                            collector.submitBlockModel(
                                    pose,
                                    blocks(f.config().throughWalls),
                                    v.parts(),
                                    v.tints(),
                                    0xF000F0,
                                    OverlayTexture.NO_OVERLAY,
                                    (f.config().alpha << 24) | rgb);
                            pose.popPose();
                        }
                        if (v.item() != null) {
                            pose.pushPose();
                            pose.translate(.5, .7 + Math.sin(f.clock() * 2) * .08, .5);
                            pose.mulPose(
                                    new org.joml.Matrix4f()
                                            .rotationY((float) (f.clock() * .5 % (Math.PI * 2))));
                            pose.scale(.7f, .7f, .7f);
                            v.item()
                                    .submit(
                                            pose,
                                            collector,
                                            0xF000F0,
                                            OverlayTexture.NO_OVERLAY,
                                            0);
                            pose.popPose();
                        }
                        if (v.head() != null) {
                            pose.pushPose();
                            pose.translate(.5, 1.5, .5);
                            pose.scale(.5f, .5f, .5f);
                            v.head()
                                    .submit(
                                            pose,
                                            collector,
                                            0xF000F0,
                                            OverlayTexture.NO_OVERLAY,
                                            0);
                            pose.popPose();
                        }
                        pose.popPose();
                        if (v.entity() != null)
                            Minecraft.getInstance()
                                    .getEntityRenderDispatcher()
                                    .submit(
                                            v.entity(),
                                            camera,
                                            p.x() + .5 - camera.pos.x,
                                            p.y() - camera.pos.y,
                                            p.z() + .5 - camera.pos.z,
                                            pose,
                                            collector);
                    }
                });
        LevelRenderEvents.BEFORE_GIZMOS.register(
                context -> {
                    Frame f = context.levelState().getData(FRAME);
                    if (f == null || !StaticView.active()) return;
                    var c = f.config();
                    try (var ignored =
                            context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
                        int index = 0;
                        for (var v : f.visuals()) {
                            var e = v.event();
                            var p = e.context().position();
                            int argb = (c.alpha << 24) | color(e, c);
                            if (e instanceof BlockEvent) {
                                if (c.outline || (c.ghostBlocks && v.parts().isEmpty()))
                                    show(
                                            Gizmos.cuboid(
                                                    new AABB(
                                                                    p.x(), p.y(), p.z(), p.x() + 1,
                                                                    p.y() + 1, p.z() + 1)
                                                            .inflate(.003),
                                                    GizmoStyle.stroke(argb, 2)),
                                            c);
                                if (c.outline && v.scale() > 0 && v.scale() < 1) {
                                    double inset = (1 - v.scale()) / 2;
                                    show(
                                            Gizmos.cuboid(
                                                    new AABB(
                                                            p.x() + inset,
                                                            p.y() + inset,
                                                            p.z() + inset,
                                                            p.x() + 1 - inset,
                                                            p.y() + 1 - inset,
                                                            p.z() + 1 - inset),
                                                    GizmoStyle.stroke(argb, 2)),
                                            c);
                                }
                            } else {
                                // Visible location marker remains useful even when the entity/item
                                // is occluded.
                                show(
                                        Gizmos.cuboid(
                                                new AABB(
                                                        p.x() + .15,
                                                        p.y() + .02,
                                                        p.z() + .15,
                                                        p.x() + .85,
                                                        p.y() + .12,
                                                        p.z() + .85),
                                                GizmoStyle.stroke(argb, 2)),
                                        c);
                            }
                            index++;
                            if (c.labels || e instanceof KillEvent) {
                                var labels = labels(e, index);
                                double base =
                                        (e instanceof KillEvent ? 2.0 : 1.8)
                                                + ((index - 1) % 3) * 1.15;
                                for (int row = 0; row < labels.size(); row++) {
                                    show(
                                            Gizmos.billboardText(
                                                    labels.get(row),
                                                    new Vec3(
                                                            p.x() + .5,
                                                            p.y()
                                                                    + base
                                                                    + (labels.size() - 1 - row)
                                                                            * .36,
                                                            p.z() + .5),
                                                    TextGizmo.Style.forColorAndCentered(argb)
                                                            .withScale(.5f)),
                                            c);
                                }
                            }
                        }
                        for (var link : f.arrows()) {
                            Vec3 a = center(link.from()), b = center(link.to());
                            int argb = (c.alpha << 24) | c.arrowColor;
                            show(Gizmos.arrow(a, b, argb, 2 * c.arrowSize), c);
                            double phase = (f.clock() * .35 * c.arrowSpeed) % 1;
                            Vec3 marker = a.lerp(b, phase),
                                    direction = b.subtract(a).normalize().scale(.3 * c.arrowSize);
                            show(
                                    Gizmos.arrow(
                                            marker.subtract(direction),
                                            marker,
                                            argb,
                                            3 * c.arrowSize),
                                    c);
                        }
                    }
                });
    }

    private static float replayScale(CoreTraceEvent event, CoreTraceConfig c) {
        if (!ReplayController.active() || !(event instanceof BlockEvent)) return 1;
        float progress = (float) ReplayController.engine.progress(event, c);
        return event.type() == EventType.BLOCK_BREAK ? 1 - progress : Math.max(.03f, progress);
    }

    private static Entity figure(KillEvent event) {
        var mc = Minecraft.getInstance();
        if (event.type() == EventType.PLAYER_KILL) {
            String name = event.victim() == null ? "Unknown" : event.victim();
            // Synthetic profile is for a display placeholder only; never written into the evidence.
            return new RemotePlayer(
                    mc.level,
                    new GameProfile(
                            event.victimUuid() == null ? new UUID(0, 0) : event.victimUuid(),
                            name));
        }
        var id = Identifier.tryParse(event.entity());
        return id == null
                ? null
                : BuiltInRegistries.ENTITY_TYPE
                        .getOptional(id)
                        .map(t -> t.create(mc.level, EntitySpawnReason.COMMAND))
                        .orElse(null);
    }

    private static ItemStackRenderState cachedItem(String id, ItemStack stack) {
        return itemModels.computeIfAbsent(id, ignored -> item(stack));
    }

    private static ItemStackRenderState item(ItemStack stack) {
        var result = new ItemStackRenderState();
        var mc = Minecraft.getInstance();
        mc.getItemModelResolver()
                .updateForTopItem(result, stack, ItemDisplayContext.GROUND, mc.level, null, 0);
        return result;
    }

    private static int color(CoreTraceEvent e, CoreTraceConfig c) {
        return switch (e.type()) {
            case BLOCK_PLACE, ITEM_ADD -> c.placeColor;
            default -> c.breakColor;
        };
    }

    private static Vec3 center(CoreTraceEvent e) {
        var p = e.context().position();
        return new Vec3(p.x() + .5, p.y() + .6, p.z() + .5);
    }

    private static void show(GizmoProperties props, CoreTraceConfig c) {
        if (c.throughWalls) props.setAlwaysOnTop();
    }

    private static List<String> labels(CoreTraceEvent e, int index) {
        String prefix = e.context().simulated() ? "[DEMO] " : "";
        if (e instanceof KillEvent k)
            return List.of(
                    prefix + shortName(k.victim() == null ? k.entity() : k.victim()),
                    "Killed by " + shortName(e.context().actor()),
                    "Cause: " + (k.cause() == null ? "Unknown" : shortName(k.cause())));
        if (e instanceof ItemEvent i)
            return List.of(
                    prefix
                            + (i.type() == EventType.ITEM_ADD ? "+" : "-")
                            + i.quantity()
                            + " "
                            + shortName(i.item()),
                    shortName(e.context().actor()));
        String approximate = e.context().source().contains("APPROXIMATE") ? "~" : "";
        return List.of(
                prefix
                        + approximate
                        + "#"
                        + index
                        + (e.type() == EventType.BLOCK_BREAK ? " broke" : " placed"),
                shortName(((BlockEvent) e).block()),
                shortName(e.context().actor()));
    }

    private static String shortName(String value) {
        String text = value.startsWith("minecraft:") ? value.substring(10) : value;
        return text.length() > 32 ? text.substring(0, 29) + "..." : text;
    }
}
