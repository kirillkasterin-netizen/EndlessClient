package dev.endless.util.render.hands;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import dev.endless.Endless;
import dev.endless.module.list.render.ShaderHands;
import dev.endless.util.IMinecraft;
import dev.endless.util.render.shader.FramebufferUtils;
import dev.endless.util.render.shader.FullscreenQuadRenderer;
import dev.endless.util.render.shader.KawaseBloomProcessor;
import dev.endless.util.render.shader.ShaderRegistry;
import dev.endless.util.render.shader.UniformHelper;

/**
 * Renders shader-based effects for player hands and held items.
 * 
 * The rendering pipeline works in three phases:
 * 1. Capture the framebuffer state BEFORE hands are rendered (depth + color)
 * 2. Capture the framebuffer state AFTER hands are rendered
 * 3. Compute a difference mask and apply visual effects (glow, fill, wave)
 * 
 * Two visual modes are supported:
 * - "Свечение" (Glow): Classic outline + glow + fill using kawase bloom
 * - "Красивый" (Pretty): Animated wave effect via block_overlay shader
 */
public class ShaderHandsRenderer implements IMinecraft {
    
    private static final float EPSILON = 0.001f;
    
    private static ShaderHandsRenderer instance;

    // Framebuffers for the rendering pipeline
    private Framebuffer beforeBuffer;  // Scene before hands rendered
    private Framebuffer afterBuffer;   // Scene after hands rendered
    private Framebuffer maskBuffer;    // Computed difference mask
    
    // Bloom processor for glow effect
    private final KawaseBloomProcessor bloomProcessor = new KawaseBloomProcessor(
            ShaderRegistry.HANDS_KAWASE_DOWN,
            ShaderRegistry.HANDS_KAWASE_UP
    );
    
    // Buffer dimensions
    private int width = -1;
    private int height = -1;
    
    // Pipeline state
    private boolean hasBeforeCapture;
    private boolean pendingComposite;
    private int configuredBeforeDepthTex = -1;
    private int configuredAfterDepthTex = -1;

    /**
     * Returns the singleton instance of the renderer.
     *
     * @return The shared ShaderHandsRenderer instance
     */
    public static ShaderHandsRenderer getInstance() {
        if (instance == null) instance = new ShaderHandsRenderer();
        return instance;
    }

    /**
     * Captures the main framebuffer before hands are rendered.
     * This should be called via mixin BEFORE GameRenderer.renderHand().
     */
    public void captureBeforeHands() {
        ShaderHands module = getModule();
        if (!isEffectEnabled(module)) {
            invalidateState();
            return;
        }
        ensureBuffers();
        if (beforeBuffer == null) return;
        
        FramebufferUtils.copyMainFramebuffer(beforeBuffer, width, height);
        hasBeforeCapture = true;
    }

    /**
     * Captures the main framebuffer after hands are rendered.
     * This should be called via mixin AFTER GameRenderer.renderHand().
     */
    public void captureAfterHands() {
        ShaderHands module = getModule();
        if (!isEffectEnabled(module)) {
            invalidateState();
            return;
        }
        ensureBuffers();
        if (beforeBuffer == null || afterBuffer == null || maskBuffer == null) return;
        if (!hasBeforeCapture) return;

        FramebufferUtils.copyMainFramebuffer(afterBuffer, width, height);
        pendingComposite = true;
    }

    /**
     * Renders the final overlay effect onto the main framebuffer.
     * Should be called from a 2D render hook (HUD/EventHUD).
     */
    public void renderOverlayIfPending() {
        if (!pendingComposite) return;
        ensureBuffers();
        if (beforeBuffer == null || afterBuffer == null || maskBuffer == null) return;
        
        ShaderHands module = getModule();
        if (!isEffectEnabled(module)) {
            invalidateState();
            return;
        }

        // Step 1: Compute the difference mask
        if (!computeMaskFromDepthDifference()) {
            invalidateState();
            return;
        }

        float glowValue = module.glow.getFloatValue();
        float fillValue = module.fill.getFloatValue();
        float alphaValue = module.alpha.getFloatValue();
        float outlineValue = module.outline.getFloatValue();

        boolean hasGlow = glowValue > EPSILON;
        boolean hasFill = fillValue > EPSILON && alphaValue > EPSILON;
        int color1 = module.resolveColor();
        int color2 = color1;

        // Step 2: Apply the appropriate rendering mode
        if (module.mode.is("Красивый")) {
            renderPrettyMode(module, color1, color2, glowValue, fillValue, alphaValue, outlineValue);
            invalidateState();
            return;
        }

        // Default: "Свечение" (Glow) mode
        renderGlowMode(color1, color2, glowValue, fillValue, alphaValue, outlineValue, hasGlow, hasFill);
        invalidateState();
    }

