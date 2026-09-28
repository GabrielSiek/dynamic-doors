package com.dynamicdoors.client.anim;

import net.minecraft.util.Mth;

public final class Easing {
    private Easing() {
    }

    /** Ease-in-out: the door starts and settles gently. */
    public static float swing(float t) {
        t = Mth.clamp(t, 0, 1);
        return t < 0.5F ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }
}
