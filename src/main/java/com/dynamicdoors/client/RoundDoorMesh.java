package com.dynamicdoors.client;

import com.dynamicdoors.block.RoundDoorGeometry;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;

import java.util.ArrayList;
import java.util.List;

/**
 * Quads of the round door in door-local pixels (see {@link RoundDoorGeometry}), greedy-meshed from
 * a 1-pixel voxel grid. Planks faces are split on 16-pixel tile borders so they tile the planks
 * texture; the panel faces map one 48x48 picture. Built once; textures are picked per door at
 * render time.
 */
public final class RoundDoorMesh {
    public enum Material { FRAME, PANEL_FACE, PANEL_EDGE, KNOB }

    public record Quad(float[] pos, float[] uv, Direction normal, Material material) {
    }

    private static final int[] DIMS = {RoundDoorGeometry.SIZE, RoundDoorGeometry.SIZE,
            RoundDoorGeometry.DEPTH + RoundDoorGeometry.KNOB_DEPTH};

    /** Static frame. */
    public static final List<Quad> FRAME = mesh(RoundDoorMesh::frame);
    /** Panel and knob, closed, per hinge side; the renderer rotates them around the hinge. */
    public static final List<Quad> PANEL_LEFT_HINGE = mesh((x, y, z) -> panel(x, y, z, DoorHingeSide.LEFT));
    public static final List<Quad> PANEL_RIGHT_HINGE = mesh((x, y, z) -> panel(x, y, z, DoorHingeSide.RIGHT));

    private static final int EMPTY = 0, FRAME_VOXEL = 1, PANEL_VOXEL = 2, KNOB_VOXEL = 3;

    private RoundDoorMesh() {
    }

    private interface Voxels {
        int at(int x, int y, int z);
    }

    private static int frame(int x, int y, int z) {
        return z < RoundDoorGeometry.FRAME_DEPTH && RoundDoorGeometry.isFrame(x, y) ? FRAME_VOXEL : EMPTY;
    }

    private static int panel(int x, int y, int z, DoorHingeSide hinge) {
        if (z >= RoundDoorGeometry.FRAME_DEPTH && z < RoundDoorGeometry.DEPTH) {
            return RoundDoorGeometry.isPanel(x, y) ? PANEL_VOXEL : EMPTY;
        }
        boolean knobDepth = z >= RoundDoorGeometry.FRAME_DEPTH - RoundDoorGeometry.KNOB_DEPTH
                && z < RoundDoorGeometry.DEPTH + RoundDoorGeometry.KNOB_DEPTH;
        return knobDepth && RoundDoorGeometry.isKnob(x, y, hinge) ? KNOB_VOXEL : EMPTY;
    }

    private static Material material(int voxel, Direction face) {
        return switch (voxel) {
            case FRAME_VOXEL -> Material.FRAME;
            case PANEL_VOXEL -> face.getAxis() == Direction.Axis.Z ? Material.PANEL_FACE : Material.PANEL_EDGE;
            default -> Material.KNOB;
        };
    }

    private static int get(Voxels voxels, int[] p) {
        for (int i = 0; i < 3; i++) if (p[i] < 0 || p[i] >= DIMS[i]) return EMPTY;
        return voxels.at(p[0], p[1], p[2]);
    }

    private static List<Quad> mesh(Voxels voxels) {
        List<Quad> quads = new ArrayList<>();
        int[] p = new int[3];
        for (Direction face : Direction.values()) {
            int a = face.getAxis().ordinal();
            int u = (a + 1) % 3;
            int v = (a + 2) % 3;
            int step = face.getAxisDirection().getStep();
            Material[][] mask = new Material[DIMS[u]][DIMS[v]];
            for (int s = 0; s < DIMS[a]; s++) {
                for (int iu = 0; iu < DIMS[u]; iu++) {
                    for (int iv = 0; iv < DIMS[v]; iv++) {
                        p[a] = s;
                        p[u] = iu;
                        p[v] = iv;
                        int voxel = get(voxels, p);
                        mask[iu][iv] = null;
                        if (voxel == EMPTY) continue;
                        p[a] = s + step;
                        if (get(voxels, p) == EMPTY) mask[iu][iv] = material(voxel, face);
                    }
                }
                for (int iv = 0; iv < DIMS[v]; iv++) {
                    for (int iu = 0; iu < DIMS[u]; iu++) {
                        Material m = mask[iu][iv];
                        if (m == null) continue;
                        int w = 1;
                        while (iu + w < DIMS[u] && (iu + w) % 16 != 0 && mask[iu + w][iv] == m) w++;
                        int h = 1;
                        grow:
                        while (iv + h < DIMS[v] && (iv + h) % 16 != 0) {
                            for (int k = 0; k < w; k++) if (mask[iu + k][iv + h] != m) break grow;
                            h++;
                        }
                        for (int k = 0; k < w; k++) for (int l = 0; l < h; l++) mask[iu + k][iv + l] = null;
                        quads.add(quad(face, a, u, v, s + (step > 0 ? 1 : 0), iu, iv, w, h, m));
                    }
                }
            }
        }
        return List.copyOf(quads);
    }

    private static Quad quad(Direction face, int a, int u, int v, int plane, int u0, int v0, int w, int h, Material m) {
        int[][] corners = {{u0, v0}, {u0 + w, v0}, {u0 + w, v0 + h}, {u0, v0 + h}};
        // (u, v, a) is a cyclic permutation of (x, y, z), so this order faces +a; flip it for -a.
        if (face.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            corners = new int[][]{corners[0], corners[3], corners[2], corners[1]};
        }
        int[] origin = new int[3];
        origin[u] = Math.floorDiv(u0, 16) * 16;
        origin[v] = Math.floorDiv(v0, 16) * 16;
        float[] pos = new float[12];
        float[] uv = new float[8];
        for (int i = 0; i < 4; i++) {
            int[] c = new int[3];
            c[a] = plane;
            c[u] = corners[i][0];
            c[v] = corners[i][1];
            pos[i * 3] = c[0];
            pos[i * 3 + 1] = c[1];
            pos[i * 3 + 2] = c[2];
            float tx = c[0] - origin[0], ty = c[1] - origin[1], tz = c[2] - origin[2];
            // Texture u runs to the viewer's right, v downwards.
            if (m == Material.PANEL_FACE) {
                // One 48x48 picture across the whole panel, scaled into the sprite's 0..16 range.
                float scale = 16F / RoundDoorGeometry.SIZE;
                uv[i * 2] = (face == Direction.SOUTH ? c[0] : RoundDoorGeometry.SIZE - c[0]) * scale;
                uv[i * 2 + 1] = (RoundDoorGeometry.SIZE - c[1]) * scale;
                continue;
            }
            switch (face) {
                case SOUTH -> { uv[i * 2] = tx; uv[i * 2 + 1] = 16 - ty; }
                case NORTH -> { uv[i * 2] = 16 - tx; uv[i * 2 + 1] = 16 - ty; }
                case EAST -> { uv[i * 2] = 16 - tz; uv[i * 2 + 1] = 16 - ty; }
                case WEST -> { uv[i * 2] = tz; uv[i * 2 + 1] = 16 - ty; }
                default -> { uv[i * 2] = tx; uv[i * 2 + 1] = tz; }
            }
        }
        return new Quad(pos, uv, face, m);
    }
}
