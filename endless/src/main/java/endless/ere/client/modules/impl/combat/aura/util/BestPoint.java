package endless.ere.client.modules.impl.combat.aura.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import endless.ere.client.modules.impl.combat.aura.Rotation;

/** Перенос BestPoint из Wraith. */
public final class BestPoint {

    private static MinecraftClient mc() { return MinecraftClient.getInstance(); }

    private static Vec3d rotationPoint = Vec3d.ZERO;
    private static Vec3d rotationMotion = Vec3d.ZERO;

    public static Vec3d getRotationPoint() { return rotationPoint; }

    public static Vec3d getNearestPoint(Entity entity) {
        Box box = entity.getBoundingBox();
        double step = 0.1;
        Vec3d best = null;
        double closest = Double.MAX_VALUE;
        for (double x = box.minX; x <= box.maxX; x += step) {
            for (double y = box.minY; y <= box.maxY; y += step) {
                for (double z = box.minZ; z <= box.maxZ; z += step) {
                    Vec3d p = new Vec3d(x, y, z);
                    double d = mc().player.getEyePos().distanceTo(p);
                    if (d < closest) {
                        closest = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    public static Vec3d getPoint(Entity target) {
        Box box = target.getBoundingBox();
        double w = box.maxX - box.minX;
        double h = box.maxY - box.minY;
        double d = box.maxZ - box.minZ;
        double bx = box.minX + w / 2.0;
        double by = box.minY + h * 0.7;
        double bz = box.minZ + d / 2.0;
        double t = (double) System.currentTimeMillis() / 50.0;
        int id = target.getId();
        double ox = Math.sin(t + id) * (w * 0.45);
        double oy = Math.cos(t * 0.8 + id) * (h * 0.1);
        double oz = Math.cos(t * 1.2 + id) * (d * 0.45);
        return new Vec3d(bx + ox, by + oy, bz + oz);
    }

    public static Vec3d getPoint2(Entity target) {
        Box box = target.getBoundingBox();
        double w = box.maxX - box.minX;
        double h = box.maxY - box.minY;
        double d = box.maxZ - box.minZ;
        double bx = box.minX + w / 2.0;
        double by = box.minY + h * 0.65;
        double bz = box.minZ + d / 2.0;
        double t = (double) System.currentTimeMillis() / 65.0;
        int id = target.getId();
        double ox = Math.sin(t + id) * (w * 0.7);
        double oy = Math.cos(t * 0.8 + id) * (h * 0.4);
        double oz = Math.cos(t * 1.2 + id) * (d * 0.7);
        return new Vec3d(bx + ox, by + oy, bz + oz);
    }

    public static Vec3d getNearestVisiblePoint(Entity target, Vec3d preferred, double range) {
        if (preferred == null || mc().player == null || mc().world == null) return preferred;
        if (isPointVisible(target, preferred, range)) return preferred;
        Box box = target.getBoundingBox();
        double step = 0.12;
        Vec3d best = null;
        double bestDist = Double.MAX_VALUE;
        for (double x = box.minX; x <= box.maxX; x += step) {
            for (double y = box.minY; y <= box.maxY; y += step) {
                for (double z = box.minZ; z <= box.maxZ; z += step) {
                    Vec3d s = new Vec3d(x, y, z);
                    if (!isPointVisible(target, s, range)) continue;
                    double dt = s.squaredDistanceTo(preferred);
                    if (dt < bestDist) {
                        bestDist = dt;
                        best = s;
                    }
                }
            }
        }
        return best != null ? best : preferred;
    }

    private static boolean isPointVisible(Entity target, Vec3d point, double range) {
        Vec3d eye = mc().player.getEyePos();
        double distance = eye.distanceTo(point);
        if (distance > range) return false;
        Vec3d dir = point.subtract(eye).normalize();
        if (!RaytraceUtil.rayTrace(dir, distance + 0.2, target.getBoundingBox())) return false;
        BlockHitResult hit = RaytraceUtil.raycast(eye, point, RaycastContext.ShapeType.OUTLINE);
        return hit.getType() == HitResult.Type.MISS
                || eye.squaredDistanceTo(hit.getPos()) >= eye.squaredDistanceTo(point) - 1.0E-4;
    }

    public static Vec3d getMultipoint(Entity target, double distance) {
        float minXZ = 0.005f, maxXZ = 0.015f, minY = 0.0015f, maxY = 0.015f;
        Box box = target.getBoundingBox();
        double lenX = box.getLengthX();
        double lenY = box.getLengthY();
        double lenZ = box.getLengthZ();

        if (rotationMotion.equals(Vec3d.ZERO)) {
            rotationMotion = new Vec3d(MathUtil.random(-0.02f, 0.02f),
                    MathUtil.random(-0.02f, 0.02f), MathUtil.random(-0.02f, 0.02f));
        }
        if (rotationPoint.equals(Vec3d.ZERO)) {
            rotationPoint = new Vec3d(0.0, lenY * 0.5, 0.0);
        }
        rotationPoint = rotationPoint.add(rotationMotion);
        double safeX = (lenX - 0.1) / 2.0;
        double safeZ = (lenZ - 0.1) / 2.0;
        if (rotationPoint.x >= safeX) rotationMotion = new Vec3d(-MathUtil.random(minXZ, maxXZ), rotationMotion.y, rotationMotion.z);
        else if (rotationPoint.x <= -safeX) rotationMotion = new Vec3d(MathUtil.random(minXZ, maxXZ), rotationMotion.y, rotationMotion.z);
        if (rotationPoint.y >= lenY * 0.75) rotationMotion = new Vec3d(rotationMotion.x, -MathUtil.random(minY, maxY), rotationMotion.z);
        else if (rotationPoint.y <= lenY * 0.3) rotationMotion = new Vec3d(rotationMotion.x, MathUtil.random(minY, maxY), rotationMotion.z);
        if (rotationPoint.z >= safeZ) rotationMotion = new Vec3d(rotationMotion.x, rotationMotion.y, -MathUtil.random(minXZ, maxXZ));
        else if (rotationPoint.z <= -safeZ) rotationMotion = new Vec3d(rotationMotion.x, rotationMotion.y, MathUtil.random(minXZ, maxXZ));

        rotationPoint.add(MathUtil.random(-0.05f, 0.05f), 0.0, MathUtil.random(-0.05f, 0.05f));
        if (!RaytraceUtil.rayTrace(mc().player.getRotationVector(), distance, box)) {
            float halfBox = (float) (lenX / 2.0) * 0.8f;
            for (float x1 = -halfBox; x1 <= halfBox; x1 += 0.1f) {
                for (float z1 = -halfBox; z1 <= halfBox; z1 += 0.1f) {
                    float y1 = (float) (lenY * 0.9);
                    while ((double) y1 >= lenY * 0.3) {
                        Vec3d v1 = new Vec3d(target.getX() + x1, target.getY() + y1, target.getZ() + z1);
                        Rotation rot = RotationUtil.fromVec3d(v1);
                        if (RaytraceUtil.rayTrace(rot.toVector(), distance, box)) {
                            rotationPoint = new Vec3d(x1, y1, z1);
                            return target.getPos().add(rotationPoint);
                        }
                        y1 -= 0.1f;
                    }
                }
            }
        }
        return target.getPos().add(rotationPoint);
    }

    private BestPoint() {}
}
