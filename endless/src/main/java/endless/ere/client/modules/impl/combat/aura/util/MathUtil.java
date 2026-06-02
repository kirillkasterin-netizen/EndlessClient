package endless.ere.client.modules.impl.combat.aura.util;

import java.util.concurrent.ThreadLocalRandom;

/** Перенос подмножества MathUtil из Wraith. */
public final class MathUtil {

    public static float random(float min, float max) {
        return (float) (Math.random() * (max - min) + min);
    }

    public static double random(double min, double max) {
        return ThreadLocalRandom.current().nextDouble() * (max - min) + min;
    }

    public static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }

    private MathUtil() {}
}
