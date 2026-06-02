package endless.ere.client.modules.impl.combat.aura.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import endless.ere.client.modules.impl.combat.aura.Rotation;

/** Перенос RotationUtil из Wraith. */
public final class RotationUtil {

    private static MinecraftClient mc() { return MinecraftClient.getInstance(); }

    public static Vec2f calculate(Vec3d fromVec, Vec3d toVec) {
        Vec3d diff = toVec.subtract(fromVec);
        double dist = Math.hypot(diff.x, diff.z);
        float yaw = (float) (MathHelper.atan2(diff.z, diff.x) * 57.29577951308232) - 90.0f;
        float pitch = (float) (-(MathHelper.atan2(diff.y, dist) * 57.29577951308232));
        return new Vec2f(pitch, yaw);
    }

    public static Vec2f calculate(Entity entity) {
        return calculate(entity.getPos().add(0, entity.getEyeHeight(entity.getPose()), 0));
    }

    public static Vec2f calculate(Vec3d toVec) {
        return calculate(getEyesPos(mc().player), toVec);
    }

    public static Vec3d getEyesPos(Entity entity) {
        return entity.getPos().add(0, entity.getEyeHeight(entity.getPose()), 0);
    }

    /** Возвращает массив [yaw, pitch]. */
    public static float[] calculateAngle(Vec3d to) {
        return calculateAngle(getEyesPos(mc().player), to);
    }

    public static float[] calculateAngle(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = (to.y - from.y) * -1.0;
        double dz = to.z - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yD = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pD = (float) MathHelper.clamp(MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dy, dist))), -90.0, 90.0);
        return new float[]{yD, pD};
    }

    public static Rotation fromVec3d(Vec3d v) {
        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(v.z, v.x)) - 90.0);
        float pitch = (float) MathHelper.wrapDegrees(Math.toDegrees(-Math.atan2(v.y, Math.hypot(v.x, v.z))));
        return new Rotation(yaw, pitch);
    }

    private RotationUtil() {}
}
