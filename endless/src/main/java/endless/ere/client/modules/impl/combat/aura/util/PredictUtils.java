package endless.ere.client.modules.impl.combat.aura.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Перенос PredictUtils из Wraith - предсказывает где будет цель через N тиков. */
public final class PredictUtils {

    public static Vec3d predict(LivingEntity target, double ticksAhead) {
        if (Math.hypot(target.prevX - target.getX(), target.prevZ - target.getZ()) * 20.0 <= 1.0
                && target.prevY - target.getY() <= 1.0) {
            return target.getPos().add(0, target.getHeight() / 2.0f, 0);
        }
        Vec3d forward = Vec3d.fromPolar(
                target.getPitch() + (target.getPitch() - target.prevPitch),
                target.getYaw() + (target.getYaw() - target.prevYaw))
                .multiply(new Vec3d(target.getX() - target.prevX, target.getY() - target.prevY,
                        target.getZ() - target.prevZ).length() * ticksAhead);

        Vec3d look = Vec3d.fromPolar(target.getPitch() + (target.getPitch() - target.prevPitch),
                target.getYaw() + (target.getYaw() - target.prevYaw));
        float pitchRad = target.getPitch() * ((float) Math.PI / 180);
        double d = Math.sqrt(look.x * look.x + look.z * look.z);
        double e = forward.length();
        boolean falling = target.getVelocity().y <= 0.0;
        double g = falling && target.hasStatusEffect(StatusEffects.SLOW_FALLING)
                ? Math.min(target.getFinalGravity(), 0.01)
                : target.getFinalGravity();
        double h = MathHelper.square(Math.cos(pitchRad));
        forward = forward.add(0, g * (-1.0 + h * 0.75), 0);

        if (forward.y < 0.0 && d > 0.0) {
            double i = forward.y * -0.1 * h;
            forward = forward.add(look.x * i / d, i, look.z * i / d);
        }
        if (pitchRad < 0.0f && d > 0.0) {
            double i = e * (double) (-MathHelper.sin(pitchRad)) * 0.04;
            forward = forward.add(-look.x * i / d, i * 2.2f, -look.z * i / d);
        }
        if (d > 0.0) {
            forward = forward.add((look.x / d * e - forward.x) * 0.1, 0.0,
                    (look.z / d * e - forward.z) * 0.1);
        }
        return target.getPos().add(forward);
    }

    private PredictUtils() {}
}
