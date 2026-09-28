package com.dynamicdoors.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/** Per-part voxel shapes of the round door, built once from {@link RoundDoorGeometry}. */
public final class RoundDoorShapes {
    /** [horizontal facing][part][open][collision]. */
    private static final VoxelShape[][][][] SHAPES = new VoxelShape[4][9][2][2];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int part = 0; part < 9; part++) {
                for (int open = 0; open < 2; open++) {
                    for (int collision = 0; collision < 2; collision++) {
                        SHAPES[facing.get2DDataValue()][part][open][collision] =
                                build(facing, part, open == 1, collision == 1);
                    }
                }
            }
        }
    }

    private RoundDoorShapes() {
    }

    /** [horizontal facing][hinge][leaf index]. */
    private static final VoxelShape[][][] LEAVES = new VoxelShape[4][2][9];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (DoorHingeSide hinge : DoorHingeSide.values()) {
                for (int k = 0; k < 9; k++) LEAVES[facing.get2DDataValue()][hinge.ordinal()][k] = buildLeaf(facing, hinge, k);
            }
        }
    }

    public static VoxelShape get(Direction facing, int part, boolean open, boolean collision) {
        return SHAPES[facing.get2DDataValue()][part][open ? 1 : 0][collision ? 1 : 0];
    }

    /** Shape of one leaf block: the slice of the open panel that lies in it. */
    public static VoxelShape leaf(Direction facing, DoorHingeSide hinge, int k) {
        return LEAVES[facing.get2DDataValue()][hinge.ordinal()][k];
    }

    /**
     * The open panel lies flat against the hinge side, {@code PANEL_THICKNESS} pixels thick. A pixel
     * {@code s} past the hinge axis (the back face of the door) holds the panel column that was
     * {@code s} pixels from the hinge edge when closed, so its height follows the circle.
     */
    private static VoxelShape buildLeaf(Direction facing, DoorHingeSide hinge, int k) {
        boolean right = hinge == DoorHingeSide.RIGHT;
        int row = k / 3;
        int depth = k % 3;
        int x0 = right ? 0 : 16 - RoundDoorGeometry.PANEL_THICKNESS;
        int x1 = x0 + RoundDoorGeometry.PANEL_THICKNESS;
        VoxelShape shape = Shapes.empty();
        int prevLo = -1, prevHi = -1, start = 0;
        for (int dz = 0; dz <= 16; dz++) {
            int lo = -1, hi = -1;
            if (dz < 16) {
                int s = depth * 16 + dz;
                int column = right ? s : RoundDoorGeometry.SIZE - 1 - s;
                for (int y = 0; y < 16; y++) {
                    if (RoundDoorGeometry.isPanel(column, row * 16 + y)) {
                        if (lo < 0) lo = y;
                        hi = y + 1;
                    }
                }
            }
            if (lo != prevLo || hi != prevHi) {
                if (prevLo >= 0) {
                    shape = Shapes.joinUnoptimized(shape, box(facing, x0, prevLo, start, x1, prevHi, dz), BooleanOp.OR);
                }
                prevLo = lo;
                prevHi = hi;
                start = dz;
            }
        }
        return shape.optimize();
    }

    private static VoxelShape build(Direction facing, int part, boolean open, boolean collision) {
        int bx = (part % 3) * 16;
        int by = (part / 3) * 16;
        VoxelShape shape = Shapes.empty();
        // The sill under the middle of the doorway is only a few pixels high: leave it out of the
        // collision so walking through does not bump.
        if (!(collision && part == RoundDoorBlock.MASTER)) {
            shape = rows(shape, facing, bx, by, 0, RoundDoorGeometry.FRAME_DEPTH, RoundDoorGeometry::isFrame);
        }
        if (!open) {
            shape = rows(shape, facing, bx, by, RoundDoorGeometry.FRAME_DEPTH, RoundDoorGeometry.DEPTH,
                    RoundDoorGeometry::isPanel);
        }
        return shape.optimize();
    }

    /** Adds one box per run of pixels, merging rows whose runs are identical. */
    private static VoxelShape rows(VoxelShape shape, Direction facing, int bx, int by, int z0, int z1,
                                   BiPredicate<Integer, Integer> filled) {
        List<int[]> prevRuns = null;
        int prevStart = 0;
        for (int y = 0; y <= 16; y++) {
            List<int[]> runs = y < 16 ? runs(bx, by + y, filled) : null;
            if (prevRuns != null && !sameRuns(prevRuns, runs)) {
                for (int[] run : prevRuns) shape = Shapes.joinUnoptimized(shape,
                        box(facing, run[0], prevStart, z0, run[1], y, z1), BooleanOp.OR);
                prevRuns = null;
            }
            if (prevRuns == null && runs != null && !runs.isEmpty()) {
                prevRuns = runs;
                prevStart = y;
            }
        }
        return shape;
    }

    private static List<int[]> runs(int bx, int y, BiPredicate<Integer, Integer> filled) {
        List<int[]> runs = new ArrayList<>();
        int start = -1;
        for (int x = 0; x <= 16; x++) {
            boolean f = x < 16 && filled.test(bx + x, y);
            if (f && start < 0) start = x;
            if (!f && start >= 0) {
                runs.add(new int[]{start, x});
                start = -1;
            }
        }
        return runs;
    }

    private static boolean sameRuns(List<int[]> a, List<int[]> b) {
        if (b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i)[0] != b.get(i)[0] || a.get(i)[1] != b.get(i)[1]) return false;
        }
        return true;
    }

    /** A box in door-local block pixels, rotated to the facing around the block centre. */
    private static VoxelShape box(Direction facing, int x0, int y0, int z0, int x1, int y1, int z1) {
        Direction left = facing.getCounterClockWise();
        double ax = 8 + left.getStepX() * (x0 - 8) + facing.getStepX() * (z0 - 8);
        double az = 8 + left.getStepZ() * (x0 - 8) + facing.getStepZ() * (z0 - 8);
        double bx = 8 + left.getStepX() * (x1 - 8) + facing.getStepX() * (z1 - 8);
        double bz = 8 + left.getStepZ() * (x1 - 8) + facing.getStepZ() * (z1 - 8);
        return Block.box(Math.min(ax, bx), y0, Math.min(az, bz), Math.max(ax, bx), y1, Math.max(az, bz));
    }
}
