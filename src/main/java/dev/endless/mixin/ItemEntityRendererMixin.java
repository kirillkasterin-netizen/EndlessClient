package dev.endless.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.endless.Endless;
import dev.endless.module.list.render.ItemPhysics;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {
    
    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", 
            at = @At(value = "INVOKE", 
                     target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionf;)V",
                     ordinal = 0))
    private void onRender(ItemEntityRenderState renderState, MatrixStack matrixStack, 
                         VertexConsumerProvider vertexConsumerProvider, int light, CallbackInfo ci) {
        ItemPhysics itemPhysics = Endless.getInstance().getModuleStorage().get(ItemPhysics.class);
        
        if (itemPhysics != null && itemPhysics.isEnabled()) {
            // Применяем физику предметов - поворот по оси X
            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
        }
    }
}
