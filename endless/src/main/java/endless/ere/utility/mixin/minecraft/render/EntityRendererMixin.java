package endless.ere.utility.mixin.minecraft.render;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import endless.ere.client.modules.impl.misc.WardenHelper;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(method = "shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z",
            at = @At("HEAD"), cancellable = true)
    private void endless$skipHidden(Entity entity, Frustum frustum,
                                   double cameraX, double cameraY, double cameraZ,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (WardenHelper.shouldHide(entity)) {
            cir.setReturnValue(false);
        }
    }
}
