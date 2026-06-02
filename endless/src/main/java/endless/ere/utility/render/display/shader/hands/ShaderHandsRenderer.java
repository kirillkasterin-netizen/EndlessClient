package endless.ere.utility.render.display.shader.hands;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import endless.ere.client.modules.impl.render.ShaderHands;
import endless.ere.utility.interfaces.IMinecraft;
import endless.ere.utility.render.display.shader.DrawUtil;
import endless.ere.utility.render.display.shader.GlProgram;

import java.util.ArrayList;
import java.util.List;

/**
 * Шейдерные эффекты на руки/предметы игрока. Перенесено из WraithClient.
 *
 * Пайплайн в три фазы:
 *   1. Снимок фреймбуфера ДО рендера рук (цвет + глубина).
 *   2. Снимок ПОСЛЕ рендера рук.
 *   3. Разностная маска по глубине → визуальные эффекты (свечение/заливка/волны).
 */
public final class ShaderHandsRenderer implements IMinecraft {

    private static final float EPSILON = 0.001f;

    private static ShaderHandsRenderer instance;

    private Framebuffer beforeBuffer;
    private Framebuffer afterBuffer;
    private Framebuffer maskBuffer;

    private final KawaseBloom bloomProcessor = new KawaseBloom();

    private int width = -1;
    private int height = -1;

    private boolean hasBeforeCapture;
    private boolean pendingComposite;
    private int configuredBeforeDepthTex = -1;
    private int configuredAfterDepthTex = -1;

    public static ShaderHandsRenderer getInstance() {
        if (instance == null) instance = new ShaderHandsRenderer();
        return instance;
    }

    // ── Захват кадров ───────────────────────────────────────────────────────

    public void captureBeforeHands() {
        ShaderHands module = ShaderHands.INSTANCE;
        if (!isEffectEnabled(module)) {
            invalidateState();
            return;
        }
        ensureBuffers();
        if (beforeBuffer == null) return;
        copyMainFramebuffer(beforeBuffer, width, height);
        hasBeforeCapture = true;
    }

    public void captureAfterHands() {
        ShaderHands module = ShaderHands.INSTANCE;
        if (!isEffectEnabled(module)) {
            invalidateState();
            return;
        }
        ensureBuffers();
        if (beforeBuffer == null || afterBuffer == null || maskBuffer == null) return;
        if (!hasBeforeCapture) return;
        copyMainFramebuffer(afterBuffer, width, height);
        pendingComposite = true;
    }

    public void renderOverlayIfPending() {
        if (!pendingComposite) return;
        ensureBuffers();
        if (beforeBuffer == null || afterBuffer == null || maskBuffer == null) return;

        ShaderHands module = ShaderHands.INSTANCE;
        if (!isEffectEnabled(module)) {
            invalidateState();
            return;
        }

        if (!computeMaskFromDepthDifference()) {
            invalidateState();
            return;
        }

        float glowValue = module.getGlowValue();
        float fillValue = module.getFillValue();
        float alphaValue = module.getAlphaValue();
        float outlineValue = module.getOutlineValue();

        boolean hasGlow = glowValue > EPSILON;
        boolean hasFill = fillValue > EPSILON && alphaValue > EPSILON;
        int color1 = module.resolveColor();
        int color2 = color1;

        if (module.isPrettyMode()) {
            renderPrettyMode(module, color1, color2, glowValue, fillValue, alphaValue, outlineValue);
            invalidateState();
            return;
        }

        renderGlowMode(color1, color2, glowValue, fillValue, alphaValue, outlineValue, hasGlow, hasFill);
        invalidateState();
    }

    public void invalidateState() {
        hasBeforeCapture = false;
        pendingComposite = false;
        configuredBeforeDepthTex = -1;
        configuredAfterDepthTex = -1;
    }

    // ── Маска по разнице глубины ────────────────────────────────────────────

    private boolean computeMaskFromDepthDifference() {
        GlProgram maskShader = DrawUtil.handsMaskDiffProgram;
        if (maskShader == null) return false;
        maskShader.use();

        clearTransparent(maskBuffer);
        maskBuffer.beginWrite(false);

        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderTexture(0, beforeBuffer.getColorAttachment());
        RenderSystem.setShaderTexture(1, afterBuffer.getColorAttachment());

        int beforeDepth = beforeBuffer.getDepthAttachment();
        int afterDepth = afterBuffer.getDepthAttachment();
        if (beforeDepth != 0 && beforeDepth != configuredBeforeDepthTex) {
            configureDepthTexture(beforeDepth);
            configuredBeforeDepthTex = beforeDepth;
        }
        if (afterDepth != 0 && afterDepth != configuredAfterDepthTex) {
            configureDepthTexture(afterDepth);
            configuredAfterDepthTex = afterDepth;
        }
        RenderSystem.setShaderTexture(2, beforeDepth);
        RenderSystem.setShaderTexture(3, afterDepth);

        drawFullscreenQuad();
        RenderSystem.enableDepthTest();
        return true;
    }

    // ── Режим "Свечение" ────────────────────────────────────────────────────

