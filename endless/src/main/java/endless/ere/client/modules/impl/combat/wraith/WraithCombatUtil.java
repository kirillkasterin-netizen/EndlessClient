package endless.ere.client.modules.impl.combat.wraith;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import endless.ere.Endless;
import endless.ere.base.rotation.RotationTarget;
import endless.ere.utility.game.player.rotation.Rotation;
import endless.ere.utility.interfaces.IMinecraft;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Утилиты для портированных из WraithClient боевых модулей (KillAura, AimAssist).
 * Заменяют разрозненный стек Wraith (RotationUtil, BestPoint, PredictUtils,
 * RaytraceUtil, GCDFixer, RotationComponent) единым набором, завязанным на
 * систему ротаций endless ({@link endless.ere.base.rotation.RotationManager}).
 */
public final class WraithCombatUtil implements IMinecraft {

    private WraithCombatUtil() {
    }

    // ── Ротация ──────────────────────────────────────────────────────────────

    /** Отправляет ротацию через RotationManager endless (silent, instant-конфиг). */
    public static void updateRotation(float yaw, float pitch, int priority, Object module) {
        if (!(module instanceof endless.ere.client.modules.api.Module m)) return;
        Rotation rot = new Rotation(yaw, MathHelper.clamp(pitch, -90f, 90f));
        var rm = Endless.getInstance().getRotationManager();
        var aim = rm.getAimManager();
        rm.setRotation(
                new RotationTarget(rot, () -> aim.rotate(aim.getInstantSetup(), rot), aim.getInstantSetup()),
                priority, m);
    }

    public static Rotation serverRotation() {
        return Endless.getInstance().getRotationManager().getCurrentRotation();
    }

    // ── Углы ──────────────────────────────────────────────────────────────────

    public static Vec3d eyes(Entity entity) {
        return entity.getPos().add(0, entity.getEyeHeight(entity.getPose()), 0);
    }

    public static float[] calculateAngle(Vec3d to) {
        return calculateAngle(eyes(mc.player), to);
    }

