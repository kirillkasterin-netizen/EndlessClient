package dev.endless.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.endless.event.list.ChatEvent;
import dev.endless.event.list.EventEntitySpawn;
import dev.endless.util.discord.DiscordRPC;
import dev.endless.util.IMinecraft;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin implements IMinecraft {
    @Inject(
            method = "sendChatMessage(Ljava/lang/String;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sendChatMessage(String string, CallbackInfo ci) {
        var event = new ChatEvent(string);
        event.post();
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "sendChatCommand(Ljava/lang/String;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sendChatCommand(String string, CallbackInfo ci) {
        var event = new ChatEvent(string);
        event.post();
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Shadow
    private ClientWorld world;

    @Inject(
            method = "onEntitySpawn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;playSpawnSound(Lnet/minecraft/entity/Entity;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void hookEntitySpawn(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        var entity = this.world.getEntityById(packet.getEntityId());

        if (entity == null) return;

        var event = new EventEntitySpawn(entity);
        event.post();
    }

    @Inject(method = "onGameJoin", at = @At("RETURN"))
    private void onGameJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
        if (mc.getCurrentServerEntry() != null) {
            String serverName = mc.getCurrentServerEntry().address;
            DiscordRPC.updateInGame(serverName);
        } else {
            DiscordRPC.updateInGame("Одиночная игра");
        }
    }
}
