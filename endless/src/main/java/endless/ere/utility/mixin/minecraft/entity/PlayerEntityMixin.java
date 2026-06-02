package endless.ere.utility.mixin.minecraft.entity;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void tickMovement(CallbackInfo ci) {

    }

    // fixElytra mixin удалён - он подменял yaw/pitch на RotationManager.currentRotation
    // в PlayerEntity.travel, что ломало синхронизацию пакетов движения когда модуль
    // пишет напрямую через setYaw/setPitch (как Aura HwTest порт из Wraith).
    // Это вызывало Simulation/BadPacketsJ на элитрах.
}