    /**
     * Resets the pipeline state and clears all flags.
     * Called when the module is disabled or rendering completes.
     */
    public void invalidateState() {
        hasBeforeCapture = false;
        pendingComposite = false;
        configuredBeforeDepthTex = -1;
        configuredAfterDepthTex = -1;
    }

    // ── Mask computation ────────────────────────────────────────────────────

    /**
     * Renders the difference mask shader, which compares the depth buffers
     * before and after hands rendering to identify which pixels were affected.
     *
     * @return true if mask was successfully computed, false otherwise
     */
    private boolean computeMaskFromDepthDifference() {
        ShaderProgram maskShader = RenderSystem.setShader(ShaderRegistry.HANDS_MASK_DIFF);
        if (maskShader == null) return false;
        
        FramebufferUtils.clearTransparent(maskBuffer);
        maskBuffer.beginWrite(false);
        
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderTexture(0, beforeBuffer.getColorAttachment());
        RenderSystem.setShaderTexture(1, afterBuffer.getColorAttachment());
        
        // Configure depth textures for sampling (one-time per texture)
        int beforeDepth = beforeBuffer.getDepthAttachment();
        int afterDepth = afterBuffer.getDepthAttachment();
        if (beforeDepth != 0 && beforeDepth != configuredBeforeDepthTex) {
            FramebufferUtils.configureDepthTexture(beforeDepth);
            configuredBeforeDepthTex = beforeDepth;
        }
        if (afterDepth != 0 && afterDepth != configuredAfterDepthTex) {
            FramebufferUtils.configureDepthTexture(afterDepth);
            configuredAfterDepthTex = afterDepth;
        }
        RenderSystem.setShaderTexture(2, beforeDepth);
        RenderSystem.setShaderTexture(3, afterDepth);
        
        FullscreenQuadRenderer.draw();
        RenderSystem.enableDepthTest();
        return true;
    }

    // ── Glow mode (default) ─────────────────────────────────────────────────

    /**
     * Renders the classic glow mode: kawase bloom outline + solid fill overlay.
     */
    private void renderGlowMode(int color1, int color2, float glowValue, float fillValue, 
                                 float alphaValue, float outlineValue, boolean hasGlow, boolean hasFill) {
        // Compute blurred mask for glow effect
        int blurredMaskTexture = 0;
        if (hasGlow) {
            int iterations = computeBloomIterations(outlineValue);
            bloomProcessor.ensureBuffers(width, height, iterations);
            blurredMaskTexture = bloomProcessor.process(maskBuffer.getColorAttachment(), iterations);
        }

        // Setup state for compositing onto main framebuffer
        mc.getFramebuffer().beginWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.colorMask(true, true, true, false);
        RenderSystem.disableDepthTest();

        // Draw glow layer
        if (hasGlow) {
            renderGlowLayer(blurredMaskTexture, color1, color2, glowValue);
        }

        // Draw fill layer
        if (hasFill) {
            if (!renderFillLayer(color1, fillValue, alphaValue)) {
                restoreCompositeState();
                return;
            }
        }

        restoreCompositeState();
    }

    private int computeBloomIterations(float outline) {
        return Math.max(3, Math.min(8, 4 + Math.round(outline * 0.7f)));
    }

    private void renderGlowLayer(int blurredMaskTexture, int color1, int color2, float glowValue) {
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ZERO,
                GlStateManager.DstFactor.ONE
        );
        ShaderProgram glowShader = RenderSystem.setShader(ShaderRegistry.HANDS_GLOW);
        if (glowShader == null) return;
        
