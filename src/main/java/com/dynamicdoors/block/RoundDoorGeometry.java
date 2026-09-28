package com.dynamicdoors.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.AABB;

/**
 * Shape of the round door in door-local pixels, shared by the collision shapes and the renderer.
 *
 * <p>The door covers 3x3 blocks = 48x48 pixels and one block (16 pixels) of depth. Local x runs
 * along the placer's left (facing.getCounterClockWise()), y up, z along the facing (away from the
 * placer). The front 12 pixels are a square frame with a round hole; the back 4 pixels hold a round
 * panel slightly larger than the hole, which swings away from the frame side like a hobbit door.
 */
public final class RoundDoorGeometry {
    public static final int SIZE = 48;
    public static final int CENTER = 24;
    public static final int HOLE_RADIUS = 22;
    public static final int PANEL_RADIUS = 24;
    /** Frame occupies z in [0, FRAME_DEPTH), the panel z in [FRAME_DEPTH, DEPTH). */
    public static final int FRAME_DEPTH = 12;
    public static final int DEPTH = 16;
    /** The knob sticks out this far on both faces of the panel. */
    public static final int KNOB_DEPTH = 2;

    private RoundDoorGeometry() {
    }

    private static boolean inCircle(int x, int y, int r) {
        double dx = x + 0.5 - CENTER;
        double dy = y + 0.5 - CENTER;
        return dx * dx + dy * dy <= r * r;
    }

    /** Pixel column (x, y) of the frame, which spans z in [0, FRAME_DEPTH). */
    public static boolean isFrame(int x, int y) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE && !inCircle(x, y, HOLE_RADIUS);
    }

    /** Pixel column (x, y) of the panel, which spans z in [FRAME_DEPTH, DEPTH) when closed. */
    public static boolean isPanel(int x, int y) {
        return inCircle(x, y, PANEL_RADIUS);
    }

    /**
     * Pixel column of the knob, near the edge that opens: the far side from the hinge. A right hinge
     * sits at x = 0, so its knob is on the high-x side.
     */
    public static boolean isKnob(int x, int y, DoorHingeSide hinge) {
        int x0 = hinge == DoorHingeSide.RIGHT ? SIZE - 9 : 6;
        return x >= x0 && x < x0 + 3 && y >= CENTER - 2 && y < CENTER + 1;
    }

    /**
     * World box of the fully open panel. It swings out of the door's own blocks, so it has no block
     * to click; the client ray-tests this box instead (and the server re-checks it).
     */
    public static AABB openPanelBox(BlockPos master, Direction facing, DoorHingeSide hinge) {
        float x0 = hinge == DoorHingeSide.RIGHT ? 0 : SIZE - PANEL_THICKNESS;
        return localBox(master, facing, x0, 0, DEPTH, x0 + PANEL_THICKNESS, SIZE, DEPTH + SIZE);
    }

    public static final int PANEL_THICKNESS = DEPTH - FRAME_DEPTH;

    /** A box in door-local pixels (origin at the frame's front bottom corner) to world coordinates. */
    private static AABB localBox(BlockPos master, Direction facing, float x0, float y0, float z0, float x1, float y1, float z1) {
        Direction left = facing.getCounterClockWise();
        double ax = master.getX() + 0.5 + left.getStepX() * (x0 / 16 - 1.5) + facing.getStepX() * (z0 / 16 - 0.5);
        double az = master.getZ() + 0.5 + left.getStepZ() * (x0 / 16 - 1.5) + facing.getStepZ() * (z0 / 16 - 0.5);
        double bx = master.getX() + 0.5 + left.getStepX() * (x1 / 16 - 1.5) + facing.getStepX() * (z1 / 16 - 0.5);
        double bz = master.getZ() + 0.5 + left.getStepZ() * (x1 / 16 - 1.5) + facing.getStepZ() * (z1 / 16 - 0.5);
        return new AABB(ax, master.getY() + y0 / 16, az, bx, master.getY() + y1 / 16, bz);
    }
}
