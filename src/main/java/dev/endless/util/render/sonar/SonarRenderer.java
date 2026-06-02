package dev.endless.util.render.sonar;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import dev.endless.Endless;
import dev.endless.module.list.render.Sonar;
import dev.endless.util.IMinecraft;
import dev.endless.util.render.shader.ColorUtils;
import dev.endless.util.render.shader.ShaderRegistry;

/**
 * Sonar scan-ring renderer.
 *
 * Reads the scene depth buffer through a private framebuffer copy, reconstructs
 * world-space positions in the shader, and draws an animated expanding ring
 * around a chosen world center. The pulse is modulated by two easing curves
 * that compress radius growth at the start and stretch it later, giving a
 * "hockey-puck" sonar feel.
 */
public class SonarRenderer implements IMinecraft {

    private static SonarRenderer instance;

    private Framebuffer depthCopyBuffer;
    private int lastFbWidth = -1;
    private int lastFbHeight = -1;

    public static SonarRenderer getInstance() {
        if (instance == null) instance = new SonarRenderer();
        return instance;
    }

    /**
     * Renders the sonar pulse (if active) using world matrices from the
     * current frame.
     *
     * @param positionMatrix   View matrix from the GameRenderer
     * @param projectionMatrix Projection matrix from the GameRenderer
     */
    public void render(Matrix4f positionMatrix, Matrix4f projectionMatrix) {
        Sonar module = getModule();
        if (module == null || !module.isEnabled()) return;
        if (mc.player == null || mc.world == null) return;

        long start = module.getCurrentStart();
        if (start <= 0L) return;

        float durationMs = module.duration.getFloatValue() * 1000f;
        float elapsed = System.currentTimeMillis() - start;
        if (elapsed >= durationMs) {
            module.clearPulse();
            return;
        }

        Framebuffer mainFb = mc.getFramebuffer();
        ensureDepthCopyFramebuffer(mainFb.textureWidth, mainFb.textureHeight);
        if (depthCopyBuffer == null) return;
        depthCopyBuffer.copyDepthFrom(mainFb);

        Matrix4f invView = new Matrix4f(positionMatrix).invert();
        Matrix4f invProj = new Matrix4f(projectionMatrix).invert();

        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        Vec3d center = module.getCenter();
        if (center == null) center = camPos;

        float far = mc.gameRenderer.getFarPlaneDistance();
        float t = MathHelper.clamp(elapsed / durationMs, 0f, 1f);
        float r1 = lerp(1f, far, easeQuintOut(t));
        float r2 = lerp(1f, far, easeQuartInOut(t));
        float baseRadius = MathHelper.lerp(0.85f, r1, r2);

        // Triangular fade: rises to 1 at t=0.5, falls back to 0 at t=1.
        float alphaPc = 1f - t;
        float alphaWave = (alphaPc > 0.5f ? 1f - alphaPc : alphaPc) * 2f;
        alphaWave = Math.min(alphaWave * 1.75f, 1f);
        float baseAlpha = MathHelper.clamp(module.alpha.getFloatValue() * alphaWave, 0f, 1f);

        int themed = module.resolveColor();
        int c1 = themed;
        int c2 = themed;
        int c3 = themed;
        int c4 = themed;

        float baseWidth = MathHelper.clamp(
                6f + baseRadius * (0.18f * module.widthMul.getFloatValue()),
                4f, Math.max(10f, far * 0.42f)
        );
        float baseSharp = module.sharpness.getFloatValue();

        renderPass(invView, invProj, camPos, center, mainFb,
                baseRadius, baseWidth, baseSharp,
                applyAlpha(c1, baseAlpha),
                applyAlpha(c2, baseAlpha),
                applyAlpha(c3, baseAlpha),
                applyAlpha(c4, baseAlpha));

        RenderSystem.defaultBlendFunc();
    }

