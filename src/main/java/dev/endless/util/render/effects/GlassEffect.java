package dev.endless.util.render.effects;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import dev.endless.util.render.providers.ResourceProvider;

/**
 * Эффект жидкого стекла для всего HUD
 * Применяется как пост-процессинг эффект ко всему интерфейсу
 */
public class GlassEffect {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean enabled = false;
    
    private static final ShaderProgramKey LIQUID_GLASS_SHADER_KEY = new ShaderProgramKey(
        ResourceProvider.getShaderIdentifier("liquid_glass"),
        VertexFormats.POSITION_COLOR, 
        Defines.EMPTY
    );
    
    private static final Supplier<SimpleFramebuffer> HUD_FBO_SUPPLIER = Suppliers
        .memoize(() -> new SimpleFramebuffer(1920, 1080, false));
    
    private static boolean isCapturing = false;
    private static float mouseX = 0;
    private static float mouseY = 0;
    
    public static void setEnabled(boolean value) {
        enabled = value;
    }
    
    public static boolean isEnabled() {
        return enabled;
    }
    
    public static void updateMousePosition(double x, double y) {
        mouseX = (float) x;
        mouseY = (float) y;
    }
    
    /**
     * Начинает захват HUD в FBO
     */
    public static void beginCapture() {
        if (!enabled || isCapturing) return;
        
        SimpleFramebuffer hudFBO = HUD_FBO_SUPPLIER.get();
        Framebuffer main = mc.getFramebuffer();
        
        // Resize FBO if needed
        if (hudFBO.textureWidth != main.textureWidth || hudFBO.textureHeight != main.textureHeight) {
            hudFBO.resize(main.textureWidth, main.textureHeight);
        }
        
        // Clear and begin writing to HUD FBO
        hudFBO.clear();
        hudFBO.beginWrite(false);
        isCapturing = true;
    }
    
    /**
     * Заканчивает захват и применяет эффект
     */
    public static void endCaptureAndApply() {
        if (!enabled || !isCapturing) return;
        
        SimpleFramebuffer hudFBO = HUD_FBO_SUPPLIER.get();
        Framebuffer main = mc.getFramebuffer();
        
        // Switch back to main framebuffer
        main.beginWrite(false);
        isCapturing = false;
        
        // Apply liquid glass effect to entire HUD
        applyEffect(hudFBO);
    }
    
    /**
     * Применяет эффект жидкого стекла ко всему HUD
     */
    private static void applyEffect(SimpleFramebuffer hudFBO) {
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            
            // Set texture from HUD FBO
            RenderSystem.setShaderTexture(0, hudFBO.getColorAttachment());
            
            // Set shader and uniforms
            ShaderProgram shader = RenderSystem.setShader(LIQUID_GLASS_SHADER_KEY);
            
            if (shader == null) {
                // Fallback: just draw the HUD without effect
                drawFallback(hudFBO);
                return;
            }
            
            float screenWidth = mc.getWindow().getScaledWidth();
            float screenHeight = mc.getWindow().getScaledHeight();
            
            // Safely set uniforms with null checks
            if (shader.getUniform("Size") != null) {
                shader.getUniform("Size").set(screenWidth, screenHeight);
            }
            if (shader.getUniform("Radius") != null) {
                shader.getUniform("Radius").set(0f, 0f, 0f, 0f);
            }
            if (shader.getUniform("Smoothness") != null) {
                shader.getUniform("Smoothness").set(1.0f);
            }
            if (shader.getUniform("ScreenSize") != null) {
                shader.getUniform("ScreenSize").set(screenWidth, screenHeight);
            }
            if (shader.getUniform("MousePos") != null) {
                shader.getUniform("MousePos").set(mouseX, mouseY);
            }
            if (shader.getUniform("Time") != null) {
                shader.getUniform("Time").set((System.currentTimeMillis() % 100000) / 1000.0f);
            }
            
            // Render full screen quad
            MatrixStack matrices = new MatrixStack();
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            
            BufferBuilder builder = Tessellator.getInstance()
                    .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            
            int white = 0xFFFFFFFF;
            builder.vertex(matrix, 0, screenHeight, 0).color(white);
            builder.vertex(matrix, screenWidth, screenHeight, 0).color(white);
            builder.vertex(matrix, screenWidth, 0, 0).color(white);
            builder.vertex(matrix, 0, 0, 0).color(white);
            
            BufferRenderer.drawWithGlobalProgram(builder.end());
            
        } catch (Exception e) {
            // If shader fails, draw without effect
            drawFallback(hudFBO);
        } finally {
            // Cleanup
            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }
    
    /**
     * Fallback: просто рисует HUD без эффекта
     */
    private static void drawFallback(SimpleFramebuffer hudFBO) {
        // Just draw the FBO texture without shader effect
        hudFBO.draw(mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());
    }
}



