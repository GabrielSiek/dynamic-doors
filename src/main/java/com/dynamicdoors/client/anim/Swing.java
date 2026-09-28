package com.dynamicdoors.client.anim;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Predicate;

/**
 * One rigid piece of a swinging block: the quads it covers (all of them when {@code quads} is null),
 * and the hinge it turns around. Turning the closed model by {@code 90 * sign} degrees lands it on
 * the open model.
 */
record Swing(@Nullable Predicate<BakedQuad> quads, Vector3f pivot, Axis axis, float sign) {
    private static final int STRIDE = DefaultVertexFormat.BLOCK.getIntegerSize();

    Quaternionf rotation(float openAmount) {
        return axis.rotationDegrees(90 * sign * openAmount);
    }

    /** Pieces of a door/trapdoor/gate, from its closed state; null when it can't be animated. */
    @Nullable
    static Swing[] of(BlockState closed) {
        try {
            if (closed.getBlock() instanceof FenceGateBlock) return gate(closed);
            return hinged(closed);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Doors and trapdoors: a slab that turns 90 degrees around the edge its closed and open shapes
     * share. The overlap of the two boxes is the hinge column; its long side is the rotation axis.
     */
    @Nullable
    private static Swing[] hinged(BlockState closed) {
        AABB c = bounds(closed);
        AABB o = bounds(closed.setValue(BlockStateProperties.OPEN, true));
        if (c == null || o == null) return null;
        double minX = Math.max(c.minX, o.minX), maxX = Math.min(c.maxX, o.maxX);
        double minY = Math.max(c.minY, o.minY), maxY = Math.min(c.maxY, o.maxY);
        double minZ = Math.max(c.minZ, o.minZ), maxZ = Math.min(c.maxZ, o.maxZ);
        if (minX >= maxX || minY >= maxY || minZ >= maxZ) return null;
        double[] sizes = {maxX - minX, maxY - minY, maxZ - minZ};
        int axisIndex = -1;
        for (int i = 0; i < 3; i++) {
            if (sizes[i] > 0.9) {
                if (axisIndex >= 0) return null;
                axisIndex = i;
            }
        }
        if (axisIndex < 0) return null;
        Axis axis = axisIndex == 0 ? Axis.XP : axisIndex == 1 ? Axis.YP : Axis.ZP;
        Vector3f pivot = new Vector3f((float) (minX + maxX) / 2, (float) (minY + maxY) / 2, (float) (minZ + maxZ) / 2);
        Vector3f from = center(c).sub(pivot);
        Vector3f to = center(o).sub(pivot);
        float sign = distanceAfter(from, to, axis, 1) <= distanceAfter(from, to, axis, -1) ? 1 : -1;
        return new Swing[]{new Swing(null, pivot, axis, sign)};
    }

    /**
     * Fence gates: two wings, each turning around its post, towards the facing (vanilla opens gates
     * away from the player and points the open wings along the facing).
     */
    private static Swing[] gate(BlockState closed) {
        Direction facing = closed.getValue(FenceGateBlock.FACING);
        Direction side = facing.getClockWise();
        Vector3f lateral = new Vector3f(side.getStepX(), 0, side.getStepZ());
        Vector3f forward = new Vector3f(facing.getStepX(), 0, facing.getStepZ());
        Swing[] wings = new Swing[2];
        for (int i = 0; i < 2; i++) {
            float s = i == 0 ? -1 : 1;
            Vector3f pivot = new Vector3f(0.5F, 0.5F, 0.5F).add(new Vector3f(lateral).mul(s * 7 / 16F));
            Vector3f wing = new Vector3f(lateral).mul(-s);
            float sign = Axis.YP.rotationDegrees(90).transform(new Vector3f(wing)).dot(forward) > 0 ? 1 : -1;
            Predicate<BakedQuad> half = q -> s * lateralOffset(q, lateral) > 0;
            wings[i] = new Swing(half, pivot, Axis.YP, sign);
        }
        return wings;
    }

    private static float lateralOffset(BakedQuad quad, Vector3f lateral) {
        int[] v = quad.getVertices();
        float x = 0, z = 0;
        for (int i = 0; i < 4; i++) {
            x += Float.intBitsToFloat(v[i * STRIDE]);
            z += Float.intBitsToFloat(v[i * STRIDE + 2]);
        }
        return (x / 4 - 0.5F) * lateral.x + (z / 4 - 0.5F) * lateral.z;
    }

    @Nullable
    private static AABB bounds(BlockState state) {
        VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return shape.isEmpty() ? null : shape.bounds();
    }

    private static Vector3f center(AABB box) {
        return new Vector3f((float) (box.minX + box.maxX) / 2, (float) (box.minY + box.maxY) / 2,
                (float) (box.minZ + box.maxZ) / 2);
    }

    private static float distanceAfter(Vector3f from, Vector3f to, Axis axis, float sign) {
        return axis.rotationDegrees(90 * sign).transform(new Vector3f(from)).distance(to);
    }
}
