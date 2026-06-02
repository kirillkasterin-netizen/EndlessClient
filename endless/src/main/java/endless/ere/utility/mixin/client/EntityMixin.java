package endless.ere.utility.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import endless.ere.Endless;
import endless.ere.utility.interfaces.IMinecraft;

@Mixin(Entity.class)
public class EntityMixin implements IMinecraft {

    @ModifyExpressionValue(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isControlledByPlayer()Z"))
    public boolean fixFalldistanceValue(boolean original) {
        if ((Object) this == mc.player) {
            return false;
        }

        return original;
    }

    @Redirect(method = "updateVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw()F"))
    public float movementCorrection(Entity instance) {
        // Включаем только для Aura когда он активен
        if (instance instanceof ClientPlayerEntity && Endless.getInstance().getRotationManager().hasActiveRequest()) {
            return Endless.getInstance().getRotationManager().getCurrentRotation().getYaw();
        }
        return instance.getYaw();
    }
    @ModifyVariable(
            method = "getRotationVector(FF)Lnet/minecraft/util/math/Vec3d;",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private float modifyPitch(float pitch) {
        // Включаем только для Aura когда он активен
        if ((Object) this instanceof ClientPlayerEntity && Endless.getInstance().getRotationManager().hasActiveRequest()) {
            return Endless.getInstance().getRotationManager().getInterpolatedRotation().getPitch();
        }
        return pitch;
    }

    @ModifyVariable(
            method = "getRotationVector(FF)Lnet/minecraft/util/math/Vec3d;",
            at = @At("HEAD"),
            ordinal = 1,
            argsOnly = true
    )
    private float modifyYaw(float yaw) {
        // Включаем только для Aura когда он активен
        if ((Object) this instanceof ClientPlayerEntity && Endless.getInstance().getRotationManager().hasActiveRequest()) {
            return Endless.getInstance().getRotationManager().getInterpolatedRotation().getYaw();
        }
        return yaw;
    }


}
