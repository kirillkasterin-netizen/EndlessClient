package endless.ere.utility.mixin.client;

import com.darkmagician6.eventapi.EventManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import endless.ere.Endless;


import endless.ere.base.events.impl.other.EventCloseScreen;

import endless.ere.base.events.impl.player.EventMove;
import endless.ere.base.events.impl.player.EventSlowWalking;
import endless.ere.base.events.impl.player.EventSprintUpdate;
import endless.ere.base.events.impl.player.EventUpdate;
import endless.ere.utility.game.other.MessageUtil;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin extends AbstractClientPlayerEntity {

    @Shadow private float lastYaw;

    public ClientPlayerEntityMixin(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }


    @Shadow protected abstract void sendSprintingPacket();

    @Shadow @Final protected MinecraftClient client;

    @Shadow protected abstract void autoJump(float dx, float dz);

    @Inject(method = "tick", at = @At("HEAD"))
    public void tick(CallbackInfo ci) {
        EventManager.call(new EventUpdate());

    }
    @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target ="Lnet/minecraft/client/network/ClientPlayerEntity;sendSprintingPacket()V"))
    public void invokeSprintUpdate(ClientPlayerEntity instance) {
        EventSprintUpdate eventSprintUpdate = new EventSprintUpdate();
        EventManager.call(eventSprintUpdate);
        if (!eventSprintUpdate.isCancelled()) {
            this.sendSprintingPacket();
        }
    }

    @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target ="Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"))
    public float replaceMovePacketYaw(ClientPlayerEntity instance) {
        // Включаем кастомную ротацию ТОЛЬКО для Aura когда он активен
        if (Endless.getInstance().getRotationManager().hasActiveRequest()) {
            float lastYaw = instance.lastYaw;
            float targetYaw = Endless.getInstance().getRotationManager().getCurrentRotation().getYaw();
            float deltaYaw = net.minecraft.util.math.MathHelper.wrapDegrees(targetYaw - lastYaw);
            if (deltaYaw > 80f) deltaYaw = 80f;
            if (deltaYaw < -80f) deltaYaw = -80f;
            return lastYaw + deltaYaw;
        }
        return instance.getYaw();
    }

    @Redirect(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"), require = 0)
    private boolean onIsUsingItemRedirect(ClientPlayerEntity player) {
        if(player.isUsingItem()) {
            EventSlowWalking slowDownEvent = new EventSlowWalking();
            EventManager.call(slowDownEvent);
            return player.isUsingItem() && player.getVehicle() == null && !slowDownEvent.isCancelled();
        }else {
            return player.isUsingItem() && player.getVehicle() == null;
        }
    }

    @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F"))
    public float replaceMovePacketPitch(ClientPlayerEntity instance) {
        if (Endless.getInstance().getRotationManager().hasActiveRequest()) {
            return net.minecraft.util.math.MathHelper.clamp(
                    Endless.getInstance().getRotationManager().getCurrentRotation().getPitch(), -90f, 90f);
        }
        return instance.getPitch();
    }
    @Inject(method = "closeHandledScreen", at = @At(value = "HEAD"), cancellable = true)
    private void closeHandledScreenHook(CallbackInfo info) {
        EventCloseScreen event = new EventCloseScreen(client.currentScreen);
        EventManager.call(event);
        if (event.isCancelled()) info.cancel();
    }
    @Inject(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V"), cancellable = true)
    public void onMoveHook(MovementType movementType, Vec3d movement, CallbackInfo ci) {
        EventMove event = new EventMove(movement);
        EventManager.call(event);
        double d = this.getX();
        double e = this.getZ();
        super.move(movementType, event.getMovePos());
        this.autoJump((float) (this.getX() - d), (float) (this.getZ() - e));
        ci.cancel();
    }

}