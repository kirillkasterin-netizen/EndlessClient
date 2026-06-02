package dev.endless.util.render.sky;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.lwjgl.opengl.GL11;
import dev.endless.Endless;
import dev.endless.module.list.render.SkyShader;
import dev.endless.util.IMinecraft;
import dev.endless.util.render.shader.ColorUtils;
import dev.endless.util.render.shader.ShaderRegistry;
import dev.endless.util.render.shader.UniformHelper;

/**
 * Animated procedural sky shader renderer.
 *
 * Sky-only masking is achieved with a hardware depth-test trick: we draw a
 * fullscreen quad at NDC z = 1.0 (the far plane) with depth-test enabled and
 * {@code glDepthFunc(GL_LEQUAL)}. The quad therefore passes only on pixels
 * where the depth buffer is still at its cleared "far" value — i.e. pure sky
 * pixels.
 *
 * The shader uses world-space view direction (built from yaw/pitch/fov in the
 * shader itself) so the pattern stays anchored to the world rather than to
 * the screen — turning the camera moves the sky pattern naturally.
 */
public class SkyShaderRenderer implements IMinecraft {

    private static final long INITIAL_TIME = System.currentTimeMillis();
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    private static SkyShaderRenderer instance;

    public static SkyShaderRenderer getInstance() {
        if (instance == null) instance = new SkyShaderRenderer();
        return instance;
    }

    public void renderIfEnabled() {
        SkyShader module = getModule();
        if (module == null || !module.isEnabled()) return;
        if (mc.world == null || mc.gameRenderer == null) return;

        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) return;

        // Capture previous GL state so we can restore it precisely.
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevCull  = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int prevDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL); // pass when z (=1.0) <= depth-buffer
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);

        ShaderProgram shader = RenderSystem.setShader(ShaderRegistry.SKY_SHADER);
        if (shader == null) {
            restoreState(prevBlend, prevDepth, prevCull, prevDepthFunc);
            return;
        }

        pushUniforms(shader, module, camera);
        drawFullscreenNdcQuad();

        restoreState(prevBlend, prevDepth, prevCull, prevDepthFunc);
    }

    private void pushUniforms(ShaderProgram shader, SkyShader module, Camera camera) {
        float time = (System.currentTimeMillis() - INITIAL_TIME) / 1000.0f;
        int width = Math.max(1, mc.getWindow().getFramebufferWidth());
        int height = Math.max(1, mc.getWindow().getFramebufferHeight());
        float aspect = (float) width / (float) height;

        // Camera angles. Minecraft yaw is in degrees, 0 means looking +Z (south),
        // increasing CW when viewed from above. The shader expects standard yaw
        // (atan2 convention) — the small sign tweak below makes the pattern
        // travel in the same direction as the world.
        float tickDelta = mc.getRenderTickCounter().getTickDelta(true);
        float yawDeg = camera.getYaw();
        float pitchDeg = camera.getPitch();
        float yawRad = -yawDeg * DEG_TO_RAD;       // flip to match atan2(x, z)
        float pitchRad = -pitchDeg * DEG_TO_RAD;   // pitch up in MC = negative pitch angle

        // Vertical FOV in radians. mc.gameRenderer.getFov returns degrees.
        double fovDeg = mc.gameRenderer.getFov(camera, tickDelta, true);
        float fovRad = (float) Math.toRadians(fovDeg);

        int color1 = module.resolvePrimaryColor();
        int color2 = module.resolveSecondaryColor();

        UniformHelper.setFloat(shader, "iTime", time);
        UniformHelper.setVec2(shader, "iResolution", width, height);
        UniformHelper.setFloat(shader, "iSpeed", module.speed.getFloatValue());
        UniformHelper.setVec3(shader, "iColor",
                ColorUtils.redf(color1), ColorUtils.greenf(color1), ColorUtils.bluef(color1));
        UniformHelper.setVec3(shader, "iColor2",
                ColorUtils.redf(color2), ColorUtils.greenf(color2), ColorUtils.bluef(color2));
        UniformHelper.setInt(shader, "iMode", module.getModeIndex());
        UniformHelper.setFloat(shader, "iYaw", yawRad);
        UniformHelper.setFloat(shader, "iPitch", pitchRad);
        UniformHelper.setFloat(shader, "iFov", fovRad);
        UniformHelper.setFloat(shader, "iAspect", aspect);
    }

    /**
     * Draws a fullscreen quad in normalized device coordinates. The vertex
     * shader pins {@code gl_Position.z = 1.0}, which combined with
     * {@code GL_LEQUAL} means it only fills cleared (sky) pixels.
     */
    private void drawFullscreenNdcQuad() {
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_TEXTURE_COLOR
        );
        buffer.vertex(-1f, -1f, 0f).texture(0f, 0f).color(1f, 1f, 1f, 1f);
        buffer.vertex(-1f,  1f, 0f).texture(0f, 1f).color(1f, 1f, 1f, 1f);
        buffer.vertex( 1f,  1f, 0f).texture(1f, 1f).color(1f, 1f, 1f, 1f);
        buffer.vertex( 1f, -1f, 0f).texture(1f, 0f).color(1f, 1f, 1f, 1f);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void restoreState(boolean prevBlend, boolean prevDepth, boolean prevCull, int prevDepthFunc) {
        RenderSystem.depthFunc(prevDepthFunc);
        RenderSystem.depthMask(true);
        if (prevCull)  RenderSystem.enableCull();    else RenderSystem.disableCull();
        if (prevDepth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        RenderSystem.defaultBlendFunc();
        if (!prevBlend) RenderSystem.disableBlend();
    }

    private SkyShader getModule() {
        try {
            return Endless.getInstance().getModuleStorage().get(SkyShader.class);
        } catch (Exception e) {
            return null;
        }
    }
}
