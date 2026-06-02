package dev.endless.util.render.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.SimpleFramebuffer;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-pass Kawase blur/bloom processor.
 * Implements the down-up Kawase blur algorithm which produces high-quality
 * bloom effects with relatively few texture samples per pass.
 *
 * Usage:
 * <pre>
 *   KawaseBloomProcessor processor = new KawaseBloomProcessor(downKey, upKey);
 *   processor.ensureBuffers(width, height, iterations);
 *   int blurredTexture = processor.process(sourceTexture, iterations);
 *   processor.dispose(); // when done
 * </pre>
 */
public class KawaseBloomProcessor {
    
    private final ShaderProgramKey downShaderKey;
    private final ShaderProgramKey upShaderKey;
    private final List<Framebuffer> buffers = new ArrayList<>();
    private int sourceWidth = -1;
    private int sourceHeight = -1;
    
    public KawaseBloomProcessor(ShaderProgramKey downShaderKey, ShaderProgramKey upShaderKey) {
        this.downShaderKey = downShaderKey;
        this.upShaderKey = upShaderKey;
    }
    
    /**
     * Ensures bloom buffers are properly sized and allocated.
     * Should be called before each bloom pass.
     *
     * @param width      Source image width
     * @param height     Source image height
     * @param iterations Number of bloom iterations
     */
    public void ensureBuffers(int width, int height, int iterations) {
        // Recreate buffers if source size changed
        if (sourceWidth != width || sourceHeight != height) {
            disposeBuffers();
            sourceWidth = width;
            sourceHeight = height;
        }
        
        // Trim excess buffers
        while (buffers.size() > iterations) {
            int last = buffers.size() - 1;
            buffers.get(last).delete();
            buffers.remove(last);
        }
        
        // Allocate or resize buffers as needed
        for (int i = 0; i < iterations; i++) {
            int w = Math.max(2, width >> (i + 1));
            int h = Math.max(2, height >> (i + 1));
            
            if (i >= buffers.size()) {
                Framebuffer fb = new SimpleFramebuffer(w, h, false);
                FramebufferUtils.setLinearFiltering(fb);
                buffers.add(fb);
                continue;
            }
            
            Framebuffer fb = buffers.get(i);
            if (fb.textureWidth != w || fb.textureHeight != h) {
                fb.delete();
                fb = new SimpleFramebuffer(w, h, false);
                FramebufferUtils.setLinearFiltering(fb);
                buffers.set(i, fb);
            }
        }
    }
    
    /**
     * Processes a source texture through the Kawase blur pipeline.
     *
     * @param sourceTexture OpenGL texture ID to blur
     * @param iterations    Number of blur iterations (down + up passes)
     * @return The OpenGL texture ID of the blurred result
     */
    public int process(int sourceTexture, int iterations) {
        if (buffers.isEmpty()) return sourceTexture;
        
        int currentTexture = sourceTexture;
        
        // Down-sampling passes
        for (int i = 0; i < iterations; i++) {
            Framebuffer dst = buffers.get(i);
            FramebufferUtils.clearTransparent(dst);
            dst.beginWrite(true);
            
            ShaderProgram downShader = RenderSystem.setShader(downShaderKey);
            if (downShader == null) return currentTexture;
            
            RenderSystem.setShaderTexture(0, currentTexture);
            UniformHelper.setKawaseUniforms(downShader, dst.textureWidth, dst.textureHeight, 1.0f + i);
            FullscreenQuadRenderer.draw();
            
            currentTexture = dst.getColorAttachment();
        }
        
        // Up-sampling passes
        for (int i = iterations - 1; i >= 1; i--) {
            Framebuffer dst = buffers.get(i - 1);
            FramebufferUtils.clearTransparent(dst);
            dst.beginWrite(true);
            
            ShaderProgram upShader = RenderSystem.setShader(upShaderKey);
            if (upShader == null) return currentTexture;
            
            RenderSystem.setShaderTexture(0, currentTexture);
            UniformHelper.setKawaseUniforms(upShader, dst.textureWidth, dst.textureHeight, 1.0f + i);
            UniformHelper.setVec3(upShader, "color", 1.0f, 1.0f, 1.0f);
            FullscreenQuadRenderer.draw();
            
            currentTexture = dst.getColorAttachment();
        }
        
        net.minecraft.client.MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
        return currentTexture;
    }
    
    /**
     * Disposes all bloom buffers and resets the processor state.
     */
    public void dispose() {
        disposeBuffers();
        sourceWidth = -1;
        sourceHeight = -1;
    }
    
    private void disposeBuffers() {
        for (Framebuffer fb : buffers) {
            FramebufferUtils.safeDelete(fb);
        }
        buffers.clear();
    }
}
