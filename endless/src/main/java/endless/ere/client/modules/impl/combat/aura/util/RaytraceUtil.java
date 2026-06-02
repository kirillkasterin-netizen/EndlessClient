package endless.ere.client.modules.impl.combat.aura.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import endless.ere.client.modules.impl.combat.aura.Rotation;

import java.util.Objects;
import java.util.function.Predicate;

/** Перенос RaytraceUtil из Wraith. */
public final class RaytraceUtil {

    private static MinecraftClient mc() { return MinecraftClient.getInstance(); }

    public static BlockHitResult raycast(Vec3d start, Vec3d end, RaycastContext.ShapeType shapeType) {
        return raycast(start, end, shapeType, mc().player);
    }

    public static BlockHitResult raycast(Vec3d start, Vec3d end, RaycastContext.ShapeType shapeType, Entity entity) {
        return mc().world.raycast(new RaycastContext(start, end, shapeType,
                RaycastContext.FluidHandling.NONE, entity));
    }

    public static EntityHitResult raytraceEntity(double range, Rotation angle, Predicate<Entity> filter) {
        Entity entity = mc().getCameraEntity();
        if (entity == null) return null;
        Vec3d cameraVec = entity.getCameraPosVec(1.0f);
        Vec3d rotationVec = angle.toVector();
        Vec3d end = cameraVec.add(rotationVec.x * range, rotationVec.y * range, rotationVec.z * range);
        Box box = entity.getBoundingBox().stretch(rotationVec.multiply(range)).expand(1.0, 1.0, 1.0);
        return ProjectileUtil.raycast(entity, cameraVec, end, box,
                e -> !e.isSpectator() && filter.test(e), range * range);
    }

    /** Базовая raytrace для проверки попадания в Box. */
    public static boolean rayTrace(Vec3d clientVec, double range, Box box) {
        Vec3d cameraVec = Objects.requireNonNull(mc().player).getEyePos();
        return box.contains(cameraVec)
                || box.raycast(cameraVec, cameraVec.add(clientVec.multiply(range))).isPresent();
    }

    private RaytraceUtil() {}
}
