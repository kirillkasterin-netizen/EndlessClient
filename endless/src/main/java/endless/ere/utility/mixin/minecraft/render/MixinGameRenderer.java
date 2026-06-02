package endless.ere.utility.mixin.minecraft.render;

import com.darkmagician6.eventapi.EventManager;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.profiler.Profiler;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import endless.ere.base.events.impl.render.*;
import endless.ere.client.modules.impl.render.Interface;
import endless.ere.client.modules.impl.render.ShaderHands;
import endless.ere.utility.render.display.base.CustomDrawContext;
import endless.ere.utility.render.display.base.UIContext;
import endless.ere.utility.render.display.shader.hands.ShaderHandsRenderer;
import endless.ere.utility.render.level.Render3DUtil;

import static endless.ere.utility.interfaces.IMinecraft.mc;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Shadow private float zoom;

    @Shadow private float zoomX;

    @Shadow private float zoomY;

    @Shadow public abstract float getFarPlaneDistance();

    @Inject(method = "getBasicProjectionMatrix", at = @At("TAIL"), cancellable = true)
    public void getBasicProjectionMatrixHook(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
        EventAspectRatio eventAspectRatio = new EventAspectRatio();
        EventManager.call(eventAspectRatio);
        if (eventAspectRatio.isCancelled()) {
            Matrix4f matrix4f = new Matrix4f();
            if (zoom != 1.0f) {
                matrix4f.translate(zoomX, -zoomY, 0.0f);
                matrix4f.scale(zoom, zoom, 1.0f);
            }
            matrix4f.perspective(fovDegrees * 0.01745329238474369F, eventAspectRatio.getRatio(), 0.05f, getFarPlaneDistance());
            cir.setReturnValue(matrix4f);
        }
    }

    @ModifyExpressionValue(method = "getFov", at = @At(value = "INVOKE", target = "Ljava/lang/Integer;intValue()I", remap = false))
    private int hookGetFov(int original) {
        EventFov event = new EventFov();
        EventManager.call(event);
        if (event.isCancelled()) return event.getFov();
        return original;
    }

    @Inject(method = "renderWorld", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/GameRenderer;renderHand:Z", opcode = Opcodes.GETFIELD, ordinal = 0))
    public void hookWorldRender(RenderTickCounter tickCounter, CallbackInfo ci, @Local(ordinal = 2) Matrix4f matrix4f) {
        MatrixStack matrixStack = new MatrixStack();
        matrixStack.multiplyPositionMatrix(matrix4f);

        Render3DUtil.setLastProjMat(RenderSystem.getProjectionMatrix());
        Render3DUtil.setLastModMat(RenderSystem.getModelViewMatrix());
        Render3DUtil.setLastWorldSpaceMatrix(matrix4f);

        // Один раз за кадр продвигаем сглаживание ротации
        endless.ere.Endless.getInstance().getRotationManager().tickRenderRotation();

        EventRender3D event = new EventRender3D(matrixStack, tickCounter.getTickDelta(false));
        EventManager.call(event);
        Render3DUtil.onEventRender3D(event.getMatrix());
    }

    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderHand(Lnet/minecraft/client/render/Camera;FLorg/joml/Matrix4f;)V", shift = At.Shift.BEFORE))
    private void endless$captureBeforeHands(CallbackInfo ci) {
        ShaderHandsRenderer.getInstance().captureBeforeHands();
    }

    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderHand(Lnet/minecraft/client/render/Camera;FLorg/joml/Matrix4f;)V", shift = At.Shift.AFTER))
    private void endless$captureAfterHands(CallbackInfo ci) {
        ShaderHandsRenderer.getInstance().captureAfterHands();
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/MinecraftClient;world:Lnet/minecraft/client/world/ClientWorld;",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 2
            ),
            locals = LocalCapture.CAPTURE_FAILHARD //Пиздец
    )
    private void renderScreenHook(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci,
                                  Profiler profiler, boolean bl, int i, int j,
                                  Window window, Matrix4f matrix4f,
                                  Matrix4fStack matrix4fStack, DrawContext drawContext) {
        EventManager.call(new EventRenderScreen(UIContext.of(drawContext,i,j,mc.getRenderTickCounter().getTickDelta(false))));

    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;draw()V", opcode = Opcodes.GETFIELD, shift = At.Shift.AFTER, ordinal = 0), method = "render")
    void renderHudHook(RenderTickCounter tickCounter, boolean tick, CallbackInfo callbackInfo) {
        // RenderSystem.clear(256);
        triggerHudRenderEvent(tickCounter);
    }
    @Unique
    private void triggerHudRenderEvent(RenderTickCounter tickCounter) {
        CustomDrawContext customDrawContext = new CustomDrawContext(mc.getBufferBuilders().getEntityVertexConsumers());
        double saveScale = MinecraftClient.getInstance().getWindow().getScaleFactor();
       setScaleFactorOutAllMods(Interface.INSTANCE.getCustomScale());


        RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0, mc.getWindow().getScaledWidth(),
                        mc.getWindow().getScaledHeight(), 0,
                        1000, 21000),
                ProjectionType.ORTHOGRAPHIC);

        RenderSystem.disableDepthTest();
        try{
            EventManager.call(new EventHudRender(customDrawContext, tickCounter.getTickDelta(false)));
        }catch(Exception e){
            e.printStackTrace();
        }
        customDrawContext.draw();
        RenderSystem.enableDepthTest();
        setScaleFactorOutAllMods(saveScale);
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0, mc.getWindow().getScaledWidth(),
                        mc.getWindow().getScaledHeight(), 0,
                        1000, 21000),
                ProjectionType.ORTHOGRAPHIC);

    }
    @Unique
    public void setScaleFactorOutAllMods(double scaleFactor) {
        mc.getWindow().scaleFactor = scaleFactor;
        int i = (int)(mc.getWindow().framebufferWidth / scaleFactor);
        mc.getWindow().scaledWidth = mc.getWindow().framebufferWidth / scaleFactor > i ? i + 1 : i;
        int j = (int)(mc.getWindow().framebufferHeight / scaleFactor);
        mc.getWindow().scaledHeight = mc.getWindow().framebufferHeight / scaleFactor > j ? j + 1 : j;
    }
}