    private void renderPass(Matrix4f invView, Matrix4f invProj, Vec3d camPos, Vec3d center,
                             Framebuffer framebuffer,
                             float radius, float width, float sharp,
                             int outerColor, int midColor, int innerColor, int scanlineColor) {
        if (radius <= 0.001f || width <= 0.001f) return;

        ShaderProgram shader = mc.getShaderLoader().getOrCreateProgram(ShaderRegistry.SONAR_SCAN);
        if (shader == null) return;

        // Push uniforms.
        setUniformMat4(shader, "invViewMat", invView);
        setUniformMat4(shader, "invProjMat", invProj);
        setUniformVec3(shader, "pos", (float) camPos.x, (float) camPos.y, (float) camPos.z);
        setUniformVec3(shader, "center", (float) center.x, (float) center.y, (float) center.z);
        setUniformFloat(shader, "radius", radius);
        setUniformFloat(shader, "width", width);
        setUniformFloat(shader, "sharpness", sharp);
        setUniformColor(shader, "outerColor", outerColor);
        setUniformColor(shader, "midColor", midColor);
        setUniformColor(shader, "innerColor", innerColor);
        setUniformColor(shader, "scanlineColor", scanlineColor);
        setUniformInt(shader, "DebugMode", 0);

        // GL state.
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        int depthTex = depthCopyBuffer.getDepthAttachment();
        if (depthTex == 0) depthTex = mc.getFramebuffer().getDepthAttachment();
        configureDepthTexture(depthTex);

        framebuffer.beginWrite(false);
        RenderSystem.setShaderTexture(0, depthTex);
        RenderSystem.setShader(ShaderRegistry.SONAR_SCAN);
        drawFullscreenQuad();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void configureDepthTexture(int depthTex) {
        RenderSystem.bindTexture(depthTex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }

    private void drawFullscreenQuad() {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(-1f, -1f, 0f).texture(0f, 0f);
        buffer.vertex(-1f,  1f, 0f).texture(0f, 1f);
        buffer.vertex( 1f,  1f, 0f).texture(1f, 1f);
        buffer.vertex( 1f, -1f, 0f).texture(1f, 0f);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void ensureDepthCopyFramebuffer(int width, int height) {
        if (depthCopyBuffer == null || lastFbWidth != width || lastFbHeight != height) {
            deleteDepthCopyFramebuffer();
            depthCopyBuffer = new SimpleFramebuffer(width, height, true);
            lastFbWidth = width;
            lastFbHeight = height;
        }
    }

    public void deleteDepthCopyFramebuffer() {
        if (depthCopyBuffer != null) {
            depthCopyBuffer.delete();
            depthCopyBuffer = null;
        }
        lastFbWidth = -1;
        lastFbHeight = -1;
    }

    // ── Easings (inlined to avoid project Easing API differences) ───────────

    private static float easeQuintOut(float t) {
        float u = 1f - t;
        return 1f - u * u * u * u * u;
    }

    private static float easeQuartInOut(float t) {
        if (t < 0.5f) return 8f * t * t * t * t;
        float u = 2f * t - 2f;
        return 1f - 0.5f * u * u * u * u;
    }

    // ── Uniform helpers ─────────────────────────────────────────────────────

    private void setUniformMat4(ShaderProgram shader, String name, Matrix4f value) {
        GlUniform u = shader.getUniform(name);
        if (u != null) u.set(value);
    }

    private void setUniformVec3(ShaderProgram shader, String name, float x, float y, float z) {
        GlUniform u = shader.getUniform(name);
        if (u != null) u.set(x, y, z);
    }

    private void setUniformFloat(ShaderProgram shader, String name, float v) {
        GlUniform u = shader.getUniform(name);
        if (u != null) u.set(v);
    }

    private void setUniformInt(ShaderProgram shader, String name, int v) {
        GlUniform u = shader.getUniform(name);
        if (u != null) u.set(v);
    }

    private void setUniformColor(ShaderProgram shader, String name, int color) {
        GlUniform u = shader.getUniform(name);
        if (u == null) return;
        int a = ColorUtils.alpha(color);
        if (a == 0) a = 255;
        u.set(ColorUtils.redf(color), ColorUtils.greenf(color), ColorUtils.bluef(color), a / 255f);
    }

    private static int applyAlpha(int color, float alphaMul) {
        int a = ColorUtils.alpha(color);
        if (a == 0) a = 255;
        a = (int) (a * MathHelper.clamp(alphaMul, 0f, 1f));
        return ColorUtils.withAlpha(color, a);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private Sonar getModule() {
        try {
            return Endless.getInstance().getModuleStorage().get(Sonar.class);
        } catch (Exception e) {
            return null;
        }
    }
}