    private void renderGlowMode(int color1, int color2, float glowValue, float fillValue,
                                float alphaValue, float outlineValue, boolean hasGlow, boolean hasFill) {
        int blurredMaskTexture = 0;
        if (hasGlow) {
            int iterations = computeBloomIterations(outlineValue);
            bloomProcessor.ensureBuffers(width, height, iterations);
            blurredMaskTexture = bloomProcessor.process(maskBuffer.getColorAttachment(), iterations);
        }

        mc.getFramebuffer().beginWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.colorMask(true, true, true, false);
        RenderSystem.disableDepthTest();

        if (hasGlow) {
            renderGlowLayer(blurredMaskTexture, color1, color2, glowValue);
        }
        if (hasFill) {
            renderFillLayer(color1, fillValue, alphaValue);
        }

        restoreCompositeState();
    }

    private int computeBloomIterations(float outline) {
        return Math.max(3, Math.min(8, 4 + Math.round(outline * 0.7f)));
    }

    private void renderGlowLayer(int blurredMaskTexture, int color1, int color2, float glowValue) {
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE);
        GlProgram glowShader = DrawUtil.handsGlowProgram;
        if (glowShader == null) return;
        glowShader.use();

        RenderSystem.setShaderTexture(0, blurredMaskTexture);
        RenderSystem.setShaderTexture(1, maskBuffer.getColorAttachment());

        setColor(glowShader, "color", color1);
        setColor(glowShader, "color2", color2);
        setFloat(glowShader, "exposure", 1.0f + glowValue * 1.8f);

