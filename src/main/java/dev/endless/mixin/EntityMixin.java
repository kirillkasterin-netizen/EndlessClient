package dev.endless.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.endless.module.list.render.SeeInvisible;
import dev.endless.util.base.Instance;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @ModifyExpressionValue(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isControlledByPlayer()Z"))
    private boolean fixFallDistanceCalculation(boolean original) {
        if ((Object) this == MinecraftClient.getInstance().player) {
            return false;
        }

        return original;
    }

    /**
     * SeeInvisible: для LivingEntity с эффектом Invisibility возвращаем false,
     * чтобы рендерер не пропустил их рисование. Альфа применяется отдельно
     * в LivingEntityRendererMixin.
     */
    @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
    private void onIsInvisibleTo(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        SeeInvisible see = Instance.get(SeeInvisible.class);
        if (see == null || !see.isEnabled()) return;

        Object self = this;
        if (!(self instanceof LivingEntity living)) return;
        if (!living.hasStatusEffect(StatusEffects.INVISIBILITY)) return;

        // Сущность с эффектом invisibility теперь "видима" для нас.
        cir.setReturnValue(false);
    }

    /**
     * SeeInvisible: альтернатива через Entity.isInvisible — некоторые рендереры
     * (например ArmorFeatureRenderer) проверяют именно его, а не isInvisibleTo.
     */
    @Inject(method = "isInvisible", at = @At("HEAD"), cancellable = true)
    private void onIsInvisible(CallbackInfoReturnable<Boolean> cir) {
        SeeInvisible see = Instance.get(SeeInvisible.class);
        if (see == null || !see.isEnabled()) return;

        Object self = this;
        if (!(self instanceof LivingEntity living)) return;
        if (!living.hasStatusEffect(StatusEffects.INVISIBILITY)) return;

        cir.setReturnValue(false);
    }
}
