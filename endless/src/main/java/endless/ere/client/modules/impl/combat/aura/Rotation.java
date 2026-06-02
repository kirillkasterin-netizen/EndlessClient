package endless.ere.client.modules.impl.combat.aura;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import endless.ere.client.modules.impl.combat.aura.util.RotationUtil;

/** Локальный Rotation - перенос из Wraith. */
@Getter
@Setter
public class Rotation {
    private float yaw;
    private float pitch;

    public Rotation() {}

    public Rotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Rotation(Entity entity) {
        this.yaw = entity.getYaw();
        this.pitch = entity.getPitch();
    }

    public Rotation(Vec2f vec) {
        this.yaw = vec.y;
        this.pitch = vec.x;
    }

    public Rotation(Vec3d vec) {
        Vec2f a = RotationUtil.calculate(vec);
        this.yaw = a.y;
        this.pitch = a.x;
    }

    public float getDelta(Rotation target) {
        float yawDelta = MathHelper.wrapDegrees(target.getYaw() - this.yaw);
        float pitchDelta = target.getPitch() - this.pitch;
        return (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));
    }

    public final Vec3d toVector() {
        float f = pitch * 0.017453292F;
        float g = -yaw * 0.017453292F;
        float h = MathHelper.cos(g);
        float i = MathHelper.sin(g);
        float j = MathHelper.cos(f);
        float k = MathHelper.sin(f);
        return new Vec3d(i * j, -k, h * j);
    }

    public static float cameraYaw() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return MathHelper.wrapDegrees(mc.gameRenderer.getCamera().getYaw());
    }

    public static float cameraPitch() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.gameRenderer.getCamera().getPitch();
    }
}
