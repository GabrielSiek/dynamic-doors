package com.dynamicdoors.client.anim;

import com.dynamicdoors.config.ClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.RenderTypeHelper;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Client-side swing animation for every door, trapdoor and fence gate, without mixins.
 *
 * <p>Their models are wrapped in {@link HideableModel}. The animator remembers the last state it saw
 * at each such block (learned when chunks are built, re-checked every tick). When OPEN flips, it
 * hides the block from the chunk mesh and draws the closed model itself, rotated around the hinge,
 * until the swing ends and the chunk is rebuilt with the static model again.
 */
public final class DoorAnimator {
    /** Keep drawing for a moment after the rebuild that shows the static model again, so it never blinks. */
    private static final long REVEAL_OVERLAP_NANOS = 150_000_000L;
    /** Give up on a rebuild that never comes (e.g. the chunk left the render distance). */
    private static final long REVEAL_TIMEOUT_NANOS = 2_000_000_000L;

    /** Last seen state of each animatable block, per chunk. Guarded by {@code DoorAnimator.class}. */
    private static final Map<Long, Map<Long, BlockState>> KNOWN = new HashMap<>();
    private static final Map<Long, Animation> ANIMATIONS = new ConcurrentHashMap<>();

    private DoorAnimator() {
    }

    private static final class Animation {
        final BlockPos pos;
        final BlockState closed;
        final Swing[] swings;
        final float from;
        final float to;
        final long start;
        final long duration;
        volatile BlockState target;
        volatile boolean revealRequested;
        volatile long revealRequestedAt;
        volatile long revealedAt;

        Animation(BlockPos pos, BlockState closed, Swing[] swings, BlockState target, float from, float to, long now) {
            this.pos = pos;
            this.closed = closed;
            this.swings = swings;
            this.target = target;
            this.from = from;
            this.to = to;
            this.start = now;
            long full = ClientConfig.get(ClientConfig.DURATION_TICKS) * 50_000_000L;
            this.duration = Math.max(1, (long) (full * Math.abs(to - from)));
        }

        float progress(long now) {
            return Math.min(1, (now - start) / (float) duration);
        }

        /** Linear open amount, 0 = closed, 1 = open. */
        float openAmount(long now) {
            return from + (to - from) * progress(now);
        }
    }

    // ---- which blocks ----

    /** Blocks that animate themselves; filled when models are baked. */
    private static volatile Set<Block> excluded = Set.of();

    private static boolean animatable(Block block) {
        return (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock)
                && !excluded.contains(block);
    }

    /**
     * Left alone: blocks with a block entity (they render themselves, like Create's sliding doors) and
     * blocks from the mods listed in the config.
     */
    private static boolean animatesItself(Block block) {
        if (block instanceof EntityBlock) return true;
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        return id != null && ClientConfig.get(ClientConfig.EXCLUDED_MODS).contains(id.getNamespace());
    }

