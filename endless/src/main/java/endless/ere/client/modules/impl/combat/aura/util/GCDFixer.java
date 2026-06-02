package endless.ere.client.modules.impl.combat.aura.util;

import net.minecraft.client.MinecraftClient;

/** Перенос GCDFixer из Wraith - работает с реальной чувствительностью игрока. */
public final class GCDFixer {

    public static float getFixRotate(float rot) {
        return getDeltaMouse(rot) * getGCDValue();
    }

    public static float getGCDValue() {
        return (float) ((double) getGCD() * 0.15);
    }

    public static float getGCD() {
        double v = MinecraftClient.getInstance().options.getMouseSensitivity().getValue() / 0.15 / 8.0;
        double cb = Math.cbrt(v);
        float f1 = (float) ((cb - 0.2) / 0.6 * 0.6 + 0.2);
        return f1 * f1 * f1 * 8.0f;
    }

    public static float getDeltaMouse(float delta) {
        return Math.round(delta / getGCDValue());
    }

    private GCDFixer() {}
}
