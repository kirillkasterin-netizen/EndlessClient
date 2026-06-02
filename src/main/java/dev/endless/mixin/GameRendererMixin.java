package dev.endless.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.endless.event.list.EventWorldRender;
import dev.endless.util.render.hands.ShaderHandsRenderer;
import dev.endless.util.render.renderers.DrawUtil;
import dev.endless.util.render.sky.SkyShaderRenderer;
import dev.endless.util.render.sonar.SonarRenderer;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "renderWorld", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/GameRenderer;renderHand:Z", opcode = Opcodes.GETFIELD, ordinal = 0))
    public void hookWorldRender(RenderTickCounter tickCounter, CallbackInfo ci,
                                @Local(ordinal = 0) Matrix4f projectionMatrix,
                                @Local(ordinal = 2) Matrix4f matrix4f) {
        var matrixStack = new MatrixStack();
        matrixStack.multiplyPositionMatrix(matrix4f);

        var event = new EventWorldRender(matrixStack, tickCounter.getTickDelta(false));
        event.post();
        DrawUtil.onRender3D(event.getMatrixStack());

        // Sky shader: at this point sky/clouds/world have all been rendered
        // and the depth buffer is finalised. Pure-sky pixels still hold
        // depth = 1.0 (far plane) so a GL_LEQUAL pass at NDC z=1.0 selects
        // exactly those pixels.
        SkyShaderRenderer.getInstance().renderIfEnabled();

        // Sonar pulse — rendered after sky shader so the ring overlays it,
        // and after world geometry so depth reconstruction is valid.
        SonarRenderer.getInstance().render(matrix4f, projectionMatrix);
    }

    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderHand(Lnet/minecraft/client/render/Camera;FLorg/joml/Matrix4f;)V", shift = At.Shift.BEFORE))
    private void captureBeforeHands(CallbackInfo ci) {
        ShaderHandsRenderer.getInstance().captureBeforeHands();
    }

    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderHand(Lnet/minecraft/client/render/Camera;FLorg/joml/Matrix4f;)V", shift = At.Shift.AFTER))
    private void captureAfterHands(CallbackInfo ci) {
        ShaderHandsRenderer.getInstance().captureAfterHands();
    }

}
