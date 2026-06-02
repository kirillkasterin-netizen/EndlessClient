package endless.ere.utility.mixin.minecraft.entity;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import endless.ere.Endless;
import endless.ere.utility.interfaces.IMinecraft;

@Mixin(LivingEntity.class)
public class LivingEntityMixin implements IMinecraft {

    //ЛИКВИДБАБУНС ЧТО ДЕЛАЕТ????
    @Redirect(method = "jump", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"))
    public float replaceMovePacketPitch(LivingEntity instance) {
        // Включаем только для Aura когда он активен
        if ((Object) this == mc.player && Endless.getInstance().getRotationManager().hasActiveRequest()) {
            return Endless.getInstance().getRotationManager().getCurrentRotation().getYaw();
        }
        return instance.getYaw();
    }


}