    public static float[] calculateAngle(Vec3d from, Vec3d to) {
        double difX = to.x - from.x;
        double difY = (to.y - from.y) * -1.0;
        double difZ = to.z - from.z;
        double dist = MathHelper.sqrt((float) (difX * difX + difZ * difZ));
        float yD = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(difZ, difX)) - 90.0);
        float pD = (float) MathHelper.clamp(MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(difY, dist))), -90f, 90f);
        return new float[]{yD, pD};
    }

    public static float calculateFov(float cameraYaw, float cameraPitch, float targetYaw, float targetPitch) {
        Vec3d a = directionVector(cameraYaw, cameraPitch);
        Vec3d b = directionVector(targetYaw, targetPitch);
        double dot = MathHelper.clamp(a.dotProduct(b), -1.0, 1.0);
        return (float) Math.toDegrees(Math.acos(dot));
    }

    public static Vec3d directionVector(float yaw, float pitch) {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        float x = -MathHelper.cos(pitchRad) * MathHelper.sin(yawRad);
        float y = -MathHelper.sin(pitchRad);
        float z = MathHelper.cos(pitchRad) * MathHelper.cos(yawRad);
        return new Vec3d(x, y, z);
    }

    // ── GCD ────────────────────────────────────────────────────────────────────

    public static float getGCDValue() {
        return (float) (getGCD() * 0.15);
    }

    public static float getGCD() {
        float f1;
        double var11 = mc.options.getMouseSensitivity().getValue() / 0.15F / 8.0D;
        double var9 = Math.cbrt(var11);
        return (f1 = (float) ((var9 - 0.2f) / 0.6f * 0.6 + 0.2)) * f1 * f1 * 8;
    }

    // ── Raytrace ─────────────────────────────────────────────────────────────

    public static boolean rayTrace(Vec3d clientVec, double range, Box box) {
        Vec3d cameraVec = mc.player.getEyePos();
        return box.contains(cameraVec) || box.raycast(cameraVec, cameraVec.add(clientVec.multiply(range))).isPresent();
    }

    public static BlockHitResult raycast(Vec3d start, Vec3d end) {
        return mc.world.raycast(new RaycastContext(start, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
    }

    public static boolean visibleThroughBlocks(Entity target, Vec3d point) {
        Vec3d eye = mc.player.getEyePos();
        BlockHitResult hit = raycast(eye, point);
        return hit.getType() == HitResult.Type.MISS
                || eye.squaredDistanceTo(hit.getPos()) >= eye.squaredDistanceTo(point) - 1e-4;
    }

    // ── Точки прицеливания ───────────────────────────────────────────────────

    public static Vec3d getNearestPoint(Entity entity) {
        Box box = entity.getBoundingBox();
        double step = 0.1;
        Vec3d best = box.getCenter();
        double closest = Double.MAX_VALUE;
        Vec3d eye = mc.player.getEyePos();
        for (double x = box.minX; x <= box.maxX; x += step) {
            for (double y = box.minY; y <= box.maxY; y += step) {
                for (double z = box.minZ; z <= box.maxZ; z += step) {
                    Vec3d sample = new Vec3d(x, y, z);
                    double dist = eye.distanceTo(sample);
                    if (dist < closest) {
                        closest = dist;
                        best = sample;
                    }
                }
            }
        }
        return best;
    }

    /** Анимированная "блуждающая" точка по хитбоксу. */
    public static Vec3d getPoint(Entity target) {
        Box box = target.getBoundingBox();
        double width = box.maxX - box.minX;
        double height = box.maxY - box.minY;
        double depth = box.maxZ - box.minZ;
        double baseX = box.minX + width / 2.0;
        double baseY = box.minY + height * 0.7;
        double baseZ = box.minZ + depth / 2.0;
        double time = System.currentTimeMillis() / 50.0;
        int id = target.getId();
        double offsetX = Math.sin(time + id) * (width * 0.45);
        double offsetY = Math.cos(time * 0.8 + id) * (height * 0.1);
        double offsetZ = Math.cos(time * 1.2 + id) * (depth * 0.45);
        return new Vec3d(baseX + offsetX, baseY + offsetY, baseZ + offsetZ);
    }

    public static Vec3d getPoint2(Entity target) {
        Box box = target.getBoundingBox();
        double width = box.maxX - box.minX;
        double height = box.maxY - box.minY;
        double depth = box.maxZ - box.minZ;
        double baseX = box.minX + width / 2.0;
        double baseY = box.minY + height * 0.65;
        double baseZ = box.minZ + depth / 2.0;
        double time = System.currentTimeMillis() / 65.0;
        int id = target.getId();
        double offsetX = Math.sin(time + id) * (width * 0.7);
        double offsetY = Math.cos(time * 0.8 + id) * (height * 0.4);
        double offsetZ = Math.cos(time * 1.2 + id) * (depth * 0.7);
        return new Vec3d(baseX + offsetX, baseY + offsetY, baseZ + offsetZ);
    }

    /** Ближайшая видимая (не сквозь блоки) точка хитбокса к preferredPoint. */
    public static Vec3d getNearestVisiblePoint(Entity target, Vec3d preferredPoint, double range) {
        if (preferredPoint == null || mc.player == null || mc.world == null) return preferredPoint;
        if (isPointVisible(target, preferredPoint, range)) return preferredPoint;

        Box box = target.getBoundingBox();
        double step = 0.12;
        Vec3d bestPoint = null;
        double bestDistance = Double.MAX_VALUE;
        for (double x = box.minX; x <= box.maxX; x += step) {
            for (double y = box.minY; y <= box.maxY; y += step) {
                for (double z = box.minZ; z <= box.maxZ; z += step) {
                    Vec3d sample = new Vec3d(x, y, z);
                    if (!isPointVisible(target, sample, range)) continue;
                    double distanceToCurrent = sample.squaredDistanceTo(preferredPoint);
                    if (distanceToCurrent < bestDistance) {
                        bestDistance = distanceToCurrent;
                        bestPoint = sample;
                    }
                }
            }
        }
        return bestPoint != null ? bestPoint : preferredPoint;
    }

    private static boolean isPointVisible(Entity target, Vec3d point, double range) {
        Vec3d eyePos = mc.player.getEyePos();
        double distance = eyePos.distanceTo(point);
        if (distance > range) return false;
        Vec3d direction = point.subtract(eyePos).normalize();
        if (!rayTrace(direction, distance + 0.2, target.getBoundingBox())) return false;
        return visibleThroughBlocks(target, point);
    }

    private static Vec3d rotationPoint = Vec3d.ZERO;
    private static Vec3d rotationMotion = Vec3d.ZERO;

    /** Плавно движущийся мультипоинт по корпусу (порт BestPoint.getMultipoint). */
    public static Vec3d getMultipoint(Entity target, double distance) {
        float minMotionXZ = 0.005f, maxMotionXZ = 0.015f;
        float minMotionY = 0.0015f, maxMotionY = 0.015f;

        double lengthX = target.getBoundingBox().getLengthX();
        double lengthY = target.getBoundingBox().getLengthY();
        double lengthZ = target.getBoundingBox().getLengthZ();

        if (rotationMotion.equals(Vec3d.ZERO))
            rotationMotion = new Vec3d(random(-0.02f, 0.02f), random(-0.02f, 0.02f), random(-0.02f, 0.02f));
        if (rotationPoint.equals(Vec3d.ZERO))
            rotationPoint = new Vec3d(0, lengthY * 0.5, 0);

        rotationPoint = rotationPoint.add(rotationMotion);

        double safeX = (lengthX - 0.1) / 2f;
        double safeZ = (lengthZ - 0.1) / 2f;

        if (rotationPoint.x >= safeX)
            rotationMotion = new Vec3d(-random(minMotionXZ, maxMotionXZ), rotationMotion.getY(), rotationMotion.getZ());
        else if (rotationPoint.x <= -safeX)
            rotationMotion = new Vec3d(random(minMotionXZ, maxMotionXZ), rotationMotion.getY(), rotationMotion.getZ());

        if (rotationPoint.y >= lengthY * 0.75)
            rotationMotion = new Vec3d(rotationMotion.getX(), -random(minMotionY, maxMotionY), rotationMotion.getZ());
        else if (rotationPoint.y <= lengthY * 0.3)
            rotationMotion = new Vec3d(rotationMotion.getX(), random(minMotionY, maxMotionY), rotationMotion.getZ());

        if (rotationPoint.z >= safeZ)
            rotationMotion = new Vec3d(rotationMotion.getX(), rotationMotion.getY(), -random(minMotionXZ, maxMotionXZ));
        else if (rotationPoint.z <= -safeZ)
            rotationMotion = new Vec3d(rotationMotion.getX(), rotationMotion.getY(), random(minMotionXZ, maxMotionXZ));

        if (!rayTrace(mc.player.getRotationVector(), distance, target.getBoundingBox())) {
            float halfBox = (float) (lengthX / 2f) * 0.8f;
            for (float x1 = -halfBox; x1 <= halfBox; x1 += 0.1f) {
                for (float z1 = -halfBox; z1 <= halfBox; z1 += 0.1f) {
                    for (float y1 = (float) (lengthY * 0.9); y1 >= lengthY * 0.3; y1 -= 0.1f) {
                        Vec3d v1 = new Vec3d(target.getX() + x1, target.getY() + y1, target.getZ() + z1);
                        Vec3d dir = directionTo(v1);
                        if (rayTrace(dir, distance, target.getBoundingBox())) {
                            rotationPoint = new Vec3d(x1, y1, z1);
                            return target.getPos().add(rotationPoint);
                        }
                    }
                }
            }
        }
        return target.getPos().add(rotationPoint);
    }

    private static Vec3d directionTo(Vec3d to) {
        float[] a = calculateAngle(to);
        return directionVector(a[0], a[1]);
    }

    private static float random(float min, float max) {
        return (float) ThreadLocalRandom.current().nextDouble(min, max);
    }

    // ── Предикт ──────────────────────────────────────────────────────────────

    public static Vec3d predict(LivingEntity target, double ticksAhead) {
        if (Math.hypot(target.prevX - target.getX(), target.prevZ - target.getZ()) * 20 <= 1
                && (target.prevY - target.getY()) <= 1) {
            return target.getPos().add(0, target.getHeight() / 2, 0);
        }

        Vec3d forward = Vec3d.fromPolar(
                target.getPitch() + (target.getPitch() - target.prevPitch),
                target.getYaw() + (target.getYaw() - target.prevYaw))
                .multiply(new Vec3d(target.getX() - target.prevX, target.getY() - target.prevY,
                        target.getZ() - target.prevZ).length() * ticksAhead);

        Vec3d vec3d = target.getRotationVector(
                target.getPitch() + (target.getPitch() - target.prevPitch),
                target.getYaw() + (target.getYaw() - target.prevYaw));
        float f = target.getPitch() * 0.017453292F;
        double d = Math.sqrt(vec3d.x * vec3d.x + vec3d.z * vec3d.z);
        double e = forward.horizontalLength();
        boolean bl = target.getVelocity().y <= 0.0;
        double g = bl && target.hasStatusEffect(StatusEffects.SLOW_FALLING)
                ? Math.min(target.getFinalGravity(), 0.01) : target.getFinalGravity();
        double h = MathHelper.square(Math.cos((double) f));
        forward = forward.add(0.0, g * (-1.0 + h * 0.75), 0.0);
        double i;
        if (forward.y < 0.0 && d > 0.0) {
            i = forward.y * -0.1 * h;
            forward = forward.add(vec3d.x * i / d, i, vec3d.z * i / d);
        }
        if (f < 0.0F && d > 0.0) {
            i = e * (-MathHelper.sin(f)) * 0.04;
            forward = forward.add(-vec3d.x * i / d, i * 2.2f, -vec3d.z * i / d);
        }
        if (d > 0.0) {
            forward = forward.add((vec3d.x / d * e - forward.x) * 0.1, 0.0, (vec3d.z / d * e - forward.z) * 0.1);
        }
        return target.getPos().add(forward);
    }

    // ── Прочее ───────────────────────────────────────────────────────────────

    public static boolean hasBlindness() {
        return mc.player != null && mc.player.hasStatusEffect(StatusEffects.BLINDNESS);
    }

    public static double random(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    public static boolean isFriend(net.minecraft.entity.player.PlayerEntity player) {
        try {
            return Endless.getInstance().getFriendManager().isFriend(player.getGameProfile().getName());
        } catch (Exception e) {
            return false;
        }
    }
}