        drawFullscreenQuad();
    }

    private void renderFillLayer(int color, float fillValue, float alphaValue) {
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE);
        GlProgram overlayShader = DrawUtil.handsOverlayProgram;
        if (overlayShader == null) return;
        overlayShader.use();

        RenderSystem.setShaderTexture(0, maskBuffer.getColorAttachment());

        setColor(overlayShader, "color", color);
        setFloat(overlayShader, "fill", fillValue);
        setFloat(overlayShader, "alpha", alphaValue);

        drawFullscreenQuad();
    }

    // ── Режим "Красивый" (волны) ────────────────────────────────────────────

    private void renderPrettyMode(ShaderHands module, int color1, int color2,
                                  float glowValue, float fillValue, float alphaValue, float outlineValue) {
        mc.getFramebuffer().beginWrite(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        GlProgram shader = DrawUtil.handsBlockOverlayProgram;
        if (shader == null) return;
        shader.use();

        RenderSystem.setShaderTexture(0, maskBuffer.getColorAttachment());

        int frameWidth = Math.max(1, mc.getWindow().getFramebufferWidth());
        int frameHeight = Math.max(1, mc.getWindow().getFramebufferHeight());

        setVec2(shader, "texelSize", 1.0f / frameWidth, 1.0f / frameHeight);
        setColor(shader, "color", color1);
        setColor(shader, "color2", color2);
        setFloat(shader, "time", (System.currentTimeMillis() % 100000L) / 1000.0f);
        setFloat(shader, "speed", module.getWaveSpeedValue());
        setFloat(shader, "scale", module.getWaveScaleValue());
        setFloat(shader, "outline", outlineValue);
        setFloat(shader, "glow", glowValue);
        setFloat(shader, "fill", fillValue);
        setFloat(shader, "alpha", alphaValue);
        setFloat(shader, "outlineOnly", 0.0f);

        drawFullscreenQuad();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        restoreCompositeState();
    }

    // ── Состояние ───────────────────────────────────────────────────────────

    private void restoreCompositeState() {
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.setShaderTexture(1, 0);
        RenderSystem.setShaderTexture(2, 0);
        RenderSystem.setShaderTexture(3, 0);
        mc.getFramebuffer().beginWrite(true);
    }

    private boolean isEffectEnabled(ShaderHands module) {
        if (module == null || !module.isEnabled()) return false;
        boolean hasGlow = module.getGlowValue() > EPSILON;
        boolean hasFill = module.getFillValue() > EPSILON && module.getAlphaValue() > EPSILON;
        return hasGlow || hasFill;
    }

    private void ensureBuffers() {
        int w = mc.getWindow().getFramebufferWidth();
        int h = mc.getWindow().getFramebufferHeight();
        if (w == width && h == height && beforeBuffer != null && afterBuffer != null && maskBuffer != null) return;

        safeDelete(beforeBuffer);
        safeDelete(afterBuffer);
        safeDelete(maskBuffer);
        bloomProcessor.dispose();

        beforeBuffer = new SimpleFramebuffer(w, h, true);
        afterBuffer = new SimpleFramebuffer(w, h, true);
        maskBuffer = new SimpleFramebuffer(w, h, true);
        width = w;
        height = h;
        configuredBeforeDepthTex = -1;
        configuredAfterDepthTex = -1;
    }

    // ── Утилиты фреймбуфера ─────────────────────────────────────────────────

    private static void copyMainFramebuffer(Framebuffer target, int width, int height) {
        MinecraftClient client = MinecraftClient.getInstance();
        int readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, client.getFramebuffer().fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.fbo);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
        client.getFramebuffer().beginWrite(true);
    }

    private static void configureDepthTexture(int depthTex) {
        RenderSystem.bindTexture(depthTex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        RenderSystem.bindTexture(0);
    }

    private static void setLinearFiltering(Framebuffer fb) {
        RenderSystem.bindTexture(fb.getColorAttachment());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        RenderSystem.bindTexture(0);
    }

    private static void clearTransparent(Framebuffer fb) {
        fb.setClearColor(0f, 0f, 0f, 0f);
        fb.clear();
    }

    private static void safeDelete(Framebuffer fb) {
        if (fb != null) fb.delete();
    }

    private static void drawFullscreenQuad() {
        MinecraftClient client = MinecraftClient.getInstance();
        float sw = Math.max(client.getWindow().getScaledWidth(), 1);
        float sh = Math.max(client.getWindow().getScaledHeight(), 1);

        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(0, 0, 0).texture(0, 1).color(1f, 1f, 1f, 1f);
        buffer.vertex(0, sh, 0).texture(0, 0).color(1f, 1f, 1f, 1f);
        buffer.vertex(sw, sh, 0).texture(1, 0).color(1f, 1f, 1f, 1f);
        buffer.vertex(sw, 0, 0).texture(1, 1).color(1f, 1f, 1f, 1f);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    // ── Униформы ────────────────────────────────────────────────────────────

    private static void setFloat(GlProgram program, String name, float value) {
        GlUniform u = program.findUniform(name);
        if (u != null) u.set(value);
    }

    private static void setVec2(GlProgram program, String name, float x, float y) {
        GlUniform u = program.findUniform(name);
        if (u != null) u.set(x, y);
    }

    private static void setVec3(GlProgram program, String name, float x, float y, float z) {
        GlUniform u = program.findUniform(name);
        if (u != null) u.set(x, y, z);
    }

    private static void setColor(GlProgram program, String name, int color) {
        setVec3(program, name,
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f);
    }

    // ── Многопроходный Kawase-блюр для свечения ─────────────────────────────

    private static final class KawaseBloom {
        private final List<Framebuffer> buffers = new ArrayList<>();
        private int sourceWidth = -1;
        private int sourceHeight = -1;

        void ensureBuffers(int width, int height, int iterations) {
            if (sourceWidth != width || sourceHeight != height) {
                disposeBuffers();
                sourceWidth = width;
                sourceHeight = height;
            }
            while (buffers.size() > iterations) {
                int last = buffers.size() - 1;
                buffers.get(last).delete();
                buffers.remove(last);
            }
            for (int i = 0; i < iterations; i++) {
                int w = Math.max(2, width >> (i + 1));
                int h = Math.max(2, height >> (i + 1));
                if (i >= buffers.size()) {
                    Framebuffer fb = new SimpleFramebuffer(w, h, false);
                    setLinearFiltering(fb);
                    buffers.add(fb);
                    continue;
                }
                Framebuffer fb = buffers.get(i);
                if (fb.textureWidth != w || fb.textureHeight != h) {
                    fb.delete();
                    fb = new SimpleFramebuffer(w, h, false);
                    setLinearFiltering(fb);
                    buffers.set(i, fb);
                }
            }
        }

        int process(int sourceTexture, int iterations) {
            if (buffers.isEmpty()) return sourceTexture;
            int currentTexture = sourceTexture;

            GlProgram down = DrawUtil.handsKawaseDownProgram;
            GlProgram up = DrawUtil.handsKawaseUpProgram;
            if (down == null || up == null) return sourceTexture;

            for (int i = 0; i < iterations; i++) {
                Framebuffer dst = buffers.get(i);
                clearTransparent(dst);
                dst.beginWrite(true);
                down.use();
                RenderSystem.setShaderTexture(0, currentTexture);
                setKawaseUniforms(down, dst.textureWidth, dst.textureHeight, 1.0f + i);
                drawFullscreenQuad();
                currentTexture = dst.getColorAttachment();
            }

            for (int i = iterations - 1; i >= 1; i--) {
                Framebuffer dst = buffers.get(i - 1);
                clearTransparent(dst);
                dst.beginWrite(true);
                up.use();
                RenderSystem.setShaderTexture(0, currentTexture);
                setKawaseUniforms(up, dst.textureWidth, dst.textureHeight, 1.0f + i);
                setVec3(up, "color", 1.0f, 1.0f, 1.0f);
                drawFullscreenQuad();
                currentTexture = dst.getColorAttachment();
            }

            MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
            return currentTexture;
        }

        void dispose() {
            disposeBuffers();
            sourceWidth = -1;
            sourceHeight = -1;
        }

        private void disposeBuffers() {
            for (Framebuffer fb : buffers) safeDelete(fb);
            buffers.clear();
        }

        private static void setKawaseUniforms(GlProgram program, int texWidth, int texHeight, float offset) {
            int safeWidth = Math.max(1, texWidth);
            int safeHeight = Math.max(1, texHeight);
            setVec2(program, "uSize", safeWidth, safeHeight);
            setVec2(program, "uOffset", offset, offset);
            setVec2(program, "uHalfPixel", 0.5f / safeWidth, 0.5f / safeHeight);
        }
    }
}