        RenderSystem.setShaderTexture(0, blurredMaskTexture);
        RenderSystem.setShaderTexture(1, maskBuffer.getColorAttachment());
        
        UniformHelper.setColor(glowShader, "color", color1);
        UniformHelper.setColor(glowShader, "color2", color2);
        UniformHelper.setFloat(glowShader, "exposure", 1.0f + glowValue * 1.8f);
        
        FullscreenQuadRenderer.draw();
    }

    private boolean renderFillLayer(int color, float fillValue, float alphaValue) {
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SrcFactor.ZERO,
                GlStateManager.DstFactor.ONE
        );
        ShaderProgram overlayShader = RenderSystem.setShader(ShaderRegistry.HANDS_OVERLAY);
        if (overlayShader == null) return false;
        
        RenderSystem.setShaderTexture(0, maskBuffer.getColorAttachment());
        
        UniformHelper.setColor(overlayShader, "color", color);
        UniformHelper.setFloat(overlayShader, "fill", fillValue);
        UniformHelper.setFloat(overlayShader, "alpha", alphaValue);
        
        FullscreenQuadRenderer.draw();
        return true;
    }

    // ── Pretty mode (animated waves) ────────────────────────────────────────

    /**
     * Renders the "pretty" mode: animated wave effect through block_overlay shader.
     */
    private void renderPrettyMode(ShaderHands module, int color1, int color2, 
                                   float glowValue, float fillValue, float alphaValue, float outlineValue) {
        mc.getFramebuffer().beginWrite(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        ShaderProgram shader = RenderSystem.setShader(ShaderRegistry.BLOCK_OVERLAY);
        if (shader == null) return;
        
        RenderSystem.setShaderTexture(0, maskBuffer.getColorAttachment());

        int frameWidth = Math.max(1, mc.getWindow().getFramebufferWidth());
        int frameHeight = Math.max(1, mc.getWindow().getFramebufferHeight());
        
        UniformHelper.setVec2(shader, "texelSize", 1.0f / frameWidth, 1.0f / frameHeight);
        UniformHelper.setColor(shader, "color", color1);
        UniformHelper.setColor(shader, "color2", color2);
        UniformHelper.setFloat(shader, "time", (System.currentTimeMillis() % 100000L) / 1000.0f);
        UniformHelper.setFloat(shader, "speed", module.waveSpeed.getFloatValue());
        UniformHelper.setFloat(shader, "scale", module.waveScale.getFloatValue());
        UniformHelper.setFloat(shader, "outline", outlineValue);
        UniformHelper.setFloat(shader, "glow", glowValue);
        UniformHelper.setFloat(shader, "fill", fillValue);
        UniformHelper.setFloat(shader, "alpha", alphaValue);
        UniformHelper.setFloat(shader, "outlineOnly", 0.0f);
        
        FullscreenQuadRenderer.draw();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        restoreCompositeState();
    }

    // ── State management ────────────────────────────────────────────────────

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
        boolean hasGlow = module.glow.getFloatValue() > EPSILON;
        boolean hasFill = module.fill.getFloatValue() > EPSILON && module.alpha.getFloatValue() > EPSILON;
        return hasGlow || hasFill;
    }

    private void ensureBuffers() {
        int w = mc.getWindow().getFramebufferWidth();
        int h = mc.getWindow().getFramebufferHeight();
        if (w == width && h == height && beforeBuffer != null && afterBuffer != null && maskBuffer != null) return;

        FramebufferUtils.safeDelete(beforeBuffer);
        FramebufferUtils.safeDelete(afterBuffer);
        FramebufferUtils.safeDelete(maskBuffer);
        bloomProcessor.dispose();

        beforeBuffer = new SimpleFramebuffer(w, h, true);
        afterBuffer = new SimpleFramebuffer(w, h, true);
        maskBuffer = new SimpleFramebuffer(w, h, true);
        width = w;
        height = h;
        configuredBeforeDepthTex = -1;
        configuredAfterDepthTex = -1;
    }

    private ShaderHands getModule() {
        try {
            return Endless.getInstance().getModuleStorage().get(ShaderHands.class);
        } catch (Exception e) {
            return null;
        }
    }
}
