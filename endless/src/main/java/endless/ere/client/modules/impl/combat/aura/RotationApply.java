package endless.ere.client.modules.impl.combat.aura;

import net.minecraft.client.MinecraftClient;
import endless.ere.Endless;
import endless.ere.base.rotation.RotationTarget;
import endless.ere.client.modules.api.Module;
import endless.ere.utility.game.player.rotation.Rotation;

/**
 * Адаптер: применяет рассчитанные углы через RotationManager Endless для silent rotation.
 * В Wraith использовался setYaw/setPitch (видно игроку), у нас тут silent через setRotation.
 */
public final class RotationApply {

    /** Применяет ротацию через silent RotationManager. */
    public static void apply(float yaw, float pitch, Module module) {
        if (MinecraftClient.getInstance().player == null) return;
        Rotation rot = new Rotation(yaw, pitch);
        Endless.getInstance().getRotationManager().setRotation(
                new RotationTarget(rot,
                        () -> Endless.getInstance().getRotationManager().getAimManager()
                                .rotate(Endless.getInstance().getRotationManager().getAimManager().getInstantSetup(), rot),
                        Endless.getInstance().getRotationManager().getAimManager().getAiSetup()),
                3, module);
    }

    /** Текущая серверная ротация (то что реально отправлено серверу). */
    public static Rotation current() {
        return Endless.getInstance().getRotationManager().getCurrentRotation();
    }

    public static float currentYaw() { return current().getYaw(); }
    public static float currentPitch() { return current().getPitch(); }

    private RotationApply() {}
}
