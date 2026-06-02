package dev.endless.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import dev.endless.event.list.EventPlayerUpdate;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.util.player.other.InventoryUtil;
import dev.endless.util.player.other.SlownessManager;
import dev.endless.util.rotation.Rotation;
import dev.endless.util.rotation.RotationComponent;

@ModuleInformation(moduleName = "Target Pearl", moduleCategory = ModuleCategory.COMBAT)
public class TargetPearl extends Module {

    @Subscribe
    public void onTick(EventPlayerUpdate e) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.getHealth() <= 5 || mc.player.isUsingItem()) return;

        Entity target = KillAura.lastTarget;
        if (target == null) return;

        mc.world.getEntities().forEach(entity -> {
            if (!(entity instanceof EnderPearlEntity pearl)) return;

            if (pearl.getOwner() != target) return;

            if (intersectsPlayer(pearl.getPos(), pearl.getPos().add(pearl.getVelocity()))) return;

            Vec3d landing = simulatePearlLanding(pearl);
            if (landing == null) return;

            if (mc.player.getBoundingBox().expand(0.3).contains(landing)) return;

            throwPearlAt(landing);
        });
    }

    private boolean intersectsPlayer(Vec3d from, Vec3d to) {
        return mc.player.getBoundingBox()
                .expand(0.3)
                .raycast(from, to)
                .isPresent();
    }

    private void throwPearlAt(Vec3d target) {
        Vec3d eyePos = mc.player.getEyePos();

        Vec3d toTarget = target.subtract(eyePos);

        Vec3d playerVel = mc.player.getVelocity();

        Vec3d pidr = null;
        Vec3d vec3d = new Vec3d(0,0,0);

        {
            double gravity = 0.03;
            double velocity = 1.5;

            double dx = toTarget.x;
            double dy = toTarget.y;
            double dz = toTarget.z;

            double distXZ = Math.sqrt(dx * dx + dz * dz);

            double v2 = velocity * velocity;
            double underRoot = v2 * v2 - gravity * (gravity * distXZ * distXZ + 2 * dy * v2);

            if (underRoot <= 0) pidr = vec3d;

            double angle = Math.atan(
                    (v2 - Math.sqrt(underRoot)) / (gravity * distXZ)
            );

            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            double vxz = velocity * cos;

            double vx = dx / distXZ * vxz;
            double vz = dz / distXZ * vxz;
            double vy = velocity * sin;

            if (pidr != vec3d) pidr = new Vec3d(vx, vy, vz);
        }

        Vec3d requiredVelocity = pidr;

        if (requiredVelocity == null) return;

        Vec3d throwVec = requiredVelocity.subtract(playerVel);

        Vec3d dir = throwVec.normalize();

        float yaw = (float) (Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90F);
        float pitch = (float) -Math.toDegrees(Math.asin(dir.y));

        RotationComponent.update(new Rotation(yaw, pitch), 360, 360, 360, 0, 88);

        SlownessManager.addTimeTask(new SlownessManager.TimeTask(50, () -> {
            InventoryUtil.swapAndUseHvH(Items.ENDER_PEARL);
        }, true));
    }

    private Vec3d simulatePearlLanding(Entity pearl) {
        Vec3d pos = pearl.getPos();
        Vec3d vel = pearl.getVelocity();

        for (int i = 0; i < 300; i++) {
            Vec3d nextPos = pos.add(vel);

            HitResult blockHit = mc.world.raycast(new RaycastContext(
                    pos,
                    nextPos,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    pearl
            ));

            if (blockHit.getType() != HitResult.Type.MISS) {
                return blockHit.getPos();
            }

            pos = nextPos;
            vel = vel.multiply(0.99).add(0, -0.03, 0);
        }
        return null;
    }
}