    private static boolean enabled(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof DoorBlock) return ClientConfig.get(ClientConfig.ANIMATE_DOORS);
        if (block instanceof TrapDoorBlock) return ClientConfig.get(ClientConfig.ANIMATE_TRAPDOORS);
        if (block instanceof FenceGateBlock) return ClientConfig.get(ClientConfig.ANIMATE_FENCE_GATES);
        return false;
    }

    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        Set<Block> skip = new HashSet<>();
        for (Block block : ForgeRegistries.BLOCKS) {
            if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock) {
                if (animatesItself(block)) skip.add(block);
            }
        }
        excluded = Set.copyOf(skip);
        for (Block block : ForgeRegistries.BLOCKS) {
            if (!animatable(block)) continue;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (!state.hasProperty(BlockStateProperties.OPEN)) continue;
                models.computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                        (key, model) -> model instanceof HideableModel ? model : new HideableModel(model));
            }
        }
    }

    // ---- state tracking ----

    /** Called from chunk-building threads. Returns true when the block must stay out of the mesh. */
    static boolean onChunkCompile(BlockPos pos, BlockState state) {
        ClientLevel level = Minecraft.getInstance().level;
        // Other renderers (schematics, previews) call models with their own fake levels.
        if (level == null || level.getBlockState(pos) != state) return false;
        observe(pos, state);
        Animation anim = ANIMATIONS.get(pos.asLong());
        if (anim == null || anim.target != state) return false;
        if (!anim.revealRequested) return true;
        if (anim.revealedAt == 0) anim.revealedAt = System.nanoTime();
        return false;
    }

    private static synchronized void observe(BlockPos pos, BlockState state) {
        long key = pos.asLong();
        BlockState old = KNOWN.computeIfAbsent(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), k -> new HashMap<>())
                .put(key, state);
        if (old == null || old == state) return;
        Animation current = ANIMATIONS.get(key);
        if (old.getBlock() != state.getBlock()) {
            ANIMATIONS.remove(key);
            return;
        }
        boolean wasOpen = old.getValue(BlockStateProperties.OPEN);
        boolean isOpen = state.getValue(BlockStateProperties.OPEN);
        if (wasOpen == isOpen) {
            // Powered or similar: nothing to animate, keep a running swing on the new state.
            if (current != null) current.target = state;
            return;
        }
        if (!enabled(state)) {
            ANIMATIONS.remove(key);
            return;
        }
        long now = System.nanoTime();
        float from = current != null ? current.openAmount(now) : wasOpen ? 1 : 0;
        BlockState closed = state.setValue(BlockStateProperties.OPEN, false);
        Swing[] swings = Swing.of(closed);
        if (swings == null) {
            ANIMATIONS.remove(key);
            return;
        }
        ANIMATIONS.put(key, new Animation(pos, closed, swings, state, from, isOpen ? 1 : 0, now));
    }

    /** Catches toggles in chunk sections that are not being rebuilt yet (e.g. behind the camera). */
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        List<BlockPos> changed = new ArrayList<>();
        synchronized (DoorAnimator.class) {
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (Map<Long, BlockState> chunk : KNOWN.values()) {
                chunk.entrySet().removeIf(e -> {
                    BlockState now = level.getBlockState(p.set(e.getKey()));
                    if (now == e.getValue()) return false;
                    if (!animatable(now.getBlock()) || !now.hasProperty(BlockStateProperties.OPEN)) {
                        ANIMATIONS.remove(e.getKey());
                        return true;
                    }
                    changed.add(p.immutable());
                    return false;
                });
            }
            for (BlockPos pos : changed) observe(pos, level.getBlockState(pos));
        }
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        ChunkPos cp = event.getChunk().getPos();
        synchronized (DoorAnimator.class) {
            Map<Long, BlockState> chunk = KNOWN.remove(cp.toLong());
            if (chunk != null) chunk.keySet().forEach(ANIMATIONS::remove);
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        synchronized (DoorAnimator.class) {
            KNOWN.clear();
            ANIMATIONS.clear();
        }
    }

    // ---- drawing ----

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS || ANIMATIONS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        long now = System.nanoTime();
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        Set<RenderType> used = new HashSet<>();

        for (Animation anim : ANIMATIONS.values()) {
            // Broken or replaced mid-swing (the next tick forgets it too).
            if (level.getBlockState(anim.pos).getBlock() != anim.closed.getBlock()) {
                ANIMATIONS.remove(anim.pos.asLong(), anim);
                continue;
            }
            if (anim.progress(now) >= 1 && !anim.revealRequested) {
                anim.revealRequested = true;
                anim.revealRequestedAt = now;
                BlockPos p = anim.pos;
                mc.levelRenderer.setBlocksDirty(p.getX(), p.getY(), p.getZ(), p.getX(), p.getY(), p.getZ());
            }
            if (anim.revealRequested && (anim.revealedAt != 0 && now - anim.revealedAt > REVEAL_OVERLAP_NANOS
                    || now - anim.revealRequestedAt > REVEAL_TIMEOUT_NANOS)) {
                ANIMATIONS.remove(anim.pos.asLong(), anim);
                continue;
            }
            draw(anim, Easing.swing(anim.openAmount(now)), level, pose, cam, buffers, dispatcher, used);
        }
        for (RenderType type : used) buffers.endBatch(type);
    }

    private static void draw(Animation anim, float openAmount, ClientLevel level, PoseStack pose, Vec3 cam,
                             MultiBufferSource.BufferSource buffers, BlockRenderDispatcher dispatcher, Set<RenderType> used) {
        BakedModel model = dispatcher.getBlockModel(anim.closed);
        BlockState state = anim.closed;
        long seed = state.getSeed(anim.pos);
        int light = LevelRenderer.getLightColor(level, state, anim.pos);
        boolean constantAmbient = level.effects().constantAmbientLight();
        RandomSource rand = RandomSource.create();
        for (Swing swing : anim.swings) {
            BakedModel piece = swing.quads() == null ? model : new FilteredModel(model, swing.quads());
            Quaternionf rotation = swing.rotation(openAmount);
            Vector3f pivot = swing.pivot();
            pose.pushPose();
            pose.translate(anim.pos.getX() - cam.x, anim.pos.getY() - cam.y, anim.pos.getZ() - cam.z);
            pose.translate(pivot.x, pivot.y, pivot.z);
            pose.mulPose(rotation);
            pose.translate(-pivot.x, -pivot.y, -pivot.z);
            rand.setSeed(seed);
            for (RenderType type : piece.getRenderTypes(state, rand, ModelData.EMPTY)) {
                RenderType moving = RenderTypeHelper.getMovingBlockRenderType(type);
                VertexConsumer vc = buffers.getBuffer(moving);
                used.add(moving);
                for (Direction side : SIDES) {
                    rand.setSeed(seed);
                    for (BakedQuad quad : piece.getQuads(state, side, rand, ModelData.EMPTY, type)) {
                        // Shade by the turned face, not the model's: the vanilla renderer would keep a
                        // trapdoor's top face at full brightness while it stands up, then pop darker.
                        float shade = quad.isShade()
                                ? shade(rotation.transform(quad.getDirection().step()), constantAmbient) : 1;
                        float r = shade, g = shade, b = shade;
                        if (quad.isTinted()) {
                            int color = Minecraft.getInstance().getBlockColors().getColor(state, level, anim.pos, quad.getTintIndex());
                            r *= (color >> 16 & 255) / 255F;
                            g *= (color >> 8 & 255) / 255F;
                            b *= (color & 255) / 255F;
                        }
                        vc.putBulkData(pose.last(), quad, r, g, b, light, OverlayTexture.NO_OVERLAY);
                    }
                }
            }
            pose.popPose();
        }
    }

    private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST, null};

    /** Forge's directional block shading for any normal; equals vanilla's per-face values on the axes. */
    private static float shade(Vector3f n, boolean constantAmbient) {
        float yFactor = constantAmbient ? 0.9F : (3 + n.y) / 4;
        return Math.min(n.x * n.x * 0.6F + n.y * n.y * yFactor + n.z * n.z * 0.8F, 1);
    }

    /** The quads of one swinging piece (a fence gate wing). */
    private static final class FilteredModel extends BakedModelWrapper<BakedModel> {
        private final Predicate<BakedQuad> filter;

        FilteredModel(BakedModel original, Predicate<BakedQuad> filter) {
            super(original);
            this.filter = filter;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                        ModelData data, @Nullable RenderType renderType) {
            List<BakedQuad> quads = super.getQuads(state, side, rand, data, renderType);
            List<BakedQuad> kept = new ArrayList<>(quads.size());
            for (BakedQuad q : quads) if (filter.test(q)) kept.add(q);
            return kept;
        }
    }
}
